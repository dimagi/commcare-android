---
name: docs-affected-by-branch
description: >
  Use when working on a CommCare Android branch, before or after its PR is opened, and the user
  asks which Confluence docs the change affects or makes out of date, or what belongs in the PR's
  Documentation section.
argument-hint: "[Jira ticket key or URL, or \"none\"]"
# Atlassian write tools. The server name differs between installs, so it is a wildcard.
disallowed-tools:
  - mcp__*__executeWrite
  - mcp__*__executeDestructive
  - mcp__*__createJiraIssue
  - mcp__*__editJiraIssue
  - mcp__*__transitionJiraIssue
  - mcp__*__addOrEditJiraIssueComment
  - mcp__*__createConfluenceContent
  - mcp__*__updateConfluenceContent
  - mcp__*__addTeamworkGraphContext
  - mcp__*__addGraphContext
---

# Docs Affected by Branch

Find the Confluence pages the current branch makes out of date and list the edits each one
needs. The code is the local checkout — don't clone anything.

Read [confluence-docs.md](confluence-docs.md) first. Steps 4 and 5 use it.

## Step 1: Check Atlassian access

Check whether the Atlassian MCP tools for Jira and Confluence are available. The later steps
say what to do in each case.

If they are not available, tell the user before going further: you can't read Jira or
Confluence, so you will ask them for the ticket's documentation links and for the pages that
cover the change, and the report will list what changed for each page instead of exact edits.

## Step 2: Get the ticket

Run `gh pr view --json body,baseRefName` to see whether the branch has a PR. Then take the
ticket from the first of these that applies:

1. **A ticket was passed as an argument** — use it. "none" means there is no ticket.
2. **The branch has a PR whose description links a Jira ticket** — use that ticket.
3. **Otherwise** — ask the user for the Jira ticket key or URL. There may not be one, so accept
   "none".

If there is a ticket, collect the Confluence pages it links: its **Documentation to Update**
list, or any Confluence links in its description. These are the pages the ticket expects to
change.

- **With Atlassian access:** fetch the ticket and read the links from it.
- **Without it:** ask the user to paste the ticket's documentation links. Accept "none".

If the PR exists and its description has a **Documentation** section, collect the pages listed
there too.

## Step 3: Read the change

Diff the branch against its base, where `<base>` is the PR's `baseRefName`, or `main` when there
is no PR. Fetch first so the base is current:

```bash
git fetch origin <base>
git diff origin/<base>...HEAD
```

From the diff, collect the things a user can see or rely on: page and button labels, column
headers, status names, error and notification strings, CSV column names, permissions, limits,
and the names of new or changed features. Ignore refactors, tests and internal code that
changes no behaviour.

If nothing user-facing changed, say so in one line, use `None` as the Documentation list, and go
to Step 6.

## Step 4: Find candidate pages

Follow **Finding affected pages** in the reference, using the terms from Step 3. Add every page
the ticket or the PR's Documentation section links, even if search didn't return it.

## Step 5: Report

- **With Atlassian access:** check each candidate page against the diff only, not against the
  whole codebase. List the edits in the **Edit format** from the reference.
- **Without it:** you can't read the pages, so for each one list the user-facing changes from
  Step 3 it most likely needs to reflect.

End with a list for the PR's **Documentation** section:

- Pages that need an edit, with links
- Pages linked on the ticket or the PR that the diff turned out not to affect
- Pages the diff affects that neither the ticket nor the PR linked
- "New page needed: …" where applicable

## Step 6: Update the PR

- **If the branch has no PR:** stop. The user pastes the list into the description when they
  open the PR.
- **If it has one:** show how its **Documentation** section differs from the list, then offer
  to replace the section with `gh pr edit`. Change only that section, and only after the user
  agrees. If the description has no Documentation section, offer to add one.
