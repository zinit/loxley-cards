---
change_id: s-01-static-ui-prototype
title: Static UI prototype
status: archived
created: 2026-05-28
updated: 2026-05-28
archived_at: 2026-05-28T17:00:00Z
---

## Notes

Shipped: `frontend/src/` — CampaignMap (fullscreen bitmap Sherwood + 10 stump waypoints, fit-to-viewport scaling) + GameBoard (monolithic component z entrance/exit animation, plansza lustro z 6 rzędami, meta-strip symmetry, ręka 5 kart, pieniek-blat jako tło). 28-card FINAL_DECK z prawdziwymi obrazami Robin Hood theme + runtime randomization (15 unique per page-load). WebP asset pipeline (23MB+75MB → 4.6MB+3.9MB raw, ~8MB total bundled). Plus iteracje visual polish (animation sequencing, label visibility, power circle font, row icon glow, back button miniaturization).

Impl-review APPROVED — 3 warnings fixed (timeout cleanup, plan text contain→cover, dead cards.ts removed), 2 observations skipped (Card a11y → S-02, mock row affinity → S-02/S-03).

Lesson reinforced (F-01): docs zsynchronizowane do shipped reality PRZED impl-review (zamiast po) daje cleaner review signal — skill znalazł realne issues w kodzie zamiast 20 ogromnych planning-vs-port driftów.
