<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Playable Game API

- **Plan**: context/changes/s-02-playable-game-api/plan.md
- **Scope**: All phases (1-4)
- **Date**: 2026-05-29
- **Verdict**: APPROVED (after fixes)
- **Findings**: 1 critical, 2 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS (after fixes) |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — getState reads mutable GameState without lock

- **Severity**: CRITICAL
- **Impact**: LOW
- **Dimension**: Safety & Quality
- **Location**: GameController.java:68-75
- **Detail**: makeMove acquires synchronized(lock) but getState reads the same mutable GameState without any lock. Concurrent GET during bot-loop could see torn state.
- **Fix**: Wrap getState body in synchronized(sessionStore.lock(gameId)).
- **Decision**: FIXED

### F2 — Bot loop has no iteration safety bound

- **Severity**: WARNING
- **Impact**: LOW
- **Dimension**: Safety & Quality
- **Location**: GameController.java:98-112
- **Detail**: driveBotMoves loops while bot's turn with no safety bound.
- **Fix**: Added safety counter with IllegalStateException after 200 iterations (surfaces engine regressions in logs instead of silent break).
- **Decision**: FIXED

### F3 — MoveRequest.kind() null causes unhandled NPE

- **Severity**: WARNING
- **Impact**: LOW
- **Dimension**: Safety & Quality
- **Location**: GameController.java:116
- **Detail**: switch(req.kind()) throws NPE if client sends null kind. Client gets 500.
- **Fix**: Added null guard: `if (req.kind() == null) throw new IllegalArgumentException("Move kind is required");`
- **Decision**: FIXED

### F4 — Frontend gameApi assumes JSON error body

- **Severity**: OBSERVATION
- **Impact**: LOW
- **Dimension**: Safety & Quality
- **Location**: frontend/src/api/gameApi.ts:10
- **Detail**: `await res.json()` throws if server returns non-JSON error body.
- **Fix**: Extracted `parseError()` helper with try/catch, fallback to res.statusText.
- **Decision**: FIXED

### F5 — WebConfig adds unplanned localhost:3000 origin

- **Severity**: OBSERVATION
- **Impact**: LOW
- **Dimension**: Plan Adherence
- **Location**: WebConfig.java:15
- **Detail**: Plan specified CORS for localhost:5173. Implementation adds localhost:3000. Harmless.
- **Decision**: SKIPPED — accepted as defensive addition.

### F6 — Test uses brittle substring parsing for gameId

- **Severity**: OBSERVATION
- **Impact**: LOW
- **Dimension**: Pattern Consistency
- **Location**: GameControllerTests.java:31-34
- **Detail**: Uses indexOf/substring to extract gameId. Conscious choice documented in comment.
- **Decision**: SKIPPED — accepted as conscious tradeoff.
