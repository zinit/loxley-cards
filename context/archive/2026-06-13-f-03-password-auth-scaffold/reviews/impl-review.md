<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Password Auth Scaffold

- **Plan**: `context/changes/f-03-password-auth-scaffold/plan.md`
- **Scope**: All phases (1-4)
- **Date**: 2026-06-13
- **Verdict**: APPROVED
- **Findings**: 0 critical, 2 warnings, 0 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS (after fixes) |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Drift Summary

20 files checked: 19 MATCH, 1 MINOR DRIFT (GlobalExceptionHandler — pre-existing IllegalArgumentException handler reused for auth validation, beneficial). Zero substantive drift from plan.

## Findings

### F1 — Registration race: concurrent signups produce 500

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: AuthController.java:46-53
- **Detail**: findByUsername → save TOCTOU race. Second concurrent save throws DataIntegrityViolationException → unhandled 500 instead of 409.
- **Fix**: Added DataIntegrityViolationException handler to GlobalExceptionHandler mapping to 409 USERNAME_TAKEN.
- **Decision**: FIXED

### F2 — No minimum key length validation on JWT secret

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: JwtTokenProvider.java:21
- **Detail**: Constructor base64-decodes secret without checking length. Short key degrades HS256 security.
- **Fix**: Added startup check: if decoded key < 32 bytes, throw IllegalStateException with clear message.
- **Decision**: FIXED

## Dismissed Agent Findings (documented plan decisions)

- All endpoints permitAll → explicitly planned ("What We're NOT Doing: No game endpoint protection")
- CORS missing production origin → mirrors pre-existing WebConfig; deploy concern, not F-03 scope
- JWT cookie missing Secure flag → plan explicitly says "Does NOT set Secure flag (local dev is HTTP)"
- CSRF disabled → plan explicitly says "No CSRF tokens — SameSite=Lax sufficient for MVP"
- GameControllerTests addFilters=false debt → planned for S-04
- @EnableScheduling "premature" → GameSessionStore @Scheduled cleanup already uses it
