# Connect Messaging: rich messages and attachments

PersonalID channel messages can carry encrypted attachments and a richer markdown text. Plain
messages behave as before. Visual design: https://claude.ai/artifact/QvRs3amdJSEMwkgzpzxAam
(private until shared).

## Data flow

1. **Sync.** `RetrieveNotificationsResponseParser` reads `version`, `rich_text`, `format`,
   `expires_at` and `attachments` for version 2 messages. Unknown versions keep only `content`
   and show an update notice. An entry that fails to parse is logged, skipped and not acked, so
   it can't block the rest of the sync.
2. **Storage** (Connect DB v30). `ConnectMessagingMessageRecord` holds the rich fields;
   `ConnectMessagingAttachmentRecord` (`connect_messaging_attachment`) holds one row per
   attachment, keyed by the server's attachment id. Re-delivered attachments keep their state.
   Decrypted files live in `filesDir/connect_messaging_attachments/<attachment id>` and are
   deleted, with the download job cancelled, on PersonalID logout.
3. **Download.** `ConnectMessagingAttachmentDownloadWorker` (unique WorkManager work, scheduled
   after every sync and restarted on a tap) downloads queued attachments oldest message first,
   checks the size, decrypts with the channel key (AES-256-GCM, nonce + ciphertext + tag) and
   writes the file atomically. Network type and a large-file threshold are parameters in
   `ConnectMessagingAttachmentDownloadConditions`; both are "any network" today.
4. **Chat.** Every state change sends `com.dimagi.messaging.update`; only the owning row
   redraws.

## Attachment states

| State | Set when | Shown as |
|---|---|---|
| `queued` | First stored, if the message is under 7 days old and among the channel's newest 50 incoming messages; or tapped | Spinner tile |
| `waiting` | First stored, otherwise | Download tile |
| `downloading` | The job picks it up (reset to `queued` if the job died) | Spinner tile |
| `available` | Decrypted file on disk; kept after expiry | Image, audio player or file chip |
| `failed` | Third failed attempt (network, HTTP error, size mismatch, decryption) | Retry tile |
| `expired` | `expires_at` passed or the server returned 410 before download | "No longer available" tile |

401/403 stop the pass without counting an attempt; the next sync tries again.

## Design (dp)

- **Row.** Messages with attachments drop the 70% text-bubble cap. Media first, text below it,
  then the timestamp. Bubble padding 10, 8 between attachments and before the text.
- **Image.** `w = min(maxW, maxH × aspect)`, `h = w ÷ aspect`; `maxW` = list width − 38
  (row margins, tail, bubble padding), `maxH` = list height × 0.5. Corners 6. Loaded with
  Glide without a disk cache, so no decrypted copies leave the attachment directory.
- **Audio.** Fills the bubble: 40 white play circle in a 48 touch target, seek bar (4 track,
  14 thumb, white over white 40%), time 12sp. Idle shows the duration, then the position. One
  track plays at a time and playback stops when the chat closes.
- **Other files.** Chip with name and size; tapping opens it with `ACTION_VIEW` through the
  app's `FileProvider`.
- **Not downloaded.** Fixed 240-wide tile (min 128 high), corners 6, `connect_blue_color`: 56
  white circle with a `connect_message_receiver_bg` icon, a 12sp white label, and the file name
  and size underneath. Spinner: 48 indeterminate ring, `connect_message_receiver_bg` track,
  white indicator. Failed: "Download failed" in `connect_red_light` over "Tap to retry".

Dimensions, styles and icons are prefixed `connect_message_` / `ConnectMessage` in `res/`.

## Not built yet

- `format` is stored but every value uses the default layout (no gallery strip).
- Download progress is indeterminate; cancelling isn't possible.
- The image lightbox and media-specific channel-list previews from the design (rich messages
  always carry plain `content`, which the preview shows).

## Edge cases

- **Entry and back:** unchanged; attachments live inside the existing chat screen.
- **Session:** behind PersonalID like the rest of messaging; logout removes files and work.
- **Lifecycle:** download state lives in the database, so it survives the app being killed.
  Audio position is not kept across rotation or leaving the chat.
- **Form factors:** phone, both orientations; image sizes follow the current list size.
- **Connectivity:** downloads wait for a network and retry with backoff; failures end in a retry
  tile.
