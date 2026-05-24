---
bootstrapped_at: 2026-05-24T16:12:00Z
starter_id: spring
starter_name: Spring Boot
project_name: loxley-cards
language_family: java
package_manager: maven
cwd_strategy: subdir-then-move
bootstrapper_confidence: verified
phase_3_status: ok
audit_command: "null"
---

## Hand-off

```yaml
starter_id: spring
package_manager: maven
project_name: loxley-cards
hints:
  language_family: java
  team_size: solo
  deployment_target: self-host
  ci_provider: github-actions
  ci_default_flow: auto-deploy-on-merge
  bootstrapper_confidence: verified
  path_taken: standard
  quality_override: false
  self_check_answers: null
  has_auth: true
  has_payments: false
  has_realtime: false
  has_ai: false
  has_background_jobs: false
```

### Why this stack

Solo developer shipping a Gwint-inspired card game as a split-stack monorepo (Java backend + React/TS frontend) within 3 weeks after-hours. Spring Boot is the recommended default for `(web-app, java)` and clears all four agent-friendly gates (typed, convention-based, popular in training data, well-documented). The multi-module Maven architecture with game engine, DB, and AI modules fits Spring's DI and module conventions naturally. Auth via magic link is the only technology-forcing feature; no payments, realtime, or AI integration in MVP scope. Deployment targets self-host (Docker on Hetzner VPS for backend, Cloudflare Pages for frontend declared separately). CI runs on GitHub Actions with auto-deploy-on-merge — standard for solo projects with shipping-first discipline.

## Pre-scaffold verification

| Signal        | Value                                           | Severity | Notes                                                    |
| ------------- | ----------------------------------------------- | -------- | -------------------------------------------------------- |
| npm package   | not run                                         | —        | starter is java-family; no npm CLI to check               |
| GitHub repo   | not run                                         | —        | docs_url (https://docs.spring.io/spring-boot/) is not a GitHub URL |

No recency signal available for this starter. Proceeded without warning.

## Scaffold log

**Resolved invocation**: `mkdir -p .bootstrap-scaffold && cd .bootstrap-scaffold && curl -s https://start.spring.io/starter.tgz -d dependencies=web,devtools -d type=maven-project -d javaVersion=21 -d groupId=com.example -d artifactId=loxley-cards | tar -xzf -`
**Strategy**: subdir-then-move
**Exit code**: 0
**Files moved**: 8 (`.gitattributes`, `.mvn/`, `HELP.md`, `mvnw`, `mvnw.cmd`, `pom.xml`, `src/`)
**Conflicts (.scaffold siblings)**: none
**.gitignore handling**: append-merged — existing `_poc` line preserved; Spring Boot patterns appended with `# from spring` separator
**.bootstrap-scaffold cleanup**: deleted

## Post-scaffold audit

**Tool**: skipped — no built-in audit tool for java
**Recommended external tool**: OWASP Dependency-Check or Snyk are common external choices for Java/Maven dependency auditing. Configure separately.

## Hints recorded but not acted on

| Hint                    | Value              |
| ----------------------- | ------------------ |
| bootstrapper_confidence | verified           |
| quality_override        | false              |
| path_taken              | standard           |
| self_check_answers      | null               |
| team_size               | solo               |
| deployment_target       | self-host          |
| ci_provider             | github-actions     |
| ci_default_flow         | auto-deploy-on-merge |
| has_auth                | true               |
| has_payments            | false              |
| has_realtime            | false              |
| has_ai                  | false              |
| has_background_jobs     | false              |

Additional hand-off sections (Frontend, Database, Backend module layout) were read but not acted on — bootstrapper scaffolds via the registry card's CLI only. The multi-module Maven layout and Vite + React frontend described in the hand-off require manual setup.

## Next steps

Next: a future skill will set up agent context (CLAUDE.md, AGENTS.md). For now, your project is scaffolded and verified — happy hacking.

Useful manual steps in the meantime:
- `git init` (if you have not already) to start your own repo history.
- Review any `.scaffold` siblings the conflict policy created and decide which version of each file to keep.
- Address audit findings per your project's risk tolerance — the full breakdown is in this log.
