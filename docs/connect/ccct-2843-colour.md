# CCCT-2843 — Colour

Raw colour references across the Connect redesign replaced with `ConnectTheme` attributes.

| | |
|---|---|
| Replacements | 107 |
| Exact hex match | 76 |
| Not exact | 31 |
| Files touched | 34 |
| Role definitions changed | 0 |

## 1 — What changed

Every change is at a call site. `themes.xml` and `colors.xml` are byte-identical to master — no role
was repointed, no hex edited, nothing added or removed.

| Where | Exact | Not exact | Total |
|---|---|---|---|
| Layout XML | 57 | 23 | 80 |
| Kotlin views | 9 | 5 | 14 |
| `styles.xml` widget styles | 10 | 3 | 13 |
| **Total** | **76** | **31** | **107** |

In Kotlin the swap is `ContextCompat.getColor(context, R.color.x)` →
`MaterialColors.getColor(view, R.attr.role)`. Note the latter **throws** when the attribute is
missing, so those views must be inflated under `ConnectTheme` — which is why several Robolectric
fixtures moved off `CommonTheme`.

## 2 — Exact matches (74 named below, 9 more in Kotlin and widget styles)

Same hex on both sides. No visual change.

| Raw colour | Hex | Role | n |
|---|---|---|---|
| `connect_text_color` | `#374151` | `colorOnSurface` | 10 |
| `connect_background_color` | `#F7F9FA` | `connectBackground` | 9 |
| `white` | `#FFFFFF` | `colorSurface` | 5 |
| `white` | `#FFFFFF` | `colorOnPrimary` | 4 |
| `connect_grey` | `#9A9A9A` | `connectOutline` | 6 |
| `connect_divider_color` | `#E5E7EB` | `connectOutlineVariant` | 6 |
| `connect_green` | `#16A085` | `connectStatusPositive` | 6 |
| `connect_light_grey` | `#E5E7EB` | `connectOutlineVariant` | 5 |
| `connect_blue_color` | `#3A42C7` | `colorPrimary` | 4 |
| `connect_dark_grey` | `#4B5563` | `connectOnSurfaceVariant` | 3 |
| `connect_light_green` | `#E6F5E5` | `connectStatusPositiveContainer` | 3 |
| `neon_blue` | `#3A42C7` | `colorPrimary` | 2 |
| `connect_subtext_color` | `#9CA3AF` | `connectOnSurfaceMuted` | 2 |

## 3 — Not exact — 31

| Raw colour | Was | Role | Now | n |
|---|---|---|---|---|
| `connect_dark_blue_color` | `#3942C7` | `colorPrimary` | `#3A42C7` | 22 |
| `connect_secondary_text` | `#6B7280` | `connectOnSurfaceVariant` | `#4B5563` | 1 |
| `connect_text_color` | `#374151` | `connectOnSurfaceEmphasis` | `#111827` | 3 |
| `connect_grey` | `#9A9A9A` | `connectOnSurfaceMuted` | `#9CA3AF` | 2 |
| `connect_dark_grey` | `#4B5563` | `colorOnSurface` | `#374151` | 3 |

**The 22 are one decision, not 22.** `connect_dark_blue_color` `#3942C7` is one hex digit off
`neon_blue` `#3A42C7`, the real brand blue, and appears in neither Figma file. Collapsing the two was
agreed up front. They split 14 layout XML · 5 Kotlin · 3 widget styles, and include five drawables
whose `fillColor` now follows the theme.

**The one to revisit is `connect_secondary_text`.** A single view —
`view_connect_learn_complete.xml` → `learn_complete_completed_on`, the "Completed on …" line. It had
no theme role, so it was folded into the nearest grey on contrast grounds: `#6B7280` is 4.83:1 on
white, `#4B5563` is 7.56:1. Since then the
[Opportunity Home Design](https://www.figma.com/design/4QLInPUv5wIfjfDGfrFESW/Opportunity-Home-Design-_-Ishwari?node-id=5650-39674)
frame has turned up specifying `#6B7280` for secondary text. That is evidence the colour deserves its
own role rather than being retired. The value still exists in `colors.xml` and is used by PersonalID.

The remaining 8 are the opportunity intro screen, checked layer by layer against the
[OpportunityHome_ New Opportunity](https://www.figma.com/design/4QLInPUv5wIfjfDGfrFESW/Opportunity-Home-Design-_-Ishwari?node-id=5619-34145)
frame. The job title and the two overview values were too light, the two overview labels too dark, and
the description and two section subtitles were one step off. Every corrected value landed on a role
that already existed.

Note this took colour from the *screen file*, where typography took it from the UI Kit — the two
disagree here, and every screen value maps to an existing role while the kit's `#6B7280` and `#92400E`
do not. Worth settling which file is authoritative for colour.

## 4 — Files touched

| Group | n | Files |
|---|---|---|
| Kotlin views | 6 | `ConnectInfoCard` · `ConnectInfoHalfCard` · `ConnectProgressCard` · `ConnectSyncStatusCard` · `SemiCircleProgressBar` · `ConnectCertificateBinder` |
| Drawables | 5 | `bg_connect_cta_banner` · `ic_connect_arrow_diagonal` · `ic_connect_delivery_app` · `ic_connect_learn_app` · `local_library` |
| Widget styles | 1 | `styles.xml` — SemiCircleProgressBar, ConnectProgressCard, ConnectSuccessFailureCard, ConnectSyncStatusCard |
| Layouts | 22 | the Connect redesign layouts, plus four the typography work skipped |

Colour scope is wider than typography scope. `fragment_connect_delivery_home`,
`fragment_connect_delivery_progress`, `fragment_connect_delivery_visits` and `view_job_card` had their
backgrounds and dividers moved onto the theme but were left out of the type work.

## 5 — Still raw — 43 references

Mostly values with no matching role. The 43 are **references, not files** — they sit in **10 layouts**,
several of them repeatedly.

| Colour | Hex | Refs | Where |
|---|---|---|---|
| `white` | `#FFFFFF` | 7 | `fragment_connect_job_detail_bottom_sheet_dialog` ×5 · `fragment_connect_delivery_payment` ×2 |
| `black` | `#000000` | 5 | `connect_payment_item` · `fragment_connect_delivery_progress` · `fragment_connect_job_detail_bottom_sheet_dialog` · `fragment_connect_learn_modules_sheet` · `view_job_card` — ×1 each |
| `connect_grey` | `#9A9A9A` | 5 | `connect_payment_item` ×2 · `view_job_card` ×2 · `fragment_connect_delivery_progress` ×1 |
| `connect_blue_color_10` | `#1A3A42C7` | 5 | `fragment_connect_delivery_visits_detail` ×4 · `connect_payment_item` ×1 |
| `connect_blue_color` | `#3A42C7` | 4 | `fragment_connect_delivery_progress` ×2 · `fragment_connect_delivery_payment` ×1 · `view_job_card` ×1 |
| `transparent` | — | 4 | `fragment_connect_delivery_visits_detail` ×4 |
| `connect_light_grey` | `#E5E7EB` | 2 | `fragment_connect_delivery_payment` ×2 |
| `connect_light_orange_color` | — | 2 | `fragment_connect_delivery_progress` ×1 · `view_job_card` ×1 |
| `connect_warning_color` | — | 2 | `fragment_connect_delivery_progress` ×1 · `view_job_card` ×1 |
| `connect_subtext_color` | `#9CA3AF` | 2 | `view_job_card` ×2 |
| `connect_yellowish_orange_color` | `#F3B34D` | 1 | `connect_delivery_item` |
| `connect_red` | `#EA6944` | 1 | `connect_payment_item` |
| `grey` | `#A3A3A3` | 1 | `fragment_connect_job_detail_bottom_sheet_dialog` |
| `orange` | `#FFA500` | 1 | `fragment_connect_job_intro` — already matches Figma |
| **Total** | | **43** | across 10 layouts |

Two layouts hold most of them: `view_job_card` (8) and `fragment_connect_delivery_visits_detail` (8).
Some values here — `connect_blue_color`, `connect_light_grey`, `connect_subtext_color` — *do* have
roles; they are still raw because those layouts were converted for typography only, or sit outside the
redesign.

### Warning and failure states — no roles defined

| Widget style item | Colour | Hex |
|---|---|---|
| `failureBackgroundColor` | `pale_coral` | `#FDECE8` |
| `failureAccentColor` | `connect_red` | `#EA6944` |
| `syncWarningBadgeColor` | `pale_buttery_cream` | `#FFF4CD` |
| `syncWarningIconColor` | `burnt_amber` | `#C67E13` |

`ConnectTheme` has `connectStatusPositive` and `connectStatusNegative` but nothing for warning, and no
failure container. Four roles would close this.

### Needs a code change first

`ConnectViewUtils.kt` holds four refs (`connect_blue_color`, `white`) in `@ColorRes` properties.
Converting them means changing the signatures to `@AttrRes`, so it was left for a follow-up rather
than folded into a resource sweep.
