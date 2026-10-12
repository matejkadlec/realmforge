#!/usr/bin/env bash
# Regression suite for .claude/scripts/gradle-queue.sh: in a cloud session it starts no Gradle run while the
# SessionStart warm-up (.claude/hooks/cloud-warmup.sh) still runs Gradle in the same checkout (RLM-74).
#
#   bash tests/test_gradle_queue.sh
#
# Each case runs the real queue and warm-up scripts in a throwaway repository whose ./gradlew is a stub that
# records whether the warm-up still held its lock when the run started.
set -euo pipefail

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TEST_ROOT="$(mktemp -d)"
holder_pid=

cleanup() {
  if [[ -n $holder_pid ]]; then
    kill "$holder_pid" 2>/dev/null || true
    wait "$holder_pid" 2>/dev/null || true
  fi
  rm -rf "$TEST_ROOT"
}

trap cleanup EXIT

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

assert_contains() {
  grep -Fq -- "$2" <<< "$1" || fail "Expected the queue output to contain: $2"
}

new_fixture() {
  local repo="$TEST_ROOT/$1"

  mkdir -p "$repo/.claude/scripts" "$repo/.claude/hooks" "$repo/.cache/warmup"
  git init -q "$repo"
  cp "$SOURCE_ROOT/.claude/scripts/gradle-queue.sh" "$repo/.claude/scripts/"
  cp "$SOURCE_ROOT/.claude/hooks/cloud-warmup.sh" "$repo/.claude/hooks/"
  printf '%s\n' \
    '#!/usr/bin/env bash' \
    'if flock -n .cache/warmup/lock true; then echo after-warmup > gradle-ran; else echo during-warmup > gradle-ran; fi' \
    'echo "stub gradle: $*"' > "$repo/gradlew"
  chmod +x "$repo/gradlew" "$repo/.claude/scripts/gradle-queue.sh" "$repo/.claude/hooks/cloud-warmup.sh"
  : > "$repo/.cache/warmup/log"
  printf '%s\n' "$repo"
}

# A warm-up that holds its lock for $2 seconds, then ends with status $3 (done or failed), as warm_up does.
start_warmup() {
  local repo="$1"

  echo running > "$repo/.cache/warmup/status"
  flock "$repo/.cache/warmup/lock" bash -c \
    'sleep "$1"; echo "warm-up summary" > "$2/summary"; echo "$3" > "$2/status"' _ "$2" "$repo/.cache/warmup" "$3" &
  holder_pid=$!
  # The lock is taken asynchronously; wait until it is held.
  until ! flock -n "$repo/.cache/warmup/lock" true; do sleep 0.1; done
}

stop_warmup() {
  wait "$holder_pid" 2>/dev/null || true
  holder_pid=
}

# Runs the queue in the fixture with fast polling and a private lock directory; prints its output, then its exit code.
run_queue() {
  local repo="$1"
  shift
  local rc=0
  local output

  output="$(cd "$repo" && env XDG_CACHE_HOME="$repo/.queue-cache" RF_GRADLE_POLL_S=1 RF_GRADLE_MIN_FREE_MIB=0 \
    RF_GRADLE_ADMIT_GRACE_S=0 "$@" .claude/scripts/gradle-queue.sh build 2>&1)" || rc=$?
  printf '%s\n%s\n' "$output" "$rc"
}

exit_code() {
  tail -n 1 <<< "$1"
}

test_cloud_run_waits_for_a_running_warmup() {
  local repo
  local result

  repo="$(new_fixture waits)"
  start_warmup "$repo" 3 done
  result="$(run_queue "$repo" CLAUDE_CODE_REMOTE=true)"
  stop_warmup

  [[ "$(exit_code "$result")" == 0 ]] || fail "Expected the queued run to succeed: $result"
  [[ "$(cat "$repo/gradle-ran")" == after-warmup ]] || fail "Expected Gradle to start only after the warm-up ended."
  assert_contains "$result" "waiting: the cloud warm-up is still running Gradle in this checkout"
}

test_failed_warmup_does_not_block_the_run() {
  local repo
  local result

  repo="$(new_fixture failed)"
  start_warmup "$repo" 1 failed
  result="$(run_queue "$repo" CLAUDE_CODE_REMOTE=true)"
  stop_warmup

  [[ "$(exit_code "$result")" == 0 ]] || fail "Expected the run after a failed warm-up to succeed: $result"
  [[ "$(cat "$repo/gradle-ran")" == after-warmup ]] || fail "Expected Gradle to start after the failed warm-up."
  assert_contains "$result" "the cloud warm-up failed or was interrupted"

  # A warm-up that died while running (status still running, lock free) does not block either.
  echo running > "$repo/.cache/warmup/status"
  rm -f "$repo/gradle-ran"
  result="$(run_queue "$repo" CLAUDE_CODE_REMOTE=true)"
  [[ "$(exit_code "$result")" == 0 ]] || fail "Expected the run after an interrupted warm-up to succeed: $result"
  [[ -f "$repo/gradle-ran" ]] || fail "Expected Gradle to run after an interrupted warm-up."
}

test_checkout_without_warmup_runs_at_once() {
  local repo
  local result

  repo="$(new_fixture none)"
  rm -rf "$repo/.cache/warmup"
  mkdir -p "$repo/.cache/warmup"
  result="$(run_queue "$repo" CLAUDE_CODE_REMOTE=true)"

  [[ "$(exit_code "$result")" == 0 ]] || fail "Expected a run without a warm-up to succeed: $result"
  [[ -f "$repo/gradle-ran" ]] || fail "Expected Gradle to run without a warm-up."
}

# Local sessions never run the warm-up, so the queue does not look for one.
test_local_run_does_not_wait() {
  local repo
  local result

  repo="$(new_fixture local)"
  start_warmup "$repo" 3 done
  result="$(run_queue "$repo" CLAUDE_CODE_REMOTE=)"
  stop_warmup

  [[ "$(exit_code "$result")" == 0 ]] || fail "Expected the local run to succeed: $result"
  [[ "$(cat "$repo/gradle-ran")" == during-warmup ]] || fail "Expected a local run not to wait for a warm-up."
}

test_queue_gives_up_on_an_endless_warmup() {
  local repo
  local result

  repo="$(new_fixture timeout)"
  start_warmup "$repo" 3 done
  result="$(run_queue "$repo" CLAUDE_CODE_REMOTE=true RF_GRADLE_QUEUE_TIMEOUT_MIN=0)"
  stop_warmup

  [[ "$(exit_code "$result")" == 75 ]] || fail "Expected the queue to give up with exit code 75: $result"
  [[ ! -e "$repo/gradle-ran" ]] || fail "Expected no Gradle run while the warm-up was still running."
  assert_contains "$result" "gave up after 0 minutes in the queue (the cloud warm-up was still running)"
}

test_cloud_run_waits_for_a_running_warmup
test_failed_warmup_does_not_block_the_run
test_checkout_without_warmup_runs_at_once
test_local_run_does_not_wait
test_queue_gives_up_on_an_endless_warmup

echo "PASS: gradle-queue.sh cloud warm-up suite"
