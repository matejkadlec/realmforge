#!/usr/bin/env bash
# Claude Code SessionStart hook: in cloud sessions (claude.ai/code, cloud sessions started from the desktop app),
# install the locked Python environment so `uv run rf ...`, pytest, ruff and the validate-content hook work from
# the first command. Local sessions (WSL) skip it. Gradle needs nothing here: `./gradlew` downloads on demand.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(pwd)}"

if ! command -v uv >/dev/null 2>&1; then
  echo "session-start hook: uv not found, skipping Python setup" >&2
  exit 0
fi

# Cloud containers resolve no hostnames themselves: every download goes through the HTTPS proxy. NO_PROXY still
# lists PyPI, so uv bypasses the proxy for it and fails with "dns error". Route PyPI through the proxy, both here
# and, via CLAUDE_ENV_FILE, in every later shell command of the session.
if [ -n "${HTTPS_PROXY:-}" ] && ! getent hosts pypi.org >/dev/null 2>&1; then
  no_proxy_fixed="$(printf '%s' "${NO_PROXY:-${no_proxy:-}}" | tr ',' '\n' \
    | { grep -vxE 'pypi\.org|files\.pythonhosted\.org' || true; } | paste -sd, -)"
  export NO_PROXY="$no_proxy_fixed" no_proxy="$no_proxy_fixed"
  if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
    printf 'export NO_PROXY=%q no_proxy=%q\n' "$no_proxy_fixed" "$no_proxy_fixed" >>"$CLAUDE_ENV_FILE"
  fi
fi

uv sync --locked
