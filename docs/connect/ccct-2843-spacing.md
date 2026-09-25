# CCCT-2843 — Spacing, corners, shadows, icons

Shadows are done. Spacing is the largest block left in the ticket, and most of it is blocked on a
decision the Figma files can actually answer.

| | |
|---|---|
| Shadows converted | 18 |
| Spacing converted | 33 |
| Spacing left | 125 |
| Corners left | 8 |
| Icon sizes left | 21 |

## 1 — Shadows — done

18 raw `dp` elevations replaced with four dimens. No raw elevation remains in any Connect layout.

| Dimen | Value | Used for |
|---|---|---|
| `connect_card_elevation_low` | 2dp | resting cards |
| `connect_card_elevation_medium` | 5dp | raised cards |
| `connect_card_elevation_high` | 10dp | sheets and overlays |
| `connect_button_elevation` | 3dp | the CTA button |

**Three elevation dimens now overlap.** `connect_info_card_elevation` (0dp) and
`connect_progress_card_elevation` (1dp) predate the new scale and sit alongside it. One of the three
sets should go.

## 2 — Spacing — two scales, same numbers

`connect_space_xs…xl` is 4 / 8 / 12 / 16 / 24. `standard_spacer*` — the global CommCare scale — is
4 / 8 / 12 / 16 / 24 / 32. Identical values, two names.

| State | Refs | |
|---|---|---|
| On `connect_space_*` | 33 | converted so far |
| On `standard_spacer*` | 67 | right value, wrong scale |
| Raw dp | 58 | hardcoded |
| **Total** | **158** | |

### The 58 raw values

| Value | n | In the UI Kit? | What it would take |
|---|---|---|---|
| `10dp` | 24 | **yes** — most common `itemSpacing` | add to the scale |
| `6dp` | 8 | **yes** — avatar padding | add to the scale |
| `20dp` | 10 | no | snap to 16 or 24, or keep |
| `9dp` | 4 | no | snap to 8 |
| `15dp` | 2 | no | snap to 16 |
| `1dp` | 3 | optical nudges | leave |
| `40dp` | 2 | no — kit says 16 | CTA button padding, needs design |
| `27dp` | 1 | pairs with a −15dp overlap | leave |
| `5dp` · `11dp` · `3dp` | 3 | no | snap to 4 or 12 |
| `0dp` | 1 | a no-op `marginHorizontal` | delete |

**32 of the 58 are values Figma actually specifies.** The UI Kit's auto-layout uses 2, 4, 5, 6, 8, 10,
12, 16, 24 and 72 — so `10dp` and `6dp` are not stray numbers, our scale is simply missing them.
Extending the scale converts 32 refs with no visual change at all; only the remaining 26 need a
judgement.

### Where the 58 sit

| Layout | Raw | Values |
|---|---|---|
| `connect_payment_item` | 18 | 10 ×10 · 6 ×5 · 20 ×3 |
| `fragment_connect_job_detail_bottom_sheet_dialog` | 15 | 20 ×7 · 10 ×6 · 11 · 3 |
| `fragment_connect_delivery_payment` | 7 | 10 ×4 · 6 ×2 · 15 |
| `fragment_connect_delivery_visits_detail` | 5 | 9 ×4 · 15 |
| `connect_delivery_item` | 4 | 10 ×3 · 5 |
| `fragment_connect_job_intro` | 3 | 1 ×2 · 0 |
| `view_connect_cta_bar` | 3 | 40 ×2 · 27 |
| `fragment_connect_delivery_home` | 1 | 10 |
| `fragment_connect_delivery_visits` | 1 | 6 |
| `view_connect_learn_complete` | 1 | 1 |
| **10 layouts** | **58** | |

### The 67 on the wrong scale

Same values, different names — swapping them is mechanical and changes nothing visually.
`view_connect_learn_complete` was done first as the proof: 20 refs converted, and every value matched
the kit's Learn Card (padding 16, spacing 16, header gap 4).

| Current | Value | Refs | Becomes |
|---|---|---|---|
| `standard_spacer_double` | 16dp | 23 | `connect_space_lg` |
| `standard_spacer_large` | 24dp | 15 | `connect_space_xl` — but many are icon sizes |
| `standard_spacer` | 8dp | 11 | `connect_space_sm` |
| `standard_spacer_half` | 4dp | 11 | `connect_space_xs` |
| `standard_spacer_one_and_half` | 12dp | 5 | `connect_space_md` |
| `standard_spacer_extra_large` | 32dp | 2 | no equivalent — scale stops at 24 |
| **Total** | | **67** | |

## 3 — Corners — 8 raw, one duplicate dimen

`connect_radius_sm / card / md / lg / pill` = 5 / 8 / 12 / 16 / 24.

| Layout | Raw | Value | Nearest role |
|---|---|---|---|
| `fragment_connect_delivery_visits_detail` | 4 | 20dp | between `lg` 16 and `pill` 24 |
| `fragment_connect_delivery_payment` | 2 | 10dp | between `card` 8 and `md` 12 |
| `connect_payment_item` | 1 | 20dp | as above |
| `fragment_connect_job_detail_bottom_sheet_dialog` | 1 | 10dp | as above |

**`connect_info_card_corner_radius` = 8dp duplicates `connect_radius_card` = 8dp.** One should go.
Separately, neither 20dp nor 10dp is on the radius scale, and the UI Kit only ever uses 8, 16 and 20 —
so 20 is arguably a missing role and 10 is drift.

## 4 — Icons — no scale at all

21 raw `dp` ImageView sizes across the in-scope layouts, on nine different values.

| Size | n |
|---|---|
| 25dp | 5 |
| 32dp | 4 |
| 24dp | 3 |
| 13dp | 2 |
| 15dp | 2 |
| 30dp | 2 |
| 7dp · 16dp · 20dp | 3 |
| **Total** | **21** |

Existing icon dimens overlap too: `connect_badge_icon_size` and `connect_chip_icon_size` are both
20dp; `connect_sync_card_badge_size` and `connect_icon_circle_lg` are both 40dp. There is no 24dp icon
dimen, which is why `view_connect_learn_complete` and `view_connect_success_failure_card` still use
`standard_spacer_large` and a raw `24dp` for their icons.
