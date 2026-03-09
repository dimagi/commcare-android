# PersonalID Photo Management — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Enable logged-in PersonalID users to add or update their profile photo from a new profile screen accessible via the side nav.

**Architecture:** New `PersonalIdProfileActivity` launched from the side nav profile header. Reuses `MicroImageActivity` for camera capture with face detection. New API endpoint + handler for photo upload. `CustomProgressDialog` shown during upload. Photo stored in `ConnectUserRecord` and synced to side nav on return.

**Tech Stack:** Java/Kotlin, AndroidX, Retrofit 2, Glide, CameraX (via existing `MicroImageActivity`), Material Components

---

### Task 1: Add API endpoint constant

**Files:**
- Modify: `app/src/org/commcare/connect/network/ApiEndPoints.java:14`

**Step 1: Add the new endpoint constant**

In `ApiEndPoints.java`, add after line 14 (`completeProfile`):

```java
public static final String updatePhoto = "/users/update_photo";
```

**Step 2: Commit**

```bash
git add app/src/org/commcare/connect/network/ApiEndPoints.java
git commit -m "feat: add update_photo API endpoint constant"
```

---

### Task 2: Add Retrofit service method

**Files:**
- Modify: `app/src/org/commcare/connect/network/ApiService.java:42`

**Step 1: Add the new service method**

In `ApiService.java`, add after line 42 (after the `completeProfile` method):

```java
@POST(ApiEndPoints.updatePhoto)
Call<ResponseBody> updatePhoto(@Header("Authorization") String token,
                               @Body Map<String, String> body);
```

**Step 2: Commit**

```bash
git add app/src/org/commcare/connect/network/ApiService.java
git commit -m "feat: add updatePhoto Retrofit service method"
```

---

### Task 3: Add API call method

**Files:**
- Modify: `app/src/org/commcare/connect/network/ApiPersonalId.java:220`

**Step 1: Add the updatePhoto static method**

In `ApiPersonalId.java`, add after the `setPhotoAndCompleteProfile` method (after line 220):

```java
public static void updatePhoto(Context context, String userId, String password,
                                String photoAsBase64, IApiCallback callback) {
    Objects.requireNonNull(photoAsBase64);
    AuthInfo authInfo = new AuthInfo.ProvidedAuth(userId, password, false);
    String tokenAuth = HttpUtils.getCredential(authInfo);

    HashMap<String, String> params = new HashMap<>();
    params.put("photo", photoAsBase64);

    ApiService apiService = PersonalIdApiClient.getClientApi();
    Call<ResponseBody> call = apiService.updatePhoto(tokenAuth, params);
    BaseApi.Companion.callApi(context, call, callback, ApiEndPoints.updatePhoto);
}
```

This follows the same auth pattern as `updateUserProfile` (line 180-197) — uses `ProvidedAuth` with stored userId/password since the user is already logged in.

**Step 2: Commit**

```bash
git add app/src/org/commcare/connect/network/ApiPersonalId.java
git commit -m "feat: add updatePhoto API call to ApiPersonalId"
```

---

### Task 4: Add API handler method

**Files:**
- Modify: `app/src/org/commcare/connect/network/connectId/PersonalIdApiHandler.java:266`

**Step 1: Add the updatePhoto handler method**

In `PersonalIdApiHandler.java`, add after the `completeProfile` method (after line 266):

```java
public void updatePhoto(Context context, String userId, String password, String photoBase64) {
    ApiPersonalId.updatePhoto(
            context,
            userId,
            password,
            photoBase64,
            createCallback(new NoParsingResponseParser<>(), null)
    );
}
```

This uses `NoParsingResponseParser` (imported already via line 15) and the `createCallback` from `BaseApiHandler` since we don't need to parse the response body — we just need success/failure.

**Step 2: Verify the import for `NoParsingResponseParser` exists**

Check line 15 — `import org.commcare.connect.network.NoParsingResponseParser;` should already be present.

**Step 3: Commit**

```bash
git add app/src/org/commcare/connect/network/connectId/PersonalIdApiHandler.java
git commit -m "feat: add updatePhoto handler to PersonalIdApiHandler"
```

---

### Task 5: Add string resources

**Files:**
- Modify: `app/res/values/strings.xml`

**Step 1: Add new string resources**

Find the PersonalID photo strings section (around line 624-627) and add nearby:

```xml
<string name="personalid_profile_title">Profile</string>
<string name="personalid_profile_add_photo">Add Photo</string>
<string name="personalid_profile_update_photo">Update Photo</string>
<string name="personalid_profile_photo_updated">Photo updated successfully</string>
<string name="personalid_profile_photo_update_failed">Failed to update photo. Please try again.</string>
```

**Step 2: Commit**

```bash
git add app/res/values/strings.xml
git commit -m "feat: add string resources for PersonalID profile screen"
```

---

### Task 6: Create the profile screen layout

**Files:**
- Create: `app/res/layout/activity_personalid_profile.xml`

**Step 1: Create the layout file**

Follow the pattern of `activity_personal_id_work_history.xml` — uses `ConstraintLayout` with `include` for `appbar_layout`, and the `NoActionBar` theme.

```xml
<?xml version="1.0" encoding="utf-8"?>
<layout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@color/white">

        <include
            android:id="@+id/include_tool_bar"
            layout="@layout/appbar_layout" />

        <ImageView
            android:id="@+id/profile_photo"
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:layout_marginTop="48dp"
            android:contentDescription="@null"
            android:scaleType="centerCrop"
            android:src="@drawable/nav_drawer_person_avatar"
            app:shapeAppearanceOverlay="@style/CircularImageView"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/include_tool_bar" />

        <TextView
            android:id="@+id/profile_name"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:textColor="@color/black"
            android:textSize="18sp"
            android:textStyle="bold"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/profile_photo" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/photo_action_button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/personalid_profile_add_photo"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/profile_name" />

    </androidx.constraintlayout.widget.ConstraintLayout>
</layout>
```

Key details:
- Uses `<layout>` wrapper for data binding (matching `screen_personalid_photo_capture.xml` pattern)
- `ImageView` uses `@style/CircularImageView` `shapeAppearanceOverlay` (same as `nav_drawer_header.xml:35`)
- Placeholder avatar is `@drawable/nav_drawer_person_avatar` (same as nav drawer)
- Includes `appbar_layout` (same as `activity_personal_id_work_history.xml:10-11`)

**Step 2: Commit**

```bash
git add app/res/layout/activity_personalid_profile.xml
git commit -m "feat: add layout for PersonalID profile screen"
```

---

### Task 7: Create PersonalIdProfileActivity

**Files:**
- Create: `app/src/org/commcare/activities/connect/PersonalIdProfileActivity.java`

**Step 1: Create the Activity**

```java
package org.commcare.activities.connect;

import static org.commcare.fragments.MicroImageActivity.MICRO_IMAGE_MAX_DIMENSION_PX_EXTRA;
import static org.commcare.fragments.MicroImageActivity.MICRO_IMAGE_MAX_SIZE_BYTES_EXTRA;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.snackbar.Snackbar;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler;
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes;
import org.commcare.connect.network.connectId.PersonalIdApiHandler;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ActivityPersonalidProfileBinding;
import org.commcare.fragments.MicroImageActivity;
import org.commcare.utils.MediaUtil;
import org.commcare.views.dialogs.CustomProgressDialog;

public class PersonalIdProfileActivity extends CommCareActivity<PersonalIdProfileActivity> {

    private static final int TASK_UPDATE_PHOTO = 1;
    private static final int PHOTO_MAX_DIMENSION_PX = 160;
    private static final int PHOTO_MAX_SIZE_BYTES = 100 * 1024; // 100 KB

    private ActivityPersonalidProfileBinding binding;
    private ActivityResultLauncher<Intent> takePhotoLauncher;
    private ConnectUserRecord user;
    private String previousPhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPersonalidProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        user = ConnectUserDatabaseUtil.getUser(this);

        initTakePhotoLauncher();
        setupUi();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getSupportActionBar().setTitle(getString(R.string.personalid_profile_title));
    }

    private void initTakePhotoLauncher() {
        takePhotoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String photoBase64 = result.getData().getStringExtra(
                                MicroImageActivity.MICRO_IMAGE_BASE_64_RESULT_KEY);
                        previousPhoto = user.getPhoto();
                        displayPhoto(photoBase64);
                        uploadPhoto(photoBase64);
                    }
                }
        );
    }

    private void setupUi() {
        binding.profileName.setText(user.getName());
        loadCurrentPhoto();

        binding.photoActionButton.setOnClickListener(v -> launchCamera());
    }

    private void loadCurrentPhoto() {
        String photo = user.getPhoto();
        if (photo != null && !photo.isEmpty()) {
            Glide.with(this)
                    .load(photo)
                    .apply(RequestOptions.circleCropTransform()
                            .placeholder(R.drawable.nav_drawer_person_avatar)
                            .error(R.drawable.nav_drawer_person_avatar))
                    .into(binding.profilePhoto);
            binding.photoActionButton.setText(R.string.personalid_profile_update_photo);
        } else {
            binding.photoActionButton.setText(R.string.personalid_profile_add_photo);
        }
    }

    private void displayPhoto(String photoBase64) {
        binding.profilePhoto.setImageBitmap(MediaUtil.decodeBase64EncodedBitmap(photoBase64));
        binding.photoActionButton.setText(R.string.personalid_profile_update_photo);
    }

    private void launchCamera() {
        Intent intent = new Intent(this, MicroImageActivity.class);
        intent.putExtra(MICRO_IMAGE_MAX_DIMENSION_PX_EXTRA, PHOTO_MAX_DIMENSION_PX);
        intent.putExtra(MICRO_IMAGE_MAX_SIZE_BYTES_EXTRA, PHOTO_MAX_SIZE_BYTES);
        takePhotoLauncher.launch(intent);
    }

    private void uploadPhoto(String photoBase64) {
        showProgressDialog(TASK_UPDATE_PHOTO);
        binding.photoActionButton.setEnabled(false);

        new PersonalIdApiHandler<Void>() {
            @Override
            public void onSuccess(Void data) {
                dismissProgressDialogForTask(TASK_UPDATE_PHOTO);
                user.setPhoto(photoBase64);
                ConnectUserDatabaseUtil.storeUser(PersonalIdProfileActivity.this, user);
                binding.photoActionButton.setEnabled(true);
                Snackbar.make(binding.getRoot(),
                        R.string.personalid_profile_photo_updated,
                        Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(PersonalIdOrConnectApiErrorCodes errorCode, Throwable t) {
                dismissProgressDialogForTask(TASK_UPDATE_PHOTO);
                revertPhoto();
                binding.photoActionButton.setEnabled(true);
                String errorMessage = PersonalIdOrConnectApiErrorHandler.handle(
                        PersonalIdProfileActivity.this, errorCode, t);
                if (!errorMessage.isEmpty()) {
                    Snackbar.make(binding.getRoot(), errorMessage, Snackbar.LENGTH_LONG).show();
                }
            }
        }.updatePhoto(this, user.getUserId(), user.getPassword(), photoBase64);
    }

    private void revertPhoto() {
        if (previousPhoto != null && !previousPhoto.isEmpty()) {
            Glide.with(this)
                    .load(previousPhoto)
                    .apply(RequestOptions.circleCropTransform()
                            .placeholder(R.drawable.nav_drawer_person_avatar)
                            .error(R.drawable.nav_drawer_person_avatar))
                    .into(binding.profilePhoto);
            binding.photoActionButton.setText(R.string.personalid_profile_update_photo);
        } else {
            binding.profilePhoto.setImageResource(R.drawable.nav_drawer_person_avatar);
            binding.photoActionButton.setText(R.string.personalid_profile_add_photo);
        }
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        return CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (!isFinishing()) {
                finish();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
```

Key patterns followed:
- Extends `CommCareActivity` (like `PersonalIdWorkHistoryActivity`)
- Uses `generateProgressDialog` + `showProgressDialog`/`dismissProgressDialogForTask` (existing `CommCareActivity` pattern)
- Uses `PersonalIdApiHandler` anonymous class with `onSuccess`/`onFailure` (like `PersonalIdPhotoCaptureFragment:89-101`)
- Photo capture via `ActivityResultLauncher` with `MicroImageActivity` (like `PersonalIdPhotoCaptureFragment:63-76`)
- Same photo constants: `PHOTO_MAX_DIMENSION_PX = 160`, `PHOTO_MAX_SIZE_BYTES = 100KB` (like `PersonalIdPhotoCaptureFragment:43-44`)
- Error handling via `PersonalIdOrConnectApiErrorHandler.handle()` (like `PersonalIdWorkHistoryActivity:72`)
- Back button handling (like `PersonalIdWorkHistoryActivity:86-91`)
- Photo display with Glide circular crop (like `BaseDrawerController:158-166`)

**Step 2: Commit**

```bash
git add app/src/org/commcare/activities/connect/PersonalIdProfileActivity.java
git commit -m "feat: add PersonalIdProfileActivity for photo management"
```

---

### Task 8: Register Activity in AndroidManifest

**Files:**
- Modify: `app/AndroidManifest.xml:586`

**Step 1: Add the Activity registration**

After the `PersonalIdWorkHistoryActivity` entry (line 586), add:

```xml
<activity
    android:name="org.commcare.activities.connect.PersonalIdProfileActivity"
    android:screenOrientation="portrait"
    android:theme="@style/CommonTheme.NoActionBar"/>
```

Uses `portrait` orientation (like `PersonalIdActivity` at line 175) and `NoActionBar` theme (like `PersonalIdWorkHistoryActivity` at line 586).

**Step 2: Commit**

```bash
git add app/AndroidManifest.xml
git commit -m "feat: register PersonalIdProfileActivity in manifest"
```

---

### Task 9: Add navigation helper method

**Files:**
- Modify: `app/src/org/commcare/connect/ConnectNavHelper.kt:61`

**Step 1: Add navigation methods for the profile screen**

In `ConnectNavHelper.kt`, add the necessary import at the top:

```kotlin
import org.commcare.activities.connect.PersonalIdProfileActivity
```

Then add after `goToWorkHistory` (after line 62):

```kotlin
fun unlockAndGoToProfile(
    activity: CommCareActivity<*>,
    listener: ConnectActivityCompleteListener,
) {
    unlockAndGoTo(activity, listener, ::goToProfile)
}

fun goToProfile(context: Context) {
    val i = Intent(context, PersonalIdProfileActivity::class.java)
    context.startActivity(i)
}
```

This follows the exact same pattern as `unlockAndGoToWorkHistory` / `goToWorkHistory` (lines 52-62).

**Step 2: Commit**

```bash
git add app/src/org/commcare/connect/ConnectNavHelper.kt
git commit -m "feat: add navigation helper for PersonalID profile screen"
```

---

### Task 10: Wire up the side nav entry point

**Files:**
- Modify: `app/res/layout/nav_drawer_header.xml:55`
- Modify: `app/src/org/commcare/navdrawer/BaseDrawerController.kt:148`
- Modify: `app/src/org/commcare/navdrawer/BaseDrawerActivity.kt:83`

**Step 1: Unhide the "Manage profile" text**

In `nav_drawer_header.xml`, change line 55 from:

```xml
android:visibility="gone"
```

to:

```xml
android:visibility="visible"
```

**Step 2: Add profile card click listener in BaseDrawerController.kt**

In `BaseDrawerController.kt`, inside the `setupListeners()` method (after line 148, the `helpView` listener), add:

```kotlin
binding.profileCard.setOnClickListener {
    onItemClicked(NavItemType.OPPORTUNITIES, null) // placeholder, handled differently
    ConnectNavHelper.unlockAndGoToProfile(
        activity,
        object : ConnectActivityCompleteListener {
            override fun connectActivityComplete(success: Boolean, error: String?) {
                if (success) {
                    closeDrawer()
                }
            }
        }
    )
}
```

Wait — looking at the pattern more carefully, the other nav items use `onItemClicked` which goes through `BaseDrawerActivity.handleDrawerItemClick`. A cleaner approach is to follow the same pattern. But the profile card is the header, not a nav item. Let me use the direct approach instead (like `signInButton` and `aboutView` listeners which call methods directly).

Replace the above with:

```kotlin
binding.profileCard.setOnClickListener {
    ConnectNavHelper.unlockAndGoToProfile(
        activity,
        object : ConnectActivityCompleteListener {
            override fun connectActivityComplete(success: Boolean, error: String?) {
                if (success) {
                    closeDrawer()
                }
            }
        }
    )
}
```

You will need to add the import at the top of `BaseDrawerController.kt`:

```kotlin
import org.commcare.connect.ConnectActivityCompleteListener
```

**Step 3: Commit**

```bash
git add app/res/layout/nav_drawer_header.xml \
    app/src/org/commcare/navdrawer/BaseDrawerController.kt
git commit -m "feat: wire up side nav profile card to launch profile screen"
```

---

### Task 11: Build and verify

**Step 1: Run the build**

```bash
./gradlew assembleCommcareDebug
```

Expected: BUILD SUCCESSFUL. Fix any compilation errors.

**Step 2: Commit any fixes if needed**

---

### Task 12: Manual testing checklist

Verify on a device or emulator:

1. **Side nav shows "Manage profile"** — Open the side nav, confirm the "Manage profile" text is visible under the user's name in the profile header card
2. **Profile card is tappable** — Tap the profile header card, confirm it navigates to the new profile screen
3. **Profile screen displays correctly** — Toolbar says "Profile", back arrow works, user's name is shown read-only, photo shows current photo or placeholder
4. **Button label is correct** — "Add Photo" if no photo exists, "Update Photo" if one does
5. **Camera launches** — Tap the button, confirm `MicroImageActivity` opens with front camera and face detection
6. **Photo preview updates** — After taking a photo, the profile screen shows the new photo immediately
7. **Progress dialog shows** — `CustomProgressDialog` ("Please wait a few moments.") appears during upload
8. **Success flow** — On successful upload: dialog dismisses, snackbar says "Photo updated successfully", button re-enabled
9. **Error flow** — On failure: dialog dismisses, photo reverts to previous, snackbar shows error, button re-enabled for retry
10. **Side nav updates** — After returning from profile screen, the side nav drawer shows the updated photo
