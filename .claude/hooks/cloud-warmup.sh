#!/usr/bin/env bash
# Claude Code SessionStart hooks for cloud sessions (claude.ai/code, cloud sessions started from the desktop app).
# The hook modes exit at once unless CLAUDE_CODE_REMOTE is true, so local sessions are unaffected.
#
#   cloud-warmup.sh --env   synchronous hook (milliseconds): route PyPI through the agent proxy for the session
#   cloud-warmup.sh         async hook at startup and resume: `uv sync --locked`, then the first Gradle run
#                           (distribution, dependencies, NeoForm setup, build/mc-sources/, compile); the Gradle
#                           daemon keeps running
#   cloud-warmup.sh --wait  block until the warm-up has finished; run it before the first Gradle command (and after
#                           a resume), because two Gradle runs in one project wait on each other's locks
#
# State lives in .cache/warmup/ (gitignored): status (running, done or failed), summary, log, and stamp (hash of
# the build inputs that were warmed, so resumed sessions skip the work).
set -uo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
state="$root/.cache/warmup"
log="$state/log"

# Cloud VMs resolve no hostnames themselves: every download goes through the agent proxy (HTTPS_PROXY). NO_PROXY
# still lists PyPI, so uv bypasses the proxy for it and fails with "dns error". Drop PyPI from NO_PROXY.
route_pypi_through_proxy() {
  [ -n "${HTTPS_PROXY:-}" ] || return 1
  timeout 5 getent hosts pypi.org >/dev/null 2>&1 && return 1
  local fixed
  fixed="$(printf '%s,%s' "${NO_PROXY:-}" "${no_proxy:-}" | tr ',' '\n' | awk 'NF && !seen[$0]++' \
    | { grep -vxE 'pypi\.org|files\.pythonhosted\.org' || true; } | paste -sd, -)"
  export NO_PROXY="$fixed" no_proxy="$fixed"
}

# Hosts the agent proxy refused since the warm-up started that the log mentions, space-separated.
blocked_hosts() {
  [ -n "${HTTPS_PROXY:-}" ] || return 0
  curl -sS --max-time 5 "$HTTPS_PROXY/__agentproxy/status" 2>/dev/null | python3 -I -c '
import json, sys
since, log = sys.argv[1], open(sys.argv[2], errors="replace").read()
try:
    failures = json.load(sys.stdin).get("recentRelayFailures", [])
except ValueError:
    sys.exit()
hosts = sorted({f["host"].rsplit(":", 1)[0] for f in failures if f.get("host") and f.get("ts", "") >= since})
print(" ".join(h for h in hosts if h in log))
' "$1" "$log" 2>/dev/null
}

# A warm-up holds the lock for as long as it runs.
warmup_running() {
  [ -f "$state/lock" ] && ! flock -n "$state/lock" true
}

wait_for_warmup() {
  local limit="${1:-540}" waited=0
  if [ ! -f "$state/status" ]; then
    echo "No cloud warm-up in this checkout; nothing to wait for."
    return 0
  fi
  while [ "$(cat "$state/status")" = "running" ] && warmup_running; do
    if [ "$waited" -ge "$limit" ]; then
      echo "Cloud warm-up still running after ${limit} s (log: .cache/warmup/log). Run --wait again."
      return 2
    fi
    sleep 5
    waited=$((waited + 5))
  done
  case "$(cat "$state/status")" in
    done)
      cat "$state/summary"
      return 0
      ;;
    running)
      echo "Cloud warm-up was interrupted (log: .cache/warmup/log). Gradle downloads what it needs on demand."
      return 1
      ;;
    *)
      cat "$state/summary"
      echo "Last lines of .cache/warmup/log:"
      grep -v '^Picked up JAVA_TOOL_OPTIONS' "$log" | tail -n 25
      return 1
      ;;
  esac
}

# Runs one warm-up step with its output in the log. `9>&-` keeps the lock out of long-lived children such as
# the Gradle daemon, which would otherwise hold it after the warm-up ends.
step() {
  local name="$1" started rc
  shift
  started=$(date +%s)
  echo "== $name: $*" >>"$log"
  "$@" >>"$log" 2>&1 </dev/null 9>&-
  rc=$?
  echo "== $name: exit code $rc after $(($(date +%s) - started)) s" >>"$log"
  return "$rc"
}

# Gradle gets two more attempts, but only after a rate limit or a transient server error: Maven Central answers
# download bursts with "429 Too Many Requests" for a few minutes. Any other failure ends the warm-up at once.
gradle_with_retries() {
  local delay from
  for delay in 0 60 120; do
    if [ "$delay" -gt 0 ]; then
      tail -n +"$from" "$log" | grep -qE 'status code (429|502|503|504)|Too Many Requests' || return 1
      echo "== retrying in $delay s" >>"$log"
      sleep "$delay"
    fi
    from=$(($(wc -l <"$log") + 1))
    step gradle ./gradlew --console=plain :mod:extractMinecraftSources classes && return 0
  done
  return 1
}

# One line of JSON on stdout: Claude Code hands additionalContext to Claude when the async hook ends.
report() {
  python3 -I -c '
import json, sys
print(json.dumps({"hookSpecificOutput": {"hookEventName": "SessionStart", "additionalContext": sys.argv[1]}}))
' "$1"
}

warm_up() {
  [ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || return 0
  mkdir -p "$state"
  exec 9>"$state/lock"
  flock -n 9 || return 0 # another warm-up is running

  local inputs
  inputs="$(cd "$root" && cat gradle.properties gradle/wrapper/gradle-wrapper.properties settings.gradle \
    build.gradle ./*/build.gradle uv.lock pyproject.toml .python-version 2>/dev/null | sha256sum | cut -d' ' -f1)"
  if [ "$(cat "$state/status" 2>/dev/null)" = "done" ] && [ "$(cat "$state/stamp" 2>/dev/null)" = "$inputs" ] \
    && [ -d "$root/build/mc-sources" ]; then
    return 0
  fi

  local since started summary hosts failed_step=""
  since="$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)"
  started=$(date +%s)
  rm -f "$state/stamp" "$state/summary"
  : >"$log"
  echo "running" >"$state/status"
  route_pypi_through_proxy || true
  cd "$root" || return 1

  if ! step uv uv sync --locked; then
    failed_step="uv sync"
  elif ! gradle_with_retries; then
    failed_step="Gradle"
  fi

  if [ -z "$failed_step" ]; then
    summary="Cloud warm-up done in $(($(date +%s) - started)) s: Python environment synced, Gradle dependencies"
    summary+=" and NeoForm setup cached, Minecraft sources in build/mc-sources/, classes compiled, Gradle daemon"
    summary+=" running."
    echo "$inputs" >"$state/stamp"
    echo "$summary" >"$state/summary"
    echo "done" >"$state/status"
  else
    summary="Cloud warm-up failed in the $failed_step step after $(($(date +%s) - started)) s."
    hosts="$(blocked_hosts "$since")"
    if [ -n "$hosts" ]; then
      summary+=" The network allowlist blocked: $hosts (add them under Network access in the cloud environment's"
      summary+=" settings)."
    elif grep -qE 'status code 403|403 Forbidden|response 403' "$log"; then
      summary+=" A download was refused (HTTP 403); \`curl -sS \"\$HTTPS_PROXY/__agentproxy/status\"\` lists the"
      summary+=" refused hosts."
    fi
    summary+=" Log: .cache/warmup/log. Gradle and uv still download what they need on demand."
    echo "== $summary" >>"$log"
    echo "$summary" >"$state/summary"
    echo "failed" >"$state/status"
  fi
  report "$summary"
}

case "${1:-}" in
  --env)
    [ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || exit 0
    if route_pypi_through_proxy && [ -n "${CLAUDE_ENV_FILE:-}" ]; then
      printf "export NO_PROXY='%s' no_proxy='%s'\n" "$NO_PROXY" "$NO_PROXY" >>"$CLAUDE_ENV_FILE"
    fi
    ;;
  --wait)
    wait_for_warmup "${2:-540}"
    ;;
  "")
    warm_up
    ;;
  *)
    echo "usage: cloud-warmup.sh [--env | --wait [seconds]]" >&2
    exit 64
    ;;
esac
