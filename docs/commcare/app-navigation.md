# App Navigation

How users move between CommCare's top-level screens.

## Sidebar sections

Messaging and Work History are **sidebar sections**: screens the user moves between from the sidebar. Each one declares itself via `BaseDrawerActivity.drawerSection`. Whether the sidebar appears follows `shouldShowDrawerAfterCheck`.

- **Sections replace each other.** Opening Messaging, Work History or Opportunities from a section's sidebar replaces the current section, so back returns to wherever the user was working before, not to the previous section.
- **Tapping the open section** just closes the sidebar.
- **CommCare Apps** from a section takes the user to the Login page as the only screen in the task. If they're logged in to an app, they're asked to confirm the logout first.
- **Screens nested inside a section**, like a Messaging chat, show back instead of the sidebar, and the sidebar can't be swiped open there.
