# CCCT-2843 — Theme adoption

Which Connect layouts resolve their colours through `ConnectTheme`, which still mix in raw values, and
what is blocking the rest.

| | |
|---|---|
| Fully on theme | 19 |
| Mixed | 8 |
| Raw only | 5 |
| Layouts with `tools:theme` | 26 |

## 1 — Where the theme applies

`?attr/` resolves against the *inflating activity's* theme, not the layout's filename. Only one
activity carries `ConnectTheme`.

| Activity | Theme | |
|---|---|---|
| `ConnectActivity` | `ConnectTheme` | roles available |
| `ConnectMessagingActivity` | `CommonTheme.NoActionBar` | no Connect roles |
| `StandardHomeActivity` | `CommonTheme.NoActionBar` | no Connect roles |
| `PersonalId*Activity` | `CommonTheme.NoActionBar` | out of scope |

**This rule caught us three times.** A `connect_` filename does not mean the layout is hosted by
`ConnectTheme`. `view_progress_job_card` and `item_progress_job_summary_visit` render on the CommCare
home screen, and `fragment_connect_message` under `ConnectMessagingActivity` — all three would have
silently resolved `?attr/colorPrimary` to `CommonTheme`'s `#5D70D2` instead of `#3A42C7`. Changes to
all three were reverted.

The same applies in Kotlin, harder: `MaterialColors.getColor(view, attr)` **throws** when the
attribute is missing, where `ContextCompat.getColor` could not. Six Robolectric fixtures had to move
from `CommonTheme` to `ConnectTheme` as a result.

## 2 — Adoption today

32 Connect layouts carry colour. Counting any raw `@color/` as "mixed".

| State | Layouts | Raw refs |
|---|---|---|
| Fully on theme | 19 | 0 |
| Mixed | 8 | 41 |
| Raw only | 5 | 19 |
| **Total** | **32** | **60** |

### Mixed — 8 layouts

| Layout | Raw | Blocking |
|---|---|---|
| `view_job_card` | 8 | Old delivery progress screen, left out of scope |
| `fragment_connect_delivery_visits_detail` | 8 | `connect_blue_color_10` ×4 — 10%-alpha primary, no container role · `transparent` ×4 |
| `fragment_connect_job_detail_bottom_sheet_dialog` | 7 | `white` ×5, `black`, `grey` — on a primary-coloured sheet; needs checking against Figma |
| `fragment_connect_delivery_progress` | 6 | Superseded by the dashboard, left out of scope |
| `connect_payment_item` | 5 | `connect_red`, `connect_blue_color_10`, `black`, `connect_grey` — no roles |
| `fragment_connect_delivery_payment` | 5 | `white` ×2, `connect_light_grey` ×2, `connect_blue_color` — on coloured cards |
| `fragment_connect_learn_modules_sheet` | 1 | `black` — Figma frame not checked yet |
| `fragment_connect_job_intro` | 1 | `orange` — already matches Figma, no role holds `#FFA500` |

### Raw only — 5 layouts

| Layout | Raw | Why |
|---|---|---|
| `view_progress_job_card` | 8 | CommCare home screen — wrong theme, must stay raw |
| `dialog_payment_confirmation` | 5 | Predates the redesign, out of scope |
| `fragment_connect_message` | 3 | `ConnectMessagingActivity` — wrong theme, must stay raw |
| `item_progress_job_summary_visit` | 2 | CommCare home screen — wrong theme, must stay raw |
| `connect_delivery_item` | 1 | `connect_yellowish_orange_color` — no role |

**31 of the 60 remaining refs are in layouts we agreed to leave alone** — `view_job_card`,
`fragment_connect_delivery_progress` and the four raw-only files outside the redesign. The in-scope
remainder is **29 refs**, and almost all of them need a role that does not exist.

## 3 — The widget-style layer

Connect's custom views read their colours from `styles.xml` rather than the layout, so those styles
had to move too.

| Style | On theme | Still raw | Raw items |
|---|---|---|---|
| `Widget.CommCare.SemiCircleProgressBar` | 4 | 0 | — |
| `Widget.CommCare.ConnectProgressCard` | 3 | 0 | — |
| `Widget.CommCare.ConnectSyncStatusCard` | 4 | 2 | `syncWarningBadgeColor` · `syncWarningIconColor` |
| `Widget.CommCare.ConnectSuccessFailureCard` | 2 | 2 | `failureBackgroundColor` · `failureAccentColor` |
| **Total** | **13** | **4** | |

All four remaining items are warning and failure states. `ConnectTheme` has `connectStatusPositive`
and `connectStatusNegative` but nothing for warning, and no failure container.

## 4 — Studio previews

`tools:theme="@style/ConnectTheme"` was added to **20 layouts** so the previews resolve `?attr/`
correctly. All 26 layouts that use `?attr/` now carry it. Design-time only — no runtime effect.

Five drawables also use `?attr/` — `bg_connect_cta_banner`, `ic_connect_arrow_diagonal`,
`ic_connect_delivery_app`, `ic_connect_learn_app`, `local_library`. `tools:theme` does not apply to
drawables; Studio resolves them against the theme of whichever layout previews them, so nothing is
needed there.
