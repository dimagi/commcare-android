# Camera Reticle for MUAC Photo Capture in CommCare Forms

- **Jira:** [CCCT-2532](https://dimagi.atlassian.net/browse/CCCT-2532)
- **Requirement doc:** [Camera Reticle for MUAC Photo Capture in CommCare Forms](https://docs.google.com/document/d/1n0g37HhqfSxrmKoYi-4aBd3PLG4vYvotm-OlpblL1ew/edit)
- **Status:** Design
- **Date:** 2026-06-24

## Overview

Add an optional rectangular reticle overlay to the CommCare in-form camera during
image-capture (`upload`) questions. The reticle helps FLWs consistently center and frame
MUAC (Mid-Upper Arm Circumference) measurements when photographing a child's arm + tape.
Its size is **responsive** — a centered rectangle inset by an equal margin on all sides,
derived from the camera-view dimensions (like the existing PersonalID oval), not a fixed
pixel size.

Unlike the PersonalID photo flow, this overlay is a **visual alignment guide only**:

- It does **not** auto-capture — the user presses a shutter button.
- It does **not** crop the photo to the reticle — the full frame is saved.
- It is **not** drawn into the captured image — the reticle is a preview overlay only.
- Its size is **not** form-configurable — a single centered rectangle whose dimensions
  scale with the camera view.

The work reuses the existing PersonalID capture stack (`MicroImageActivity` +
`FaceCaptureView`, CameraX) by adding a second capture mode, rather than building a new
camera screen.

## Requirements (from the requirement doc)

- Camera view displays a rectangular reticle overlay when the question is configured to show one.
- Reticle is medium-sized, centered, and sized to frame a MUAC measurement (arm + tape).
- Reticle is visible during preview and is excluded from the captured image.
- Feature is opt-in and configurable at the question level in the form definition.
- Reticle rendering must not introduce perceptible latency to the camera preview.
- Overlay must be visible across typical lighting conditions (light/dark backgrounds).
- Must function on currently-supported Android versions.

### Out of scope

- Dynamic reticle resizing by the user.
- Reticle shapes other than rectangle.
- Configurable reticle size.
- Auto-capture once the subject is inside the reticle (PersonalID behavior).
- Automatic alignment / ML-based centering.

## Affected components

| Component | Path | Change |
| --- | --- | --- |
| `ImageWidget` | `app/src/org/commcare/views/widgets/ImageWidget.java` | New `rectangle-overlay` constant; parse appearance value; route Take Picture to `MicroImageActivity` |
| `MicroImageActivity` | `app/src/org/commcare/fragments/MicroImageActivity.java` | New activity-level `CaptureMode`; select overlay view + camera + capture path; back camera; full-res file output |
| `RectangleOverlayView` (new) | `app/src/org/commcare/views/RectangleOverlayView.java` | New view: draws a centered rectangle reticle (scrim + clear center), sized from view dimensions |
| `FaceCaptureView` | `app/src/org/commcare/views/FaceCaptureView.java` | **Unchanged** — remains the face-capture overlay |
| `micro_image_widget.xml` | `app/res/layout/micro_image_widget.xml` | Add `RectangleOverlayView` over the preview; toggle visibility by mode |
| `PersonalIdPhotoCaptureFragment` | `app/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragment.java` | Pass explicit `FaceDetection` mode (no behavior change) |
| `strings.xml` | `app/res/values/strings.xml` | New action-bar title for rectangle mode (existing `micro_image_activity_title` is "Take Profile Photo", PersonalID-specific) |

## Form-Level Configuration (HQ)

The feature is opt-in via the question's **appearance** attribute, the same mechanism
`ImageWidget` already uses for `"acquire"`.

- New appearance value: **`rectangle-overlay`**.
- Applies to image (`upload` / `CONTROL_IMAGE_CHOOSE`) questions.
- A single value, not a family of values (per the requirement: not configurable, single
  fixed reticle).
- Routing in `WidgetFactory` (`app/src/org/commcare/logic/WidgetFactory.java`, lines ~54-59)
  is **unchanged**: `rectangle-overlay != "signature"`, so the question still builds an
  `ImageWidget`. No new widget class is introduced.

Example `upload` controls with the appearance attribute (illustrative — HQ emits labels as
itext refs, e.g. `<label ref="jr:itext('…-label')"/>`; the salient part is the `appearance`
attribute, matching the `<upload mediatype="image/*" ref="…">` shape used in the repo's test
forms):

```xml
<!-- reticle, with Choose Image still available -->
<upload mediatype="image/*" ref="/data/muac_photo" appearance="rectangle-overlay">
  <label ref="jr:itext('muac_photo-label')"/>
</upload>

<!-- reticle + capture-only (Choose Image hidden) -->
<upload mediatype="image/*" ref="/data/muac_photo" appearance="acquire rectangle-overlay">
  <label ref="jr:itext('muac_photo-label')"/>
</upload>
```

The appearance attribute is delivered to the widget **raw, exactly as authored** — the
engine reads it verbatim in `XFormParser` (`question.setAppearanceAttr(e.getAttributeValue(
null, "appearance"))`), stores it as a single `String` in `QuestionDef.appearanceAttr`, and
`FormEntryCaption.getAppearanceHint()` returns it unchanged. It is never trimmed, lowercased,
or split into tokens by the core engine.

**`rectangle-overlay` and `acquire` can both be present on the same question**, authored as
one space-separated string, e.g. `appearance="acquire rectangle-overlay"` — meaning
capture-only (no Choose Image) **and** the reticle camera. Because the engine never splits
the string and delivers it whole, exact matching fails for the combined case. `ImageWidget`
must therefore match each hint by **substring `.contains()`** (order-independent), consistent
with how `WidgetFactory` matches `compact` / `combobox` / `legacy`:

```java
String acq = mPrompt.getAppearanceHint();
boolean acquire        = acq != null && acq.contains(QuestionWidget.ACQUIREFIELD);   // "acquire"
boolean useRectOverlay = acq != null && acq.contains(RECTANGLE_OVERLAY);             // "rectangle-overlay"
```

This handles `acquire`, `rectangle-overlay`, and `acquire rectangle-overlay` (either order).
It requires changing the existing `acquire` check from `equalsIgnoreCase` to `.contains()` —
a minor behavior change, low risk since `acquire` is not a substring of any other image
appearance value.

## UI Layer

### 1. `ImageWidget` — appearance parsing and capture routing

Add a private constant in `ImageWidget` (only this widget uses it, so it does **not** go in
the shared `QuestionWidget` base class where `ACQUIREFIELD` lives):

```java
private static final String RECTANGLE_OVERLAY = "rectangle-overlay";
```

Today (`ImageWidget.java:173-176`) the widget only handles `acquire`:

```java
String acq = mPrompt.getAppearanceHint();
if (QuestionWidget.ACQUIREFIELD.equalsIgnoreCase(acq)) {
    mChooseButton.setVisibility(View.GONE);
}
```

Because `acquire` and `rectangle-overlay` can co-occur in one authored string (and the
engine never splits it), both hints are matched by substring `.contains()`:

```java
String acq = mPrompt.getAppearanceHint();
boolean acquire = acq != null && acq.contains(QuestionWidget.ACQUIREFIELD);
useRectangleOverlay = acq != null && acq.contains(RECTANGLE_OVERLAY);
if (acquire) {
    mChooseButton.setVisibility(View.GONE);
}
```

Changes:

- Convert the existing `acquire` check from `ACQUIREFIELD.equalsIgnoreCase(acq)` to
  `acq.contains(ACQUIREFIELD)` so it is still detected inside a combined string such as
  `acquire rectangle-overlay`. Low risk: `acquire` is not a substring of any other image
  appearance value.
- Match `rectangle-overlay` with `acq.contains(RECTANGLE_OVERLAY)` and store
  `useRectangleOverlay`. Order-independent; handles `acquire rectangle-overlay` and
  `rectangle-overlay acquire`.
- The reticle affects **Take Picture** only; whether **Choose Image** is hidden is governed
  solely by `acquire`, independent of the reticle. So `acquire rectangle-overlay` →
  capture-only with the reticle; `rectangle-overlay` alone → reticle with Choose Image still
  available.

`takePicture()` (`ImageWidget.java:259-283`) branches on `useRectangleOverlay`:

```java
private void takePicture() {
    if (useRectangleOverlay) {
        takePictureWithReticle();
        return;
    }
    // ... existing ACTION_IMAGE_CAPTURE path, unchanged ...
}

private void takePictureWithReticle() {
    Uri uri = FileUtil.getUriForExternalFile(getContext(), getTempFileForImageCapture());
    Intent i = new Intent(getContext(), MicroImageActivity.class);
    i.putExtra(MicroImageActivity.MICRO_IMAGE_CAPTURE_MODE_EXTRA,
            MicroImageActivity.CaptureMode.RectangleOverlay.name());
    i.putExtra(MicroImageActivity.MICRO_IMAGE_OUTPUT_FILE_URI_EXTRA, uri);
    try {
        ((AppCompatActivity)getContext()).startActivityForResult(i, FormEntryConstants.IMAGE_CAPTURE);
        pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
    } catch (ActivityNotFoundException e) {
        // existing activity_not_found toast
    }
}
```

Rationale for reusing the existing temp file + `FormEntryConstants.IMAGE_CAPTURE` request
code: the form's existing `onActivityResult` pipeline reads the captured image from the
temp file path that `getTempFileForImageCapture()` returns. By having `MicroImageActivity`
write the full-resolution image to **that same URI**, the result-handling path
(`FormEntryActivity` / `ImageWidget.setBinaryData`) requires **no changes**.

### 2. `MicroImageActivity` — two caller-selected modes

`MicroImageActivity` (`app/src/org/commcare/fragments/MicroImageActivity.java`) selects
between two modes via a new **activity-level** enum, then wires up the matching overlay view,
camera, capture use case, and result path. The PersonalID base64 path is preserved unchanged.

```java
public enum CaptureMode { FaceDetection, RectangleOverlay }
```

This is deliberately separate from `FaceCaptureView.CaptureMode`
(`FaceDetectionMode` / `ManualMode`), which stays an internal detail of the face path
(`ManualMode` = the Play-Services-missing shutter fallback for face capture). The two enums
describe different things — *which overlay/feature* (activity) vs. *which capture trigger
within face capture* (view) — and must not be conflated.

New intent extras:

```java
public static final String MICRO_IMAGE_CAPTURE_MODE_EXTRA = "micro_image_capture_mode_extra";
public static final String MICRO_IMAGE_OUTPUT_FILE_URI_EXTRA = "micro_image_output_file_uri_extra";
```

Mode resolution in `onCreate()`:

- Read `MICRO_IMAGE_CAPTURE_MODE_EXTRA`; default to `CaptureMode.FaceDetection` when absent
  (preserves all current PersonalID callers).
- **`FaceDetection`** (PersonalID): existing behavior — `FaceCaptureView` visible, ML Kit /
  Google Play Services check, `ImageAnalysis.Analyzer` wiring, auto-capture with the
  `ManualMode` shutter fallback. `RectangleOverlayView` is `GONE`.
- **`RectangleOverlay`** (forms): `RectangleOverlayView` visible, `FaceCaptureView` `GONE`;
  make `cameraShutterButton` visible (manual capture; reuse the existing shutter view); skip
  the ML Kit / Google Play Services checks and the `ImageAnalysis.Analyzer` wiring entirely.

Action-bar title is set by mode: `FaceDetection` keeps `R.string.micro_image_activity_title`
("Take Profile Photo"); `RectangleOverlay` uses a new neutral string (e.g.
`R.string.image_capture_activity_title` = "Take Photo"), since the existing title is
PersonalID-specific.

Camera selection in `bindUseCases()` (`MicroImageActivity.java:157-181`):

- `FaceDetection`: keep `CameraSelector.DEFAULT_FRONT_CAMERA`.
- `RectangleOverlay`: `CameraSelector.DEFAULT_BACK_CAMERA` — the FLW photographs the child's
  arm.

Use-case selection in `bindUseCases()`:

- `FaceDetection` → `buildImageAnalysisUseCase(...)` (auto) with the existing `ManualMode`
  shutter fallback (`MicroImageActivity.java:104-107, 283-288`) — unchanged.
- `RectangleOverlay` → image-capture use case, built for **high quality**:
  - `ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)`.
  - **Do not** drive target resolution from `faceCaptureView.getImageWidth()/getImageHeight()`
    (that 480×640 sizing is face/micro-image specific). Let CameraX pick a high/native
    resolution so the tape reading is legible.

### 3. `MicroImageActivity` — output handling

Today `finalizeImageCapture(Rect)` (`MicroImageActivity.java:295-321`) crops to the passed
area, scales to `maxDimension`, compresses to `maxSize`, and returns base64. This is kept
for the PersonalID `FaceDetection` path. A separate path is used when an output file URI is
present:

- If `MICRO_IMAGE_OUTPUT_FILE_URI_EXTRA` is present (forms / `RectangleOverlay`):
  - **No crop** — save the full captured frame. The reticle is a guide, not a crop region;
    the photo must include enough of the child to confirm it is an arm and read the tape.
  - The manual-capture callback (`onCaptureSuccess` in `buildImageCaptureUseCase`) must
    **not** call `calcPreviewCaptureArea()` in this mode — that helper reads
    `faceCaptureView`'s dimensions, and `faceCaptureView` is `GONE` here. Branch on the mode
    (or on the presence of the output URI) and write the full bitmap directly.
  - Write the full-resolution JPEG to the provided file URI via a content-resolver
    `OutputStream` (the URI is the form's temp image file from `getTempFileForImageCapture()`).
  - `setResult(RESULT_OK)` with no data extra needed; the form reads the file. `finish()`.
- If the URI extra is absent (PersonalID): existing base64 behavior via
  `finalizeImageCapture(Rect)`, unchanged.

Because the reticle is rendered by `RectangleOverlayView` (a sibling overlay `View` over the
`PreviewView`) and the saved image comes from the CameraX `ImageCapture` sensor frame, the
reticle is **inherently absent** from the saved photo. No masking/removal step is required.

The `maxDimension` / `maxSize` extras (`MICRO_IMAGE_MAX_DIMENSION_PX_EXTRA`,
`MICRO_IMAGE_MAX_SIZE_BYTES_EXTRA`) remain supported. Forms either omit them (full
resolution) or pass generous values; PersonalID continues to pass its small values. This is
the "configurable size, same activity serves both" approach.

### 4. `RectangleOverlayView` (new) — responsive rectangle reticle

A new, self-contained view at `app/src/org/commcare/views/RectangleOverlayView.java`,
independent of `FaceCaptureView`. `FaceCaptureView` is **not modified** — the rectangle path
needs none of its face-detection machinery (`FaceOvalGraphic`, ML Kit `Face`, the
preview→image scale/translate used only for face cropping, `ImageStabilizedListener`, the
countdown).

**Sizing — uniform inset, responsive.** The reticle is a centered rectangle inset by an
**equal margin on all four sides**, computed from the view dimensions in `onSizeChanged()`
so it auto-adjusts to the view size. It is **not** a fixed pixel size and **not**
form-configurable. The equal inset makes the reticle follow the (portrait) preview's shape
and nearly fill it, encouraging the user to frame the subject within it.

- The margin is the same number of pixels on every side, derived from the smaller view
  dimension (`viewWidth` in the portrait-locked activity):
  `margin = RETICLE_MARGIN_RATIO × viewWidth`. Default `RETICLE_MARGIN_RATIO = 0.08f`
  (≈8% of the width on every side).
- Resulting rect:
  `left = margin`, `top = margin`, `right = viewWidth − margin`, `bottom = viewHeight − margin`.
- Because `viewHeight > viewWidth` (portrait), the reticle is a **centered portrait
  rectangle** that tracks the preview's aspect minus the uniform border. There is no separate
  aspect-ratio constant. Recomputed whenever the view is resized.

**Drawing.** A plain custom `View` (not `AppCompatImageView`) overriding `onDraw()`. No
styleable attributes required; colors/margin are constants in the view. To avoid a
`PorterDuff`/offscreen-layer cutout, the dim scrim is drawn as **four rectangles around the
centered reticle** (top, bottom, left, right bands); the center is never painted, so the
transparent view lets the `PreviewView` below show through. Then a high-contrast stroke is
drawn around the reticle — a white stroke plus a thin dark outline (two `drawRect` passes) —
so the edge reads on both light and dark backgrounds.

- No per-frame work — the reticle is static, so there is no preview latency.

Because there is **no crop**, this view does not need to expose its rect to the activity; it
is purely a visual guide.

### 5. Layout (`micro_image_widget.xml`)

The layout (`app/res/layout/micro_image_widget.xml`) currently overlays `FaceCaptureView`
(`@id/face_overlay`) and the shutter button on the `PreviewView` (`@id/view_finder`). Add a
`RectangleOverlayView` (`@id/rectangle_overlay`) over the same `PreviewView`, initially
`GONE`. `MicroImageActivity` shows exactly one overlay per mode (the other `GONE`):

- `FaceDetection` → `face_overlay` visible, `rectangle_overlay` gone.
- `RectangleOverlay` → `rectangle_overlay` visible, `face_overlay` gone.

This keeps a single activity and one camera/permission setup; only the overlay view and the
capture wiring differ by mode.

### 6. `PersonalIdPhotoCaptureFragment`

`executeTakePhoto()` (`PersonalIdPhotoCaptureFragment.java:160-166`) should pass
`MICRO_IMAGE_CAPTURE_MODE_EXTRA = MicroImageActivity.CaptureMode.FaceDetection.name()`
explicitly for clarity. No behavior change — `FaceDetection` is also the default when the
extra is absent.

## Data Flow

```
Form image question (appearance="rectangle-overlay")
        │
        ▼
ImageWidget.takePicture()  ── useRectangleOverlay? ──► takePictureWithReticle()
        │                                                     │
        │ (no)                                                │ Intent extras:
        ▼                                                     │  - CAPTURE_MODE = RectangleOverlay
ACTION_IMAGE_CAPTURE (system camera, unchanged)              │  - OUTPUT_FILE_URI = temp image file
                                                              ▼
                                            MicroImageActivity (CaptureMode.RectangleOverlay)
                                              - RectangleOverlayView visible (FaceCaptureView gone)
                                              - back camera, CameraX ImageCapture (max quality)
                                              - reticle drawn statically (no ML Kit)
                                              - user taps shutter
                                              - full frame written to OUTPUT_FILE_URI (no crop)
                                              - setResult(RESULT_OK); finish()
                                                              │
                                                              ▼
                                            FormEntryActivity onActivityResult
                                            (IMAGE_CAPTURE) — existing pipeline reads temp file
```

PersonalID flow is unchanged: `CaptureMode.FaceDetection`, `FaceCaptureView`, front camera,
oval reticle, auto-capture, base64 result via `MICRO_IMAGE_BASE_64_RESULT_KEY`.
