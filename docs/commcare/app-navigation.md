# App Navigation

How users move between CommCare's top-level screens.

## Where the app opens

`DispatchActivity` decides the landing screen once per launch (on create or a new intent), not on every resume. Notifications, deep links, external launches and shortcuts are handled first; otherwise the pure `LandingScreenResolver` picks:

- **Active session** → its app's Opportunity Home if the app has an opportunity, else CommCare App Home.
- **App seated, no session** → its opportunity's Opportunity Home if it has one, else CommCare Login.
- **No usable app** → the last accessed opportunity, the only opportunity, or the Opportunity List; CommCare Setup when nothing is set up.

An ended opportunity reopens on the next launch only; later launches land on the Opportunity List. Connect landings open behind `ConnectUnlockFragment`; a declined unlock relaunches `DispatchActivity`, which closes any active session and then picks CommCare Login or CommCare Setup. `PersonalIdUserPreferences` stores the last accessed opportunity and the ended opportunities already seen, both written by Opportunity Home.

## Back path

Homes (CommCare App Home and Opportunity Home) sit at the bottom of the task, so back exits the app from them; every other screen backs out to the one that opened it.

- **Landing.** `DispatchActivity` opens App Home and Connect landings with `NEW_TASK | CLEAR_TASK` and finishes. CommCare Login and Setup still open on top of it, since signing in hands back to it to open App Home. An Opportunity Home landing still on its intro gets the Opportunity List beneath it.
- **Returning to routing.** App Home at the bottom of the task or asking for recovery measures with no caller waiting, a declined startup unlock, and session expiry relaunch `DispatchActivity` in a cleared task instead of returning a result to it. So does an external launch above other screens, after the user saves or discards any form in progress.
- **Opportunity Home from the list** becomes the bottom of the task, unless it shows an intro, which opens on top of the list.
- **Signing into an app** from an Opportunity Home with screens beneath it reopens that Opportunity Home in a new task, with App Home on top.
- **Notifications and invite links** open in a new cleared task built by `NotificationTaskStack`. A Messaging chat opens above the channel list, and it and an opportunity showing its intro get the Connect home beneath them: the last accessed opportunity's Opportunity Home, else the Opportunity List. Any other target is the only screen. A declined unlock on these paths re-runs routing, like at startup.

## Sidebar sections

Messaging and Work History are **sidebar sections**: screens the user moves between from the sidebar. Each drawer screen names the sidebar item it corresponds to via `BaseDrawerActivity.currentDrawerSection`, and sections also set `isSidebarSection`. Whether the sidebar appears follows `shouldShowDrawerAfterCheck`.

- **Sections replace each other.** Opening Messaging, Work History or Opportunities from a section's sidebar replaces the current section, so back returns to wherever the user was working before, not to the previous section.
- **Tapping the open section** just closes the sidebar.
- **CommCare Apps** from a section or Home takes the user to the Login page as the only screen in the task. If they're logged in to an app, they're asked to confirm the logout first.
- **Screens nested inside a section**, like a Messaging chat, show back instead of the sidebar, and the sidebar can't be swiped open there.

## Analytics

Back presses on screens extending `CommonBaseActivity` log `nav_control_used` with the screen and which control was used: the app-bar arrow, the system back button, or the system back gesture. Gesture and button are told apart by the device's navigation mode, since both reach the app as the same back key.
