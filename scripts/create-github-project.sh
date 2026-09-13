#!/usr/bin/env bash
# Creates the GitHub Project (v2) board and adds every issue to it.
#
# Requires the `project` scope, which the default `gh` login does not include:
#   gh auth refresh -s project,read:project
#
# Safe to re-run: adding an issue that is already on the board is a no-op.
set -euo pipefail

OWNER="${OWNER:-Artanniel}"
REPO="${REPO:-Artanniel/quarkus-edge-tts}"
TITLE="${TITLE:-Quarkus Edge-TTS}"

# 'project' (full control) implies read access; either scope is sufficient.
if ! gh auth status 2>&1 | grep -E "Token scopes.*'(read:)?project'" >/dev/null; then
  echo "error: token is missing the project scope." >&2
  echo "run: gh auth refresh -s project,read:project" >&2
  exit 1
fi

number=$(gh project list --owner "$OWNER" --format json \
  | jq -r --arg t "$TITLE" '.projects[] | select(.title == $t) | .number' | head -1)

if [ -z "$number" ]; then
  echo "creating project '$TITLE'..."
  number=$(gh project create --owner "$OWNER" --title "$TITLE" --format json | jq -r '.number')
else
  echo "project '$TITLE' already exists as #$number, reusing it"
fi

url=$(gh project view "$number" --owner "$OWNER" --format json | jq -r '.url')
echo "project: $url"

total=$(gh issue list --repo "$REPO" --state all --limit 200 --json number --jq 'length')
echo "adding $total issues..."

gh issue list --repo "$REPO" --state all --limit 200 --json number,title \
  | jq -r '.[] | "\(.number)\t\(.title)"' \
  | sort -n \
  | while IFS=$'\t' read -r n title; do
      gh project item-add "$number" --owner "$OWNER" \
        --url "https://github.com/$REPO/issues/$n" >/dev/null 2>&1 \
        && printf '  added #%-3s %s\n' "$n" "$title" \
        || printf '  skipped #%-3s (already present)\n' "$n"
    done

echo
echo "done: $url"
echo "group the board by Milestone to get the M0-M7 delivery order."
