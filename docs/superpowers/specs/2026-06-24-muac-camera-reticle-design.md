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

The work reuses the existing PersonalID camera plumbing (CameraX, permission handling,
preview setup) by **extracting it into an abstract `BaseCameraActivity`**, then implementing
the two distinct flows as separate subclasses: `MicroImageActivity` (existing face-detection
capture) and a new `CameraOverlayActivity` (rectangle reticle, manual capture). Each activity
stays small and single-purpose, with no capture-mode state management inside one class — the
class identity *is* the mode, so no runtime mode flag or intent extra is needed to select
behavior.

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
| `ImageWidget` | `app/src/org/commcare/views/widgets/ImageWidget.java` | New `rectangle-overlay` constant; parse appearance value; route Take Picture to `CameraOverlayActivity` |
| `BaseCameraActivity` (new) | `app/src/org/commcare/fragments/BaseCameraActivity.java` | New abstract activity: shared CameraX plumbing (permission flow, provider acquisition, preview, action bar, error exit) + abstract hooks |
| `MicroImageActivity` | `app/src/org/commcare/fragments/MicroImageActivity.java` | Refactor to extend `BaseCameraActivity`; keep face-detection behavior identical (front camera, oval, ML Kit, base64) |
| `CameraOverlayActivity` (new) | `app/src/org/commcare/fragments/CameraOverlayActivity.java` | New subclass: back camera, manual shutter, `RectangleOverlayView`, full-res file output |
| `RectangleOverlayView` (new) | `app/src/org/commcare/views/RectangleOverlayView.java` | New view: draws a centered rectangle reticle (scrim + clear center), sized from view dimensions |
| `FaceCaptureView` | `app/src/org/commcare/views/FaceCaptureView.java` | **Unchanged** — remains the face-capture overlay |
| `camera_overlay_activity.xml` (new) | `app/res/layout/camera_overlay_activity.xml` | New layout: `PreviewView` + `RectangleOverlayView` + shutter button |
| `AndroidManifest.xml` | `app/AndroidManifest.xml` | Register `CameraOverlayActivity` (portrait, like `MicroImageActivity`) |
| `strings.xml` | `app/res/values/strings.xml` | New action-bar title for `CameraOverlayActivity` (existing `micro_image_activity_title` is "Take Profile Photo", PersonalID-specific) |

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
        takePictureWithOverlay();
        return;
    }
    // ... existing ACTION_IMAGE_CAPTURE path, unchanged ...
}

private void takePictureWithOverlay() {
    Uri uri = FileUtil.getUriForExternalFile(getContext(), getTempFileForImageCapture());
    Intent i = CameraOverlayActivity.newIntent(getContext(), uri);
    try {
        ((AppCompatActivity)getContext()).startActivityForResult(i, FormEntryConstants.IMAGE_CAPTURE);
        pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
    } catch (ActivityNotFoundException e) {
        // existing activity_not_found toast
    }
}
```

The reticle flow targets the dedicated `CameraOverlayActivity` — no capture-mode extra is
needed because the class itself is the mode. The intent is built via a static factory
(`CameraOverlayActivity.newIntent(context, outputFileUri)`) that takes the output file URI as
a required parameter, so a caller cannot construct the intent without it.

Rationale for reusing the existing temp file + `FormEntryConstants.IMAGE_CAPTURE` request
code: the form's existing `onActivityResult` pipeline reads the captured image from the
temp file path that `getTempFileForImageCapture()` returns. By having `CameraOverlayActivity`
write the full-resolution image to **that same URI**, the result-handling path
(`FormEntryActivity` / `ImageWidget.setBinaryData`) requires **no changes**.

### 2. `BaseCameraActivity` (new, abstract) — shared camera plumbing

Extract the camera scaffolding currently inside `MicroImageActivity` into an abstract base so
both capture flows share it without duplication. `BaseCameraActivity extends CommonBaseActivity
implements RuntimePermissionRequester` and owns only what is genuinely common:

- The CAMERA permission flow: the permission `ActivityResultLauncher`,
  `checkForCameraPermission()`, `requestNeededPermissions()`, and the rationale dialog.
- `ProcessCameraProvider` acquisition (`startCamera()`), and binding the `Preview` use case +
  surface provider to the `PreviewView`.
- Action-bar setup (`setDisplayHomeAsUpEnabled`, home/back via `onOptionsItemSelected`).
- `logErrorAndExit(...)` (log + toast + `setResult(RESULT_CANCELED)` + finish).

Everything mode-specific is an abstract hook the subclass implements, e.g.:

```java
@LayoutRes protected abstract int getContentLayout();      // which layout to inflate
@StringRes protected abstract int getTitleRes();           // action-bar title
protected abstract CameraSelector getCameraSelector();     // front vs back
protected abstract UseCase buildCaptureUseCase(Size targetResolution, int targetRotation);
```

The base calls `getCameraSelector()` and `buildCaptureUseCase(...)` when binding use cases; it
does **not** know about ML Kit, face detection, overlays, or output format — those live in the
subclasses. The base must remain a **behavior-preserving** extraction: the PersonalID flow
through `MicroImageActivity` must be byte-for-byte equivalent after the refactor.

### 3. `MicroImageActivity` (refactor) — face-detection capture

`MicroImageActivity` becomes a `BaseCameraActivity` subclass holding everything face-specific,
with **no behavior change** to the PersonalID flow:

- Implements the analyzer / `FaceCaptureView.ImageStabilizedListener`, ML Kit / Google Play
  Services check, oval `FaceCaptureView`, the `ManualMode` shutter fallback, and the base64
  result via `MICRO_IMAGE_BASE_64_RESULT_KEY` (with the existing `maxDimension`/`maxSize`
  extras and crop-to-face in `finalizeImageCapture(Rect)`).
- Hook implementations: `getCameraSelector()` → `DEFAULT_FRONT_CAMERA`; `getTitleRes()` →
  `R.string.micro_image_activity_title` ("Take Profile Photo"); `buildCaptureUseCase(...)` →
  the existing `ImageAnalysis` use case (its `ManualMode` fallback still builds an
  `ImageCapture`); `getContentLayout()` → `R.layout.micro_image_widget`.

### 4. `CameraOverlayActivity` (new) — rectangle reticle capture

A new `BaseCameraActivity` subclass for the form/MUAC flow. No ML Kit, no face detection, no
mode flag — its existence *is* the rectangle mode.

**Launch contract.** The output file URI is passed via a single extra, and the intent is
built through a static factory that takes the URI as a **required parameter**, so callers
cannot construct it without one:

```java
public static final String OUTPUT_FILE_URI_EXTRA = "camera_overlay_output_file_uri_extra";

public static Intent newIntent(Context context, Uri outputFileUri) {
    return new Intent(context, CameraOverlayActivity.class)
            .putExtra(OUTPUT_FILE_URI_EXTRA, outputFileUri);
}
```

The activity has no intent-filter and is not exported, so the only possible launchers are
in-app (today, just `ImageWidget`).

**Missing-URI guard (fail fast).** Even with the factory, `onCreate()` validates the extra at
this entry boundary and bails out before starting the camera if it is absent, rather than
capturing a photo with nowhere to write it:

```java
Uri outputUri = getIntent().getParcelableExtra(OUTPUT_FILE_URI_EXTRA);
if (outputUri == null) {
    logErrorAndExit("CameraOverlayActivity launched without output file URI",
            "camera.overlay.missing.output.uri", null);
    return; // do not start the camera
}
```

`logErrorAndExit(...)` (the `BaseCameraActivity` helper) logs, toasts, sets
`RESULT_CANCELED`, and finishes — which the form's existing `onActivityResult` already
handles. With the factory in place this is a fail-fast on a programming error, not runtime
branching.

- Hook implementations: `getCameraSelector()` → `DEFAULT_BACK_CAMERA` (the FLW photographs the
  child's arm); `getTitleRes()` → a new neutral string `R.string.image_capture_activity_title`
  ("Take Photo"); `getContentLayout()` → `R.layout.camera_overlay_activity`;
  `buildCaptureUseCase(...)` → an `ImageCapture` use case built for **high quality**
  (`ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)`), with
  target resolution left to CameraX (high/native) rather than the 480×640 micro-image sizing,
  so the tape reading is legible.
- Capture is **manual**: the shutter button (always visible) triggers `imageCapture.takePicture(...)`.
- **Output (no crop):** on `onCaptureSuccess`, write the full-resolution JPEG of the captured
  frame to the `OUTPUT_FILE_URI_EXTRA` URI via a content-resolver `OutputStream`, then
  `setResult(RESULT_OK)` (no data extra needed — the form reads the file) and `finish()`. The
  full frame is saved deliberately: the reticle is a framing guide, not a crop region; the
  photo must include enough of the child to confirm it is an arm and read the tape.

Because the reticle is rendered by `RectangleOverlayView` (a sibling overlay `View` over the
`PreviewView`) and the saved image comes from the CameraX `ImageCapture` sensor frame, the
reticle is **inherently absent** from the saved photo. No masking/removal step is required.

### 5. `RectangleOverlayView` (new) — responsive rectangle reticle

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

### 6. Layouts

Each activity has its own layout (no shared visibility-toggling):

- `micro_image_widget.xml` — **unchanged**, used by `MicroImageActivity` (`FaceCaptureView`
  `@id/face_overlay` + shutter over `PreviewView` `@id/view_finder`).
- `camera_overlay_activity.xml` — **new**, used by `CameraOverlayActivity`: a `PreviewView`,
  a `RectangleOverlayView` over it, and an always-visible shutter button. (The shared
  view ids the base references — preview, shutter — should be named consistently across both
  layouts so `BaseCameraActivity` can look them up.)

`CameraOverlayActivity` is registered in `AndroidManifest.xml` with `portrait` orientation,
matching `MicroImageActivity`.

`PersonalIdPhotoCaptureFragment` is unchanged — it still launches `MicroImageActivity`
directly, and no intent extra is needed to select the face flow.

## Data Flow

```
Form image question (appearance="rectangle-overlay")
        │
        ▼
ImageWidget.takePicture()  ── useRectangleOverlay? ──► takePictureWithOverlay()
        │                                                     │
        │ (no)                                                │ Intent extra:
        ▼                                                     │  - OUTPUT_FILE_URI = temp image file
ACTION_IMAGE_CAPTURE (system camera, unchanged)              ▼
                                            CameraOverlayActivity  (extends BaseCameraActivity)
                                              - RectangleOverlayView over PreviewView
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

The two activities share `BaseCameraActivity` (permission flow, provider acquisition, preview,
action bar, error exit); each implements its own camera selector, use case, overlay, and
result handling. The PersonalID flow is unchanged: `MicroImageActivity`, front camera, oval
`FaceCaptureView`, auto-capture, base64 result via `MICRO_IMAGE_BASE_64_RESULT_KEY`.
