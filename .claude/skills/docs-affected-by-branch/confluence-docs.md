# Confluence Documentation Checks

CommCare Android ships both CommCare and Connect features, and their docs live in different
Confluence spaces:

- **CommCare:** `commcarepublic` (public help site) and `saas` (internal).
- **Connect and PersonalID:** `connectpublic` (public) and `connect` (internal). The
  [Connect Documentation Process](https://dimagi.atlassian.net/wiki/spaces/connect/pages/3092250699/Connect+Documentation+Process)
  defines which pages a Connect feature needs and what each page must contain.

## Finding affected pages

Start from the terms the change touches: feature names, page names, button and column labels,
status names, error and notification strings, CSV column names, permissions and limits.

- **If Atlassian MCP tools are available:** search Confluence for those terms in the spaces of
  each product the change touches. Code under `org.commcare.connect`,
  `org.commcare.fragments.personalId`, `docs/connect` or `docs/personalid` is Connect; the rest
  is CommCare. Search all four spaces when unsure. Read the top results and keep only pages
  that describe behaviour the change alters. Search results are noisy, so present the pages you keep as
  candidates for the user to confirm, not as the final list.
- **If they are not available:** show the user the terms and ask which pages cover them. Accept
  free text, since not every item is a link, and accept "None".

If the change introduces a new Connect term, include the
[Connect Glossary](https://dimagi.atlassian.net/wiki/spaces/connectpublic/pages/3146907685/Connect+Glossary).
If no page covers a new user-facing feature, say a new page is needed.

## Edit format

Report only what needs to change; skip anything already accurate. Order items by severity:
things that would make a user do something wrong first, then confusing things, then minor
wording issues.

Use this format for each item:

---
**[High/Medium/Low] Page: [Page name] — [Short title of issue]**

> [Quote the exact text that's wrong]

Change to: [Exact replacement text, or clear description of what to add/remove]

*(Code: `File.kt` — one-line explanation of what the code actually does)*

---

Keep it tight. The code reference is just enough to justify the change — a filename and one
sentence, not a walkthrough. Don't explain what you checked and found to be correct.
