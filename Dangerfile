jira.check(
  key: ["SAAS"],
  url: "https://dimagi-dev.atlassian.net/browse",
  fail_on_warning: false
)

# Warning to encourage a PR description
if github.pr_body.length == 0
    warn "Please add a description to your PR to make it easier to review 👌"
end