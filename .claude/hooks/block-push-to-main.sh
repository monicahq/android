#!/usr/bin/env bash
# Refuse any git push that targets main, or any push made while main is checked out.
# Work always goes through a branch and a pull request.
cmd=$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("tool_input",{}).get("command",""))')
echo "$cmd" | grep -qE '(^|[;&|[:space:]])git([[:space:]]+-C[[:space:]]+[^[:space:]]+)?[[:space:]]+push' || exit 0

branch=$(git -C "${CLAUDE_PROJECT_DIR:-.}" rev-parse --abbrev-ref HEAD 2>/dev/null)
if echo "$cmd" | grep -qE '(^|[[:space:]:+])(refs/heads/)?main([[:space:]]|$)' || [ "$branch" = "main" ]; then
  echo "Blocked: never push to main. Create a dated branch (YYYY-MM-DD-name) and open a pull request." >&2
  exit 2
fi
exit 0
