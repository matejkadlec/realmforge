#!/usr/bin/env bash
set -euo pipefail

# The script under test: git_clean_worktrees.sh by default, or its PowerShell
# port git_clean_worktrees.ps1, which the fixtures start through a shim under
# the same name, so every test runs unchanged against either. The port needs
# pwsh (PowerShell 7) on PATH, or its path in PWSH.
SCRIPT_UNDER_TEST="${1:-$(cd "$(dirname "$0")/.." && pwd)/git_clean_worktrees.sh}"
IMPLEMENTATION=sh
if [[ "$SCRIPT_UNDER_TEST" == *.ps1 ]]; then
  IMPLEMENTATION=ps1
  command -v "${PWSH:-pwsh}" >/dev/null || { echo "FAIL: pwsh is needed to test $SCRIPT_UNDER_TEST" >&2; exit 1; }
fi
TEST_ROOT="$(mktemp -d)"

cleanup() {
  rm -rf "$TEST_ROOT"
}

trap cleanup EXIT

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

assert_branch_exists() {
  git -C "$1" show-ref --verify --quiet "refs/heads/$2" || fail "Expected branch $2 to exist."
}

assert_branch_missing() {
  if git -C "$1" show-ref --verify --quiet "refs/heads/$2"; then
    fail "Expected branch $2 to be removed."
  fi
}

assert_path_exists() {
  [[ -e "$1" ]] || fail "Expected path $1 to exist."
}

assert_path_missing() {
  [[ ! -e "$1" ]] || fail "Expected path $1 to be removed."
}

assert_output_contains() {
  grep -Fq -- "$2" <<< "$1" || fail "Expected cleanup output to contain: $2"
}

strip_colors() {
  sed $'s/\x1b\\[[0-9;]*m//g'
}

assert_output_excludes() {
  if grep -Fq -- "$2" <<< "$1"; then
    fail "Expected cleanup output not to contain: $2"
  fi
}

# The two worktree homes of a fixture checkout, as in the real repository:
# Claude Code's session worktrees inside it, the flow worktrees beside it.
session_home() {
  printf '%s/.claude/worktrees\n' "$1"
}

flow_home() {
  printf '%s/realmforge-worktrees\n' "${1%/*}"
}

new_fixture() {
  local name="$1"
  local repo_name="${2:-repo}"
  local fixture="$TEST_ROOT/$name"
  local remote="$fixture/remote.git"
  local repo="$fixture/$repo_name"

  mkdir -p "$fixture" "${repo%/*}"
  git init --bare --initial-branch=master "$remote" >/dev/null
  git clone "$remote" "$repo" >/dev/null
  # Fixture commands must finish synchronously so the EXIT trap cannot race a
  # detached maintenance process that is still writing into the object store.
  git -C "$repo" config maintenance.auto false
  git -C "$repo" config gc.auto 0
  git -C "$repo" config user.email "cleanup-test@example.test"
  git -C "$repo" config user.name "Cleanup Test"
  if [[ "$IMPLEMENTATION" == ps1 ]]; then
    cp "$SCRIPT_UNDER_TEST" "$repo/git_clean_worktrees.ps1"
    printf '%s\n' \
      '#!/usr/bin/env bash' \
      'exec "${PWSH:-pwsh}" -NoProfile -File "${0%/*}/git_clean_worktrees.ps1" "$@"' > "$repo/git_clean_worktrees.sh"
    git -C "$repo" add git_clean_worktrees.ps1
  else
    cp "$SCRIPT_UNDER_TEST" "$repo/git_clean_worktrees.sh"
  fi
  chmod +x "$repo/git_clean_worktrees.sh"
  git -C "$repo" add git_clean_worktrees.sh
  git -C "$repo" commit -m "Add cleanup helper" >/dev/null
  git -C "$repo" push origin master >/dev/null
  # Session worktrees live under the ignored .claude/worktrees/ directory, as in
  # the real repository; flow worktrees live beside the checkout.
  printf '.claude/worktrees/\n' >> "$repo/.git/info/exclude"

  mkdir -p "$repo/fake-bin"
  printf '%s\n' \
    '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    'if [[ "$1" == "api" ]]; then' \
    '  sha="${2##*/}"' \
    '  if [[ " ${FAKE_GH_KNOWN_COMMITS:-} " == *" $sha "* ]]; then printf "%s\\n" "$sha"; exit 0; fi' \
    '  echo "No commit found for SHA: $sha" >&2; exit 1' \
    'fi' \
    'branch=""' \
    'state=""' \
    'while [[ $# -gt 0 ]]; do' \
    '  if [[ "$1" == "--head" ]]; then branch="$2"; shift 2; continue; fi' \
    '  if [[ "$1" == "--state" ]]; then state="$2"; shift 2; continue; fi' \
    '  shift' \
    'done' \
    'if [[ "$state" == "all" ]]; then' \
    '  merge_commit="$(git log --merges -1 --format=%H --grep="from $branch\$" origin/master 2>/dev/null || true)"' \
    '  case "${FAKE_GH_ALL_MODE:-none}" in' \
    '    merged) printf "421|MERGED|master|%s|%s\\n" "$branch" "$merge_commit" ;;' \
    '    merged-then-open) printf "421|MERGED|master|%s|%s\\n422|OPEN|master|%s|\\n" "$branch" "$merge_commit" "$branch" ;;' \
    '    merged-then-closed) printf "421|MERGED|master|%s|%s\\n422|CLOSED|master|%s|\\n" "$branch" "$merge_commit" "$branch" ;;' \
    '    open-then-merged) printf "420|OPEN|master|%s|\\n421|MERGED|master|%s|%s\\n" "$branch" "$branch" "$merge_commit" ;;' \
    '    merged-elsewhere) printf "421|MERGED|release|%s|%s\\n" "$branch" "$merge_commit" ;;' \
    '    none) exit 0 ;;' \
    '    *) exit 1 ;;' \
    '  esac' \
    '  exit 0' \
    'fi' \
    'if [[ "$state" == "merged" ]]; then' \
    '  case "${FAKE_GH_MODE:-unavailable}" in' \
    '    merged) printf "325|%s|%s|https://example.test/pr/325\\n" "$FAKE_GH_HEAD_NAME" "$FAKE_GH_HEAD_OID" ;;' \
    '    ambiguous-merged) printf "325|%s|%s|https://example.test/pr/325\\n326|%s|%s|https://example.test/pr/326\\n" "$FAKE_GH_HEAD_NAME" "$FAKE_GH_HEAD_OID" "$FAKE_GH_OTHER_NAME" "$FAKE_GH_OTHER_OID" ;;' \
    '    empty) exit 0 ;;' \
    '    unavailable) exit 1 ;;' \
    '    *) exit 1 ;;' \
    '  esac' \
    'else' \
    '  case "${FAKE_GH_MODE:-closed}" in' \
    '    closed) printf "218|UNMERGED|%s|%s|https://example.test/pr/218\\n" "$branch" "$FAKE_GH_HEAD_OID" ;;' \
    '    ambiguous) printf "218|UNMERGED|%s|%s|https://example.test/pr/218\\n219|UNMERGED|%s|%s|https://example.test/pr/219\\n" "$branch" "$FAKE_GH_HEAD_OID" "$branch" "$FAKE_GH_HEAD_OID" ;;' \
    '    unavailable) exit 1 ;;' \
    '    *) exit 1 ;;' \
    '  esac' \
    'fi' > "$repo/fake-bin/gh"
  chmod +x "$repo/fake-bin/gh"

  printf '%s\n' "$repo"
}

create_unmerged_branch() {
  local repo="$1"
  local branch="$2"
  local message="$3"

  git -C "$repo" checkout -b "$branch" >/dev/null
  mkdir -p "$(dirname "$repo/$branch.txt")"
  printf '%s\n' "$message" > "$repo/$branch.txt"
  git -C "$repo" add "$branch.txt"
  git -C "$repo" commit -m "$message" >/dev/null
  git -C "$repo" checkout master >/dev/null
}

create_merged_branch() {
  local repo="$1"
  local branch="$2"

  create_unmerged_branch "$repo" "$branch" "Merged $branch work"
  git -C "$repo" merge --ff-only "$branch" >/dev/null
  git -C "$repo" push origin master >/dev/null
}

run_closed_cleanup_output() {
  local repo="$1"
  local mode="$2"
  local head_oid="$3"

  printf 'DELETE\n' | env PATH="$repo/fake-bin:$PATH" FAKE_GH_MODE="$mode" FAKE_GH_HEAD_OID="$head_oid" "$repo/git_clean_worktrees.sh" --closed-prs 2>&1
}

run_closed_cleanup() {
  local repo="$1"
  local mode="$2"
  local head_oid="$3"
  local confirmation="${4:-yes}"

  if [[ "$confirmation" == "interactive" ]]; then
    run_closed_cleanup_output "$repo" "$mode" "$head_oid" >/dev/null
    return
  fi

  env PATH="$repo/fake-bin:$PATH" FAKE_GH_MODE="$mode" FAKE_GH_HEAD_OID="$head_oid" "$repo/git_clean_worktrees.sh" --closed-prs --yes >/dev/null
}

test_merged_branch_and_worktree_removal() {
  local repo
  local worktree

  repo="$(new_fixture merged)"
  create_merged_branch "$repo" merged-branch
  worktree="$TEST_ROOT/merged/worktree"
  git -C "$repo" worktree add "$worktree" merged-branch >/dev/null

  "$repo/git_clean_worktrees.sh" >/dev/null

  assert_branch_missing "$repo" merged-branch
  assert_path_missing "$worktree"
}

test_worktree_path_with_trailing_newline_is_not_truncated() {
  local repo
  local preserved_worktree
  local newline_worktree

  repo="$(new_fixture newline-worktree-mapping)"
  create_unmerged_branch "$repo" preserved-worktree "Preserved worktree"
  preserved_worktree="$TEST_ROOT/newline-worktree-mapping/worktree"
  git -C "$repo" worktree add "$preserved_worktree" preserved-worktree >/dev/null

  git -C "$repo" branch merged-newline master
  newline_worktree="$preserved_worktree"$'\n'
  git -C "$repo" worktree add "$newline_worktree" merged-newline >/dev/null

  "$repo/git_clean_worktrees.sh" >/dev/null

  assert_branch_exists "$repo" preserved-worktree
  assert_path_exists "$preserved_worktree"
  assert_branch_missing "$repo" merged-newline
  assert_path_missing "$newline_worktree"
}

test_generated_only_orphan_residue_cleanup_is_scoped() {
  local repo
  local system_find
  local session_worktrees
  local flow_worktrees
  local generated_residue
  local flow_generated_residue
  local empty_generated_residue
  local metadata_race_residue
  local metadata_race_artifact
  local content_race_residue
  local content_race_artifact
  local unreadable_residue
  local unreadable_artifact
  local unreadable_artifact_was_preserved=false
  local empty_directory
  local private_inputs_directory
  local world_directory
  local unrecognized_empty_directory
  local unrecognized_directory
  local sibling_generated_residue

  repo="$(new_fixture generated-residue)"
  session_worktrees="$(session_home "$repo")"
  flow_worktrees="$(flow_home "$repo")"
  generated_residue="$session_worktrees/generated-residue"
  mkdir -p "$generated_residue/mod/build/tmp/jar" "$generated_residue/.gradle/8.14"
  printf 'generated\n' > "$generated_residue/mod/build/tmp/jar/MANIFEST.MF"
  printf 'generated\n' > "$generated_residue/.gradle/8.14/gc.properties"
  flow_generated_residue="$flow_worktrees/feat-rlm-1-generated-residue"
  mkdir -p "$flow_generated_residue/.venv/bin" "$flow_generated_residue/tools/__pycache__"
  printf 'generated\n' > "$flow_generated_residue/.venv/bin/activate"
  printf 'cache\n' > "$flow_generated_residue/tools/__pycache__/module.pyc"
  empty_generated_residue="$session_worktrees/empty-generated-residue"
  mkdir -p "$empty_generated_residue/build"
  # The two races hook the bash script's second `find`, which the PowerShell
  # port does not run: it lists a folder once, through .NET.
  metadata_race_residue="$session_worktrees/metadata-race-generated-residue"
  metadata_race_artifact="$metadata_race_residue/build/tmp/output.txt"
  content_race_residue="$session_worktrees/content-race-generated-residue"
  content_race_artifact="$content_race_residue/build/tmp/output.txt"
  if [[ "$IMPLEMENTATION" == sh ]]; then
    mkdir -p "$(dirname "$metadata_race_artifact")"
    printf 'generated but keep after worktree repair\n' > "$metadata_race_artifact"
    mkdir -p "$(dirname "$content_race_artifact")"
    printf 'generated but keep after content change\n' > "$content_race_artifact"
  fi
  unreadable_residue="$session_worktrees/unreadable-generated-residue"
  unreadable_artifact="$unreadable_residue/build/tmp/output.txt"
  mkdir -p "$(dirname "$unreadable_artifact")" "$unreadable_residue/restricted/nested"
  printf 'generated but keep after incomplete scan\n' > "$unreadable_artifact"
  printf 'unreadable local data\n' > "$unreadable_residue/restricted/nested/keep.txt"
  chmod 000 "$unreadable_residue/restricted"
  empty_directory="$session_worktrees/empty"
  mkdir -p "$empty_directory"
  # The owner's private inputs and a dev world, which may hold rooms built by
  # hand, are never generated residue.
  private_inputs_directory="$session_worktrees/private-inputs-only"
  mkdir -p "$private_inputs_directory/.local"
  printf 'private input\n' > "$private_inputs_directory/.local/input.txt"
  world_directory="$flow_worktrees/feat-rlm-1-world-only"
  mkdir -p "$world_directory/run/client/saves/Rooms" "$world_directory/build"
  printf 'world\n' > "$world_directory/run/client/saves/Rooms/level.dat"
  printf 'generated\n' > "$world_directory/build/output.txt"
  unrecognized_empty_directory="$session_worktrees/empty-notes"
  mkdir -p "$unrecognized_empty_directory/notes"
  unrecognized_directory="$flow_worktrees/important-copy"
  mkdir -p "$unrecognized_directory"
  printf 'keep me\n' > "$unrecognized_directory/notes.txt"
  # A directory beside the checkout but outside realmforge-worktrees/ is never
  # scanned.
  sibling_generated_residue="$TEST_ROOT/generated-residue/repo-generated-residue"
  mkdir -p "$sibling_generated_residue/build"
  printf 'generated\n' > "$sibling_generated_residue/build/output.txt"

  printf '%s\n' \
    '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    'if [[ $# -eq 8 && ( "$1" == "${RACE_METADATA_CANDIDATE:-}" || "$1" == "${RACE_CONTENT_CANDIDATE:-}" ) && "$2" == "-xdev" && "$3" == "-mindepth" && "$4" == "1" && "$5" == "-type" && "$6" == "d" && "$7" == "-empty" && "$8" == "-print0" ]]; then' \
    '  if "$SYSTEM_FIND" "$@"; then status=0; else status=$?; fi' \
    '  if [[ "$status" -eq 0 && "$1" == "${RACE_METADATA_CANDIDATE:-}" ]]; then printf "gitdir: repaired-worktree-metadata\\n" > "$RACE_METADATA_CANDIDATE/.git"; fi' \
    '  if [[ "$status" -eq 0 && "$1" == "${RACE_CONTENT_CANDIDATE:-}" ]]; then printf "new local data\\n" > "$RACE_CONTENT_CANDIDATE/important.txt"; fi' \
    '  exit "$status"' \
    'fi' \
    'exec "$SYSTEM_FIND" "$@"' > "$repo/fake-bin/find"
  chmod +x "$repo/fake-bin/find"
  system_find="$(command -v find)"

  env PATH="$repo/fake-bin:$PATH" SYSTEM_FIND="$system_find" \
    RACE_METADATA_CANDIDATE="$metadata_race_residue" RACE_CONTENT_CANDIDATE="$content_race_residue" \
    "$repo/git_clean_worktrees.sh" >/dev/null

  if [[ -e "$unreadable_artifact" ]]; then
    unreadable_artifact_was_preserved=true
  fi
  chmod 700 "$unreadable_residue/restricted"

  assert_path_missing "$generated_residue"
  assert_path_missing "$flow_generated_residue"
  assert_path_missing "$empty_generated_residue"
  if [[ "$IMPLEMENTATION" == sh ]]; then
    assert_path_exists "$metadata_race_residue"
    assert_path_exists "$metadata_race_artifact"
    assert_path_exists "$content_race_residue"
    assert_path_exists "$content_race_artifact"
    assert_path_exists "$content_race_residue/important.txt"
  fi
  [[ "$unreadable_artifact_was_preserved" == true ]] || \
    fail "Expected an incomplete orphan scan to preserve already inspected artifacts."
  assert_path_exists "$unreadable_residue"
  assert_path_exists "$empty_directory"
  assert_path_exists "$private_inputs_directory/.local/input.txt"
  assert_path_exists "$world_directory/run/client/saves/Rooms/level.dat"
  assert_path_exists "$world_directory/build/output.txt"
  assert_path_exists "$unrecognized_empty_directory"
  assert_path_exists "$unrecognized_directory"
  assert_path_exists "$sibling_generated_residue"
}

# A primary worktree path cut at its newline would name a different checkout,
# whose .claude/worktrees/ directory must not be scanned.
test_primary_worktree_path_with_newline_keeps_unrelated_worktree_home() {
  local repo
  local unrelated_residue
  local own_residue

  repo="$(new_fixture newline-primary-worktree $'repo\nactual')"
  unrelated_residue="$TEST_ROOT/newline-primary-worktree/repo/.claude/worktrees/unrelated"
  mkdir -p "$unrelated_residue/build"
  printf 'unrelated generated data\n' > "$unrelated_residue/build/output.txt"
  own_residue="$(session_home "$repo")/own"
  mkdir -p "$own_residue/build"
  printf 'own generated data\n' > "$own_residue/build/output.txt"

  "$repo/git_clean_worktrees.sh" >/dev/null

  assert_path_exists "$unrelated_residue"
  assert_path_missing "$own_residue"
}

# Cut at the newline, the primary checkout would be parent/, whose session home
# and realmforge-worktrees/ beside it belong to another checkout.
test_primary_worktree_parent_with_trailing_newline_keeps_other_directory() {
  local repo
  local unrelated_residue
  local unrelated_flow_residue

  repo="$(new_fixture newline-primary-parent $'parent\n/repo')"
  unrelated_residue="$TEST_ROOT/newline-primary-parent/parent/.claude/worktrees/victim"
  mkdir -p "$unrelated_residue/build"
  printf 'unrelated generated data\n' > "$unrelated_residue/build/output.txt"
  unrelated_flow_residue="$TEST_ROOT/newline-primary-parent/realmforge-worktrees/victim"
  mkdir -p "$unrelated_flow_residue/build"
  printf 'unrelated generated data\n' > "$unrelated_flow_residue/build/output.txt"

  "$repo/git_clean_worktrees.sh" >/dev/null

  assert_path_exists "$unrelated_residue"
  assert_path_exists "$unrelated_flow_residue"
}

test_directory_holding_a_registered_worktree_is_untouched() {
  local repo
  local container
  local nested
  local output

  repo="$(new_fixture worktree-container)"
  create_unmerged_branch "$repo" nested-live "Live nested work"
  container="$(session_home "$repo")/container"
  nested="$container/nested"
  mkdir -p "$container"
  git -C "$repo" worktree add "$nested" nested-live >/dev/null
  # Seen from the container, a nested checkout holding only a Python cache looks
  # exactly like generated residue (`*/__pycache__/*`), and losing its .git
  # pointer lets `git worktree prune` drop the record, so only the mapping
  # captured before pruning still says the directory is live.
  find "$nested" -xdev -mindepth 1 -depth -delete
  mkdir -p "$nested/tools/__pycache__"
  printf 'cache\n' > "$nested/tools/__pycache__/module.pyc"

  output="$("$repo/git_clean_worktrees.sh" 2>&1)"

  assert_path_exists "$nested/tools/__pycache__/module.pyc"
  assert_output_excludes "$output" "Removing generated worktree residue"
  assert_output_excludes "$output" "Keeping unrecognized directory"
}

# Each home is skipped when it, or the .claude/ directory holding the session
# home, is a symlink.
test_symlinked_worktree_home_is_not_followed() {
  local fixture_name
  local repo
  local elsewhere
  local output

  for fixture_name in symlinked-session-home symlinked-claude-directory symlinked-flow-home; do
    repo="$(new_fixture "$fixture_name")"
    elsewhere="$TEST_ROOT/$fixture_name/elsewhere"
    mkdir -p "$elsewhere/worktrees/residue/build"
    printf 'generated\n' > "$elsewhere/worktrees/residue/build/output.txt"
    case "$fixture_name" in
      symlinked-session-home)
        mkdir -p "$repo/.claude"
        ln -s "$elsewhere/worktrees" "$(session_home "$repo")"
        ;;
      symlinked-claude-directory)
        ln -s "$elsewhere" "$repo/.claude"
        ;;
      symlinked-flow-home)
        ln -s "$elsewhere/worktrees" "$(flow_home "$repo")"
        ;;
    esac

    output="$("$repo/git_clean_worktrees.sh" 2>&1)"

    assert_path_exists "$elsewhere/worktrees/residue/build/output.txt"
    assert_output_contains "$output" "Not following a symlinked worktree home"
  done
}

test_missing_worktree_record_is_pruned_before_branch_cleanup() {
  local repo
  local missing_worktree

  repo="$(new_fixture stale-record)"
  create_merged_branch "$repo" merged-with-missing-worktree
  missing_worktree="$TEST_ROOT/stale-record/missing-worktree"
  git -C "$repo" worktree add "$missing_worktree" merged-with-missing-worktree >/dev/null
  find "$missing_worktree" -xdev -depth -delete

  "$repo/git_clean_worktrees.sh" >/dev/null

  assert_branch_missing "$repo" merged-with-missing-worktree
}

test_worktree_below_inaccessible_ancestor_is_preserved() {
  local repo
  local inaccessible_parent
  local worktree

  if [[ "$EUID" -eq 0 ]]; then
    return
  fi

  repo="$(new_fixture inaccessible-worktree)"
  create_merged_branch "$repo" merged-below-inaccessible-ancestor

  inaccessible_parent="$TEST_ROOT/inaccessible-worktree/restricted"
  worktree="$inaccessible_parent/worktree"
  mkdir -p "$inaccessible_parent"
  git -C "$repo" worktree add "$worktree" merged-below-inaccessible-ancestor >/dev/null
  chmod 000 "$inaccessible_parent"

  "$repo/git_clean_worktrees.sh" >/dev/null

  chmod 700 "$inaccessible_parent"
  assert_branch_exists "$repo" merged-below-inaccessible-ancestor
  assert_path_exists "$worktree"
}

test_existing_checkout_with_missing_pointer_is_preserved() {
  local repo
  local worktree
  local head_oid

  repo="$(new_fixture existing-checkout)"
  create_unmerged_branch "$repo" closed-with-missing-pointer "Closed PR work"
  head_oid="$(git -C "$repo" rev-parse closed-with-missing-pointer)"
  worktree="$TEST_ROOT/existing-checkout-outside/checkout"
  git -C "$repo" worktree add "$worktree" closed-with-missing-pointer >/dev/null
  find "$worktree/.git" -xdev -delete
  printf 'uncommitted\n' > "$worktree/dirty.txt"

  run_closed_cleanup "$repo" closed "$head_oid"

  assert_branch_exists "$repo" closed-with-missing-pointer
  assert_path_exists "$worktree"
}

test_merged_recovery_branch_is_preserved() {
  local repo

  repo="$(new_fixture merged-recovery)"
  create_merged_branch "$repo" recovery/snapshot

  "$repo/git_clean_worktrees.sh" >/dev/null

  assert_branch_exists "$repo" recovery/snapshot
}

test_stale_merged_orphan_requires_matching_files() {
  local fixture_name
  local repo
  local candidate
  local metadata_path
  local merged_head
  local merged_file_was_preserved
  local branch_name
  local initial_head
  local gh_mode

  for fixture_name in stale-clean stale-slugged-branch stale-cli-worktree stale-flow-home stale-flow-other-branch stale-ambiguous-merged stale-unmerged-local-branch stale-modified stale-literal-pathspec stale-unreadable stale-directory-replacement stale-pointer-trailing-data; do
    repo="$(new_fixture "$fixture_name")"
    initial_head="$(git -C "$repo" rev-parse HEAD)"
    # A desktop session's directory carries its branch name without claude/, a
    # `claude --worktree` one without worktree-, and the branch may add a slug;
    # a flow directory is its whole branch name with "/" replaced by "-".
    branch_name=claude/stale-checkout
    candidate="$(session_home "$repo")/stale-checkout"
    case "$fixture_name" in
      stale-slugged-branch) branch_name=fix/stale-checkout-with-slug ;;
      stale-cli-worktree) branch_name="worktree-stale-checkout" ;;
      stale-flow-home|stale-flow-other-branch)
        branch_name=feat/stale-checkout
        candidate="$(flow_home "$repo")/feat-stale-checkout"
        # A directory named after another branch has no owner here.
        [[ "$fixture_name" == stale-flow-home ]] || branch_name=feat/other-checkout
        ;;
    esac
    git -C "$repo" checkout -b "$branch_name" >/dev/null
    printf 'merged work\n' > "$repo/merged-work.txt"
    if [[ "$fixture_name" == "stale-directory-replacement" ]]; then
      mkdir -p "$repo/tracked-directory"
      printf 'tracked descendant\n' > "$repo/tracked-directory/file.txt"
    fi
    git -C "$repo" add merged-work.txt
    if [[ "$fixture_name" == "stale-directory-replacement" ]]; then
      git -C "$repo" add tracked-directory/file.txt
    fi
    git -C "$repo" commit -m "Merged worktree content" >/dev/null
    merged_head="$(git -C "$repo" rev-parse HEAD)"
    git -C "$repo" checkout master >/dev/null
    git -C "$repo" merge --ff-only "$branch_name" >/dev/null
    git -C "$repo" push origin master >/dev/null

    git -C "$repo" worktree add "$candidate" "$branch_name" >/dev/null 2>&1
    metadata_path="$(sed -n 's/^gitdir: //p' "$candidate/.git")"
    [[ "$metadata_path" == "$repo/.git/worktrees/"* ]] || fail "Unexpected fixture worktree metadata path."
    find "$metadata_path" -xdev -depth -delete
    git -C "$repo" branch -D "$branch_name" >/dev/null

    gh_mode=merged
    if [[ "$fixture_name" == "stale-ambiguous-merged" ]]; then
      # A second merged pull request for the same directory name reports
      # another head, so the residue's owner is unknown.
      gh_mode=ambiguous-merged
    elif [[ "$fixture_name" == "stale-unmerged-local-branch" ]]; then
      create_unmerged_branch "$repo" claude/stale-checkout-follow-up "Unmerged follow-up"
    elif [[ "$fixture_name" == "stale-modified" ]]; then
      printf 'local modification\n' > "$candidate/merged-work.txt"
    elif [[ "$fixture_name" == "stale-literal-pathspec" ]]; then
      printf 'untracked literal pathspec\n' > "$candidate/*.txt"
    elif [[ "$fixture_name" == "stale-unreadable" ]]; then
      mkdir -p "$candidate/restricted/nested"
      printf 'unreadable local data\n' > "$candidate/restricted/nested/keep.txt"
      chmod 000 "$candidate/restricted"
    elif [[ "$fixture_name" == "stale-directory-replacement" ]]; then
      find "$candidate/tracked-directory" -xdev -depth -delete
      printf 'untracked replacement\n' > "$candidate/tracked-directory"
    elif [[ "$fixture_name" == "stale-pointer-trailing-data" ]]; then
      printf 'unexpected local metadata\n' >> "$candidate/.git"
    fi

    env PATH="$repo/fake-bin:$PATH" FAKE_GH_MODE="$gh_mode" \
      FAKE_GH_HEAD_NAME="$branch_name" FAKE_GH_HEAD_OID="$merged_head" \
      FAKE_GH_OTHER_NAME=fix/stale-checkout-other FAKE_GH_OTHER_OID="$initial_head" \
      "$repo/git_clean_worktrees.sh" >/dev/null

    if [[ "$fixture_name" == "stale-unreadable" ]]; then
      merged_file_was_preserved=false
      if [[ -e "$candidate/merged-work.txt" ]]; then
        merged_file_was_preserved=true
      fi
      chmod 700 "$candidate/restricted"
      [[ "$merged_file_was_preserved" == true ]] || \
        fail "Expected an incomplete stale-orphan scan to preserve already inspected tracked files."
    fi

    case "$fixture_name" in
      stale-clean|stale-slugged-branch|stale-cli-worktree|stale-flow-home)
        assert_path_missing "$candidate"
        ;;
      *)
        assert_path_exists "$candidate"
        ;;
    esac
  done
}

test_stale_orphan_requires_github_derived_head() {
  local repo
  local candidate

  repo="$(new_fixture stale-local-head-only)"
  candidate="$(session_home "$repo")/master"
  mkdir -p "$candidate"
  git -C "$repo" archive master | tar -x -C "$candidate"
  printf 'gitdir: %s/.git/worktrees/missing\n' "$repo" > "$candidate/.git"

  env PATH="$repo/fake-bin:$PATH" FAKE_GH_MODE=empty \
    FAKE_GH_HEAD_NAME=master FAKE_GH_HEAD_OID="$(git -C "$repo" rev-parse master)" \
    "$repo/git_clean_worktrees.sh" >/dev/null

  assert_path_exists "$candidate"
}

test_stale_orphan_metadata_lookup_failure_is_fail_closed() {
  local repo
  local candidate
  local metadata_path
  local merged_head

  repo="$(new_fixture stale-metadata-failure)"
  git -C "$repo" checkout -b claude/stale-checkout >/dev/null
  printf 'merged work\n' > "$repo/merged-work.txt"
  git -C "$repo" add merged-work.txt
  git -C "$repo" commit -m "Merged worktree content" >/dev/null
  merged_head="$(git -C "$repo" rev-parse HEAD)"
  git -C "$repo" checkout master >/dev/null
  git -C "$repo" merge --ff-only claude/stale-checkout >/dev/null
  git -C "$repo" push origin master >/dev/null

  candidate="$(session_home "$repo")/stale-checkout"
  git -C "$repo" worktree add "$candidate" claude/stale-checkout >/dev/null 2>&1
  metadata_path="$(sed -n 's/^gitdir: //p' "$candidate/.git")"
  find "$metadata_path" -xdev -depth -delete
  git -C "$repo" branch -D claude/stale-checkout >/dev/null

  # The merged pull request lookup fails, so no owner can be established.
  env PATH="$repo/fake-bin:$PATH" FAKE_GH_MODE=unavailable \
    FAKE_GH_HEAD_NAME=claude/stale-checkout FAKE_GH_HEAD_OID="$merged_head" \
    "$repo/git_clean_worktrees.sh" >/dev/null

  assert_path_exists "$candidate"
}

test_closed_unmerged_worktree_matching_target_is_removed() {
  local repo
  local matching_head
  local dirty_head
  local matching_worktree
  local dirty_worktree
  local output

  repo="$(new_fixture closed-regenerated)"
  printf 'generated v1\n' > "$repo/generated.txt"
  git -C "$repo" add generated.txt
  git -C "$repo" commit -m "Add generated content" >/dev/null
  git -C "$repo" push origin master >/dev/null

  create_unmerged_branch "$repo" closed-regenerated "Closed regenerated work"
  matching_head="$(git -C "$repo" rev-parse closed-regenerated)"
  create_unmerged_branch "$repo" closed-real-work "Closed real work"
  dirty_head="$(git -C "$repo" rev-parse closed-real-work)"

  # Master keeps moving after both branches, so their checkouts can never look
  # clean again once a development server rewrites the generated file.
  printf 'generated v2\n' > "$repo/generated.txt"
  git -C "$repo" commit -am "Advance master past the closed branches" >/dev/null
  git -C "$repo" push origin master >/dev/null

  matching_worktree="$TEST_ROOT/closed-regenerated/matching"
  git -C "$repo" worktree add "$matching_worktree" closed-regenerated >/dev/null
  printf 'generated v2\n' > "$matching_worktree/generated.txt"

  dirty_worktree="$TEST_ROOT/closed-regenerated/real-work"
  git -C "$repo" worktree add "$dirty_worktree" closed-real-work >/dev/null
  printf 'never merged\n' > "$dirty_worktree/generated.txt"

  # Only the branch whose head the fake pull request reports is a candidate, so
  # each run exercises exactly one of the two worktrees.
  output="$(run_closed_cleanup_output "$repo" closed "$dirty_head")"
  assert_branch_exists "$repo" closed-real-work
  assert_path_exists "$dirty_worktree"
  assert_output_contains "$output" "Keeping uncommitted work in the worktree of:"
  assert_output_excludes "$output" "changed after confirmation"

  output="$(run_closed_cleanup_output "$repo" closed "$matching_head")"
  assert_branch_missing "$repo" closed-regenerated
  assert_path_missing "$matching_worktree"
  assert_output_contains "$output" "Removing closed PR worktree:"
  assert_output_excludes "$output" "changed after confirmation"
}

# run/ and .local/ are gitignored, so Git calls a worktree that holds a world or
# a private input clean; the cleanup keeps it anyway, on the merged path and for
# a --closed-prs candidate. Build output, GameTest output in run/gametest/ and
# empty directories keep nothing.
test_worktree_with_run_or_local_files_is_kept() {
  local repo
  local world_worktree
  local private_worktree
  local build_worktree
  local closed_worktree
  local closed_head
  local output

  repo="$(new_fixture local-files)"
  printf '%s\n' 'run/' '.local/' 'build/' >> "$repo/.git/info/exclude"

  create_merged_branch "$repo" merged-world
  world_worktree="$TEST_ROOT/local-files/world"
  git -C "$repo" worktree add "$world_worktree" merged-world >/dev/null
  mkdir -p "$world_worktree/run/client/saves/Rooms"
  printf 'world\n' > "$world_worktree/run/client/saves/Rooms/level.dat"

  create_merged_branch "$repo" merged-private
  private_worktree="$TEST_ROOT/local-files/private"
  git -C "$repo" worktree add "$private_worktree" merged-private >/dev/null
  mkdir -p "$private_worktree/.local"
  printf 'private input\n' > "$private_worktree/.local/input.txt"

  create_merged_branch "$repo" merged-build
  build_worktree="$TEST_ROOT/local-files/build"
  git -C "$repo" worktree add "$build_worktree" merged-build >/dev/null
  mkdir -p "$build_worktree/build/classes" "$build_worktree/run/gametest/logs" "$build_worktree/run/client/saves"
  printf 'class\n' > "$build_worktree/build/classes/Main.class"
  printf 'log\n' > "$build_worktree/run/gametest/logs/latest.log"

  create_unmerged_branch "$repo" closed-world "Closed world work"
  closed_head="$(git -C "$repo" rev-parse closed-world)"
  closed_worktree="$TEST_ROOT/local-files/closed"
  git -C "$repo" worktree add "$closed_worktree" closed-world >/dev/null
  mkdir -p "$closed_worktree/run/server/world" "$closed_worktree/build"
  printf 'world\n' > "$closed_worktree/run/server/world/level.dat"
  printf 'jar\n' > "$closed_worktree/build/mod.jar"

  [[ -z "$(git -C "$world_worktree" status --porcelain)" ]] || fail "Expected the world worktree to read clean."

  output="$("$repo/git_clean_worktrees.sh" 2>&1 | strip_colors)"
  assert_branch_exists "$repo" merged-world
  assert_path_exists "$world_worktree/run/client/saves/Rooms/level.dat"
  assert_output_contains "$output" "Keeping run/ or .local/ files in the worktree of: merged-world"
  assert_branch_exists "$repo" merged-private
  assert_path_exists "$private_worktree/.local/input.txt"
  assert_output_contains "$output" "Keeping run/ or .local/ files in the worktree of: merged-private"
  assert_branch_missing "$repo" merged-build
  assert_path_missing "$build_worktree"

  output="$(run_closed_cleanup_output "$repo" closed "$closed_head" | strip_colors)"
  assert_branch_exists "$repo" closed-world
  assert_path_exists "$closed_worktree/run/server/world/level.dat"
  assert_output_contains "$output" "Keeping run/ or .local/ files in the worktree of: closed-world"
  assert_output_excludes "$output" "Removing closed PR worktree:"

  # Once the world is moved away, only build output is left and the next run removes it.
  rm -r "$closed_worktree/run/server/world"
  output="$(run_closed_cleanup_output "$repo" closed "$closed_head" | strip_colors)"
  assert_branch_missing "$repo" closed-world
  assert_path_missing "$closed_worktree"
}

test_closed_unmerged_branch_requires_and_accepts_confirmation() {
  local repo
  local worktree
  local head_oid

  repo="$(new_fixture closed)"
  create_unmerged_branch "$repo" closed-branch "Closed PR work"
  head_oid="$(git -C "$repo" rev-parse closed-branch)"
  worktree="$TEST_ROOT/closed/worktree"
  git -C "$repo" worktree add "$worktree" closed-branch >/dev/null

  run_closed_cleanup "$repo" closed "$head_oid" interactive

  assert_branch_missing "$repo" closed-branch
  assert_path_missing "$worktree"
}

test_local_only_commits_and_dirty_worktrees_are_preserved() {
  local repo
  local dirty_worktree
  local expected_head
  local dirty_head

  repo="$(new_fixture protected)"
  create_unmerged_branch "$repo" locally-advanced "Original PR head"
  expected_head="$(git -C "$repo" rev-parse locally-advanced)"
  git -C "$repo" checkout locally-advanced >/dev/null
  printf 'local only\n' > "$repo/local-only.txt"
  git -C "$repo" add local-only.txt
  git -C "$repo" commit -m "Local-only follow-up" >/dev/null
  git -C "$repo" checkout master >/dev/null
  run_closed_cleanup "$repo" closed "$expected_head"
  assert_branch_exists "$repo" locally-advanced

  create_unmerged_branch "$repo" dirty-worktree "Dirty worktree PR"
  dirty_head="$(git -C "$repo" rev-parse dirty-worktree)"
  dirty_worktree="$TEST_ROOT/protected/dirty-worktree"
  git -C "$repo" worktree add "$dirty_worktree" dirty-worktree >/dev/null
  printf 'uncommitted\n' > "$dirty_worktree/dirty.txt"
  run_closed_cleanup "$repo" closed "$dirty_head"
  assert_branch_exists "$repo" dirty-worktree
  assert_path_exists "$dirty_worktree"
}

test_recovery_current_master_and_unavailable_or_ambiguous_metadata_are_preserved() {
  local repo
  local recovery_head
  local current_head
  local gh_mode
  local remote_head

  repo="$(new_fixture safeguards)"
  create_unmerged_branch "$repo" recovery/preserved "Recovery branch"
  recovery_head="$(git -C "$repo" rev-parse recovery/preserved)"
  run_closed_cleanup "$repo" closed "$recovery_head"
  assert_branch_exists "$repo" recovery/preserved
  assert_branch_exists "$repo" master

  git -C "$repo" checkout -b current-preserved >/dev/null
  printf 'current\n' > "$repo/current.txt"
  git -C "$repo" add current.txt
  git -C "$repo" commit -m "Current branch" >/dev/null
  current_head="$(git -C "$repo" rev-parse current-preserved)"
  run_closed_cleanup "$repo" closed "$current_head"
  assert_branch_exists "$repo" current-preserved
  git -C "$repo" checkout master >/dev/null

  for gh_mode in unavailable ambiguous; do
    create_unmerged_branch "$repo" "$gh_mode-metadata" "$gh_mode metadata"
    run_closed_cleanup "$repo" "$gh_mode" "$(git -C "$repo" rev-parse "$gh_mode-metadata")"
    assert_branch_exists "$repo" "$gh_mode-metadata"
  done

  create_unmerged_branch "$repo" remote-still-exists "Remote branch"
  remote_head="$(git -C "$repo" rev-parse remote-still-exists)"
  git -C "$repo" push origin remote-still-exists >/dev/null
  run_closed_cleanup "$repo" closed "$remote_head"
  assert_branch_exists "$repo" remote-still-exists
}

test_merged_worktree_matching_target_is_removed() {
  local repo
  local merged_head
  local output
  local regenerated_worktree
  local state
  local worktree
  local kept_states=(modified staged-delete untracked permission staged-add staged-modify)

  repo="$(new_fixture regenerated-dirt)"
  printf 'generated v1\n' > "$repo/generated.txt"
  printf 'stale\n' > "$repo/stale.txt"
  git -C "$repo" add generated.txt stale.txt
  git -C "$repo" commit -m "Add generated and stale content" >/dev/null

  git -C "$repo" checkout -b merged-regenerated >/dev/null
  printf 'branch work\n' > "$repo/work.txt"
  git -C "$repo" add work.txt
  git -C "$repo" commit -m "Merged branch work" >/dev/null
  merged_head="$(git -C "$repo" rev-parse merged-regenerated)"
  git -C "$repo" checkout master >/dev/null
  git -C "$repo" merge --ff-only merged-regenerated >/dev/null

  # Master keeps moving after the merge: the generated file is rewritten and the
  # stale one disappears, so the older checkout can never look clean again.
  printf 'generated v2\n' > "$repo/generated.txt"
  git -C "$repo" rm --quiet stale.txt
  git -C "$repo" commit -am "Advance master past the merged branch" >/dev/null
  git -C "$repo" push origin master >/dev/null

  regenerated_worktree="$TEST_ROOT/regenerated-dirt/regenerated"
  git -C "$repo" worktree add "$regenerated_worktree" merged-regenerated >/dev/null
  printf 'generated v2\n' > "$regenerated_worktree/generated.txt"
  git -C "$regenerated_worktree" rm --quiet stale.txt

  for state in "${kept_states[@]}"; do
    git -C "$repo" branch "merged-$state" "$merged_head"
    worktree="$TEST_ROOT/regenerated-dirt/$state"
    git -C "$repo" worktree add "$worktree" "merged-$state" >/dev/null
    case "$state" in
      modified) printf 'never merged\n' > "$worktree/generated.txt" ;;
      staged-delete) git -C "$worktree" rm --quiet generated.txt ;;
      untracked) printf 'extra\n' > "$worktree/extra.txt" ;;
      permission)
        printf 'generated v2\n' > "$worktree/generated.txt"
        chmod +x "$worktree/generated.txt"
        ;;
      staged-add)
        printf 'staged only\n' > "$worktree/staged-only.txt"
        git -C "$worktree" add staged-only.txt
        rm "$worktree/staged-only.txt"
        ;;
      # The working copy matches the merge target, but the index still holds
      # content that is in no commit and in no file, so discarding it would
      # destroy it.
      staged-modify)
        printf 'staged only\n' > "$worktree/generated.txt"
        git -C "$worktree" add generated.txt
        printf 'generated v2\n' > "$worktree/generated.txt"
        ;;
    esac
  done

  output="$("$repo/git_clean_worktrees.sh" 2>&1)"

  assert_branch_missing "$repo" merged-regenerated
  assert_path_missing "$regenerated_worktree"
  assert_output_contains "$output" "Removing worktree whose changes already match origin/master:"

  for state in "${kept_states[@]}"; do
    assert_branch_exists "$repo" "merged-$state"
    assert_path_exists "$TEST_ROOT/regenerated-dirt/$state"
  done

  # Preserving real uncommitted work is the expected outcome, not a failure.
  assert_output_contains "$output" "Keeping uncommitted work in the worktree of:"
  assert_output_excludes "$output" "ERROR:"
}

test_uninspectable_merged_worktree_is_reported_as_an_error() {
  local repo
  local worktree
  local output

  repo="$(new_fixture uninspectable-worktree)"
  create_merged_branch "$repo" merged-uninspectable

  worktree="$TEST_ROOT/uninspectable-worktree/checkout"
  git -C "$repo" worktree add "$worktree" merged-uninspectable >/dev/null
  printf 'uncommitted\n' > "$worktree/dirty.txt"
  find "$worktree/.git" -xdev -delete

  output="$("$repo/git_clean_worktrees.sh" 2>&1)"

  assert_branch_exists "$repo" merged-uninspectable
  assert_path_exists "$worktree"
  assert_output_contains "$output" "Keeping merged-uninspectable because its worktree could not be inspected:"
}

test_closed_pr_worktree_follows_confirmation() {
  local repo
  local head_oid
  local worktree

  repo="$(new_fixture closed-session)"
  create_unmerged_branch "$repo" claude/closed-session "Closed session work"
  head_oid="$(git -C "$repo" rev-parse claude/closed-session)"
  worktree="$(session_home "$repo")/closed-session"
  git -C "$repo" worktree add "$worktree" claude/closed-session >/dev/null 2>&1

  printf 'no\n' | env PATH="$repo/fake-bin:$PATH" FAKE_GH_MODE=closed FAKE_GH_HEAD_OID="$head_oid" \
    "$repo/git_clean_worktrees.sh" --closed-prs >/dev/null 2>&1
  assert_branch_exists "$repo" claude/closed-session
  assert_path_exists "$worktree"

  run_closed_cleanup "$repo" closed "$head_oid"
  assert_branch_missing "$repo" claude/closed-session
  assert_path_missing "$worktree"
}

# A copy of a branch whose commits were rewritten before they merged: the copy
# keeps its own commits while master gains the work as different commits, and
# the remote branch is gone unless the variant keeps it.
#   same-patch         master gains the same patch under another subject
#   squashed           master gains the change and its follow-up fix as one
#                      commit
#   revised            a merged pull request carries the commit under the same
#                      subject with a line added, which a review fix then
#                      rewrites, so all of the copy's lines are in master
#   revised-partial    the pull request carries the commit with one line
#                      changed, so one of the copy's ten lines is nowhere
#   revised-dropped    the pull request carries the commit with a line added,
#                      then a review fix removes one of the copy's lines, which
#                      only the pull request's intermediate commit still has
#   whitespace         master gains the change re-indented, which only a
#                      whitespace-blind comparison calls the same patch
#   revised-unpushed   revised, but GitHub never received the copy's commit
#   subject-collision  a merged pull request carries a commit with the same
#                      subject but other content
#   local-commit       same-patch, plus a commit that never left this checkout
#   dropped-change     same-patch, plus a pushed change the merge left out
#   keep-remote        same-patch, but the remote branch still holds the copy
#   remote-merged      same-patch, and the remote branch holds master's commit
create_rewritten_merged_branch() {
  local repo="$1"
  local fixture_name="$2"
  local branch="$3"
  local variant="${4:-same-patch}"
  local file="${branch//\//-}.txt"
  local change
  local pull_request_branch="pull-request/$branch"

  change="$(printf 'line %s\n' 1 2 3 4 5 6 7 8 9 10)"
  git -C "$repo" checkout -b "$branch" master >/dev/null 2>&1
  printf '%s\n' "$change" > "$repo/$file"
  git -C "$repo" add -- "$file"
  git -C "$repo" commit -m "feat(tools): add the $branch change" >/dev/null
  case "$variant" in
    squashed)
      printf '%s\nits follow-up\n' "$change" > "$repo/$file"
      git -C "$repo" commit -am "fix(tools): follow up on the $branch change" >/dev/null
      ;;
    dropped-change)
      printf 'later dropped\n' > "$repo/dropped-$file"
      git -C "$repo" add -- "dropped-$file"
      git -C "$repo" commit -m "feat(tools): a $branch change the merge left out" >/dev/null
      ;;
  esac
  git -C "$repo" push origin "$branch" >/dev/null 2>&1
  if [[ "$variant" == local-commit ]]; then
    printf 'local only\n' > "$repo/$file"
    git -C "$repo" commit -am "feat(tools): a $branch follow-up made only here" >/dev/null
  fi

  git -C "$repo" checkout master >/dev/null 2>&1
  printf 'unrelated master change\n' > "$repo/master-$file"
  git -C "$repo" add -- "master-$file"
  git -C "$repo" commit -m "chore: unrelated master change before $branch" >/dev/null
  case "$variant" in
    squashed)
      printf '%s\nits follow-up\n' "$change" > "$repo/$file"
      git -C "$repo" add -- "$file"
      git -C "$repo" commit -m "Squash the $branch pull request" >/dev/null
      ;;
    whitespace)
      printf '  %s\n' $'line 1\nline 2\nline 3\nline 4\nline 5\nline 6\nline 7\nline 8\nline 9\nline 10' \
        > "$repo/$file"
      git -C "$repo" add -- "$file"
      git -C "$repo" commit -m "Merge the re-indented $branch" >/dev/null
      ;;
    revised|revised-partial|revised-dropped|revised-unpushed|subject-collision)
      git -C "$repo" checkout -b "$pull_request_branch" master >/dev/null 2>&1
      case "$variant" in
        subject-collision) printf 'other line %s\n' 1 2 3 4 5 6 7 8 9 10 > "$repo/$file" ;;
        revised-partial) printf '%s\n' "${change/line 10/line 10, rebased}" > "$repo/$file" ;;
        *) printf '%s\nline 11, added in review\n' "$change" > "$repo/$file" ;;
      esac
      git -C "$repo" add -- "$file"
      git -C "$repo" commit -m "feat(art): add the $branch change" >/dev/null
      case "$variant" in
        revised|revised-unpushed)
          sed -i 's/^line 11, added in review$/line 11, revised in review/' "$repo/$file"
          git -C "$repo" commit -am "fix(art): revise the $branch change in review" >/dev/null
          ;;
        revised-dropped)
          sed -i '/^line 5$/d' "$repo/$file"
          git -C "$repo" commit -am "fix(art): drop a line of the $branch change in review" >/dev/null
          ;;
      esac
      git -C "$repo" checkout master >/dev/null 2>&1
      git -C "$repo" merge --no-ff -m "Merge pull request #421 from $branch" "$pull_request_branch" >/dev/null
      git -C "$repo" branch -D "$pull_request_branch" >/dev/null
      ;;
    *)
      printf '%s\n' "$change" > "$repo/$file"
      git -C "$repo" add -- "$file"
      git -C "$repo" commit -m "Merge the rebased $branch" >/dev/null
      ;;
  esac
  git -C "$repo" push origin master >/dev/null 2>&1

  case "$variant" in
    keep-remote)
      ;;
    remote-merged)
      git -C "$repo" push --force origin "master:refs/heads/$branch" >/dev/null 2>&1
      ;;
    *)
      git --git-dir="$TEST_ROOT/$fixture_name/remote.git" update-ref -d "refs/heads/$branch"
      ;;
  esac
}

test_rewritten_branches_merged_into_master() {
  local repo
  local output
  local name
  local known_commits=""
  local deleted=(same-patch squashed revised remote-merged)
  local kept=(revised-partial revised-dropped whitespace revised-unpushed subject-collision local-commit dropped-change keep-remote dirty)

  repo="$(new_fixture rewritten-merged)"
  for name in "${deleted[@]}" "${kept[@]}"; do
    if [[ "$name" == dirty ]]; then
      create_rewritten_merged_branch "$repo" rewritten-merged "claude/rewritten-$name"
    else
      create_rewritten_merged_branch "$repo" rewritten-merged "claude/rewritten-$name" "$name"
    fi
    git -C "$repo" worktree add "$(session_home "$repo")/rewritten-$name" "claude/rewritten-$name" >/dev/null 2>&1
    # GitHub has every pushed commit, but never the unpushed copy's.
    if [[ "$name" != revised-unpushed ]]; then
      known_commits+=" $(git -C "$repo" rev-parse "claude/rewritten-$name")"
    fi
  done
  printf 'uncommitted\n' > "$(session_home "$repo")/rewritten-dirty/notes.txt"

  # Without colour codes, so each message can be matched with its branch.
  output="$(env PATH="$repo/fake-bin:$PATH" FAKE_GH_ALL_MODE=merged \
    FAKE_GH_KNOWN_COMMITS="$known_commits" \
    "$repo/git_clean_worktrees.sh" 2>&1 | sed $'s/\x1b\\[[0-9;]*m//g')"

  for name in "${deleted[@]}"; do
    assert_branch_missing "$repo" "claude/rewritten-$name"
    assert_path_missing "$(session_home "$repo")/rewritten-$name"
  done
  for name in same-patch squashed remote-merged; do
    assert_output_contains "$output" \
      "Deleting rewritten branch whose commits are all in origin/master: claude/rewritten-$name"
  done
  assert_output_contains "$output" "Deleting rewritten branch merged in PR #421: claude/rewritten-revised"

  for name in "${kept[@]}"; do
    assert_branch_exists "$repo" "claude/rewritten-$name"
    assert_path_exists "$(session_home "$repo")/rewritten-$name"
  done
  for name in whitespace revised-unpushed subject-collision local-commit dropped-change keep-remote; do
    assert_output_contains "$output" "Keeping unmerged: claude/rewritten-$name"
  done
  for name in revised-partial revised-dropped; do
    assert_output_contains "$output" "Keeping rewritten branch merged in PR #421: claude/rewritten-$name (1 of its 10 changed lines are not in origin/master; --rewritten-prs deletes it after confirmation)"
  done
  assert_output_contains "$output" "Keeping uncommitted work in the worktree of: claude/rewritten-dirty"
}

# A branch its merged pull request covers only in part goes only under
# --rewritten-prs, once DELETE or --yes confirms it, and never with real
# uncommitted work in its worktree.
test_partly_covered_rewritten_branch_needs_confirmation() {
  local repo
  local output
  local known_commits=""
  local name

  repo="$(new_fixture rewritten-partial)"
  for name in partial partial-dirty; do
    create_rewritten_merged_branch "$repo" rewritten-partial "claude/rewritten-$name" revised-partial
    git -C "$repo" worktree add "$(session_home "$repo")/rewritten-$name" "claude/rewritten-$name" >/dev/null 2>&1
    known_commits+=" $(git -C "$repo" rev-parse "claude/rewritten-$name")"
  done
  printf 'uncommitted\n' > "$(session_home "$repo")/rewritten-partial-dirty/notes.txt"

  output="$(printf 'no\n' | env PATH="$repo/fake-bin:$PATH" FAKE_GH_ALL_MODE=merged \
    FAKE_GH_KNOWN_COMMITS="$known_commits" "$repo/git_clean_worktrees.sh" --rewritten-prs 2>&1 \
    | sed $'s/\x1b\\[[0-9;]*m//g')"
  assert_branch_exists "$repo" claude/rewritten-partial
  assert_path_exists "$(session_home "$repo")/rewritten-partial"
  assert_output_contains "$output" "Partly covered rewritten branch candidate: claude/rewritten-partial (PR #421; 1 of its 10 changed lines are not in origin/master)"
  assert_output_contains "$output" "Partly covered rewritten branch cleanup cancelled; no candidates were removed."

  output="$(printf 'DELETE\n' | env PATH="$repo/fake-bin:$PATH" FAKE_GH_ALL_MODE=merged \
    FAKE_GH_KNOWN_COMMITS="$known_commits" "$repo/git_clean_worktrees.sh" --rewritten-prs 2>&1 \
    | sed $'s/\x1b\\[[0-9;]*m//g')"
  assert_branch_missing "$repo" claude/rewritten-partial
  assert_path_missing "$(session_home "$repo")/rewritten-partial"
  assert_output_contains "$output" "Deleting partly covered rewritten branch merged in PR #421: claude/rewritten-partial"
  assert_output_contains "$output" "Deleted partly covered rewritten branches: 1"
  assert_branch_exists "$repo" claude/rewritten-partial-dirty
  assert_path_exists "$(session_home "$repo")/rewritten-partial-dirty"
  assert_output_contains "$output" "Keeping uncommitted work in the worktree of: claude/rewritten-partial-dirty"
}

# Claude Code gives every session its own branch and worktree: claude/<name> in
# .claude/worktrees/<name> from the desktop app, worktree-<name> from
# `claude --worktree`. The prefix decides nothing. A session that never
# committed goes as an ancestor of master, wherever its worktree is; one whose
# pull request merged after a rebase or a review revision goes under the
# rewritten-branch rules, or, when the revision changed its lines, only under
# --rewritten-prs; one holding uncommitted changes, commits that exist nowhere
# else, or pushed commits that are still unmerged stays.
test_claude_session_branches() {
  local repo
  local output
  local name
  local known_commits=""
  local session_worktrees
  local flow_worktrees
  local removed=(idle-session cli-session rebased-session revised-session)
  local kept=(dirty-session local-session pushed-session)

  repo="$(new_fixture claude-sessions)"
  session_worktrees="$(session_home "$repo")"
  flow_worktrees="$(flow_home "$repo")"

  # Sessions that never committed stay where master was.
  git -C "$repo" worktree add -b claude/idle-session "$session_worktrees/idle-session" master >/dev/null 2>&1
  git -C "$repo" worktree add -b worktree-cli-session "$session_worktrees/cli-session" master >/dev/null 2>&1
  git -C "$repo" worktree add -b claude/idle-flow "$flow_worktrees/claude-idle-flow" master >/dev/null 2>&1
  git -C "$repo" worktree add -b claude/dirty-session "$session_worktrees/dirty-session" master >/dev/null 2>&1
  printf 'uncommitted\n' > "$session_worktrees/dirty-session/notes.txt"

  # Sessions whose pull request merged after a rebase, after a review revision,
  # and after a revision that changed one of the session's lines.
  create_rewritten_merged_branch "$repo" claude-sessions claude/rebased-session
  create_rewritten_merged_branch "$repo" claude-sessions claude/revised-session revised
  create_rewritten_merged_branch "$repo" claude-sessions claude/partial-session revised-partial
  for name in rebased-session revised-session partial-session; do
    git -C "$repo" worktree add "$session_worktrees/$name" "claude/$name" >/dev/null 2>&1
    known_commits+=" $(git -C "$repo" rev-parse "claude/$name")"
  done

  # A session with a commit that never left this checkout, and one whose pushed
  # commit is still unmerged.
  git -C "$repo" worktree add -b claude/local-session "$session_worktrees/local-session" master >/dev/null 2>&1
  printf 'local only\n' > "$session_worktrees/local-session/local.txt"
  git -C "$session_worktrees/local-session" add local.txt
  git -C "$session_worktrees/local-session" commit -m "feat(tools): work only this checkout has" >/dev/null
  git -C "$repo" worktree add -b claude/pushed-session "$session_worktrees/pushed-session" master >/dev/null 2>&1
  printf 'pushed\n' > "$session_worktrees/pushed-session/pushed.txt"
  git -C "$session_worktrees/pushed-session" add pushed.txt
  git -C "$session_worktrees/pushed-session" commit -m "feat(tools): work still in review" >/dev/null
  git -C "$session_worktrees/pushed-session" push origin claude/pushed-session >/dev/null 2>&1
  known_commits+=" $(git -C "$repo" rev-parse claude/pushed-session)"

  # GitHub reports a merged pull request for every branch, so only the checks
  # themselves keep the unmerged sessions.
  output="$(env PATH="$repo/fake-bin:$PATH" FAKE_GH_ALL_MODE=merged \
    FAKE_GH_KNOWN_COMMITS="$known_commits" \
    "$repo/git_clean_worktrees.sh" 2>&1 | sed $'s/\x1b\\[[0-9;]*m//g')"

  for name in partial-session "${kept[@]}"; do
    assert_branch_exists "$repo" "claude/$name"
    assert_path_exists "$session_worktrees/$name"
  done
  assert_output_contains "$output" "Keeping rewritten branch merged in PR #421: claude/partial-session (1 of its 10 changed lines are not in origin/master; --rewritten-prs deletes it after confirmation)"
  assert_output_contains "$output" "Keeping uncommitted work in the worktree of: claude/dirty-session"
  assert_output_contains "$output" "Keeping unmerged: claude/local-session"
  assert_output_contains "$output" "Keeping unmerged: claude/pushed-session"

  for name in "${removed[@]}"; do
    assert_path_missing "$session_worktrees/$name"
  done
  assert_branch_missing "$repo" claude/idle-session
  assert_branch_missing "$repo" worktree-cli-session
  assert_branch_missing "$repo" claude/rebased-session
  assert_branch_missing "$repo" claude/revised-session
  assert_branch_missing "$repo" claude/idle-flow
  assert_path_missing "$flow_worktrees/claude-idle-flow"
  assert_output_contains "$output" "Deleting: claude/idle-session"
  assert_output_contains "$output" "Deleting: claude/idle-flow"
  assert_output_contains "$output" \
    "Deleting rewritten branch whose commits are all in origin/master: claude/rebased-session"
  assert_output_contains "$output" "Deleting rewritten branch merged in PR #421: claude/revised-session"

  output="$(printf 'DELETE\n' | env PATH="$repo/fake-bin:$PATH" FAKE_GH_ALL_MODE=merged \
    FAKE_GH_KNOWN_COMMITS="$known_commits" "$repo/git_clean_worktrees.sh" --rewritten-prs 2>&1 \
    | sed $'s/\x1b\\[[0-9;]*m//g')"

  assert_branch_missing "$repo" claude/partial-session
  assert_path_missing "$session_worktrees/partial-session"
  assert_output_contains "$output" "Deleting partly covered rewritten branch merged in PR #421: claude/partial-session"
  for name in "${kept[@]}"; do
    assert_branch_exists "$repo" "claude/$name"
    assert_path_exists "$session_worktrees/$name"
  done
  assert_path_exists "$session_worktrees/dirty-session/notes.txt"
}

# A commit whose patch is already in master needs no GitHub at all, while one
# a merged pull request rewrote counts only under a settled merged pull request.
test_subject_match_needs_a_settled_merged_pull_request() {
  local repo
  local mode

  for mode in merged-then-open open-then-merged merged-then-closed merged-elsewhere none unavailable; do
    repo="$(new_fixture "rewritten-$mode")"
    create_rewritten_merged_branch "$repo" "rewritten-$mode" claude/rewritten-revised revised
    create_rewritten_merged_branch "$repo" "rewritten-$mode" claude/rewritten-same-patch

    env PATH="$repo/fake-bin:$PATH" FAKE_GH_ALL_MODE="$mode" \
      FAKE_GH_KNOWN_COMMITS="$(git -C "$repo" rev-parse claude/rewritten-revised)" \
      "$repo/git_clean_worktrees.sh" >/dev/null 2>&1

    assert_branch_exists "$repo" claude/rewritten-revised
    assert_branch_missing "$repo" claude/rewritten-same-patch
  done
}

# With --closed-prs and --rewritten-prs together, each group needs its own
# confirmation: the DELETE that answers the closed pull request prompt never
# deletes the partly covered rewritten branches too.
test_each_confirmation_answers_only_its_own_group() {
  local repo
  local output
  local closed_head

  repo="$(new_fixture rewritten-two-prompts)"
  create_rewritten_merged_branch "$repo" rewritten-two-prompts claude/rewritten-partial revised-partial
  create_unmerged_branch "$repo" closed-branch "Closed PR work"
  closed_head="$(git -C "$repo" rev-parse closed-branch)"

  output="$(printf 'DELETE\n' | env PATH="$repo/fake-bin:$PATH" FAKE_GH_ALL_MODE=merged \
    FAKE_GH_MODE=closed FAKE_GH_HEAD_OID="$closed_head" \
    FAKE_GH_KNOWN_COMMITS="$(git -C "$repo" rev-parse claude/rewritten-partial)" \
    "$repo/git_clean_worktrees.sh" --closed-prs --rewritten-prs 2>&1 | sed $'s/\x1b\\[[0-9;]*m//g')"

  assert_branch_missing "$repo" closed-branch
  assert_branch_exists "$repo" claude/rewritten-partial
  assert_output_contains "$output" "Partly covered rewritten branch cleanup cancelled; no candidates were removed."
}

# A listing that fails must keep the branch, never read as nothing pending.
test_rewritten_branch_enumeration_failure_keeps_it() {
  local repo
  local failing

  for failing in patch-id merges; do
    repo="$(new_fixture "rewritten-enumeration-$failing")"
    create_rewritten_merged_branch "$repo" "rewritten-enumeration-$failing" claude/rewritten-revised revised
    mkdir -p "$repo/failing-git-bin"
    printf '%s\n' \
      '#!/usr/bin/env bash' \
      'if [[ "$FAILING" == patch-id && "${1:-}" == "patch-id" ]]; then exit 128; fi' \
      'if [[ "$FAILING" == merges && "${1:-}" == "rev-list" && "${2:-}" == "--merges" ]]; then exit 128; fi' \
      'exec "$SYSTEM_GIT" "$@"' > "$repo/failing-git-bin/git"
    chmod +x "$repo/failing-git-bin/git"

    env PATH="$repo/failing-git-bin:$repo/fake-bin:$PATH" SYSTEM_GIT="$(command -v git)" \
      FAILING="$failing" FAKE_GH_ALL_MODE=merged \
      FAKE_GH_KNOWN_COMMITS="$(git -C "$repo" rev-parse claude/rewritten-revised)" \
      "$repo/git_clean_worktrees.sh" >/dev/null 2>&1

    assert_branch_exists "$repo" claude/rewritten-revised
  done
}

# Git older than 2.40 has no `merge-tree --merge-base` and answers with its
# usage status, so a squashed copy that only a merge proves is kept and the run
# says why once, while a copy whose patch is in master still goes.
test_rewritten_branch_needs_merge_tree_merge_base() {
  local repo
  local output

  repo="$(new_fixture rewritten-old-git)"
  create_rewritten_merged_branch "$repo" rewritten-old-git claude/rewritten-first squashed
  create_rewritten_merged_branch "$repo" rewritten-old-git claude/rewritten-second squashed
  create_rewritten_merged_branch "$repo" rewritten-old-git claude/rewritten-same-patch
  mkdir -p "$repo/old-git-bin"
  printf '%s\n' \
    '#!/usr/bin/env bash' \
    'if [[ "${1:-}" == "merge-tree" ]]; then echo "usage: git merge-tree <base-tree> <branch1> <branch2>" >&2; exit 129; fi' \
    'exec "$SYSTEM_GIT" "$@"' > "$repo/old-git-bin/git"
  chmod +x "$repo/old-git-bin/git"

  output="$(env PATH="$repo/old-git-bin:$repo/fake-bin:$PATH" SYSTEM_GIT="$(command -v git)" \
    FAKE_GH_ALL_MODE=merged "$repo/git_clean_worktrees.sh" 2>&1 | sed $'s/\x1b\\[[0-9;]*m//g')"

  assert_branch_exists "$repo" claude/rewritten-first
  assert_branch_exists "$repo" claude/rewritten-second
  assert_branch_missing "$repo" claude/rewritten-same-patch
  [[ "$(grep -c "git merge-tree --write-tree --merge-base could not run" <<< "$output")" -eq 1 ]] \
    || fail "Expected the missing merge-tree mode to be reported exactly once."
}

test_rewritten_branches_merged_into_master
test_subject_match_needs_a_settled_merged_pull_request
test_partly_covered_rewritten_branch_needs_confirmation
test_claude_session_branches
test_rewritten_branch_enumeration_failure_keeps_it
test_each_confirmation_answers_only_its_own_group
test_rewritten_branch_needs_merge_tree_merge_base
test_closed_pr_worktree_follows_confirmation
test_merged_branch_and_worktree_removal
test_merged_worktree_matching_target_is_removed
test_worktree_with_run_or_local_files_is_kept
test_uninspectable_merged_worktree_is_reported_as_an_error
test_worktree_path_with_trailing_newline_is_not_truncated
test_generated_only_orphan_residue_cleanup_is_scoped
test_primary_worktree_path_with_newline_keeps_unrelated_worktree_home
test_primary_worktree_parent_with_trailing_newline_keeps_other_directory
test_directory_holding_a_registered_worktree_is_untouched
test_symlinked_worktree_home_is_not_followed
test_missing_worktree_record_is_pruned_before_branch_cleanup
test_worktree_below_inaccessible_ancestor_is_preserved
test_existing_checkout_with_missing_pointer_is_preserved
test_merged_recovery_branch_is_preserved
test_stale_merged_orphan_requires_matching_files
test_stale_orphan_metadata_lookup_failure_is_fail_closed
test_stale_orphan_requires_github_derived_head
test_closed_unmerged_branch_requires_and_accepts_confirmation
test_closed_unmerged_worktree_matching_target_is_removed
test_local_only_commits_and_dirty_worktrees_are_preserved
test_recovery_current_master_and_unavailable_or_ambiguous_metadata_are_preserved

echo "PASS: ${SCRIPT_UNDER_TEST##*/} safety regression suite"
