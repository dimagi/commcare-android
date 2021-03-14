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

# Make danger out jira ticket link
jira.check(
  key: ["SAAS"],
  url: "https://dimagi-dev.atlassian.net/browse",
  fail_on_warning: false
)

# Output lint issues on PR using 'danger-android_lint'  https://github.com/loadsmart/danger-android_lint
android_lint.report_file = "app/build/reports/lint-results-commcareRelease.xml"
android_lint.filtering = true
android_lint.skip_gradle_task = true
android_lint.lint(inline_mode: true)