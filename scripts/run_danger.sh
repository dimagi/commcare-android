#!/bin/bash
# To run on jenkins, use `bash scripts/run_danger.sh` in shell.

# Assumes that we're in commcare-android directory.

# Unfortunately jenkins errors out `source: not found` so we can't really save these in .bashrc
export PATH="$HOME/.rbenv/bin:$PATH"
export PATH="$HOME/.rbenv/shims:$PATH"
export PATH="$HOME/.rbenv/plugins/ruby-build/bin:$PATH"

# We need ruby v3.0.0 to be able to use gems.
rbenv global 3.0.0

gem install danger
gem install danger-jira
gem install danger-android_lint

danger --fail-on-errors=true