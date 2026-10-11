#!/usr/bin/env bash
# Runs one Gradle invocation through a queue shared by every worktree on this machine, so parallel agent sessions
# wait for memory instead of failing (docs/ai-development-flow.md, "Gradle queue").
#
#   .claude/scripts/gradle-queue.sh build
#   .claude/scripts/gradle-queue.sh runGameTestServer
#
# A run starts once one of the slots is free and enough memory is available; until then it waits and says why.
# Runs are admitted one at a time: the admission lock stays held for a grace period after a start (or until that
# run ends), so the next run checks memory only after the new JVMs have claimed theirs. A run that dies of memory
# (a JVM that cannot reserve its heap, an OutOfMemoryError, an OOM-killed daemon or game JVM) waits for memory and
# runs again, once. Every other failure keeps Gradle's own exit code. Exit code 75 means the queue gave up waiting,
# which is not a build result.
#
# Settings (environment):
#   RF_GRADLE_SLOTS             concurrent runs; default one per 5 GiB of RAM, at least 1
#   RF_GRADLE_MIN_FREE_MIB      MemAvailable a run needs before it starts; default 4096
#   RF_GRADLE_QUEUE_TIMEOUT_MIN how long each wait (for a slot, for admission) may last; default 120
#   RF_GRADLE_ADMIT_GRACE_S     how long a start keeps the admission lock; default 60
set -euo pipefail

if [[ $# -eq 0 ]]; then
    echo "usage: $0 <gradle arguments>" >&2
    exit 64
fi

cd "$(git rev-parse --show-toplevel)"

if [[ -z ${JAVA_HOME:-} && -s $HOME/.sdkman/bin/sdkman-init.sh ]]; then
    set +u
    # shellcheck disable=SC1091
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    set -u
fi

mem_available_mib() { awk '/^MemAvailable:/ { print int($2 / 1024) }' /proc/meminfo; }
mem_total_mib() { awk '/^MemTotal:/ { print int($2 / 1024) }' /proc/meminfo; }

slots=${RF_GRADLE_SLOTS:-$(($(mem_total_mib) / 5120))}
((slots >= 1)) || slots=1
need_mib=${RF_GRADLE_MIN_FREE_MIB:-4096}
timeout_min=${RF_GRADLE_QUEUE_TIMEOUT_MIN:-120}
grace_s=${RF_GRADLE_ADMIT_GRACE_S:-60}
lock_dir=${XDG_CACHE_HOME:-$HOME/.cache}/realmforge/gradle-queue
mkdir -p "$lock_dir"

lock_fd=
slot_no=
grace_pid=
deadline=0
last_note=0

log=$(mktemp "${TMPDIR:-/tmp}/gradle-queue.XXXXXX")
# shellcheck disable=SC2317  # invoked by the EXIT trap
cleanup() {
    rm -f "$log"
    # A finished run no longer needs the admission lock its grace period holds.
    if [[ -n $grace_pid ]]; then kill "$grace_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

note() {
    # Prints a queue message at most once a minute.
    local now
    now=$(date +%s)
    if ((now - last_note >= 60)); then
        echo "gradle-queue: $*" >&2
        last_note=$now
    fi
}

start_wait_clock() {
    # Every wait gets the full timeout; time spent running Gradle never counts against it.
    deadline=$(($(date +%s) + timeout_min * 60))
    last_note=0
}

give_up_if_late() {
    if (($(date +%s) >= deadline)); then
        echo "gradle-queue: gave up after ${timeout_min} minutes in the queue ($1)." >&2
        echo "gradle-queue: this is not a build result; run it again once other Gradle runs have finished." >&2
        exit 75
    fi
}

take_slot() {
    local i fd
    start_wait_clock
    while true; do
        for ((i = 1; i <= slots; i++)); do
            exec {fd}>"$lock_dir/slot-$i.lock"
            if flock -n "$fd"; then
                lock_fd=$fd
                slot_no=$i
                return
            fi
            exec {fd}>&-
        done
        note "waiting: all $slots slot(s) busy with Gradle runs from other worktrees"
        give_up_if_late "no free slot"
        sleep 15
    done
}

admit() {
    # Checks memory and starts under one lock, so two waiting runs never start on the same free memory.
    local fd free
    start_wait_clock
    exec {fd}>"$lock_dir/admission.lock"
    until flock -w 15 "$fd"; do
        note "waiting: another Gradle run is starting"
        give_up_if_late "another run was starting"
    done
    while true; do
        free=$(mem_available_mib)
        ((free >= need_mib)) && break
        note "waiting: ${free} MiB available, a run needs ${need_mib} MiB"
        give_up_if_late "not enough memory"
        sleep 15
    done
    # A background sleep shares the lock and keeps it through the grace period; this script lets go of its copy.
    if [[ -n $grace_pid ]]; then kill "$grace_pid" 2>/dev/null || true; fi
    sleep "$grace_s" >/dev/null 2>&1 {lock_fd}>&- &
    grace_pid=$!
    exec {fd}>&-
}

ran_out_of_memory() {
    # $1: Gradle's exit code. 137 is SIGKILL, which the kernel's OOM killer sends.
    (($1 == 137)) || grep -q -E \
        'java\.lang\.OutOfMemoryError|Could not reserve enough space|insufficient memory for the Java Runtime|Gradle build daemon disappeared unexpectedly|non-zero exit value 137' \
        "$log"
}

take_slot
for attempt in 1 2; do
    admit
    echo "gradle-queue: running ./gradlew $* (slot $slot_no of $slots, $(mem_available_mib) MiB available)" >&2
    set +e
    # The slot lock stays with this script; Gradle and the processes it starts don't inherit it.
    ./gradlew "$@" {lock_fd}>&- 2>&1 | tee "$log"
    rc=${PIPESTATUS[0]}
    set -e
    if ((rc == 0)) || ! ran_out_of_memory "$rc"; then
        exit "$rc"
    fi
    if ((attempt == 1)); then
        echo "gradle-queue: the run ran out of memory (exit $rc); waiting for memory and running it again." >&2
    fi
done
echo "gradle-queue: the run ran out of memory twice with ${need_mib} MiB available at its start; this is a" >&2
echo "gradle-queue: real failure (look for a leak or a runaway test), not a queue problem." >&2
exit "$rc"
