# App Navigation

How users move between CommCare's top-level screens.

## Where the app opens

`DispatchActivity` decides the landing screen once per launch (on create or a new intent), not on every resume. Notifications, deep links, external launches and shortcuts are handled first; otherwise the pure `LandingScreenResolver` picks:

- **Active session** → its app's Opportunity Home if the app has an opportunity, else CommCare App Home.
- **App seated, no session** → its opportunity's Opportunity Home if it has one, else CommCare Login.
- **No usable app** → the last accessed opportunity, the only opportunity, or the Opportunity List; CommCare Setup when nothing is set up.

An ended opportunity reopens on the next launch only; later launches land on the Opportunity List. Connect landings open behind `ConnectUnlockFragment`; a declined unlock returns to `DispatchActivity`, which closes any active session and then picks CommCare Login or CommCare Setup. `PersonalIdUserPreferences` stores the last accessed opportunity and the ended opportunities already seen, both written by Opportunity Home.

## Sidebar sections

Messaging and Work History are **sidebar sections**: screens the user moves between from the sidebar. Each drawer screen names the sidebar item it corresponds to via `BaseDrawerActivity.currentDrawerSection`, and sections also set `isSidebarSection`. Whether the sidebar appears follows `shouldShowDrawerAfterCheck`.

- **Sections replace each other.** Opening Messaging, Work History or Opportunities from a section's sidebar replaces the current section, so back returns to wherever the user was working before, not to the previous section.
- **Tapping the open section** just closes the sidebar.
- **CommCare Apps** from a section or Home takes the user to the Login page as the only screen in the task. If they're logged in to an app, they're asked to confirm the logout first.
- **Screens nested inside a section**, like a Messaging chat, show back instead of the sidebar, and the sidebar can't be swiped open there.
