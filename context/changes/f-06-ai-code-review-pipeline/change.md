---
change_id: f-06-ai-code-review-pipeline
title: AI Code Review Pipeline
status: implementing
created: 2026-06-28
updated: 2026-06-28
---

# f-06-ai-code-review-pipeline

Nowy GitHub Actions workflow (ai-review.yml) + composable agent (Vercel AI SDK 6 + Zod + Claude Sonnet 4.6) oceniający PR diff wg 5-wymiarowej rubryki i postujący strukturalny komentarz na PR. Informacyjny gate (nie blokujący merge). Zero zmian w istniejącym ci.yml i runtime backend/frontend.
