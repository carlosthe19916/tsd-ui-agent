#!/bin/bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

# Validate required environment variables
: "${GITHUB_PAT:?Environment variable GITHUB_PAT is not set}"
: "${JIRA_TOKEN:?Environment variable JIRA_TOKEN is not set}"

# Create credential: github
echo "Creating credential 'github'..."
GITHUB_CREDENTIAL_ID=$(http --json POST "$BASE_URL/credentials" \
  name=github token="$GITHUB_PAT" | jq -r '.id')
echo "Created credential 'github' with id: $GITHUB_CREDENTIAL_ID"

# Create credential: jira
echo "Creating credential 'jira'..."
JIRA_CREDENTIAL_ID=$(http --json POST "$BASE_URL/credentials" \
  name=jira token="$JIRA_TOKEN" | jq -r '.id')
echo "Created credential 'jira' with id: $JIRA_CREDENTIAL_ID"

# Create git repository
echo "Creating git repository..."
http --json POST "$BASE_URL/gits" \
  url=git@github.com:carlosthe19916/tsd-ui-agent.git
echo "Created git repository"

# Create project: tsd-ui-agent (GitHub)
echo "Creating project 'tsd-ui-agent'..."
GITHUB_PROJECT_ID=$(http --json POST "$BASE_URL/projects" \
  name=tsd-ui-agent \
  type=GITHUB \
  apiUrl=https://api.github.com/repos/carlosthe19916/tsd-ui-agent \
  credential:="{\"id\": $GITHUB_CREDENTIAL_ID}" \
  query="is:issue state:open " | jq -r '.id')
echo "Created project 'tsd-ui-agent' with id: $GITHUB_PROJECT_ID"

# Create project: atlasian-carlosthe19916 (Jira)
echo "Creating project 'atlasian-carlosthe19916'..."
JIRA_PROJECT_ID=$(http --json POST "$BASE_URL/projects" \
  name=atlasian-carlosthe19916 \
  type=JIRA \
  apiUrl=https://carlosthe19916-1773473418920.atlassian.net/ \
  credential:="{\"id\": $JIRA_CREDENTIAL_ID}" \
  query="project = KAN ORDER BY created DESC" | jq -r '.id')
echo "Created project 'atlasian-carlosthe19916' with id: $JIRA_PROJECT_ID"

# Sync projects
echo "Syncing project 'tsd-ui-agent'..."
http POST "$BASE_URL/projects/$GITHUB_PROJECT_ID/sync"
echo "Synced project 'tsd-ui-agent'"

echo "Syncing project 'atlasian-carlosthe19916'..."
http POST "$BASE_URL/projects/$JIRA_PROJECT_ID/sync"
echo "Synced project 'atlasian-carlosthe19916'"

echo "Done! Seed data created successfully."
