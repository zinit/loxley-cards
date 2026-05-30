---
change_id: s-03b-polished-game-ui-improvements
title: Polished game UI improvements
status: archived
created: 2026-05-30
updated: 2026-05-30
archived_at: 2026-05-30
---

## Notes

<!-- Free-form notes for this change: links, ad-hoc context, decisions that don't belong in research/frame/plan. -->

### Mid-flow addition during /10x-plan (2026-05-30)

**Issue #8 — Round resolution bug (diagnose-first, like horn bug):**

Daniel zaobserwował podczas playtestu: wygrał rundę 1, w rundzie 2 kliknął PASS, dostał komunikat match-end mówiący że przegrał obie rundy. Nonsens — best-of-3 z 1-1 nie kończy gry, plus round 1 był wygrany. Możliwe źródła:
- Backend: round-end / match-end logic nieprawidłowo liczy round 2 jako loss dla obu stron, albo pas → bot wygrywa round 2 → ale match-end odpala mimo 1-1 (powinno iść do round 3)
- Backend: scoring round 1 był wrong (UI pokazało wygraną ale `roundHistory` ma loss)
- Frontend reducer: `detectMatchEnd` źle interpretuje `roundHistory` (false-positive na match-end)
- Frontend UI: match-end overlay copy mówi "wszystkie przegrałeś" zamiast "1-2 defeat" — bug typograficzny, scoring poprawny

Diagnoza: Network response w momencie kliknięcia PASS w rundzie 2 → sprawdzić `roundHistory[]` (czy round 1 ma `winner: "player"`), `matchEnded` (czy true), `matchWinner` (kto), `roundResult` (kto wygrał round 2). Dopiero potem fix.

**Priorytet:** wysoki, na równi z horn bug (#1) — oba to gameplay-correctness bugi, oba diagnose-first. Razem te dwa MUSZĄ być fixed w tym slice'em.

### Mid-flow addition pre-commit (2026-05-30)

**Issue #9 — Desktop-only guard (PRD §Non-Goals gap):**

PRD explicit mówi "Brak mobile / responsive — desktop-only" ale do tej pory brak żadnego guard'a — mobile user dostawał normalny board layout który się rozsypuje na małym viewporcie. Realny gap dla shipowania.

Fix (CSS-only, ~10 linii HTML + ~55 linii CSS): warning div w `frontend/index.html` jako sibling do `#root`, w `index.css` media query `@media (max-width: 1023px)` ukrywa `#root` i pokazuje warning z komunikatem "This game is designed for desktop only. Open this page on a larger screen (≥ 1024px wide) to play." Styling spójny z Sherwood theme (Cinzel font, cream + gold border, dark wood background). Zero JS, zero React state, automatic resize handling, zero FOUC.

Próg 1024px wybrany jako typowy laptop landscape minimum. Wąskie okna na desktopie to wybór usera (osobne issue layout responsiveness dla wide-short viewportów deferred do przyszłego polish slice'a — patrz Lesson w roadmap Done entry).
