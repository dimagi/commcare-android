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
3. **Download.** `ConnectMessagingAttachmentDownloadWorker` downloads queued attachments oldest
   message first, checks the size, decrypts with the channel key (AES-256-GCM, nonce +
   ciphertext + tag) and writes the file atomically. Automatic downloads run as unique work
   scheduled after every sync; tapping a message gives each of its unfinished attachments its
   own job, run at once without waiting for a network so a failure shows right away, and ahead
   of automatic downloads. Each attachment downloads independently.
4. **Chat.** A rich message with attachments stays hidden behind one generic tile, with no text
   or attachment details, until every attachment is available; the channel list previews it as
   "[Media message to download]". Every state change sends `com.dimagi.messaging.update`; only
   the owning row redraws.

## Automatic download setting

Messaging home › ⋮ › Settings › "Download attachments automatically", stored in
`ConnectMessagingPreferences` and cleared on PersonalID logout:

| Option | Automatic downloads |
|---|---|
| On Wi-Fi and mobile data (default) | All attachments, any network |
| Large attachments (over 1 MB) only on Wi-Fi | Up to 1 MB on any network; larger ones wait for an unmetered network |
| Manual download only | None. New attachments start as `waiting`; switching to this returns queued downloads to `waiting` |

Tapping a pending message always downloads it right away, whatever the setting.

## Attachment states

The message tile shows the combined state, in priority order: any `expired` part makes it "No
longer available", a part downloading or just tapped shows the spinner, a failed part shows the
retry tile, otherwise the download tile. It opens fully once every part is `available`. The
spinner never stands for waiting on a network or a retry backoff.

| State | Set when | Tile while the message is pending |
|---|---|---|
| `queued` | First stored, if automatic download is on, the message is under 7 days old and among the channel's newest 50 incoming messages | Download tile |
| `waiting` | First stored, otherwise | Download tile |
| `requested` | Tapped | Spinner tile |
| `downloading` | A job is fetching it (marked `failed` if the job died) | Spinner tile |
| `available` | Decrypted file on disk; kept after expiry | Shown as image, audio player or file chip once all parts are |
| `failed` | Any unsuccessful attempt (network, HTTP error, size mismatch, decryption, 401/403) | Retry tile |
| `expired` | `expires_at` passed or the server returned 410 before download | "No longer available" tile |

A failed part stays eligible for background retry until its third counted failure. The
automatic jobs retry on their usual backoff; a failed tap schedules its own retry, 3 minutes
later once a network is available and backing off after that. Tapping retries at once and
resets the count. 401/403 stop the pass without counting an attempt.

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
- **Not downloaded.** One fixed 240-wide tile per message (min 128 high), corners 6,
  `connect_blue_color`: 56 white circle with a `connect_message_receiver_bg` icon and a 12sp
  white label; no text, names, sizes or media types. Spinner: 48 indeterminate ring, `connect_message_receiver_bg` track,
  white indicator. Failed: "Download failed" in `connect_red_light` over "Tap to retry".

Dimensions, styles and icons are prefixed `connect_message_` / `ConnectMessage` in `res/`.

## Not built yet

- `format` is stored but every value uses the default layout (no gallery strip).
- Download progress is indeterminate; cancelling isn't possible.
- The image lightbox from the design.

## Edge cases

- **Entry and back:** attachments live inside the existing chat screen; settings open from the
  channel list menu and Back returns to the list.
- **Session:** behind PersonalID like the rest of messaging; logout removes files and work.
- **Lifecycle:** download state lives in the database, so it survives the app being killed.
  Audio position is not kept across rotation or leaving the chat.
- **Form factors:** phone, both orientations; image sizes follow the current list size.
- **Connectivity:** automatic downloads wait for a network; every failure shows the retry tile
  at once while background retries continue with backoff.
