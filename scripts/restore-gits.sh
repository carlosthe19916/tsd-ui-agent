#!/bin/bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
REPOS_DIR="$HOME/.tsd-agent-ui/repositories"

if [ ! -d "$REPOS_DIR" ]; then
  echo "No repositories directory found at $REPOS_DIR"
  exit 0
fi

# Note: The create flow clones the repo again, so the old clone dirs become orphaned.
# You may want to clean up $REPOS_DIR after restoring.

count=0
errors=0

for repo_dir in "$REPOS_DIR"/*/default; do
  [ -d "$repo_dir/.git" ] || continue

  uuid=$(basename "$(dirname "$repo_dir")")
  echo "Processing $uuid..."

  # Extract remote URL
  url=$(git -C "$repo_dir" remote get-url origin 2>/dev/null || true)
  if [ -z "$url" ]; then
    echo "  SKIP: no origin remote found"
    continue
  fi

  # Extract branch (use empty string for main/master since the app defaults branch to "")
  branch=$(git -C "$repo_dir" rev-parse --abbrev-ref HEAD 2>/dev/null || true)
  if [ "$branch" = "HEAD" ] || [ "$branch" = "main" ] || [ "$branch" = "master" ]; then
    branch=""
  fi

  # Extract fork URL (if fork remote exists)
  fork_url=$(git -C "$repo_dir" remote get-url fork 2>/dev/null || true)

  echo "  url=$url branch=$branch forkUrl=$fork_url"

  # Build httpie args
  args=(--json POST "$BASE_URL/gits" "url=$url")
  [ -n "$branch" ] && args+=("branch=$branch")
  [ -n "$fork_url" ] && args+=("forkUrl=$fork_url")

  if http "${args[@]}" > /dev/null 2>&1; then
    echo "  OK"
    ((count++))
  else
    echo "  FAIL"
    ((errors++))
  fi
done

echo ""
echo "Restored $count repo(s), $errors error(s)."
if [ $count -gt 0 ]; then
  echo "Note: old clone dirs in $REPOS_DIR are now orphaned and can be removed."
fi
