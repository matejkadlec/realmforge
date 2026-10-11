#!/usr/bin/env bash
# Claude Code PostToolUse hook: after an edit in content/ or tools/, run the fast content validation
# (`uv run rf validate`, ~1 s). Exit code 2 sends the problems back to Claude so it fixes them right away.
# Deliberately no Gradle here: it is far too slow for a per-edit hook.
set -uo pipefail

root="${CLAUDE_PROJECT_DIR:-$(pwd)}"
file_path="$(python3 -c 'import json, sys; print(json.load(sys.stdin).get("tool_input", {}).get("file_path", ""))' 2>/dev/null || true)"

case "$file_path" in
  "$root"/content/* | "$root"/tools/* | content/* | tools/*) ;;
  *) exit 0 ;;
esac

if ! command -v uv >/dev/null 2>&1; then
  echo "validate-content hook: uv not found, skipping content validation" >&2
  exit 0
fi

cd "$root" || exit 0
if ! output="$(uv run --frozen --quiet rf validate 2>&1)"; then
  {
    echo "Content validation failed after editing ${file_path#"$root"/}:"
    echo "$output"
    echo "Fix the problems above, then run 'uv run rf gen' to regenerate derived files."
  } >&2
  exit 2
fi
exit 0
