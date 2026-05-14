#!/bin/bash
jenkins_branch_name="$1"
local_branch_name="$(git rev-parse --abbrev-ref HEAD)"
branchName=''

if [[ -z "$jenkins_branch_name" ]]; then
  branchName="$local_branch_name"
else
  branchName="$jenkins_branch_name"
  if [[ "$branchName" == "main" || "$branchName" == "develop" || "$branchName" == "staging" || "$branchName" == "testing" ]]; then
    exit 0
  fi
fi

valid_branch_regex='^(feat|fix|hotfix|refactor|chore|test|docs|perf|style)\/[a-zA-Z0-9][a-zA-Z0-9\-]*$'

message="
Branch name validation failed: '$branchName'
Branch names must follow: <type>/<description>

Allowed types: feat | fix | hotfix | refactor | chore | test | docs | perf | style
Examples: feat/user-authentication, fix/login-bug, hotfix/null-pointer
"

if [[ ! "$branchName" =~ $valid_branch_regex ]]; then
  echo "$message"
  exit 1
else
  echo "Branch name '$branchName' is valid."
fi

exit 0
