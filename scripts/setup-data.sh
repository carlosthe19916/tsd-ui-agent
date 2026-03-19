#!/bin/bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

# Validate required environment variables
: "${GITHUB_PAT:?Environment variable GITHUB_PAT is not set}"
: "${CARLOS_JIRA_TOKEN:?Environment variable CARLOS_JIRA_TOKEN is not set}"
: "${REDHAT_JIRA_TOKEN:?Environment variable REDHAT_JIRA_TOKEN is not set}"

# Helper functions

create_credential() {
  local name="$1"
  local token="$2"
  echo "Creating credential '$name'..." >&2
  local id
  id=$(http --json POST "$BASE_URL/credentials" \
    name="$name" token="$token" | jq -r '.id')
  echo "Created credential '$name' with id: $id" >&2
  echo "$id"
}

create_git() {
  local url="$1"
  local credential_id="$2"
  local fork_url="${3:-}"
  local repo_name
  repo_name=$(basename "$url" .git)
  echo "Creating $repo_name repository..." >&2
  local args=(url="$url" credential:="{\"id\": $credential_id}")
  if [[ -n "$fork_url" ]]; then
    args+=(forkUrl="$fork_url")
  fi
  local id
  id=$(http --json POST "$BASE_URL/gits" "${args[@]}" | jq -r '.id')
  echo "Created $repo_name repository with id: $id" >&2
  echo "$id"
}

create_git_mapping() {
  local project_id="$1"
  local git_id="$2"
  local space="$3"
  local labels="$4"
  echo "Creating git mapping (project=$project_id, git=$git_id, space=$space)..."
  http --json POST "$BASE_URL/projects/$project_id/git-mappings" \
    gitId:="$git_id" space="$space" labels:="$labels"
  echo "Created git mapping"
}

create_project() {
  local name="$1"
  local type="$2"
  local api_url="$3"
  local credential_id="$4"
  local query="$5"
  echo "Creating project '$name'..." >&2
  local id
  id=$(http --json POST "$BASE_URL/projects" \
    name="$name" type="$type" apiUrl="$api_url" \
    credential:="{\"id\": $credential_id}" \
    query="$query" | jq -r '.id')
  echo "Created project '$name' with id: $id" >&2
  echo "$id"
}

sync_project() {
  local name="$1"
  local project_id="$2"
  echo "Syncing project '$name'..."
  http POST "$BASE_URL/projects/$project_id/sync"
  echo "Synced project '$name'"
}

# Credentials
GITHUB_CREDENTIAL_ID=$(create_credential github "$GITHUB_PAT")
CARLOS_JIRA_CREDENTIAL_ID=$(create_credential jira-carlos "$CARLOS_JIRA_TOKEN")
REDHAT_JIRA_CREDENTIAL_ID=$(create_credential jira-redhat "$REDHAT_JIRA_TOKEN")

# Git repositories
TSD_UI_AGENT_GIT_ID=$(create_git git@github.com:carlosthe19916/tsd-ui-agent.git "$GITHUB_CREDENTIAL_ID")

TRUSTIFY_GIT_ID=$(create_git git@github.com:trustificationdemo/trustify.git "$GITHUB_CREDENTIAL_ID" \
  git@github.com:carlosthe19916/trustify.git)
TRUSTIFY_UI_GIT_ID=$(create_git git@github.com:trustificationdemo/trustify-ui.git "$GITHUB_CREDENTIAL_ID" \
  git@github.com:carlosthe19916/trustify-ui.git)

# Projects
TSD_UI_AGENT_PROJECT_ID=$(create_project tsd-ui-agent GITHUB \
  https://api.github.com/repos/carlosthe19916/tsd-ui-agent "$GITHUB_CREDENTIAL_ID" \
  "is:issue state:open ")
TRUSTIFY_PROJECT_ID=$(create_project trustify GITHUB \
  https://api.github.com/repos/trustificationdemo/trustify "$GITHUB_CREDENTIAL_ID" \
  "is:issue state:open ")
TRUSTIFY_UI_PROJECT_ID=$(create_project trustify-ui GITHUB \
  https://api.github.com/repos/trustificationdemo/trustify-ui "$GITHUB_CREDENTIAL_ID" \
  "is:issue state:open ")

CARLOS_JIRA_PROJECT_ID=$(create_project atlasian-carlosthe19916 JIRA \
  https://carlosthe19916-1773473418920.atlassian.net/ "$CARLOS_JIRA_CREDENTIAL_ID" \
  "project = KAN ORDER BY created DESC")
REDHAT_JIRA_PROJECT_ID=$(create_project atlasian-redhat JIRA \
  https://redhat.atlassian.net/ "$REDHAT_JIRA_CREDENTIAL_ID" \
  "labels in (TSD-UI)")

# Git mappings
create_git_mapping "$REDHAT_JIRA_PROJECT_ID" "$TRUSTIFY_UI_GIT_ID" "TC" '["TSD-UI"]'

# Sync projects
sync_project tsd-ui-agent "$TSD_UI_AGENT_PROJECT_ID"
sync_project trustify "$TRUSTIFY_PROJECT_ID"
sync_project trustify-ui "$TRUSTIFY_UI_PROJECT_ID"
sync_project atlasian-carlosthe19916 "$CARLOS_JIRA_PROJECT_ID"
sync_project atlasian-redhat "$REDHAT_JIRA_PROJECT_ID"

echo "Done! Seed data created successfully."
