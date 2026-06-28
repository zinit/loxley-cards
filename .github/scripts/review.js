import { readFileSync } from "node:fs";
import { generateObject } from "ai";
import { anthropic } from "@ai-sdk/anthropic";
import { z } from "zod";
import { Octokit } from "@octokit/rest";

// --- Constants ---

const DIFF_SIZE_LIMIT = 50_000;
const MARKER = "<!-- ai-review-bot -->";

// --- Zod schema for structured output ---

const reviewSchema = z.object({
  criteria: z
    .array(
      z.object({
        name: z.string().describe("Criterion name"),
        score: z
          .number()
          .min(1)
          .max(10)
          .describe("Score from 1 (worst) to 10 (best)"),
        rationale: z
          .string()
          .describe("Brief justification for the score (1-3 sentences)"),
      })
    )
    .length(5)
    .describe(
      "Exactly 5 criteria: correctness, idiomatic, complexity, tests, security"
    ),
  summary: z
    .string()
    .describe(
      "2-3 sentence overall summary of the PR quality and key observations"
    ),
  verdict: z
    .enum(["APPROVED", "NEEDS_ATTENTION", "REJECTED"])
    .describe(
      "APPROVED if all scores >= 7, NEEDS_ATTENTION if any score 4-6, REJECTED if any score < 4"
    ),
});

// --- System prompt ---

const SYSTEM_PROMPT = `You are a senior code reviewer for a Gwint-inspired web card game (Spring Boot 4 / Java 21 backend, React/TypeScript frontend).

Evaluate the PR diff against these 5 criteria, each scored 1-10:

1. **correctness** — Logical correctness. Does the code do what it intends? Are there bugs, off-by-one errors, null dereferences, race conditions, or broken control flow?

2. **idiomatic** — Idiomatic code. Does it follow Java/Spring conventions (DI, annotations, REST patterns) and React/TS conventions (hooks, component structure, type safety)? Does it match the existing codebase style?

3. **complexity** — Complexity & readability. Is the code unnecessarily complex? Could it be simplified without losing functionality? Are names clear and functions appropriately sized?

4. **tests** — Test coverage. Are there tests for the changed code? Do they cover the happy path AND meaningful edge cases? Are existing tests updated if behavior changed?

5. **security** — Security (OWASP top 10). Are there injection risks, broken auth, exposed secrets, insecure defaults, or missing input validation?

Verdict rules:
- APPROVED: every criterion scores >= 7
- NEEDS_ATTENTION: at least one criterion scores 4-6 (none below 4)
- REJECTED: at least one criterion scores < 4

Be concise in rationales. Focus on what matters most in each criterion.`;

// --- Main ---

async function main() {
  const {
    PR_DIFF_FILE,
    PR_TITLE = "",
    PR_BODY = "",
    PR_NUMBER,
    GITHUB_REPOSITORY,
    GITHUB_TOKEN,
  } = process.env;

  if (!PR_DIFF_FILE || !PR_NUMBER || !GITHUB_REPOSITORY || !GITHUB_TOKEN) {
    console.error(
      "Missing required env vars: PR_DIFF_FILE, PR_NUMBER, GITHUB_REPOSITORY, GITHUB_TOKEN"
    );
    process.exit(0);
  }

  const [owner, repo] = GITHUB_REPOSITORY.split("/");
  const prNumber = Number(PR_NUMBER);
  const octokit = new Octokit({ auth: GITHUB_TOKEN });

  // Read diff from temp file
  let diff;
  try {
    diff = readFileSync(PR_DIFF_FILE, "utf-8");
  } catch (err) {
    console.error(`Failed to read diff file: ${err.message}`);
    process.exit(0);
  }

  // Diff size guard
  if (diff.length > DIFF_SIZE_LIMIT) {
    const body = [
      MARKER,
      `## ℹ️ Diff too large for automated AI review`,
      "",
      `Diff size: ${diff.length.toLocaleString()} chars (limit: ${DIFF_SIZE_LIMIT.toLocaleString()}).`,
      "Manual review required.",
      "",
      "---",
      "🤖 AI Code Review (Claude Sonnet 4.6, temperature=0). Ocena wygenerowana przez LLM, skala 1-10 jest wskazówką.",
    ].join("\n");

    await upsertComment(octokit, owner, repo, prNumber, body);
    console.log("Diff too large — posted info comment, skipping review.");
    return;
  }

  // Call Claude via Vercel AI SDK
  let result;
  try {
    const prompt = [
      `# PR: ${PR_TITLE}`,
      "",
      PR_BODY ? `## Description\n${PR_BODY}` : "",
      "",
      "## Diff",
      "```diff",
      diff,
      "```",
    ]
      .filter(Boolean)
      .join("\n");

    const response = await generateObject({
      model: anthropic("claude-sonnet-4-6"),
      schema: reviewSchema,
      system: SYSTEM_PROMPT,
      prompt,
      temperature: 0,
      maxOutputTokens: 2000,
      maxRetries: 1,
    });

    result = response.object;
  } catch (err) {
    console.error(`AI review failed: ${err.message}`);
    process.exit(0);
  }

  // Format and post comment
  const body = formatComment(result);
  await upsertComment(octokit, owner, repo, prNumber, body);
  console.log(`Review posted: ${result.verdict}`);
}

// --- Formatting ---

function formatComment(result) {
  const { criteria, summary, verdict } = result;

  const emoji =
    verdict === "APPROVED"
      ? "🟢"
      : verdict === "NEEDS_ATTENTION"
        ? "⚠️"
        : "🔴";

  // Key flags: criteria with score < 7
  const flags = criteria
    .filter((c) => c.score < 7)
    .map((c) => `${c.name}: ${c.score}/10`)
    .join(", ");

  const headline = flags
    ? `## ${emoji} ${verdict} — ${flags}`
    : `## ${emoji} ${verdict}`;

  // Table
  const table = [
    "| Criterion | Score | Rationale |",
    "|-----------|-------|-----------|",
    ...criteria.map(
      (c) => `| ${c.name} | ${c.score}/10 | ${c.rationale} |`
    ),
  ].join("\n");

  const reviewBody = `${table}\n\n**Summary:** ${summary}`;

  // Conditional collapsibility
  const bodySection =
    verdict === "APPROVED"
      ? `<details><summary>Full review</summary>\n\n${reviewBody}\n\n</details>`
      : reviewBody;

  const footer =
    "---\n🤖 AI Code Review (Claude Sonnet 4.6, temperature=0). Ocena wygenerowana przez LLM, skala 1-10 jest wskazówką.";

  return [MARKER, headline, "", bodySection, "", footer].join("\n");
}

// --- GitHub comment upsert ---

async function upsertComment(octokit, owner, repo, prNumber, body) {
  try {
    const { data: comments } = await octokit.rest.issues.listComments({
      owner,
      repo,
      issue_number: prNumber,
      per_page: 100,
    });

    const existing = comments.find(
      (c) => c.body && c.body.startsWith(MARKER)
    );

    if (existing) {
      await octokit.rest.issues.updateComment({
        owner,
        repo,
        comment_id: existing.id,
        body,
      });
      console.log(`Updated existing comment #${existing.id}`);
    } else {
      await octokit.rest.issues.createComment({
        owner,
        repo,
        issue_number: prNumber,
        body,
      });
      console.log("Created new comment");
    }
  } catch (err) {
    console.error(`Failed to post comment: ${err.message}`);
  }
}

main();
