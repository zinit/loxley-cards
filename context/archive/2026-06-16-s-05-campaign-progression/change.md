---
change_id: s-05-campaign-progression
title: Campaign progression with Phase 1 test coverage
status: archived
created: 2026-06-16
updated: 2026-06-16
archived_at: 2026-06-16
---

## Notes

Implement final MVP slice S-05 (campaign progression) from roadmap.md AND its tests per Phase 1 of test-plan.md.

Scope:
- Feature: persist stage outcome on win, unlock next stage, return user's progress on /campaign or /auth/me load.
- Tests (Phase 1 of test-plan.md): cover Risk #1 (campaign progress disappears between sessions) and Risk #2 (API contract drift).
  - Backend integration test: MockMvc + H2, "win stage N → fresh session → stage N+1 unlocked, progress restored from DB".
  - One E2E Playwright test (frontend has zero tests today): login → win first stage → logout → login → stage 2 unlocked. This is the "test from user perspective" required for the 10xDevs Builder certification.
- Risk #2 lighter touch: a shared DTO contract assertion (a single test that pins the campaign-progress JSON shape).
- Out of scope for this change: Risks #3, #4, #5, #6 (Phases 2 and 3 of test-plan.md, deferred).

Phase 4 of test-plan.md (CI gates / GitHub Actions) is a SEPARATE follow-up change — don't bundle it here.
