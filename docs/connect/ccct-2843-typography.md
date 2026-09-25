# CCCT-2843 — Typography

Nine text styles derived from the Figma design system, applied to every Connect redesign layout.
Replaces every inline size and hardcoded bold in those layouts.

| | |
|---|---|
| Styles created | 9 |
| Layouts changed | 24 |
| Styles applied | 102 |
| Bolds removed | 33 |
| Layouts reverted | 1 |

## 1 · 2 — Sources

Both files were read through the Figma REST API rather than from exported images, so every number
below is the published value, not a measurement.

| File | Key | What it gave us |
|---|---|---|
| [Design System Mobile](https://www.figma.com/design/VGPQtE7EbAeenAQYC2SSM1/Design-System-Mobile) | `VGPQtE7Eb…` | The type scale — size, weight, line height, letter-spacing for every style |
| [UI Kit](https://www.figma.com/design/mqfizPFcAlJMDlAH9VEIGm/UI-Kit) | `mqfizPFcAl…` | Which style each component uses, so views could be mapped by component rather than by guess |

An earlier export was unusable: the SVGs originally supplied had "Outline text" enabled, so every
label had become vector paths — no readable text and no numbers. The API route is what unblocked
this.

## 3 — What the design system defines

**30 published text styles** — six families × five sizes. Verified against the file's own Typography
documentation page: all 30 match on size, line height, letter-spacing and weight, zero mismatches.

| Family | Weight | Sizes | Android weight |
|---|---|---|---|
| Display | Bold 700 | 36 → 72 | `sans-serif` + bold |
| Headline | SemiBold 600 | 20 → 36 | **no system weight below API 31** |
| Title | Medium 500 | 12 → 22 | `sans-serif-medium` |
| Body | Regular 400 | 11 → 18 | `sans-serif` |
| Label | Medium 500 | 9 → 16 | `sans-serif-medium` |
| Button | Medium 500 | 11 → 18 | `sans-serif-medium` |
| _Caption_ | Regular 400 | 9 → 14 | variables only — no published style |

The Typography variables collection holds 78 variables in a single mode: 3 font families, 5 weights,
and 35 size/line-height pairs. Caption accounts for the 5 pairs that exist as variables but were
never published as styles, which is why the API returns 30 and not 35.

## 4 — What the UI Kit actually uses

The UI Kit has **30 pages** — 21 component pages grouped into Cards, Navigation, Pop Over and List,
plus an index, four group headers and four separators. Its components use **12 of the 30 styles**.

Counting only text inside the component artboards; the kit's own documentation prose and
variant-name chips are excluded, and they are the bulk of the file's 1,746 text layers.

| Style | Uses | Style | Uses |
|---|---|---|---|
| `Label/s` | 95 | `Title/s` | 13 |
| `Label/m` | 79 | `Button/s` | 12 |
| `Body/s` | 63 | `Label/lg` | 10 |
| `Body/lg` | 52 | `Button/m` | 5 |
| `Body/m` | 48 | `Body/xs` | 1 |
| `Title/xl` | 34 | — 18 unused — | 0 |
| `Title/m` | 28 | | |

Those 12 names collapse to **9 distinct value sets**, because three pairs are byte-identical:
`Button/s` = `Label/m`, and `Button/m` = `Label/lg` = `Title/s`.

## 5 — The nine styles

Two layers in `app/res/values/styles.xml`. `TextAppearance.Connect.*` carries size, weight and
letter-spacing; `TextStyle.Connect.*` wraps it and adds line height, and is what layouts apply via
`style=`.

| Style | Figma | Size | Weight | Line height | Tracking | Role |
|---|---|---|---|---|---|---|
| `TitleXl` | `Title/xl` | 22sp | medium | 28sp | 0 | metric, amount, screen title |
| `TitleM` | `Title/m` | 16sp | medium | 24sp | 0.0094em | section and sheet heading |
| `TitleS` | `Title/s` | 14sp | medium | 20sp | 0.0071em | card heading, CTA title |
| `LabelM` | `Label/m` | 12sp | medium | 16sp | 0.0417em | chip, badge, status, button |
| `LabelS` | `Label/s` | 11sp | medium | 16sp | 0.0455em | field label above a value |
| `BodyLg` | `Body/lg` | 16sp | regular | 24sp | 0.0313em | the value under a `LabelS` |
| `BodyM` | `Body/m` | 14sp | regular | 20sp | 0.0179em | descriptive paragraph |
| `BodyS` | `Body/s` | 12sp | regular | 16sp | 0.0333em | secondary line, date, subtitle |
| `BodyXs` | `Body/xs` | 11sp | regular | 16sp | 0.0364em | smallest metadata |

### Three decisions worth knowing

**Medium weight needs no bundled font.** `sans-serif-medium` is Roboto Medium and has been available
since API 21, so w500 works at `minSdk 23` with no `textFontWeight` and no font file.

**Line height cannot live in a TextAppearance.** `lineHeight` is not in the `TextAppearance`
styleable on any Android version. It is a view attribute, which is why there is a second layer. The
styles use AppCompat's `app:lineHeight`, which computes the spacing from the real font at runtime —
so a future font change needs no recalculation.

**Sizes reference the existing dimens.** All five sizes already existed as `connect_text_*` and
matched exactly, so there is still one scale rather than two.

## 6 — Layouts changed

24 files, 102 style applications. **specified** means the mapping came from a UI Kit component;
**size-matched** means the component does not exist and the style was chosen by matching the current
size to the scale.

### Components — 11 files

| Layout | UI Kit component | n | Basis |
|---|---|---|---|
| `view_connect_learn_progress` | Learn Module · header size-matched | 8 | specified |
| `view_connect_learn_certificate` | Learn Card — certificate block | 5 | specified |
| `view_connect_learn_complete` | Learn Card — outer | 5 | specified |
| `view_connect_progress_card` | Progress Card · heading from the screen design | 6 | specified |
| `view_connect_cta_bar` | Bottom Bar | 3 | specified |
| `view_connect_info_half_card` | Delivery KPI Card | 3 | specified |
| `view_connect_info_card` | Opportunity Detail | 3 | specified |
| `view_connect_sync_status_card` | Sync Card | 2 | specified |
| `view_connect_task_card` | Task | 2 | specified |
| `view_connect_tab_label` | Action Bar → Tabs | 0 | reverted — see below |
| `view_connect_success_failure_card` | none | 1 | size-matched |

### Screens — 8 files

| Layout | UI Kit component | n | Basis |
|---|---|---|---|
| `fragment_connect_job_intro` | Opportunity Intro | 11 | specified |
| `fragment_connect_delivery_more` | Re Visit Learning | 9 | specified |
| `fragment_connect_job_detail_bottom_sheet_dialog` | Bottom Sheet · title only | 8 | size-matched |
| `fragment_connect_delivery_dashboard` | none | 5 | size-matched |
| `fragment_connect_delivery_payment` | Payment Overview | 4 | specified |
| `fragment_connect_delivery_visits_detail` | none | 4 | size-matched |
| `fragment_connect_learn_modules_sheet` | Bottom Sheet | 2 | specified |
| `fragment_connect_jobs_list` | none | 1 | size-matched |

### List items — 6 files

| Layout | UI Kit component | n | Basis |
|---|---|---|---|
| `connect_delivery_progress_item` | Visit Types · header only | 6 | size-matched |
| `connect_payment_item` | Payment Detail | 5 | specified |
| `connect_delivery_item` | Visit Status | 3 | specified |
| `connect_job_list_item` | Opportunity | 3 | specified |
| `item_connect_learn_module` | Learn Module | 2 | specified |
| `connect_job_list_item_section_header` | [Opportunity Home screen design](https://www.figma.com/design/4QLInPUv5wIfjfDGfrFESW/Opportunity-Home-Design-_-Ishwari?node-id=5588-27172) | 1 | specified |

## 7 — Discrepancies found

### Styles not in the design system
_Opportunity · Visit Status · Action Bar · Visit Types · Progress Card_

Some layers in these [UI Kit](https://www.figma.com/design/mqfizPFcAlJMDlAH9VEIGm/UI-Kit?node-id=241-3359)
components reference text styles that don't exist in the
[design system](https://www.figma.com/design/VGPQtE7EbAeenAQYC2SSM1/Design-System-Mobile?node-id=34-5)
— `body/lg`, `body/sm`, `label/md`, `label/medium`, `label/sm`, `label/small`. Verified by style key:
none of the design system's 30 published styles matches them.

The values are identical to design system styles (e.g. `body/lg` = `Body/lg`, 16px/w400), so they
appear to come from an older or separate library. The practical effect is that these layers won't
pick up changes if the design system's type scale is updated.

**Handling:** mapped to the matching design system style, since the values are the same.

### CTA bar button
`ConnectCtaBar · cta_button`

The button had no size of its own — 14sp came from Material's default button appearance, bold at
0.0893em tracking.

Two separate Figma files specify this bar, and they disagree on the button:

1. **UI Kit** — component [Bottom Bar](https://www.figma.com/design/mqfizPFcAlJMDlAH9VEIGm/UI-Kit?node-id=241-3359). Button is `Label/m`, 12px medium.
2. **Opportunity Home Design** — instances [Bottom Sticky Button (delivery)](https://www.figma.com/design/4QLInPUv5wIfjfDGfrFESW/Opportunity-Home-Design-_-Ishwari?node-id=5650-40046) and [Bottom Sticky Button (learn)](https://www.figma.com/design/4QLInPUv5wIfjfDGfrFESW/Opportunity-Home-Design-_-Ishwari?node-id=5650-39729). Button is `body/medium`, 14px regular, in both.

The bar's title and subtitle match across the two files; only the button differs.

**Handling:** took the UI Kit as the source of truth and applied `LabelM`, so the button is now 12sp
medium. **Worth a designer call** — 12sp is small for the primary action on the learn and delivery
screens, the screen design says 14sp, and the design system does define `Button/m` at 14sp; the
Bottom Bar simply does not use it. If 14sp regular is correct the style becomes `BodyM`.

### Same disagreement elsewhere
`ConnectProgressCard · ConnectTaskCard · ConnectInfoCard`

The CTA bar is not the only place the two files disagree, and the pattern is the same each time —
the UI Kit uses medium, the screen design uses regular.

- **Progress Card caption** (`progress_card_bar_caption`) — UI Kit `Label/s` 11px medium; [screen design](https://www.figma.com/design/4QLInPUv5wIfjfDGfrFESW/Opportunity-Home-Design-_-Ishwari?node-id=5650-39394) `label/small` 11px regular.
- **Task card expiry** (`task_card_expiry`) — UI Kit `Label/m` 12px medium; [screen design](https://www.figma.com/design/4QLInPUv5wIfjfDGfrFESW/Opportunity-Home-Design-_-Ishwari?node-id=6001-24129) 11px regular.
- **Full-width info card support text** (`info_card_subtitle_text`) — UI Kit `Label/s` 11px medium; [screen design](https://www.figma.com/design/4QLInPUv5wIfjfDGfrFESW/Opportunity-Home-Design-_-Ishwari?node-id=5650-40217) 11px regular.

**Handling:** took the UI Kit as the source of truth in all three, as with the CTA button. **One
designer call settles all four** — if the screen design is correct, the caption, the task expiry and
the info card support text all become `BodyXs`.

### Semicircle progress card missing from the UI Kit
`ConnectProgressCard · progress_card_title`

The card has a second mode with a semicircle gauge and a heading above it. The UI Kit's Progress Card
component has neither — no heading layer and no semicircle variant — so there was nothing to map the
heading to.

**Handling:** taken from the [Opportunity Home Design](https://www.figma.com/design/4QLInPUv5wIfjfDGfrFESW/Opportunity-Home-Design-_-Ishwari?node-id=6001-23893)
frame instead: "Delivery Progress" is 14px w500, line height 20, giving `TitleS`. A second instance on
the same file agrees. Worth adding this variant to the kit.

### List section header missing from the UI Kit
`ConnectOpportunityListAdapter · tv_section_header`

The opportunity list is broken into "In Progress" and "Completed" sections. The UI Kit's `Opportunity`
component covers only the card itself and has no section header, so there was nothing to map it to.
The view was also using `@dimen/text_large` = 21sp, a global CommCare value that is on no Connect or
Figma scale.

**Handling:** taken from the [Opportunity Home Design](https://www.figma.com/design/4QLInPUv5wIfjfDGfrFESW/Opportunity-Home-Design-_-Ishwari?node-id=5588-27172)
frame instead: "In Progress" is 16px w500, line height 24, giving `TitleM`. The 21sp was an
implementation error. Worth adding the header to the kit.

### Screens have no styles attached
_Opportunity Home Design _ Ishwari_

The Figma screens used for development don't have text styles attached to many layers. In the
[OpportunityHome_ Delivery](https://www.figma.com/design/4QLInPUv5wIfjfDGfrFESW/Opportunity-Home-Design-_-Ishwari?node-id=5650-39380)
frame, only 7 of 40 text layers are linked to a published style — the other 33 have size, weight and
line height set directly on the layer.

This shows up as drift: the Progress Card's "2 of 5" is 24px, a size no style defines, and the same
file draws the tabs at 14/16 on
[one node](https://www.figma.com/design/4QLInPUv5wIfjfDGfrFESW/Opportunity-Home-Design-_-Ishwari?node-id=5650-39425)
and 14/20 on
[another](https://www.figma.com/design/4QLInPUv5wIfjfDGfrFESW/Opportunity-Home-Design-_-Ishwari?node-id=6001-23964).

**Handling:** a developer can't tell which style a layer is meant to use when nothing is attached, so
the file can't be trusted as a source on its own. Mappings were taken from the UI Kit. This file was
used only where the UI Kit has no component.

### Tab label reverted — text truncates
`view_connect_tab_label · @android:id/text1`

The UI Kit's `Tabs` specifies `Body/m` — 14px regular, line height 20 — and the
[screen design](https://www.figma.com/design/4QLInPUv5wIfjfDGfrFESW/Opportunity-Home-Design-_-Ishwari?node-id=5650-39445)
agrees on size and weight but sets letter-spacing to 0 where `Body/m` has 0.25px.

Applying the style made "Dashboard" truncate to an ellipsis on device. The tab strip is
`tabMode="fixed"` with `tabGravity="fill"`, so each tab is locked to a quarter of the width and the
longest label has no slack. Removing the letter-spacing alone did not fix it; only dropping the style
entirely did.

**Handling:** left on the original `@dimen/connect_text_tab` (14sp) — the same size the kit specifies,
just not applied through the scale. The one change kept is `TabTextStyle` losing its hardcoded bold,
since both Figma sources say regular. Unresolved: which of line height or letter-spacing caused the
overflow.

## 8 — Omitted

### Files left alone

| Layout | Inline | Reason |
|---|---|---|
| `view_progress_job_card` | 8 | Included by `home_screen.xml` — the CommCare home screen, not part of the redesign |
| `dialog_payment_confirmation` | 5 | Predates the redesign, no kit counterpart |
| `fragment_connect_delivery_progress` | 3 | Superseded by the dashboard |
| `item_progress_job_summary_visit` | 2 | Same — inflated on the CommCare home screen |
| `fragment_connect_message` | 1 | `ConnectMessagingActivity`, not redesigned |
| PersonalID layouts | — | Out of scope, separate theme |
| `nav_drawer_*` | — | The Connect side menu. Left unchanged — the UI Kit's `Menu Item` specifies `Label/m` (12px medium) while the [screen design](https://www.figma.com/design/4QLInPUv5wIfjfDGfrFESW/Opportunity-Home-Design-_-Ishwari?node-id=5940-21847) and the current implementation are both 14px regular. Not included pending the same designer call as the other weight disagreements. |

No views were left behind inside the converted files — every inline size and hardcoded bold is now on
the scale.
