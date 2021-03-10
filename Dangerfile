# Make it more obvious that a PR is a work in progress and shouldn't be merged yet
if github.pr_title.include? "[WIP]"
  warn "PR is classed as Work in Progress"
end

# Warning to encourage a PR description
if github.pr_body.length == 0
    warn "Please provide a summary in the PR description to make it easier to review"
end

# Ensure that labels have been used on the PR
if github.pr_labels.empty?
    failure "Please add labels to this PR"
end

jira.check(
  key: ["SAAS"],
  url: "https://dimagi-dev.atlassian.net/browse",
  fail_on_warning: false
)
