# Notifications

## Overview

This document provides an overview of the push notification system in the CommCare Android
application. The system is designed to deliver timely and relevant information to users, even when
the app is not actively in use. It leverages Firebase Cloud Messaging (FCM) to handle the delivery
of notifications and a combination of services, workers, and helpers to process and display them.

### Types of Notifications

There are three main types of notifications:

1. **Standard Notifications**: These are simple notifications that display a title and a message.
   They are used for general announcements and alerts.
2. **Action-Triggering Notifications**: These notifications contain a data payload that triggers a
   specific in-app action. This is used to update the app's data in response to a server-side event
   or to navigate the user to a specific screen. The following actions are supported:
    - `ccc_message`: Navigates the user to the Connect messaging screen.
    - `ccc_dest_payments`: Navigates the user to the Connect payments screen.
    - `ccc_payment_info_confirmation`: Navigates the user to the Connect payment confirmation
      screen.
    - `ccc_dest_opportunity_summary_page`: Navigates the user to the Connect opportunity summary
      page.
    - `ccc_dest_learn_progress`: Navigates the user to the Connect learn progress screen.
    - `ccc_dest_delivery_progress`: Navigates the user to the Connect delivery progress screen.
    - `ccc_generic_opportunity` : Navigates the user to the Connect opportunity learn/deliver
      progress screen or payment depending upon the payload.

3. **Data Syncer Notifications**: These notifications trigger a background data sync. The
   `FirebaseMessagingDataSyncer` class is responsible for handling these notifications.

## High-Level Data Flow

1. **FCM Message Reception**: The `CommCareFirebaseMessagingService` receives all incoming push
   notifications from FCM.
2. **Sync Trigger**: If the notification contains a `sync` action, the
   `NotificationsSyncWorkerManager` is invoked to start a background sync. The
   `NotificationsSyncWorkerManager` is responsible for creating and enqueueing a`OneTimeWorkRequest`
   for the `NotificationSyncWorker`. This ensures that the sync operation is executed in a reliable
   and battery-efficient manner, even if the app is not in the foreground.
3. **Sync Execution**: The `NotificationSyncWorker` performs the actual data sync. It runs in the
   background and is managed by Android's `WorkManager`. This worker is responsible for
   communicating with the server, making sure that required data is retrieved and processed.
4. **Notification Display**: If the notification is a standard notification (without a `sync`
   action), `FirebaseMessagingUtil` is used to display the notification to the user.
5. **Notification History**: The `PushNotificationActivity` displays a history of all received
   notifications.
6. **API Interaction**: The `PushNotificationApiHelper` is responsible for all communication with
   the backend API, including retrieving the latest notifications and acknowledging their receipt.

## Key Classes

- **`CommCareFirebaseMessagingService`**: The entry point for all FCM messages. It parses the
  incoming message and delegates to the appropriate handler.
  * If the message contains a `sync` action, the `NotificationsSyncWorkerManager` is invoked to
    start a background sync and push notification is shown after that sync.
  * If the message is a standard notification, `FirebaseMessagingUtil` is used to display the
    notification to the user immediately, without any sync action.
- **`FirebaseMessagingUtil`**: A utility class for handling and displaying notifications. Its
  `handleNotification` method is the main entry point for processing notifications. It first checks
  if the notification is a CCC-related notification by looking for a "ccc_" prefix in the action
  string.
  - If it is a CCC notification, it further delegates to `handleCCCActionPushNotification`, which
    then calls a specific handler based on the action (e.g.,
    `handleCCCMessageChannelPushNotification`, `handleCCCPaymentPushNotification`).
  - If it is a sync notification, it delegates to `FirebaseMessagingDataSyncer`.
  - For all other notifications, it calls `handleGeneralApplicationPushNotification`, which
    displays the notification and launches the `DispatchActivity` when the user clicks on it.
- **`NotificationsSyncWorkerManager`**: Manages the lifecycle of the `NotificationsSyncWorker`.
  * It is responsible for creating and enqueueing a `OneTimeWorkRequest` for the
    `NotificationsSyncWorker` using Android's `WorkManager`.
  * This manager class passes the necessary notification data from the FCM message to the worker
    as input, ensuring the sync is performed for the correct user and domain.
- **`NotificationsSyncWorker`**: A background worker that performs a data sync when triggered by a
  push notification.
  * It runs in the background, managed by `WorkManager`, ensuring the sync operation is reliable
    and executes even if the app is not in the foreground.
  * This worker is responsible for executing the `DataPullTask`, which communicates with the
    server, downloads the latest data, and updates the local database. After the sync is complete,
    it triggers a local notification to inform the user about the sync's completion.
  * It supports the following sync actions:
    - `SYNC_OPPORTUNITY`: Syncs all the opportunities.
    - `SYNC_PERSONALID_NOTIFICATIONS`: Syncs all the notifications for the current user.
    - `SYNC_DELIVERY_PROGRESS`: Syncs the delivery progress for a specific opportunity.
    - `SYNC_LEARNING_PROGRESS`: Syncs the learning progress for a specific opportunity.
    - `SYNC_GENERIC_OPPORTUNITY` : Syncs the opportunity learn/deliver progress or payment
      depending upon the payload.
- **`PushNotificationActivity`**: An activity that displays a list of all received notifications.
  * When a user clicks on a notification in the list, the `onNotificationClick` listener is
    triggered. This method then calls `FirebaseMessagingUtil.getIntentForPNClick()`, which creates
    the appropriate `Intent` to navigate the user to the correct screen based on the
    notification's action. The activity then starts the new activity using this intent.
- **`PushNotificationApiHelper`**: A helper class that encapsulates all API interactions related to
  push notifications.
  * It is the central point for fetching new notifications from the server and acknowledging their
    receipt.
  * The process starts with `retrieveLatestPushNotifications`, which calls the
    `retrieveNotifications` API endpoint.
  * Upon receiving a successful response, the helper processes the data: it parses the incoming
    notifications, messages, and channels, and stores them in the local database using helpers
    like `ConnectMessagingDatabaseHelper` and `NotificationRecordDatabaseHelper`.
  * After successfully storing the data, it immediately calls `acknowledgeNotificationsReceipt`.
    This function makes a `POST` request to the `updateNotifications` API endpoint, sending the
    IDs of the received notifications to the server to confirm their delivery.
  * Finally, upon successful acknowledgment from the server, it updates the `acknowledged` status
    of the corresponding notifications in the local database.

## API Endpoints

The notification system interacts with the following API endpoints:

### Retrieve Latest Notifications

- **Endpoint**: `/messaging/retrieve_notifications/`
- **Method**: `GET`
- **Description**: This endpoint is called by the application to fetch all pending notifications,
  including general alerts, messages, and channel information for the logged-in user. The
  application calls this endpoint when it is opened, or when the user manually triggers a refresh
  from the notification history screen. This ensures that the user always has the most up-to-date
  list of notifications.

**Example Response Payload**:

```json
{
  "notifications": [
    {
      "action": "ccc_dest_delivery_progress",
      "title": "New Data Available",
      "body": "Please sync your device to get the latest data.",
      "notification_id": "12345-RIOU-UOU-423",
      "created": "2024-05-21T10:00:00Z",
      "status": "unread",
      "opportunity_uuid": "abcde-328-32mkj-43",
      "payment_uuid": "42309-fdfjl4-4343",
      "opportunity_id": "32",
      "payment_id": "21"
    }
  ],
  "channels": []
}
```

### Acknowledge Notification Receipt

- **Endpoint**: `/messaging/update_notification_received/`
- **Method**: `POST`
- **Description**: After the application successfully fetches and stores the notifications from the
  `/messaging/retrieve_notifications/` endpoint, it must call this endpoint to inform the server
  that the notifications have been received. This prevents the server from resending the same
  notifications in subsequent calls. The request body must contain a list of the unique
  `notification_id`s that were successfully processed and stored locally.

**Example Request Payload**:

```json
{
  "notification_ids": [
    "12345-rewo23-423",
    "67890-afljfa-243"
  ]
}
```

**Example Response Payload**:

```json
{
  "success": true
}
```
