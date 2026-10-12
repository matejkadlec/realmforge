#!/usr/bin/env bash
# Remove local branches and their worktrees safely after they are merged into
# origin/master, together with orphaned residue in the worktree homes.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

TARGET_REMOTE="origin"
TARGET_BRANCH="master"
TARGET_REF="$TARGET_REMOTE/$TARGET_BRANCH"
# A rewritten commit tied to its merged pull request by subject must share at
# least this percentage of its changed lines with that pull request's commits
# before --rewritten-prs may offer it. On the owner's branches in the project
# this script was ported from, a commit's own pull request measured 89 to 100
# percent on 2026-10-11, an unrelated one at most 11 (RLM-97).
REWRITTEN_COMMIT_MINIMUM_SHARE=80
CLOSED_PRS_MODE=false
REWRITTEN_PRS_MODE=false
ASSUME_YES=false

usage() {
  cat <<'EOF'
Usage: ./git_clean_worktrees.sh [--closed-prs] [--rewritten-prs] [--yes]

Without options, safely remove local branches and their removable attached
worktrees that are already merged into origin/master. A worktree is removable
when it is pristine, or when every change Git reports is already byte-identical
to origin/master, and it holds no file under run/ (worlds; GameTest output in
run/gametest/ aside) or .local/, which Git ignores. This also removes fail-closed orphan worktree residue directly
inside the two worktree homes: .claude/worktrees/ in the primary checkout
(Claude Code session worktrees) and realmforge-worktrees/ beside it (flow
worktrees).

A branch whose commits were rewritten before they merged, such as a playtest
branch that qa1 rebased, also counts as merged when its remote branch is gone or
already in origin/master and every commit it holds outside origin/master is
already there: the whole branch merges as a no-op, or each commit's exact
patch, whitespace included, is in origin/master or applies onto it as a no-op,
or, only once GitHub's newest pull request for the branch is merged into master
with none open, GitHub holds the exact commit, a commit of that pull request has
its subject (type and scope aside), and every line it adds or removes is
covered, file by file: the pull request's final change made the same change in
that file, or origin/master's version of the file holds the added line or no
longer holds the removed one. A branch its pull request covers only in part is
kept and reported with its uncovered line count.

  --closed-prs  Also consider local branches for closed, unmerged pull requests.
                Each candidate must have no remote branch, match the pull
                request head exactly, and have a removable attached worktree
                under the same rule.
  --rewritten-prs
                Also delete those partly covered rewritten branches once
                confirmed: the lines review rewrote, such as a renumbered
                revision id, are not in origin/master, but the pull request's
                own commits made at least 80 percent of each commit's changed
                lines. Each needs a removable attached worktree under the same
                rule, and its confirmation is asked apart from --closed-prs.
  --yes         Confirm --closed-prs or --rewritten-prs deletion
                non-interactively. This is valid only together with one of
                them.
EOF
}

print_header() {
  echo -e "${BLUE}============================================${NC}"
  echo -e "${BLUE}$1${NC}"
  echo -e "${BLUE}============================================${NC}"
}

print_error() {
  echo -e "${RED}ERROR: $1${NC}"
}

print_info() {
  echo -e "${YELLOW}$1${NC}"
}

print_success() {
  echo -e "${GREEN}$1${NC}"
}

discard_temporary_directory() {
  local temporary_directory="$1"

  [[ -d "$temporary_directory" && ! -L "$temporary_directory" ]] || return 1
  find "$temporary_directory" -xdev -depth -delete
}

create_worktree_list_snapshot() {
  local temporary_directory

  if ! temporary_directory="$(mktemp -d)"; then
    return 1
  fi

  if ! git worktree list --porcelain -z > "$temporary_directory/porcelain"; then
    discard_temporary_directory "$temporary_directory" || true
    return 1
  fi

  printf '%s\n' "$temporary_directory"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --closed-prs)
      CLOSED_PRS_MODE=true
      ;;
    --rewritten-prs)
      REWRITTEN_PRS_MODE=true
      ;;
    --yes)
      ASSUME_YES=true
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      print_error "Unknown option: $1"
      usage
      exit 1
      ;;
  esac
  shift
done

if [[ "$ASSUME_YES" == true && "$CLOSED_PRS_MODE" != true && "$REWRITTEN_PRS_MODE" != true ]]; then
  print_error "--yes is only valid together with --closed-prs or --rewritten-prs."
  usage
  exit 1
fi

cd "$SCRIPT_DIR"

print_header "Git Worktree Cleanup"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  print_error "This script must be run inside a Git repository."
  exit 1
fi

if ! git remote get-url "$TARGET_REMOTE" >/dev/null 2>&1; then
  print_error "Remote '$TARGET_REMOTE' does not exist."
  exit 1
fi

print_info "Fetching and pruning deleted remote branches from $TARGET_REMOTE..."
git fetch --prune "$TARGET_REMOTE"

if ! git rev-parse --verify --quiet "$TARGET_REF" >/dev/null; then
  print_error "Target ref '$TARGET_REF' does not exist after fetch."
  exit 1
fi
TARGET_TREE="$(git rev-parse "$TARGET_REF^{tree}")"

REPOSITORY_ROOT="$(git rev-parse --show-toplevel)"
if ! worktree_list_snapshot="$(create_worktree_list_snapshot)"; then
  print_error "Could not inspect registered worktrees."
  exit 1
fi
PRIMARY_WORKTREE_ROOT=""
while IFS= read -r -d '' worktree_record; do
  if [[ "$worktree_record" == "worktree "* ]]; then
    PRIMARY_WORKTREE_ROOT="${worktree_record#worktree }"
    break
  fi
done < "$worktree_list_snapshot/porcelain"
if ! discard_temporary_directory "$worktree_list_snapshot"; then
  print_error "Could not discard the temporary worktree snapshot."
  exit 1
fi
if [[ -z "$PRIMARY_WORKTREE_ROOT" ]]; then
  print_error "Could not determine the primary worktree path."
  exit 1
fi
case "$PRIMARY_WORKTREE_ROOT" in
  /*)
    ;;
  *)
    print_error "Primary worktree path is not absolute: $PRIMARY_WORKTREE_ROOT"
    exit 1
    ;;
esac
# Linked worktrees live one directory each in two homes: Claude Code puts its
# session worktrees in the primary checkout's ignored `.claude/worktrees/`, and
# the flows put theirs in `realmforge-worktrees/` beside the primary checkout
# (CLAUDE.md). Orphan residue is looked for and removed only directly inside
# them.
PRIMARY_WORKTREE_PARENT="${PRIMARY_WORKTREE_ROOT%/}"
PRIMARY_WORKTREE_PARENT="${PRIMARY_WORKTREE_PARENT%/*}"
WORKTREE_HOMES=(
  "${PRIMARY_WORKTREE_ROOT%/}/.claude/worktrees"
  "$PRIMARY_WORKTREE_PARENT/realmforge-worktrees"
)
COMMON_GIT_DIR="$(cd "$(git rev-parse --git-common-dir)" && pwd -P)"

current_branch="$(git branch --show-current)"
if [[ -z "$current_branch" ]]; then
  print_error "Detached HEAD detected. Check out a branch before running cleanup."
  exit 1
fi

declare -a preserved_worktree_paths=()
declare -a preserved_worktree_branches=()
declare -a preserved_worktree_branch_paths=()

worktree_path_is_confirmed_missing() {
  local worktree_path="$1"
  local worktree_parent

  if [[ -e "$worktree_path" || -L "$worktree_path" ]]; then
    return 1
  fi

  worktree_parent="${worktree_path%/*}"
  [[ -n "$worktree_parent" ]] || worktree_parent="/"
  [[ -d "$worktree_parent" && -x "$worktree_parent" ]]
}

preserve_existing_worktree_mappings() {
  local record
  local snapshot_directory
  local worktree_path=""
  local preserve_worktree=false

  if ! snapshot_directory="$(create_worktree_list_snapshot)"; then
    print_error "Could not preserve the existing worktree mappings."
    return 1
  fi

  while IFS= read -r -d '' record; do
    case "$record" in
      "worktree "*)
        worktree_path="${record#worktree }"
        preserve_worktree=false
        if ! worktree_path_is_confirmed_missing "$worktree_path"; then
          preserve_worktree=true
          preserved_worktree_paths+=("$worktree_path")
        fi
        ;;
      "branch "*)
        if [[ "$preserve_worktree" == true ]]; then
          preserved_worktree_branches+=("${record#branch refs/heads/}")
          preserved_worktree_branch_paths+=("$worktree_path")
        fi
        ;;
    esac
  done < "$snapshot_directory/porcelain"

  discard_temporary_directory "$snapshot_directory"
}

worktree_path_for_branch() {
  local branch="$1"
  local output_variable="$2"
  local branch_ref="refs/heads/$1"
  local index
  local record
  local snapshot_directory
  local matched_path=""
  local listed_worktree_path=""

  printf -v "$output_variable" '%s' ""

  for index in "${!preserved_worktree_branches[@]}"; do
    if [[ "${preserved_worktree_branches[$index]}" == "$branch" ]]; then
      printf -v "$output_variable" '%s' "${preserved_worktree_branch_paths[$index]}"
      return 0
    fi
  done

  if ! snapshot_directory="$(create_worktree_list_snapshot)"; then
    return 1
  fi

  while IFS= read -r -d '' record; do
    case "$record" in
      "worktree "*)
        listed_worktree_path="${record#worktree }"
        ;;
      "branch "*)
        if [[ "${record#branch }" == "$branch_ref" ]]; then
          matched_path="$listed_worktree_path"
          break
        fi
        ;;
    esac
  done < "$snapshot_directory/porcelain"

  if ! discard_temporary_directory "$snapshot_directory"; then
    return 1
  fi

  if [[ -n "$matched_path" ]]; then
    printf -v "$output_variable" '%s' "$matched_path"
    return 0
  fi

  return 1
}

# Leaves the raw NUL-separated status stream in "$status_entries" so a dirty
# checkout is scanned once instead of once per inspection step.
classify_worktree_state() {
  local worktree_path="$1"
  local status_entries="$2"
  local output_variable="$3"

  printf -v "$output_variable" '%s' 'unreadable'

  if [[ ! -e "$worktree_path/.git" && ! -L "$worktree_path/.git" ]]; then
    return 0
  fi

  if ! git -C "$worktree_path" status --porcelain=v1 --untracked-files=all -z \
    > "$status_entries" 2>/dev/null; then
    return 0
  fi

  if [[ -s "$status_entries" ]]; then
    printf -v "$output_variable" '%s' 'dirty'
  else
    printf -v "$output_variable" '%s' 'clean'
  fi
}

# Renames and copies report two paths per record, and an unmerged record
# describes a conflict rather than one comparable working-tree state. A staged
# change the working tree then modified or deleted (AM, AD, MM, MD) leaves index
# content that is in no commit and in no file, so it cannot survive the removal.
worktree_status_record_is_comparable() {
  case "$1" in
    *[RCU]* | AA | DD | [AM][MD])
      return 1
      ;;
  esac
}

# A tracked file a tool rewrites, such as uv.lock after a newer `uv sync`, can
# leave an older checkout dirty for good even though the rewritten content is
# exactly what is already merged. Discarding such a checkout loses nothing, but
# only when every reported change is already byte-identical to the merge target.
#
# Tracked paths are compared with a single `git diff`, which applies exactly the
# content, mode and `core.fileMode` rules Git used to report the change. An
# untracked path has no index entry for `git diff` to work from, so those paths
# are staged into a throwaway index seeded with the merge target and compared the
# same way. Returns 2 when the comparison could not be performed at all.
worktree_changes_match_target() {
  local worktree_path="$1"
  local temporary_directory="$2"
  local status_entries="$3"
  local entry
  local change_seen=false
  local -a tracked_paths=()
  local -a untracked_paths=()

  while IFS= read -r -d '' entry; do
    change_seen=true
    if [[ ${#entry} -le 3 ]]; then
      return 1
    fi
    if ! worktree_status_record_is_comparable "${entry:0:2}"; then
      return 1
    fi
    if [[ "${entry:0:2}" == "??" ]]; then
      untracked_paths+=("${entry:3}")
    else
      tracked_paths+=("${entry:3}")
    fi
  done < "$status_entries"

  [[ "$change_seen" == true ]] || return 1

  if [[ ${#tracked_paths[@]} -gt 0 ]]; then
    if ! git -C "$worktree_path" --literal-pathspecs diff --quiet \
      --ignore-submodules=none "$TARGET_REF" -- "${tracked_paths[@]}"; then
      return 1
    fi
  fi

  if [[ ${#untracked_paths[@]} -gt 0 ]]; then
    if ! printf '%s\0' "${untracked_paths[@]}" > "$temporary_directory/untracked"; then
      return 2
    fi
    if ! GIT_INDEX_FILE="$temporary_directory/index" \
      git -C "$worktree_path" read-tree "$TARGET_REF" 2>/dev/null; then
      return 2
    fi
    if ! GIT_INDEX_FILE="$temporary_directory/index" \
      git -C "$worktree_path" update-index --add -z --stdin \
      < "$temporary_directory/untracked" 2>/dev/null; then
      return 1
    fi
    if ! GIT_INDEX_FILE="$temporary_directory/index" \
      git -C "$worktree_path" diff-index --quiet --cached "$TARGET_REF" --; then
      return 1
    fi
  fi
}

# Reports whether the checkout can be discarded, and returns the observed state
# so the caller can separate a real inspection failure from uncommitted work
# that is deliberately preserved. A failure to set up or discard the scratch
# directory is an inspection failure, never an ordinary keep.
worktree_is_disposable() {
  local worktree_path="$1"
  local state_variable="$2"
  # Deliberately not named after the caller's variable: a local of the same name
  # would shadow the indirect assignment and never reach it.
  local observed_state
  local temporary_directory
  local comparison_status=1

  printf -v "$state_variable" '%s' 'unreadable'

  if ! temporary_directory="$(mktemp -d)"; then
    return 1
  fi

  classify_worktree_state "$worktree_path" "$temporary_directory/status" observed_state
  printf -v "$state_variable" '%s' "$observed_state"

  case "$observed_state" in
    clean)
      comparison_status=0
      ;;
    dirty)
      if worktree_changes_match_target "$worktree_path" "$temporary_directory" \
        "$temporary_directory/status"; then
        comparison_status=0
      else
        comparison_status=$?
      fi
      ;;
  esac

  if ! discard_temporary_directory "$temporary_directory"; then
    comparison_status=2
  fi

  if [[ "$comparison_status" -gt 1 ]]; then
    printf -v "$state_variable" '%s' 'unreadable'
  fi

  if [[ "$comparison_status" -eq 0 ]] && worktree_holds_local_files "$worktree_path"; then
    printf -v "$state_variable" '%s' 'local-files'
    comparison_status=1
  fi

  [[ "$comparison_status" -eq 0 ]]
}

# run/ (dev client and server worlds, which may hold rooms built by hand) and
# .local/ (the owner's private inputs) are gitignored, so a worktree holding
# them still reads clean, and `git worktree remove` would delete them without a
# word. Any file there keeps the worktree, except in run/gametest/: the GameTest
# server's output, which every `runGameTestServer` of the gate recreates. A scan
# that fails could have missed a world, so it keeps the worktree too.
worktree_holds_local_files() {
  local worktree_path="$1"
  local search_paths=()
  local first_file

  [[ -e "$worktree_path/run" || -L "$worktree_path/run" ]] && search_paths+=(run)
  [[ -e "$worktree_path/.local" || -L "$worktree_path/.local" ]] && search_paths+=(.local)
  [[ ${#search_paths[@]} -gt 0 ]] || return 1

  # Relative paths keep -path free of whatever glob characters the worktree path holds.
  if ! first_file="$(cd "$worktree_path" && find "${search_paths[@]}" -path run/gametest -prune \
    -o ! -type d -print -quit 2>/dev/null)"; then
    return 0
  fi

  [[ -n "$first_file" ]]
}

# Preserving a checkout that holds real work is the expected outcome, so only a
# genuine inspection failure is reported as an error.
report_preserved_worktree() {
  local branch="$1"
  local worktree_path="$2"
  local worktree_state="$3"

  if [[ "$worktree_state" == "dirty" ]]; then
    echo -e "${YELLOW}Keeping uncommitted work in the worktree of:${NC} $branch ($worktree_path)"
    return
  fi

  if [[ "$worktree_state" == "local-files" ]]; then
    echo -e "${YELLOW}Keeping run/ or .local/ files in the worktree of:${NC} $branch ($worktree_path); move them, then rerun"
    return
  fi

  print_error "Keeping $branch because its worktree could not be inspected: $worktree_path"
}

# A checkout whose changes only reproduce the merge target is still dirty for
# Git, so removing it needs the explicit override. `--force` also skips the
# validation `git worktree remove` performs for itself, so the disposability
# check is repeated immediately before it, and the ignored files the removal
# discards without ever comparing them are named first. Either removal deletes
# ignored files, so run/ and .local/ are checked again right before it: a dev
# client can save a world after the caller's check.
remove_worktree_checkout() {
  local worktree_path="$1"
  local worktree_state="$2"
  local recheck_state=""
  local ignored_entries=""

  if worktree_holds_local_files "$worktree_path"; then
    print_error "Worktree gained run/ or .local/ files while it was being removed: $worktree_path"
    return 1
  fi

  if [[ "$worktree_state" != "dirty" ]]; then
    git worktree remove "$worktree_path"
    return
  fi

  if ! worktree_is_disposable "$worktree_path" recheck_state; then
    print_error "Worktree became $recheck_state while it was being removed: $worktree_path"
    return 1
  fi

  # Git never reports ignored files as changes, so nothing above compared them.
  if ignored_entries="$(git -C "$worktree_path" status --porcelain=v1 \
    --untracked-files=no --ignored=traditional 2>/dev/null)" \
    && grep -q '^!! ' <<< "$ignored_entries"; then
    print_info "Also discarding ignored files that were never compared, such as build output, caches or run/gametest/: $worktree_path"
  fi

  git worktree remove --force "$worktree_path"
}

is_registered_worktree_path() {
  local candidate_path="$1"
  local preserved_path
  local record
  local snapshot_directory
  local registered=false

  for preserved_path in "${preserved_worktree_paths[@]}"; do
    if [[ "$preserved_path" == "$candidate_path" ]]; then
      return 0
    fi
  done

  if ! snapshot_directory="$(create_worktree_list_snapshot)"; then
    print_error "Treating $candidate_path as registered because current worktree metadata could not be inspected."
    return 0
  fi

  while IFS= read -r -d '' record; do
    if [[ "$record" == "worktree $candidate_path" ]]; then
      registered=true
      break
    fi
  done < "$snapshot_directory/porcelain"

  if ! discard_temporary_directory "$snapshot_directory"; then
    print_error "Treating $candidate_path as registered because its worktree snapshot could not be discarded safely."
    return 0
  fi

  [[ "$registered" == true ]]
}

# A directory in a worktree home can hold nested worktrees instead of being one. Its contents are relative to the wrong root for every residue check,
# so it is never a removal candidate while any registered worktree lies below it.
holds_registered_worktree() {
  local candidate_path="$1"
  local preserved_path
  local record
  local snapshot_directory
  local holds=false

  for preserved_path in "${preserved_worktree_paths[@]}"; do
    if [[ "$preserved_path" == "$candidate_path/"* ]]; then
      return 0
    fi
  done

  if ! snapshot_directory="$(create_worktree_list_snapshot)"; then
    print_error "Treating $candidate_path as holding a worktree because current worktree metadata could not be inspected."
    return 0
  fi

  while IFS= read -r -d '' record; do
    if [[ "$record" == "worktree $candidate_path/"* ]]; then
      holds=true
      break
    fi
  done < "$snapshot_directory/porcelain"

  if ! discard_temporary_directory "$snapshot_directory"; then
    print_error "Treating $candidate_path as holding a worktree because its worktree snapshot could not be discarded safely."
    return 0
  fi

  [[ "$holds" == true ]]
}

# Only build output and caches a later run recreates. run/ (dev client and
# server worlds, which may hold rooms built by hand) and .local/ (the owner's
# private inputs) are never recognized, so residue holding them is kept.
is_recognized_runtime_artifact_path() {
  local relative_path="$1"

  case "$relative_path" in
    .gradle|.gradle/*|\
    build|build/*|\
    mod/build|mod/build/*|\
    qa-mod/build|qa-mod/build/*|\
    .venv|.venv/*|\
    .pytest_cache|.pytest_cache/*|\
    .ruff_cache|.ruff_cache/*|\
    .cache|.cache/*|\
    */__pycache__|*/__pycache__/*)
      return 0
      ;;
  esac

  return 1
}

# Through a symlink the scan would reach some other directory, so a home is
# never followed when it, or the directory holding it (.claude/ for the session
# home), is one. Git records the primary checkout with symlinks resolved.
worktree_home_is_symlinked() {
  local worktree_home="$1"

  [[ -L "$worktree_home" || -L "${worktree_home%/*}" ]]
}

remove_recognized_orphan_directory() {
  local candidate_path="$1"
  local worktree_home="$2"
  local expected_state="$3"
  local merged_head="${4:-}"
  local candidate_name="${candidate_path#"$worktree_home/"}"

  if [[ "$candidate_name" == "$candidate_path" || -z "$candidate_name" || "$candidate_name" == */* ]]; then
    print_error "Refusing to remove an orphan path that is not a direct child of $worktree_home: $candidate_path"
    return 1
  fi

  if [[ ! -d "$worktree_home" ]] || worktree_home_is_symlinked "$worktree_home"; then
    print_error "Refusing to remove an orphan path whose worktree home is no longer a real directory: $candidate_path"
    return 1
  fi

  if [[ "$candidate_path" == "$PRIMARY_WORKTREE_ROOT" || "$candidate_path" == "$REPOSITORY_ROOT" || ! -d "$candidate_path" || -L "$candidate_path" ]]; then
    print_error "Refusing to remove an invalid orphan worktree path: $candidate_path"
    return 1
  fi

  if is_registered_worktree_path "$candidate_path"; then
    print_error "Refusing to remove a path that is now a registered worktree: $candidate_path"
    return 1
  fi

  if holds_registered_worktree "$candidate_path"; then
    print_error "Refusing to remove a path that now holds a registered worktree: $candidate_path"
    return 1
  fi

  case "$expected_state" in
    generated)
      if [[ -e "$candidate_path/.git" || -L "$candidate_path/.git" ]]; then
        print_error "Refusing to remove generated residue whose worktree metadata changed: $candidate_path"
        return 1
      fi
      if ! generated_residue_is_disposable "$candidate_path"; then
        print_error "Refusing to remove generated residue whose contents changed: $candidate_path"
        return 1
      fi
      ;;
    stale-pointer)
      if ! stale_pointer_belongs_to_repository "$candidate_path"; then
        print_error "Refusing to remove stale residue whose worktree pointer changed: $candidate_path"
        return 1
      fi
      if [[ -z "$merged_head" ]] || ! orphan_residue_matches_merged_head "$candidate_path" "$merged_head"; then
        print_error "Refusing to remove stale residue whose contents changed: $candidate_path"
        return 1
      fi
      ;;
    *)
      print_error "Refusing to remove orphan residue with an unknown verification state: $candidate_path"
      return 1
      ;;
  esac

  if ! find "$candidate_path" -xdev -depth -delete; then
    print_error "Could not remove orphan worktree residue: $candidate_path"
    return 1
  fi

  if [[ -e "$candidate_path" || -L "$candidate_path" ]]; then
    print_error "Orphan worktree residue still exists after cleanup: $candidate_path"
    return 1
  fi
}

generated_residue_is_disposable() {
  local candidate_path="$1"
  local entry
  local relative_path
  local temporary_directory
  local file_entries
  local empty_directories
  local recognized_artifact_found=false
  local result=0

  if [[ -e "$candidate_path/.git" || -L "$candidate_path/.git" ]]; then
    return 1
  fi

  if ! temporary_directory="$(mktemp -d)"; then
    return 1
  fi
  file_entries="$temporary_directory/files"
  empty_directories="$temporary_directory/empty-directories"

  if ! find "$candidate_path" -xdev -mindepth 1 ! -type d -print0 > "$file_entries"; then
    result=1
  elif ! find "$candidate_path" -xdev -mindepth 1 -type d -empty -print0 > "$empty_directories"; then
    result=1
  fi

  if [[ "$result" -eq 0 ]]; then
    while IFS= read -r -d '' entry; do
      relative_path="${entry#"$candidate_path"/}"
      if ! is_recognized_runtime_artifact_path "$relative_path"; then
        result=1
        break
      fi
      recognized_artifact_found=true
    done < "$file_entries"
  fi

  if [[ "$result" -eq 0 ]]; then
    while IFS= read -r -d '' entry; do
      relative_path="${entry#"$candidate_path"/}"
      if ! is_recognized_runtime_artifact_path "$relative_path"; then
        result=1
        break
      fi
      recognized_artifact_found=true
    done < "$empty_directories"
  fi

  if ! discard_temporary_directory "$temporary_directory"; then
    return 1
  fi

  [[ "$result" -eq 0 && "$recognized_artifact_found" == true ]]
}

stale_pointer_belongs_to_repository() {
  local candidate_path="$1"
  local pointer_line
  local trailing_line=""
  local pointer_path
  local pointer_name

  if [[ ! -f "$candidate_path/.git" || -L "$candidate_path/.git" ]]; then
    return 1
  fi

  {
    IFS= read -r pointer_line || [[ -n "$pointer_line" ]] || return 1
    if IFS= read -r trailing_line || [[ -n "$trailing_line" ]]; then
      return 1
    fi
  } < "$candidate_path/.git"
  if [[ "$pointer_line" != "gitdir: "* ]]; then
    return 1
  fi

  pointer_path="${pointer_line#gitdir: }"
  case "$pointer_path" in
    "$COMMON_GIT_DIR/worktrees/"*)
      ;;
    *)
      return 1
      ;;
  esac

  pointer_name="${pointer_path#"$COMMON_GIT_DIR/worktrees/"}"
  if [[ -z "$pointer_name" || "$pointer_name" == */* ]]; then
    return 1
  fi

  [[ ! -e "$pointer_path" && ! -L "$pointer_path" ]]
}

# A worktree directory is named after its branch in one of three layouts:
# realmforge-worktrees/feat-rlm-17-feature-skeleton holds the flow branch
# feat/rlm-17-feature-skeleton (every "/" replaced by "-"), a desktop session's
# .claude/worktrees/bold-hawking holds claude/bold-hawking, and
# `claude --worktree bold-hawking` holds worktree-bold-hawking. A branch belongs
# to directory NAME when it is NAME with every "/" replaced by "-", when it is
# worktree-NAME, or when it is NAME or NAME-<slug> under at most one prefix
# segment such as claude/ or fix/.
branch_matches_worktree_name() {
  local branch="$1"
  local name="$2"
  local leaf="$branch"

  [[ -n "$name" ]] || return 1
  if [[ "${branch//\//-}" == "$name" || "$branch" == "worktree-$name" ]]; then
    return 0
  fi

  if [[ "$branch" == */* ]]; then
    leaf="${branch#*/}"
    [[ "$leaf" != */* ]] || return 1
  fi

  [[ "$leaf" == "$name" || "$leaf" == "$name-"* ]]
}

find_merged_head_for_orphan() {
  local candidate_path="$1"
  local candidate_name
  local branch_ref
  local branch
  local local_head
  local gh_output
  local _pr_number
  local pr_head_name
  local pr_head_oid
  local _pr_url
  local matched_head
  local existing_head
  local github_match_found=false
  local -a matched_heads=()

  candidate_name="${candidate_path##*/}"
  if [[ -z "$candidate_name" ]]; then
    return 1
  fi

  while IFS= read -r branch_ref; do
    branch="${branch_ref#refs/heads/}"
    branch_matches_worktree_name "$branch" "$candidate_name" || continue
    # An unmerged branch of the same name may own the residue, so it makes the
    # owner ambiguous rather than being passed over.
    local_head="$(git rev-parse --verify --quiet "$branch_ref^{commit}")" || return 1
    git merge-base --is-ancestor "$local_head" "$TARGET_REF" || return 1
    matched_heads+=("$local_head")
  done < <(git for-each-ref --format='%(refname)' refs/heads)

  if command -v gh >/dev/null 2>&1; then
    # Every merged pull request naming this directory must report a local commit
    # already in the target, and every matching head, local or merged, must be
    # the same commit.
    if ! gh_output="$(gh pr list --state merged --limit 1000 --json number,headRefName,headRefOid,mergedAt,url --jq '.[] | select(.mergedAt != null) | [.number, .headRefName, .headRefOid, .url] | join("|")' 2>/dev/null)"; then
      return 1
    fi

    while IFS='|' read -r _pr_number pr_head_name pr_head_oid _pr_url; do
      [[ -z "$pr_head_oid" ]] && continue
      branch_matches_worktree_name "$pr_head_name" "$candidate_name" || continue
      if ! git cat-file -e "$pr_head_oid^{commit}" 2>/dev/null; then
        return 1
      fi
      if ! git merge-base --is-ancestor "$pr_head_oid" "$TARGET_REF"; then
        return 1
      fi
      matched_heads+=("$pr_head_oid")
      github_match_found=true
    done <<< "$gh_output"
  fi

  matched_head=""
  for existing_head in "${matched_heads[@]}"; do
    if [[ -z "$matched_head" ]]; then
      matched_head="$existing_head"
    elif [[ "$matched_head" != "$existing_head" ]]; then
      return 1
    fi
  done

  [[ -n "$matched_head" && "$github_match_found" == true ]] || return 1
  printf '%s\n' "$matched_head"
}

orphan_residue_matches_merged_head() {
  local candidate_path="$1"
  local merged_head="$2"
  local temporary_directory
  local temporary_index
  local file_entries
  local index_matches
  local diff_output
  local status_code
  local _changed_path
  local entry
  local indexed_path
  local relative_path
  local exact_index_match
  local result=0

  if ! temporary_directory="$(mktemp -d)"; then
    return 1
  fi
  temporary_index="$temporary_directory/index"
  file_entries="$temporary_directory/files"
  index_matches="$temporary_directory/index-matches"

  if ! GIT_DIR="$COMMON_GIT_DIR" GIT_WORK_TREE="$candidate_path" GIT_INDEX_FILE="$temporary_index" git read-tree "$merged_head"; then
    result=1
  fi

  if [[ "$result" -eq 0 ]]; then
    GIT_DIR="$COMMON_GIT_DIR" GIT_WORK_TREE="$candidate_path" GIT_INDEX_FILE="$temporary_index" \
      git update-index --really-refresh >/dev/null 2>&1 || true

    if ! diff_output="$(GIT_DIR="$COMMON_GIT_DIR" GIT_WORK_TREE="$candidate_path" GIT_INDEX_FILE="$temporary_index" git diff-files --name-status --ignore-submodules)"; then
      result=1
    else
      while IFS=$'\t' read -r status_code _changed_path; do
        [[ -z "$status_code" ]] && continue
        if [[ "$status_code" != "D" ]]; then
          result=1
          break
        fi
      done <<< "$diff_output"
    fi
  fi

  if [[ "$result" -eq 0 ]]; then
    if ! find "$candidate_path" -xdev -mindepth 1 ! -type d -print0 > "$file_entries"; then
      result=1
    fi
  fi

  if [[ "$result" -eq 0 ]]; then
    while IFS= read -r -d '' entry; do
      relative_path="${entry#"$candidate_path"/}"
      [[ "$relative_path" == ".git" ]] && continue

      exact_index_match=false
      if ! GIT_DIR="$COMMON_GIT_DIR" GIT_WORK_TREE="$candidate_path" GIT_INDEX_FILE="$temporary_index" \
        git --literal-pathspecs ls-files -z -- "$relative_path" > "$index_matches"; then
        result=1
        break
      fi

      while IFS= read -r -d '' indexed_path; do
        if [[ "$indexed_path" != "$relative_path" ]]; then
          result=1
          break
        fi
        exact_index_match=true
      done < "$index_matches"

      if [[ "$result" -ne 0 ]]; then
        break
      fi

      if [[ "$exact_index_match" == true ]]; then
        continue
      fi

      if ! is_recognized_runtime_artifact_path "$relative_path"; then
        result=1
        break
      fi
    done < "$file_entries"
  fi

  if ! discard_temporary_directory "$temporary_directory"; then
    return 1
  fi
  [[ "$result" -eq 0 ]]
}

cleanup_orphaned_worktree_directories() {
  local worktree_home="$1"
  local candidate_path
  local merged_head

  if worktree_home_is_symlinked "$worktree_home"; then
    echo -e "${YELLOW}Not following a symlinked worktree home:${NC} $worktree_home"
    return 0
  fi
  if [[ ! -d "$worktree_home" ]]; then
    return 0
  fi

  while IFS= read -r -d '' candidate_path; do
    if is_registered_worktree_path "$candidate_path" || holds_registered_worktree "$candidate_path"; then
      continue
    fi

    if generated_residue_is_disposable "$candidate_path"; then
      echo -e "${GREEN}Removing generated worktree residue:${NC} $candidate_path"
      if remove_recognized_orphan_directory "$candidate_path" "$worktree_home" generated; then
        orphan_deleted_count=$((orphan_deleted_count + 1))
      else
        orphan_skipped_count=$((orphan_skipped_count + 1))
      fi
      continue
    fi

    if stale_pointer_belongs_to_repository "$candidate_path"; then
      if ! merged_head="$(find_merged_head_for_orphan "$candidate_path")"; then
        print_error "Keeping orphan worktree because its merged HEAD could not be established safely: $candidate_path"
        orphan_skipped_count=$((orphan_skipped_count + 1))
        continue
      fi

      if ! orphan_residue_matches_merged_head "$candidate_path" "$merged_head"; then
        print_error "Keeping orphan worktree because it contains modified or unrecognized files: $candidate_path"
        orphan_skipped_count=$((orphan_skipped_count + 1))
        continue
      fi

      echo -e "${GREEN}Removing verified merged orphan worktree:${NC} $candidate_path"
      if remove_recognized_orphan_directory "$candidate_path" "$worktree_home" stale-pointer "$merged_head"; then
        orphan_deleted_count=$((orphan_deleted_count + 1))
      else
        orphan_skipped_count=$((orphan_skipped_count + 1))
      fi
      continue
    fi

    echo -e "${YELLOW}Keeping unrecognized directory:${NC} $candidate_path"
    orphan_skipped_count=$((orphan_skipped_count + 1))
  done < <(find "$worktree_home" -xdev -mindepth 1 -maxdepth 1 -type d -print0)
}

is_protected_branch() {
  local branch="$1"
  [[ "$branch" == "$current_branch" || "$branch" == "$TARGET_BRANCH" || "$branch" == recovery/* ]]
}

# Succeeds when merging the given revision into the merge target would change
# nothing; with a third argument, only the changes since that merge base are
# merged, so a single commit can be applied alone. Exit status 1 from
# `git merge-tree` is a conflict; anything higher means the merge could not run,
# as on Git older than 2.40, which is said once instead of every branch
# silently reading as unmerged.
merge_into_target_changes_nothing() {
  local revision="$1"
  local merge_base="${2:-}"
  local merged_tree
  local merge_tree_status=0
  local -a merge_base_option=()

  [[ -z "$merge_base" ]] || merge_base_option=("--merge-base=$merge_base")
  merged_tree="$(git merge-tree --write-tree "${merge_base_option[@]}" "$TARGET_REF" "$revision" 2>/dev/null)" \
    || merge_tree_status=$?
  if [[ "$merge_tree_status" -ne 0 ]]; then
    if [[ "$merge_tree_status" -gt 1 && "$merge_tree_failure_reported" != true ]]; then
      print_error "git merge-tree --write-tree --merge-base could not run (it needs Git 2.40 or newer), so only commits whose patch is already in $TARGET_REF, or that their merged pull request rewrote, count as merged."
      merge_tree_failure_reported=true
    fi
    return 1
  fi
  [[ "${merged_tree%%$'\n'*}" == "$TARGET_TREE" ]]
}

# Sets the named variable to the commit subject without its conventional-commit
# type and scope, so a commit a later revision re-scoped (`fix(tools): x`
# became `fix(art): x`) still names the same change.
subject_without_type_and_scope() {
  # Deliberately not named after the caller's variable: a local of the same name
  # would shadow the indirect assignment and never reach it.
  local full_subject="$1"
  local output_variable="$2"
  local conventional_pattern='^[a-z]+(\([^)]*\))?!?: (.+)$'

  if [[ "$full_subject" =~ $conventional_pattern ]]; then
    full_subject="${BASH_REMATCH[2]}"
  fi
  printf -v "$output_variable" '%s' "$full_subject"
}

# Sets the first named variable to the number of GitHub's newest pull request
# for the branch, and the second to its merge commit, when every one it lists
# targets the merge target, none is open, and the newest is merged.
branch_has_settled_merged_pull_request() {
  local branch="$1"
  local number_variable="$2"
  local merge_commit_variable="$3"
  local pr_output
  local pr_row
  local pr_number=""
  local pr_state=""
  local pr_base
  local pr_head_name
  local pr_merge_commit=""

  printf -v "$number_variable" '%s' ""
  printf -v "$merge_commit_variable" '%s' ""
  command -v gh >/dev/null 2>&1 || return 1
  pr_output="$(gh pr list --state all --head "$branch" --limit 100 --json number,state,baseRefName,headRefName,createdAt,mergeCommit --jq 'sort_by(.createdAt) | .[] | [.number, .state, .baseRefName, .headRefName, (.mergeCommit.oid // "")] | join("|")' 2>/dev/null)" || return 1
  [[ -n "$pr_output" ]] || return 1
  while IFS= read -r pr_row; do
    IFS='|' read -r pr_number pr_state pr_base pr_head_name pr_merge_commit <<< "$pr_row"
    [[ "$pr_head_name" == "$branch" && "$pr_base" == "$TARGET_BRANCH" && "$pr_state" != "OPEN" ]] || return 1
  done <<< "$pr_output"
  [[ "$pr_state" == "MERGED" && -n "$pr_number" && -n "$pr_merge_commit" ]] || return 1

  printf -v "$number_variable" '%s' "$pr_number"
  printf -v "$merge_commit_variable" '%s' "$pr_merge_commit"
}

# Prints, sorted and once each, "<path>TAB<sign><line>" for every line the
# change between two revisions adds (+) or removes (-), trimmed, file by file,
# so a line counts only in the file it changed. Fails on a path holding a tab
# or newline, which the format cannot carry, and when any diff cannot be read.
diff_changed_lines() {
  local from="$1"
  local to="$2"
  local path
  local diff_output
  local -a paths=()

  # NUL-separated paths cannot be captured in a variable, so the listing is
  # proven readable before the loop reads it.
  git diff-tree -r --no-commit-id --name-only --no-renames "$from" "$to" >/dev/null || return 1
  while IFS= read -r -d '' path; do
    paths+=("$path")
  done < <(git diff-tree -r -z --no-commit-id --name-only --no-renames "$from" "$to")

  for path in "${paths[@]}"; do
    [[ "$path" != *$'\t'* && "$path" != *$'\n'* ]] || return 1
    diff_output="$(git --literal-pathspecs diff --no-color --no-ext-diff --no-renames -U0 \
      "$from" "$to" -- "$path")" || return 1
    CHANGED_PATH="$path" awk '
      /^diff --git / { in_hunk = 0; next }
      /^@@ / { in_hunk = 1; next }
      in_hunk && /^[+-]/ {
        line = substr($0, 2)
        gsub(/^[ \t]+|[ \t]+$/, "", line)
        if (line != "") print ENVIRON["CHANGED_PATH"] "\t" substr($0, 1, 1) line
      }' <<< "$diff_output"
  done | LC_ALL=C sort -u
}

# Succeeds when GitHub already holds the exact commit, so it exists outside
# this checkout.
github_has_commit() {
  local commit="$1"
  local known_commit

  known_commit="$(gh api "repos/{owner}/{repo}/commits/$commit" --jq .sha 2>/dev/null)" || return 1
  [[ "$known_commit" == "$commit" ]]
}

# Prints the number of the given changed lines (diff_changed_lines format) that
# origin/master does not bear out, file by file: an added line its version of
# the file does not hold, or a removed line it still holds. A file missing from
# origin/master holds nothing.
lines_not_in_target() {
  local changed="$1"
  local path
  local listing
  local paths
  local content
  local expected
  local missing=0

  paths="$(cut -f1 <<< "$changed" | LC_ALL=C sort -u)" || return 1
  while IFS= read -r path; do
    [[ -n "$path" ]] || continue
    listing="$(git --literal-pathspecs ls-tree --name-only "$TARGET_REF" -- "$path")" || return 1
    content=""
    if [[ -n "$listing" ]]; then
      content="$(git cat-file blob "$TARGET_REF:$path" \
        | awk '{ gsub(/^[ \t]+|[ \t]+$/, ""); if ($0 != "") print }' \
        | LC_ALL=C sort -u)" || return 1
    fi
    expected="$(CHANGED_PATH="$path" awk -F '\t' '$1 == ENVIRON["CHANGED_PATH"] { print substr($0, length($1) + 2) }' \
      <<< "$changed")"
    missing=$((missing + $(LC_ALL=C comm -23 <(sed -n 's/^+//p' <<< "$expected" | LC_ALL=C sort -u) \
      <(printf '%s\n' "$content") | grep -c . || true)))
    missing=$((missing + $(LC_ALL=C comm -12 <(sed -n 's/^-//p' <<< "$expected" | LC_ALL=C sort -u) \
      <(printf '%s\n' "$content") | grep -c . || true)))
  done <<< "$paths"

  printf '%s\n' "$missing"
}

# Measures one rewritten commit against its merged pull request's final change
# (the merge commit against its first parent, never the intermediate commits,
# which may add a line a later review commit removes). Sets the named variables
# to the number of lines the commit adds or removes, and to how many of them
# are uncovered: the pull request's final change does not make them in the
# same file, and origin/master does not bear them out there either. Fails when
# the pull request's own commits, files aside, made fewer than
# REWRITTEN_COMMIT_MINIMUM_SHARE percent of them: that only asks whether the
# commit belongs to this pull request at all, so an unrelated commit is never
# offered, and never counts a line as covered.
rewritten_commit_coverage() {
  local commit="$1"
  local pull_request_lines="$2"
  local pull_request_commit_texts="$3"
  local total_variable="$4"
  local uncovered_variable="$5"
  local commit_lines
  local commit_texts
  local outside_lines
  local commit_line_count
  local commit_text_count
  local shared_text_count
  local uncovered_line_count=0

  printf -v "$total_variable" '%s' 0
  printf -v "$uncovered_variable" '%s' 0
  commit_lines="$(diff_changed_lines "$commit^" "$commit")" || return 1
  [[ -n "$commit_lines" ]] || return 1
  commit_line_count="$(wc -l <<< "$commit_lines")"

  commit_texts="$(cut -f2- <<< "$commit_lines" | LC_ALL=C sort -u)"
  commit_text_count="$(wc -l <<< "$commit_texts")"
  shared_text_count="$(LC_ALL=C comm -12 <(printf '%s\n' "$commit_texts") \
    <(printf '%s\n' "$pull_request_commit_texts") | grep -c . || true)"
  (( shared_text_count * 100 >= commit_text_count * REWRITTEN_COMMIT_MINIMUM_SHARE )) || return 1

  outside_lines="$(LC_ALL=C comm -23 <(printf '%s\n' "$commit_lines") \
    <(printf '%s\n' "$pull_request_lines"))" || return 1
  if [[ -n "$outside_lines" ]]; then
    uncovered_line_count="$(lines_not_in_target "$outside_lines")" || return 1
  fi

  printf -v "$total_variable" '%s' "$commit_line_count"
  printf -v "$uncovered_variable" '%s' "$uncovered_line_count"
}

# A branch whose commits were rewritten before they merged — a playtest branch
# `qa1` rebased, a commit a review fix later changed, a re-scoped follow-up —
# is no ancestor of the merge target although its work is there. It counts as
# merged when its remote branch is gone or already in the target, so no
# in-flight branch goes, and every commit it holds outside the target is
# already delivered, so nothing goes with it:
#
# 1. merging the whole branch into the target would change nothing, as after a
#    squash; or, commit by commit,
# 2. the target gained the same patch since the branch forked, compared
#    whitespace and all (`git patch-id --verbatim`; `git cherry` ignores
#    whitespace, which can change what a line means), or applying the commit
#    alone onto the target changes nothing, as for a fixup folded into another
#    commit; a merge commit is applied against its first parent; or
# 3. for a commit a rebase with conflicts or a later review fix in the same
#    pull request rewrote, only once GitHub's newest pull request for the
#    branch is merged into master with none open, and all of: GitHub already
#    holds the exact commit, so nothing exists only in this checkout; a commit
#    of that pull request has its subject, type and scope aside; and every line
#    it adds or removes is covered under rewritten_commit_coverage, so a
#    subject never decides alone.
#
# A commit made only in this checkout, or a change the merge left out, passes
# none of them and keeps the branch. Returns 0 when the branch's work is in the
# target, 2 when it passes everything but the coverage of the third check and
# every commit still reaches REWRITTEN_COMMIT_MINIMUM_SHARE (a candidate for
# --rewritten-prs), and 1 otherwise. Sets the named variables to the merged
# pull request's number when the third check was needed, and to the changed
# and uncovered line counts of its commits.
branch_work_is_in_target() {
  local branch="$1"
  local output_variable="$2"
  local total_output_variable="$3"
  local uncovered_output_variable="$4"
  local branch_ref="refs/heads/$1"
  local remote_output
  local remote_head
  local fork_point
  local patch_id
  local commit
  local parent
  local subject
  local target_patch_ids
  local branch_patch_ids
  local branch_commits
  local merge_commits
  local pull_request_subject_list
  local pull_request_lines
  local pull_request_commit
  local pull_request_commits
  local pull_request_commit_texts
  local commit_total
  local commit_uncovered
  local branch_total=0
  local branch_uncovered=0
  # Not named after the caller's variables, which a local would shadow.
  local settled_pull_request=""
  local settled_merge_commit=""
  local -a pending_commits=()
  local -A target_patches=()
  local -A delivered_commits=()
  local -A pull_request_subjects=()

  printf -v "$output_variable" '%s' ""
  printf -v "$total_output_variable" '%s' 0
  printf -v "$uncovered_output_variable" '%s' 0
  remote_output="$(git ls-remote --heads "$TARGET_REMOTE" "$branch_ref" 2>/dev/null)" || return 1
  if [[ -n "$remote_output" ]]; then
    remote_head="${remote_output%%[[:space:]]*}"
    git cat-file -e "$remote_head^{commit}" 2>/dev/null || return 1
    git merge-base --is-ancestor "$remote_head" "$TARGET_REF" || return 1
  fi

  if merge_into_target_changes_nothing "$branch_ref"; then
    return 0
  fi

  # Each listing is read only after its command succeeded: a listing that
  # failed would otherwise read as nothing pending.
  fork_point="$(git merge-base "$TARGET_REF" "$branch_ref")" || return 1
  target_patch_ids="$(git log --no-merges --no-color --no-decorate -p "$fork_point..$TARGET_REF" \
    | git patch-id --verbatim)" || return 1
  while IFS=' ' read -r patch_id _; do
    [[ -z "$patch_id" ]] || target_patches["$patch_id"]=1
  done <<< "$target_patch_ids"
  branch_patch_ids="$(git log --no-merges --no-color --no-decorate -p "$TARGET_REF..$branch_ref" \
    | git patch-id --verbatim)" || return 1
  while IFS=' ' read -r patch_id commit; do
    [[ -n "$patch_id" && -n "${target_patches[$patch_id]+x}" ]] || continue
    delivered_commits["$commit"]=1
  done <<< "$branch_patch_ids"

  branch_commits="$(git rev-list --no-merges "$TARGET_REF..$branch_ref")" || return 1
  while IFS= read -r commit; do
    [[ -n "$commit" && -z "${delivered_commits[$commit]+x}" ]] || continue
    parent="$(git rev-parse --verify --quiet "$commit^1")" || return 1
    if ! merge_into_target_changes_nothing "$commit" "$parent"; then
      pending_commits+=("$commit")
    fi
  done <<< "$branch_commits"
  merge_commits="$(git rev-list --merges "$TARGET_REF..$branch_ref")" || return 1
  while IFS= read -r commit; do
    [[ -n "$commit" ]] || continue
    merge_into_target_changes_nothing "$commit" "$commit^1" || return 1
  done <<< "$merge_commits"

  [[ ${#pending_commits[@]} -gt 0 ]] || return 0

  # The pull request's own commits are the ones its merge commit brought into
  # the target; a squash or rebase merge has no second parent and keeps the
  # branch.
  branch_has_settled_merged_pull_request "$branch" settled_pull_request settled_merge_commit || return 1
  git cat-file -e "$settled_merge_commit^{commit}" 2>/dev/null || return 1
  git merge-base --is-ancestor "$settled_merge_commit" "$TARGET_REF" || return 1
  git rev-parse --verify --quiet "$settled_merge_commit^2" >/dev/null || return 1
  pull_request_subject_list="$(git log --no-merges --format=%s \
    "$settled_merge_commit^1..$settled_merge_commit^2")" || return 1
  while IFS= read -r subject; do
    subject_without_type_and_scope "$subject" subject
    [[ -z "$subject" ]] || pull_request_subjects["$subject"]=1
  done <<< "$pull_request_subject_list"
  pull_request_lines="$(diff_changed_lines "$settled_merge_commit^1" "$settled_merge_commit")" || return 1
  pull_request_commits="$(git rev-list --no-merges "$settled_merge_commit^1..$settled_merge_commit^2")" \
    || return 1
  pull_request_commit_texts="$(
    while IFS= read -r pull_request_commit; do
      [[ -n "$pull_request_commit" ]] || continue
      diff_changed_lines "$pull_request_commit^" "$pull_request_commit" || exit 1
    done <<< "$pull_request_commits" | cut -f2- | LC_ALL=C sort -u
  )" || return 1

  for commit in "${pending_commits[@]}"; do
    subject="$(git log -1 --format=%s "$commit")" || return 1
    subject_without_type_and_scope "$subject" subject
    [[ -n "$subject" && -n "${pull_request_subjects[$subject]+x}" ]] || return 1
    github_has_commit "$commit" || return 1
    rewritten_commit_coverage "$commit" "$pull_request_lines" "$pull_request_commit_texts" \
      commit_total commit_uncovered || return 1
    branch_total=$((branch_total + commit_total))
    branch_uncovered=$((branch_uncovered + commit_uncovered))
  done

  printf -v "$output_variable" '%s' "$settled_pull_request"
  printf -v "$total_output_variable" '%s' "$branch_total"
  printf -v "$uncovered_output_variable" '%s' "$branch_uncovered"
  [[ "$branch_uncovered" -eq 0 ]] || return 2
}

declare -a rewritten_candidate_branches=()
declare -a rewritten_candidate_heads=()
declare -a rewritten_candidate_pr_numbers=()
declare -a rewritten_candidate_worktrees=()

# A branch its merged pull request covers only in part is kept by default and
# reported with what is not in the target; under --rewritten-prs it becomes a
# candidate once its worktree is removable, deleted only after confirmation.
consider_partly_covered_branch() {
  local branch="$1"
  local pr_number="$2"
  local uncovered="$3"
  local total="$4"
  local worktree_path=""
  local worktree_state=""

  if [[ "$REWRITTEN_PRS_MODE" != true ]]; then
    echo -e "${YELLOW}Keeping rewritten branch merged in PR #$pr_number:${NC} $branch ($uncovered of its $total changed lines are not in $TARGET_REF; --rewritten-prs deletes it after confirmation)"
    return
  fi

  if worktree_path_for_branch "$branch" worktree_path; then
    if ! worktree_is_disposable "$worktree_path" worktree_state; then
      report_preserved_worktree "$branch" "$worktree_path" "$worktree_state"
      return
    fi
  fi

  rewritten_candidate_branches+=("$branch")
  rewritten_candidate_heads+=("$(git rev-parse "refs/heads/$branch")")
  rewritten_candidate_pr_numbers+=("$pr_number")
  rewritten_candidate_worktrees+=("$worktree_path")
  echo -e "${GREEN}Partly covered rewritten branch candidate:${NC} $branch (PR #$pr_number; $uncovered of its $total changed lines are not in $TARGET_REF)"
}

remove_partly_covered_candidate() {
  local branch="$1"
  local expected_head="$2"
  local pr_number="$3"
  local worktree_path="$4"
  local worktree_state=""

  if is_protected_branch "$branch"; then
    print_error "Keeping $branch because it became protected during cleanup."
    return
  fi
  if [[ "$(git rev-parse --verify --quiet "refs/heads/$branch")" != "$expected_head" ]]; then
    print_error "Keeping $branch because it moved after it was checked."
    return
  fi

  if [[ -n "$worktree_path" ]]; then
    if ! worktree_is_disposable "$worktree_path" worktree_state; then
      report_preserved_worktree "$branch" "$worktree_path" "$worktree_state"
      return
    fi

    echo -e "${GREEN}Removing partly covered rewritten worktree:${NC} $worktree_path"
    if ! remove_worktree_checkout "$worktree_path" "$worktree_state"; then
      print_error "Could not remove worktree for $branch; keeping the branch."
      return
    fi
  fi

  echo -e "${GREEN}Deleting partly covered rewritten branch merged in PR #$pr_number:${NC} $branch"
  if git branch -D -- "$branch" >/dev/null; then
    rewritten_deleted_count=$((rewritten_deleted_count + 1))
  else
    print_error "Could not delete $branch after removing its worktree."
  fi
}

deleted_count=0
rewritten_deleted_count=0
skipped_count=0
orphan_deleted_count=0
orphan_skipped_count=0
merge_tree_failure_reported=false

preserve_existing_worktree_mappings
git worktree prune

echo ""
print_info "Checking local branches merged into $TARGET_REF..."

while IFS= read -r branch; do
  worktree_state=""
  removal_reason=""

  if is_protected_branch "$branch"; then
    echo -e "${BLUE}Keeping protected branch:${NC} $branch"
    skipped_count=$((skipped_count + 1))
    continue
  fi

  merged_pull_request=""
  rewritten_branch=false
  if ! git merge-base --is-ancestor "$branch" "$TARGET_REF"; then
    work_status=0
    changed_line_total=0
    uncovered_line_total=0
    branch_work_is_in_target "$branch" merged_pull_request changed_line_total \
      uncovered_line_total || work_status=$?
    if [[ "$work_status" -eq 2 ]]; then
      consider_partly_covered_branch "$branch" "$merged_pull_request" \
        "$uncovered_line_total" "$changed_line_total"
      skipped_count=$((skipped_count + 1))
      continue
    fi
    if [[ "$work_status" -ne 0 ]]; then
      echo -e "${YELLOW}Keeping unmerged:${NC} $branch"
      skipped_count=$((skipped_count + 1))
      continue
    fi
    rewritten_branch=true
  fi

  if worktree_path_for_branch "$branch" worktree_path; then
    if ! worktree_is_disposable "$worktree_path" worktree_state; then
      report_preserved_worktree "$branch" "$worktree_path" "$worktree_state"
      skipped_count=$((skipped_count + 1))
      continue
    fi

    removal_reason="Removing worktree"
    if [[ "$worktree_state" == "dirty" ]]; then
      removal_reason="$removal_reason whose changes already match $TARGET_REF"
    fi
    echo -e "${GREEN}${removal_reason}:${NC} $worktree_path"

    if ! remove_worktree_checkout "$worktree_path" "$worktree_state"; then
      print_error "Could not remove worktree for $branch; keeping the branch. Resolve any uncommitted changes or worktree lock, then retry."
      skipped_count=$((skipped_count + 1))
      continue
    fi
  fi

  if [[ -n "$merged_pull_request" ]]; then
    echo -e "${GREEN}Deleting rewritten branch merged in PR #$merged_pull_request:${NC} $branch"
  elif [[ "$rewritten_branch" == true ]]; then
    echo -e "${GREEN}Deleting rewritten branch whose commits are all in $TARGET_REF:${NC} $branch"
  else
    echo -e "${GREEN}Deleting:${NC} $branch"
  fi
  git branch -D -- "$branch" >/dev/null
  deleted_count=$((deleted_count + 1))
done < <(git for-each-ref --format='%(refname:short)' refs/heads)

for worktree_home in "${WORKTREE_HOMES[@]}"; do
  echo ""
  print_info "Checking for orphaned worktree directories under $worktree_home..."
  cleanup_orphaned_worktree_directories "$worktree_home"
done

closed_candidate_branches=()
closed_candidate_pr_numbers=()
closed_candidate_worktrees=()

consider_closed_pr_branch() {
  local branch="$1"
  local branch_head
  local remote_output
  local pr_output
  local pr_number
  local pr_merged_at
  local pr_head_name
  local pr_head_oid
  local _pr_url
  local worktree_path=""
  local worktree_state=""
  local -a pr_rows=()

  if is_protected_branch "$branch"; then
    echo -e "${BLUE}Keeping protected branch:${NC} $branch"
    return
  fi

  if ! remote_output="$(git ls-remote --heads "$TARGET_REMOTE" "refs/heads/$branch" 2>&1)"; then
    print_error "Keeping $branch because its remote branch state could not be verified."
    return
  fi

  if [[ -n "$remote_output" ]]; then
    echo -e "${YELLOW}Keeping remote branch:${NC} $branch"
    return
  fi

  if ! command -v gh >/dev/null 2>&1; then
    print_error "Keeping $branch because GitHub CLI metadata is unavailable."
    return
  fi

  if ! pr_output="$(gh pr list --state closed --head "$branch" --json number,mergedAt,headRefName,headRefOid,url --jq '.[] | [.number, (.mergedAt // "UNMERGED"), .headRefName, .headRefOid, .url] | join("|")' 2>/dev/null)"; then
    print_error "Keeping $branch because GitHub pull request metadata could not be established safely."
    return
  fi

  if [[ -z "$pr_output" ]]; then
    echo -e "${YELLOW}Keeping without a matching closed pull request:${NC} $branch"
    return
  fi

  mapfile -t pr_rows < <(printf '%s\n' "$pr_output")
  if [[ ${#pr_rows[@]} -ne 1 ]]; then
    print_error "Keeping $branch because GitHub returned ambiguous closed pull request metadata."
    return
  fi

  IFS='|' read -r pr_number pr_merged_at pr_head_name pr_head_oid _pr_url <<< "${pr_rows[0]}"
  if [[ "$pr_merged_at" != "UNMERGED" || "$pr_head_name" != "$branch" || -z "$pr_head_oid" ]]; then
    print_error "Keeping $branch because its pull request is not unambiguously closed and unmerged."
    return
  fi

  branch_head="$(git rev-parse "$branch")"
  if [[ "$branch_head" != "$pr_head_oid" ]]; then
    print_error "Keeping $branch because local HEAD differs from closed PR #$pr_number."
    return
  fi

  if worktree_path_for_branch "$branch" worktree_path; then
    if ! worktree_is_disposable "$worktree_path" worktree_state; then
      report_preserved_worktree "$branch" "$worktree_path" "$worktree_state"
      return
    fi
  fi

  closed_candidate_branches+=("$branch")
  closed_candidate_pr_numbers+=("$pr_number")
  closed_candidate_worktrees+=("$worktree_path")
  echo -e "${GREEN}Closed PR candidate:${NC} $branch (PR #$pr_number, closed and unmerged)"
}

remove_closed_pr_candidate() {
  local branch="$1"
  local pr_number="$2"
  local worktree_path="$3"
  local worktree_state=""

  if is_protected_branch "$branch"; then
    print_error "Keeping $branch because it became protected during cleanup."
    return
  fi

  if [[ -n "$worktree_path" ]]; then
    if ! worktree_is_disposable "$worktree_path" worktree_state; then
      report_preserved_worktree "$branch" "$worktree_path" "$worktree_state"
      return
    fi

    echo -e "${GREEN}Removing closed PR worktree:${NC} $worktree_path"
    if ! remove_worktree_checkout "$worktree_path" "$worktree_state"; then
      print_error "Could not remove worktree for $branch; keeping the branch."
      return
    fi
  fi

  echo -e "${GREEN}Deleting closed PR branch:${NC} $branch (PR #$pr_number)"
  if git branch -D -- "$branch" >/dev/null; then
    closed_deleted_count=$((closed_deleted_count + 1))
  else
    print_error "Could not delete $branch after removing its worktree."
  fi
}

closed_deleted_count=0

if [[ "$CLOSED_PRS_MODE" == true ]]; then
  echo ""
  print_info "Checking local branches for closed, unmerged pull requests..."

  while IFS= read -r branch; do
    consider_closed_pr_branch "$branch"
  done < <(git for-each-ref --format='%(refname:short)' refs/heads)

  if [[ ${#closed_candidate_branches[@]} -gt 0 ]]; then
    # Its own answer: a DELETE typed here never confirms the rewritten group.
    confirmed_closed="$ASSUME_YES"
    if [[ "$confirmed_closed" != true ]]; then
      read -r -p "Type DELETE to remove the ${#closed_candidate_branches[@]} closed pull request candidate(s): " confirmation || confirmation=""
      if [[ "$confirmation" != "DELETE" ]]; then
        print_info "Closed pull request cleanup cancelled; no candidates were removed."
      else
        confirmed_closed=true
      fi
    fi

    if [[ "$confirmed_closed" == true ]]; then
      for candidate_index in "${!closed_candidate_branches[@]}"; do
        remove_closed_pr_candidate \
          "${closed_candidate_branches[$candidate_index]}" \
          "${closed_candidate_pr_numbers[$candidate_index]}" \
          "${closed_candidate_worktrees[$candidate_index]}"
      done
    fi
  else
    print_info "No safe closed pull request candidates found."
  fi
fi

if [[ "$REWRITTEN_PRS_MODE" == true ]]; then
  echo ""
  if [[ ${#rewritten_candidate_branches[@]} -gt 0 ]]; then
    confirmed_rewritten=false
    if [[ "$ASSUME_YES" == true ]]; then
      confirmed_rewritten=true
    else
      read -r -p "Type DELETE to remove the ${#rewritten_candidate_branches[@]} partly covered rewritten branch(es) listed above: " confirmation || confirmation=""
      if [[ "$confirmation" == "DELETE" ]]; then
        confirmed_rewritten=true
      else
        print_info "Partly covered rewritten branch cleanup cancelled; no candidates were removed."
      fi
    fi

    if [[ "$confirmed_rewritten" == true ]]; then
      for candidate_index in "${!rewritten_candidate_branches[@]}"; do
        remove_partly_covered_candidate \
          "${rewritten_candidate_branches[$candidate_index]}" \
          "${rewritten_candidate_heads[$candidate_index]}" \
          "${rewritten_candidate_pr_numbers[$candidate_index]}" \
          "${rewritten_candidate_worktrees[$candidate_index]}"
      done
    fi
  else
    print_info "No partly covered rewritten branches found."
  fi
fi

echo ""
print_header "Cleanup Complete"
print_success "Deleted merged local branches: $deleted_count"
print_success "Deleted orphan worktree directories: $orphan_deleted_count"
if [[ "$CLOSED_PRS_MODE" == true ]]; then
  print_success "Deleted closed pull request branches: $closed_deleted_count"
fi
if [[ "$REWRITTEN_PRS_MODE" == true ]]; then
  print_success "Deleted partly covered rewritten branches: $rewritten_deleted_count"
fi
print_success "Kept local branches: $skipped_count"
if [[ "$orphan_skipped_count" -gt 0 ]]; then
  print_success "Kept ambiguous or modified worktree-home directories: $orphan_skipped_count"
fi
print_success "Pruned deleted remote branch references from $TARGET_REMOTE"
