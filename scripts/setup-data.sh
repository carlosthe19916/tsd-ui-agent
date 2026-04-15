#!/bin/bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Validate required tools
for cmd in envsubst yq curl jq; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "Error: '$cmd' is not installed" >&2; exit 1; }
done

# Validate required environment variables
: "${REDHAT_JIRA_TOKEN:?Environment variable REDHAT_JIRA_TOKEN is not set}"
: "${GITHUB_PAT:?Environment variable GITHUB_PAT is not set}"
: "${REDHAT_GITLAB_PAT:?Environment variable REDHAT_GITLAB_PAT is not set}"

# Substitute env vars in YAML, convert to JSON, and POST to the import endpoint
envsubst < "$SCRIPT_DIR/setup-data.yaml" \
  | yq -o=json \
  | curl -s -X POST "$BASE_URL/import" \
      -H "Content-Type: application/json" \
      -d @- \
  | jq .

echo "Done! Seed data imported successfully."