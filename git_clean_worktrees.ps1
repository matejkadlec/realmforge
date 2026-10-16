# Remove local branches and their worktrees safely after they are merged into
# origin/master, together with orphaned residue in the worktree homes.
#
# The PowerShell port of git_clean_worktrees.sh for the Windows clone (RLM-229):
# the same rules, options and messages, function by function, so the regression
# suite tests/test_git_clean_worktrees.sh checks both scripts. A change to one
# belongs in the other. It runs in Windows PowerShell 5.1 and PowerShell 7, and
# stays pure ASCII: Windows PowerShell 5.1 reads a file without a byte order mark
# in the ANSI code page.
#
# Start it from the primary checkout:
#   powershell -ExecutionPolicy Bypass -File .\git_clean_worktrees.ps1 [--closed-prs] [--rewritten-prs] [--yes]

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'

$TargetRemote = 'origin'
$TargetBranch = 'master'
$TargetRef = "$TargetRemote/$TargetBranch"
# A rewritten commit tied to its merged pull request by subject must share at
# least this percentage of its changed lines with that pull request's commits
# before --rewritten-prs may offer it. On the owner's branches in the project
# the bash script was ported from, a commit's own pull request measured 89 to
# 100 percent on 2026-10-11, an unrelated one at most 11 (RLM-97).
$RewrittenCommitMinimumShare = 80
$ClosedPrsMode = $false
$RewrittenPrsMode = $false
$AssumeYes = $false

$script:IsWin = [System.IO.Path]::DirectorySeparatorChar -eq '\'
$script:PathComparison = [StringComparison]::Ordinal
if ($script:IsWin) {
  # NTFS is case-insensitive, and Git keeps whatever case a tool registered a
  # worktree path with.
  $script:PathComparison = [StringComparison]::OrdinalIgnoreCase
}
$script:Utf8 = New-Object System.Text.UTF8Encoding $false
# Line contents are compared byte for byte; Latin-1 maps every byte to one
# character and back.
$script:Latin1 = [System.Text.Encoding]::GetEncoding(28591)
$script:HasArgumentList = $null -ne [System.Diagnostics.ProcessStartInfo].GetProperty('ArgumentList')
$script:WorkingDirectory = $PSScriptRoot

function Show-Usage {
  Write-Host @'
Usage: powershell -ExecutionPolicy Bypass -File .\git_clean_worktrees.ps1 [--closed-prs] [--rewritten-prs] [--yes]

Without options, safely remove local branches and their removable attached
worktrees that are already merged into origin/master. A worktree is removable
when it is pristine, or when every change Git reports is already byte-identical
to origin/master, and it holds no file under run/ (worlds; GameTest output in
run/gametest/ aside) or .local/, which Git ignores. This also removes
fail-closed orphan worktree residue directly inside the two worktree homes:
.claude/worktrees/ in the primary checkout (Claude Code session worktrees) and
realmforge-worktrees/ beside it (flow worktrees).

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
'@
}

function Write-Header([string]$Text) {
  Write-Host '============================================' -ForegroundColor Cyan
  Write-Host $Text -ForegroundColor Cyan
  Write-Host '============================================' -ForegroundColor Cyan
}

function Write-ErrorLine([string]$Text) {
  Write-Host "ERROR: $Text" -ForegroundColor Red
}

function Write-Info([string]$Text) {
  Write-Host $Text -ForegroundColor Yellow
}

function Write-Success([string]$Text) {
  Write-Host $Text -ForegroundColor Green
}

# A coloured label followed by plain text, as `echo -e "${COLOUR}label${NC} text"`.
function Write-Labeled([string]$Label, [string]$Text, [System.ConsoleColor]$Color) {
  Write-Host $Label -ForegroundColor $Color -NoNewline
  Write-Host " $Text"
}

# --- Processes ---------------------------------------------------------------

# Quotes one argument for the command line Windows hands to a program, the way
# the C runtime and Go split it again.
function ConvertTo-QuotedArgument([string]$Argument) {
  if ($Argument.Length -gt 0 -and $Argument -notmatch '[\s"]') {
    return $Argument
  }
  $builder = New-Object System.Text.StringBuilder
  [void]$builder.Append('"')
  $backslashes = 0
  foreach ($character in $Argument.ToCharArray()) {
    if ($character -eq [char]'\') {
      $backslashes++
      continue
    }
    if ($character -eq [char]'"') {
      [void]$builder.Append([char]'\', 2 * $backslashes + 1)
    } elseif ($backslashes -gt 0) {
      [void]$builder.Append([char]'\', $backslashes)
    }
    [void]$builder.Append($character)
    $backslashes = 0
  }
  if ($backslashes -gt 0) {
    [void]$builder.Append([char]'\', 2 * $backslashes)
  }
  [void]$builder.Append('"')
  return $builder.ToString()
}

# Runs a program with exact arguments, environment additions and raw stdin
# bytes, and returns its exit code with its raw output. PowerShell's own native
# command calls re-encode piped text and, in Windows PowerShell 5.1, mangle
# arguments holding quotes or nothing at all, so every Git and GitHub CLI call
# goes through here. Output and ErrorOutput are Capture, Discard (2>/dev/null)
# or Inherit (shown as the program writes it).
function Invoke-Native {
  param(
    [string]$FilePath,
    [string[]]$Arguments = @(),
    [hashtable]$Environment = @{},
    [byte[]]$InputBytes = $null,
    [switch]$InheritInput,
    [string]$Output = 'Capture',
    [string]$ErrorOutput = 'Inherit'
  )

  $failed = [pscustomobject]@{ ExitCode = 127; Bytes = [byte[]]@(); Text = ''; ErrorText = '' }
  if (-not $FilePath) {
    return $failed
  }

  $info = New-Object System.Diagnostics.ProcessStartInfo
  $info.FileName = $FilePath
  $info.UseShellExecute = $false
  $info.WorkingDirectory = $script:WorkingDirectory
  if ($script:HasArgumentList) {
    foreach ($argument in $Arguments) {
      $info.ArgumentList.Add($argument)
    }
  } else {
    $info.Arguments = (@($Arguments | ForEach-Object { ConvertTo-QuotedArgument $_ }) -join ' ')
  }
  foreach ($name in $Environment.Keys) {
    $info.EnvironmentVariables[$name] = [string]$Environment[$name]
  }
  $info.RedirectStandardInput = -not $InheritInput
  $info.RedirectStandardOutput = $Output -ne 'Inherit'
  $info.RedirectStandardError = $ErrorOutput -ne 'Inherit'

  $process = New-Object System.Diagnostics.Process
  $process.StartInfo = $info
  try {
    [void]$process.Start()
  } catch {
    return $failed
  }

  $outputBuffer = New-Object System.IO.MemoryStream
  $errorBuffer = New-Object System.IO.MemoryStream
  $outputTask = $null
  $errorTask = $null
  if ($info.RedirectStandardOutput) {
    $outputTarget = [System.IO.Stream]::Null
    if ($Output -eq 'Capture') {
      $outputTarget = $outputBuffer
    }
    $outputTask = $process.StandardOutput.BaseStream.CopyToAsync($outputTarget)
  }
  if ($info.RedirectStandardError) {
    $errorTarget = [System.IO.Stream]::Null
    if ($ErrorOutput -eq 'Capture') {
      $errorTarget = $errorBuffer
    }
    $errorTask = $process.StandardError.BaseStream.CopyToAsync($errorTarget)
  }
  if ($info.RedirectStandardInput) {
    try {
      if ($null -ne $InputBytes -and $InputBytes.Length -gt 0) {
        $process.StandardInput.BaseStream.Write($InputBytes, 0, $InputBytes.Length)
      }
      $process.StandardInput.Close()
    } catch {
      # The program exited without reading all of its input.
    }
  }
  $process.WaitForExit()
  if ($null -ne $outputTask) {
    $outputTask.Wait()
  }
  if ($null -ne $errorTask) {
    $errorTask.Wait()
  }
  $exitCode = $process.ExitCode
  $process.Dispose()

  $bytes = $outputBuffer.ToArray()
  return [pscustomobject]@{
    ExitCode = $exitCode
    Bytes = $bytes
    Text = $script:Utf8.GetString($bytes)
    ErrorText = $script:Utf8.GetString($errorBuffer.ToArray())
  }
}

function Invoke-Git {
  param(
    [string[]]$Arguments,
    [hashtable]$Environment = @{},
    [byte[]]$InputBytes = $null,
    [switch]$InheritInput,
    [string]$Output = 'Capture',
    [string]$ErrorOutput = 'Inherit'
  )
  return Invoke-Native -FilePath $script:GitPath -Arguments $Arguments -Environment $Environment `
    -InputBytes $InputBytes -InheritInput:$InheritInput -Output $Output -ErrorOutput $ErrorOutput
}

function Invoke-Gh {
  param([string[]]$Arguments, [string]$ErrorOutput = 'Discard')
  return Invoke-Native -FilePath $script:GhPath -Arguments $Arguments -ErrorOutput $ErrorOutput
}

# A Git command whose failure ends the run, as a plain command under `set -e`.
function Invoke-GitOrExit {
  param([string[]]$Arguments, [string]$Output = 'Capture')
  $result = Invoke-Git -Arguments $Arguments -Output $Output
  if ($result.ExitCode -ne 0) {
    exit $result.ExitCode
  }
  return $result
}

function Resolve-Program([string]$Name) {
  foreach ($command in @(Get-Command -Name $Name -CommandType Application -ErrorAction SilentlyContinue)) {
    if (-not $script:IsWin -or $command.Extension -eq '.exe') {
      return $command.Path
    }
  }
  return $null
}

# The lines of a command's output as `$(...)` sees them: trailing newlines
# dropped, then split; nothing at all for empty output.
function Split-Lines([string]$Text) {
  $trimmed = $Text.TrimEnd([char]"`n")
  if ($trimmed.Length -eq 0) {
    return
  }
  $trimmed.Split([char]"`n")
}

# The NUL-terminated records of a -z listing, as `read -d ''` reads them: a
# final piece without its terminator is dropped.
function Split-NulRecords([string]$Text) {
  $pieces = $Text.Split([char]0)
  for ($index = 0; $index -lt $pieces.Length - 1; $index++) {
    $pieces[$index]
  }
}

function Get-FirstLine([string]$Text) {
  $newline = $Text.IndexOf([char]"`n")
  if ($newline -lt 0) {
    return $Text
  }
  return $Text.Substring(0, $newline)
}

function New-StringSet {
  return , (New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal))
}

# --- Paths and files ---------------------------------------------------------

# Git for Windows reports paths with forward slashes; a path given by Windows
# itself may carry backslashes, which on Linux are ordinary file name characters.
function ConvertTo-GitPath([string]$Path) {
  if ($script:IsWin) {
    return $Path.Replace('\', '/')
  }
  return $Path
}

function Test-SamePath([string]$Left, [string]$Right) {
  return [string]::Equals($Left, $Right, $script:PathComparison)
}

function Test-PathStartsWith([string]$Path, [string]$Prefix) {
  return $Path.StartsWith($Prefix, $script:PathComparison)
}

function Test-AbsolutePath([string]$Path) {
  if ($script:IsWin) {
    return $Path -match '^([A-Za-z]:/|//)'
  }
  return $Path.StartsWith('/')
}

function Get-InnermostException($ErrorRecord) {
  $exception = $ErrorRecord.Exception
  while ($exception -is [System.Management.Automation.MethodInvocationException] -and $null -ne $exception.InnerException) {
    $exception = $exception.InnerException
  }
  return $exception
}

# 'present' (a file, a directory or a link, even a broken one), 'missing' (the
# system said so), or 'unknown' (it could not be looked at, such as below a
# directory without permissions). A link is never followed.
function Get-EntryState([string]$Path) {
  try {
    [void][System.IO.File]::GetAttributes($Path)
    return 'present'
  } catch {
    $exception = Get-InnermostException $_
    if ($exception -is [System.IO.FileNotFoundException] -or $exception -is [System.IO.DirectoryNotFoundException]) {
      return 'missing'
    }
    return 'unknown'
  }
}

# The entry's attributes without following a link, or $null.
function Get-EntryAttributes([string]$Path) {
  try {
    return [System.IO.File]::GetAttributes($Path)
  } catch {
    return $null
  }
}

function Test-EntryPresent([string]$Path) {
  return (Get-EntryState $Path) -eq 'present'
}

# A symlink, a junction or a mount point: never followed.
function Test-IsLink([string]$Path) {
  $attributes = Get-EntryAttributes $Path
  return $null -ne $attributes -and ($attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0
}

function Test-IsRealDirectory([string]$Path) {
  $attributes = Get-EntryAttributes $Path
  if ($null -eq $attributes) {
    return $false
  }
  return ($attributes -band [System.IO.FileAttributes]::Directory) -ne 0 -and
    ($attributes -band [System.IO.FileAttributes]::ReparsePoint) -eq 0
}

function Get-LeafName([string]$Path) {
  $separator = $Path.LastIndexOf('/')
  if ($script:IsWin) {
    $separator = [Math]::Max($separator, $Path.LastIndexOf('\'))
  }
  return $Path.Substring($separator + 1)
}

# Everything below the directory, without following links and without the
# directory itself: the relative paths of its non-directories (links included)
# and of its empty directories, as `find -mindepth 1 ! -type d` and
# `find -mindepth 1 -type d -empty` list them. Ok is $false when any directory
# could not be read.
function Get-TreeListing([string]$Root) {
  $listing = [pscustomobject]@{
    Ok = $true
    Files = (New-Object 'System.Collections.Generic.List[string]')
    EmptyDirectories = (New-Object 'System.Collections.Generic.List[string]')
  }
  try {
    Add-TreeListingEntries $Root '' $listing
  } catch {
    $listing.Ok = $false
  }
  return $listing
}

function Add-TreeListingEntries([string]$Directory, [string]$Prefix, $Listing) {
  $children = [System.IO.Directory]::GetFileSystemEntries($Directory)
  if ($children.Length -eq 0 -and $Prefix -ne '') {
    $Listing.EmptyDirectories.Add($Prefix)
  }
  foreach ($child in $children) {
    $name = Get-LeafName $child
    $relative = $name
    if ($Prefix -ne '') {
      $relative = "$Prefix/$name"
    }
    $attributes = [System.IO.File]::GetAttributes($child)
    if (($attributes -band [System.IO.FileAttributes]::Directory) -ne 0 -and
      ($attributes -band [System.IO.FileAttributes]::ReparsePoint) -eq 0) {
      Add-TreeListingEntries $child $relative $Listing
    } else {
      $Listing.Files.Add($relative)
    }
  }
}

# Deletes a file, a link or a directory tree without ever following a link or
# a junction into what it points at (`find -xdev -depth -delete`). Returns
# $false when anything could not be deleted.
function Remove-TreeNoFollow([string]$Path) {
  try {
    Remove-TreeEntry $Path
    return $true
  } catch {
    return $false
  }
}

function Remove-TreeEntry([string]$Path) {
  $attributes = [System.IO.File]::GetAttributes($Path)
  $isDirectory = ($attributes -band [System.IO.FileAttributes]::Directory) -ne 0
  if (($attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
    # Windows removes a directory link or junction as a directory, which deletes
    # only the link; elsewhere a link is unlinked like a file.
    if ($script:IsWin -and $isDirectory) {
      [System.IO.Directory]::Delete($Path, $false)
    } else {
      [System.IO.File]::Delete($Path)
    }
    return
  }
  if (($attributes -band [System.IO.FileAttributes]::ReadOnly) -ne 0) {
    [System.IO.File]::SetAttributes($Path, $attributes -band (-bnot [System.IO.FileAttributes]::ReadOnly))
  }
  if ($isDirectory) {
    foreach ($child in [System.IO.Directory]::GetFileSystemEntries($Path)) {
      Remove-TreeEntry $child
    }
    [System.IO.Directory]::Delete($Path, $false)
  } else {
    [System.IO.File]::Delete($Path)
  }
}

function New-TemporaryDirectory {
  try {
    $path = Join-Path ([System.IO.Path]::GetTempPath()) ('git-clean-worktrees-' + [guid]::NewGuid().ToString('N'))
    [void][System.IO.Directory]::CreateDirectory($path)
    return $path
  } catch {
    return $null
  }
}

function Remove-TemporaryDirectory([string]$Path) {
  if (-not (Test-IsRealDirectory $Path)) {
    return $false
  }
  return Remove-TreeNoFollow $Path
}

# --- Arguments and setup -----------------------------------------------------

foreach ($argument in $args) {
  switch -CaseSensitive ([string]$argument) {
    '--closed-prs' {
      $ClosedPrsMode = $true
    }
    '--rewritten-prs' {
      $RewrittenPrsMode = $true
    }
    '--yes' {
      $AssumeYes = $true
    }
    { $_ -ceq '-h' -or $_ -ceq '--help' } {
      Show-Usage
      exit 0
    }
    default {
      Write-ErrorLine "Unknown option: $argument"
      Show-Usage
      exit 1
    }
  }
}

if ($AssumeYes -and -not $ClosedPrsMode -and -not $RewrittenPrsMode) {
  Write-ErrorLine '--yes is only valid together with --closed-prs or --rewritten-prs.'
  Show-Usage
  exit 1
}

Set-Location -LiteralPath $PSScriptRoot

Write-Header 'Git Worktree Cleanup'

$script:GitPath = Resolve-Program 'git'
$script:GhPath = Resolve-Program 'gh'

if ((Invoke-Git -Arguments @('rev-parse', '--is-inside-work-tree') -ErrorOutput Discard).ExitCode -ne 0) {
  Write-ErrorLine 'This script must be run inside a Git repository.'
  exit 1
}

if ((Invoke-Git -Arguments @('remote', 'get-url', $TargetRemote) -ErrorOutput Discard).ExitCode -ne 0) {
  Write-ErrorLine "Remote '$TargetRemote' does not exist."
  exit 1
}

Write-Info "Fetching and pruning deleted remote branches from $TargetRemote..."
# Inherits the console, so Git can ask for credentials.
[void](Invoke-GitOrExit -Arguments @('fetch', '--prune', $TargetRemote) -Output Inherit)

if ((Invoke-Git -Arguments @('rev-parse', '--verify', '--quiet', $TargetRef) -ErrorOutput Discard).ExitCode -ne 0) {
  Write-ErrorLine "Target ref '$TargetRef' does not exist after fetch."
  exit 1
}
$TargetTree = (Invoke-GitOrExit -Arguments @('rev-parse', "$TargetRef^{tree}")).Text.TrimEnd([char]"`n")

$RepositoryRoot = ConvertTo-GitPath (Invoke-GitOrExit -Arguments @('rev-parse', '--show-toplevel')).Text.TrimEnd([char]"`n")

# The records of `git worktree list --porcelain -z`, paths in Git's form, or
# $null when the listing failed.
function Get-WorktreeRecords {
  $result = Invoke-Git -Arguments @('worktree', 'list', '--porcelain', '-z')
  if ($result.ExitCode -ne 0) {
    return $null
  }
  $records = New-Object 'System.Collections.Generic.List[string]'
  foreach ($record in @(Split-NulRecords $result.Text)) {
    if ($record.StartsWith('worktree ')) {
      $record = 'worktree ' + (ConvertTo-GitPath $record.Substring(9))
    }
    $records.Add($record)
  }
  return , $records
}

$worktreeRecords = Get-WorktreeRecords
if ($null -eq $worktreeRecords) {
  Write-ErrorLine 'Could not inspect registered worktrees.'
  exit 1
}
$PrimaryWorktreeRoot = ''
foreach ($record in $worktreeRecords) {
  if ($record.StartsWith('worktree ')) {
    $PrimaryWorktreeRoot = $record.Substring(9)
    break
  }
}
if ($PrimaryWorktreeRoot -eq '') {
  Write-ErrorLine 'Could not determine the primary worktree path.'
  exit 1
}
if (-not (Test-AbsolutePath $PrimaryWorktreeRoot)) {
  Write-ErrorLine "Primary worktree path is not absolute: $PrimaryWorktreeRoot"
  exit 1
}
# Linked worktrees live one directory each in two homes: Claude Code puts its
# session worktrees in the primary checkout's ignored `.claude/worktrees/`, and
# the flows put theirs in `realmforge-worktrees/` beside the primary checkout
# (CLAUDE.md). Orphan residue is looked for and removed only directly inside
# them.
$PrimaryWorktreeRootTrimmed = $PrimaryWorktreeRoot
if ($PrimaryWorktreeRootTrimmed.EndsWith('/')) {
  $PrimaryWorktreeRootTrimmed = $PrimaryWorktreeRootTrimmed.Substring(0, $PrimaryWorktreeRootTrimmed.Length - 1)
}
$PrimaryWorktreeParent = $PrimaryWorktreeRootTrimmed
$lastSeparator = $PrimaryWorktreeParent.LastIndexOf('/')
if ($lastSeparator -ge 0) {
  $PrimaryWorktreeParent = $PrimaryWorktreeParent.Substring(0, $lastSeparator)
}
$WorktreeHomes = @(
  "$PrimaryWorktreeRootTrimmed/.claude/worktrees",
  "$PrimaryWorktreeParent/realmforge-worktrees"
)
$CommonGitDir = ConvertTo-GitPath (Invoke-GitOrExit -Arguments @('rev-parse', '--path-format=absolute', '--git-common-dir')).Text.TrimEnd([char]"`n")
if ($CommonGitDir.Length -gt 1 -and $CommonGitDir.EndsWith('/')) {
  $CommonGitDir = $CommonGitDir.Substring(0, $CommonGitDir.Length - 1)
}

$currentBranch = (Invoke-GitOrExit -Arguments @('branch', '--show-current')).Text.TrimEnd([char]"`n")
if ($currentBranch -eq '') {
  Write-ErrorLine 'Detached HEAD detected. Check out a branch before running cleanup.'
  exit 1
}

$preservedWorktreePaths = New-Object 'System.Collections.Generic.List[string]'
$preservedWorktreeBranches = New-Object 'System.Collections.Generic.List[string]'
$preservedWorktreeBranchPaths = New-Object 'System.Collections.Generic.List[string]'

# --- Worktrees ---------------------------------------------------------------

function Test-WorktreePathConfirmedMissing([string]$WorktreePath) {
  if ((Get-EntryState $WorktreePath) -ne 'missing') {
    return $false
  }

  $separator = $WorktreePath.LastIndexOf('/')
  $worktreeParent = '/'
  if ($separator -gt 0) {
    $worktreeParent = $WorktreePath.Substring(0, $separator)
  }
  $attributes = Get-EntryAttributes $worktreeParent
  return $null -ne $attributes -and ($attributes -band [System.IO.FileAttributes]::Directory) -ne 0
}

function Save-ExistingWorktreeMappings {
  $records = Get-WorktreeRecords
  if ($null -eq $records) {
    Write-ErrorLine 'Could not preserve the existing worktree mappings.'
    return $false
  }

  $worktreePath = ''
  $preserveWorktree = $false
  foreach ($record in $records) {
    if ($record.StartsWith('worktree ')) {
      $worktreePath = $record.Substring(9)
      $preserveWorktree = $false
      if (-not (Test-WorktreePathConfirmedMissing $worktreePath)) {
        $preserveWorktree = $true
        $preservedWorktreePaths.Add($worktreePath)
      }
    } elseif ($record.StartsWith('branch ')) {
      if ($preserveWorktree) {
        $branchName = $record
        if ($record.StartsWith('branch refs/heads/')) {
          $branchName = $record.Substring(18)
        }
        $preservedWorktreeBranches.Add($branchName)
        $preservedWorktreeBranchPaths.Add($worktreePath)
      }
    }
  }
  return $true
}

# The path of the worktree that has the branch checked out, or $null.
function Get-WorktreePathForBranch([string]$Branch) {
  for ($index = 0; $index -lt $preservedWorktreeBranches.Count; $index++) {
    if ($preservedWorktreeBranches[$index] -ceq $Branch) {
      return $preservedWorktreeBranchPaths[$index]
    }
  }

  $records = Get-WorktreeRecords
  if ($null -eq $records) {
    return $null
  }
  $listedWorktreePath = ''
  foreach ($record in $records) {
    if ($record.StartsWith('worktree ')) {
      $listedWorktreePath = $record.Substring(9)
    } elseif ($record -ceq "branch refs/heads/$Branch") {
      return $listedWorktreePath
    }
  }
  return $null
}

# Renames and copies report two paths per record, and an unmerged record
# describes a conflict rather than one comparable working-tree state. A staged
# change the working tree then modified or deleted (AM, AD, MM, MD) leaves index
# content that is in no commit and in no file, so it cannot survive the removal.
function Test-StatusRecordComparable([string]$Code) {
  if ($Code -cmatch '[RCU]' -or $Code -ceq 'AA' -or $Code -ceq 'DD' -or $Code -cmatch '^[AM][MD]$') {
    return $false
  }
  return $true
}

# A tracked file a tool rewrites, such as uv.lock after a newer `uv sync`, can
# leave an older checkout dirty for good even though the rewritten content is
# exactly what is already merged. Discarding such a checkout loses nothing, but
# only when every reported change is already byte-identical to the merge target.
#
# Tracked paths are compared with `git diff`, which applies exactly the content,
# mode and `core.fileMode` rules Git used to report the change. An untracked path
# has no index entry for `git diff` to work from, so those paths are staged into
# a throwaway index seeded with the merge target and compared the same way.
# Returns 0 when everything matches, 1 when something does not, and 2 when the
# comparison could not be performed at all.
function Compare-WorktreeChangesWithTarget([string]$WorktreePath, [string]$TemporaryDirectory, [string]$StatusText) {
  $changeSeen = $false
  $trackedPaths = New-Object 'System.Collections.Generic.List[string]'
  $untrackedPaths = New-Object 'System.Collections.Generic.List[string]'

  foreach ($entry in @(Split-NulRecords $StatusText)) {
    $changeSeen = $true
    if ($entry.Length -le 3) {
      return 1
    }
    $code = $entry.Substring(0, 2)
    if (-not (Test-StatusRecordComparable $code)) {
      return 1
    }
    if ($code -ceq '??') {
      $untrackedPaths.Add($entry.Substring(3))
    } else {
      $trackedPaths.Add($entry.Substring(3))
    }
  }

  if (-not $changeSeen) {
    return 1
  }

  # In batches, so a long list stays within the Windows command line limit.
  $batch = New-Object 'System.Collections.Generic.List[string]'
  $batchLength = 0
  for ($index = 0; $index -le $trackedPaths.Count; $index++) {
    if ($index -lt $trackedPaths.Count) {
      $batch.Add($trackedPaths[$index])
      $batchLength += $trackedPaths[$index].Length + 3
    }
    if ($batch.Count -gt 0 -and ($index -eq $trackedPaths.Count -or $batchLength -gt 8000)) {
      $arguments = @('-C', $WorktreePath, '--literal-pathspecs', 'diff', '--quiet', '--ignore-submodules=none', $TargetRef, '--') + $batch.ToArray()
      if ((Invoke-Git -Arguments $arguments).ExitCode -ne 0) {
        return 1
      }
      $batch.Clear()
      $batchLength = 0
    }
  }

  if ($untrackedPaths.Count -gt 0) {
    $environment = @{ GIT_INDEX_FILE = (Join-Path $TemporaryDirectory 'index') }
    if ((Invoke-Git -Arguments @('-C', $WorktreePath, 'read-tree', $TargetRef) -Environment $environment -ErrorOutput Discard).ExitCode -ne 0) {
      return 2
    }
    $untrackedInput = $script:Utf8.GetBytes((@($untrackedPaths | ForEach-Object { $_ + [char]0 }) -join ''))
    if ((Invoke-Git -Arguments @('-C', $WorktreePath, 'update-index', '--add', '-z', '--stdin') -Environment $environment `
          -InputBytes $untrackedInput -ErrorOutput Discard).ExitCode -ne 0) {
      return 1
    }
    if ((Invoke-Git -Arguments @('-C', $WorktreePath, 'diff-index', '--quiet', '--cached', $TargetRef, '--') -Environment $environment).ExitCode -ne 0) {
      return 1
    }
  }
  return 0
}

# Reports whether the checkout can be discarded, with the observed state, so the
# caller can separate a real inspection failure from uncommitted work that is
# deliberately preserved. A failure to set up or discard the scratch directory
# is an inspection failure, never an ordinary keep.
function Test-WorktreeDisposable([string]$WorktreePath) {
  $state = 'unreadable'
  $comparisonStatus = 1

  $temporaryDirectory = New-TemporaryDirectory
  if ($null -eq $temporaryDirectory) {
    return [pscustomobject]@{ Disposable = $false; State = 'unreadable' }
  }

  $statusText = ''
  if (Test-EntryPresent "$WorktreePath/.git") {
    $status = Invoke-Git -Arguments @('-C', $WorktreePath, 'status', '--porcelain=v1', '--untracked-files=all', '-z') -ErrorOutput Discard
    if ($status.ExitCode -eq 0) {
      if ($status.Bytes.Length -gt 0) {
        $state = 'dirty'
        $statusText = $status.Text
      } else {
        $state = 'clean'
      }
    }
  }

  if ($state -eq 'clean') {
    $comparisonStatus = 0
  } elseif ($state -eq 'dirty') {
    $comparisonStatus = Compare-WorktreeChangesWithTarget $WorktreePath $temporaryDirectory $statusText
  }

  if (-not (Remove-TemporaryDirectory $temporaryDirectory)) {
    $comparisonStatus = 2
  }

  if ($comparisonStatus -gt 1) {
    $state = 'unreadable'
  }

  if ($comparisonStatus -eq 0 -and (Test-WorktreeHoldsLocalFiles $WorktreePath)) {
    $state = 'local-files'
    $comparisonStatus = 1
  }

  return [pscustomobject]@{ Disposable = ($comparisonStatus -eq 0); State = $state }
}

# Whether a non-directory exists at or below the path, run/gametest aside;
# throws when the scan cannot read a directory.
function Test-TreeHoldsFile([string]$Path, [string]$Relative) {
  if ($Relative -ceq 'run/gametest') {
    return $false
  }
  $attributes = [System.IO.File]::GetAttributes($Path)
  if (($attributes -band [System.IO.FileAttributes]::Directory) -eq 0 -or
    ($attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
    return $true
  }
  foreach ($child in [System.IO.Directory]::GetFileSystemEntries($Path)) {
    if (Test-TreeHoldsFile $child ($Relative + '/' + (Get-LeafName $child))) {
      return $true
    }
  }
  return $false
}

# run/ (dev client and server worlds, which may hold rooms built by hand) and
# .local/ (the owner's private inputs) are gitignored, so a worktree holding
# them still reads clean, and `git worktree remove` would delete them without a
# word. Any file there keeps the worktree, except in run/gametest/: the GameTest
# server's output, which every `runGameTestServer` of the gate recreates. A scan
# that fails could have missed a world, so it keeps the worktree too.
function Test-WorktreeHoldsLocalFiles([string]$WorktreePath) {
  foreach ($root in @('run', '.local')) {
    if (-not (Test-EntryPresent "$WorktreePath/$root")) {
      continue
    }
    try {
      if (Test-TreeHoldsFile "$WorktreePath/$root" $root) {
        return $true
      }
    } catch {
      return $true
    }
  }
  return $false
}

# Preserving a checkout that holds real work is the expected outcome, so only a
# genuine inspection failure is reported as an error.
function Write-PreservedWorktree([string]$Branch, [string]$WorktreePath, [string]$WorktreeState) {
  if ($WorktreeState -eq 'dirty') {
    Write-Labeled 'Keeping uncommitted work in the worktree of:' "$Branch ($WorktreePath)" Yellow
    return
  }

  if ($WorktreeState -eq 'local-files') {
    Write-Labeled 'Keeping run/ or .local/ files in the worktree of:' "$Branch ($WorktreePath); move them, then rerun" Yellow
    return
  }

  Write-ErrorLine "Keeping $Branch because its worktree could not be inspected: $WorktreePath"
}

# A checkout whose changes only reproduce the merge target is still dirty for
# Git, so removing it needs the explicit override. `--force` also skips the
# validation `git worktree remove` performs for itself, so the disposability
# check is repeated immediately before it, and the ignored files the removal
# discards without ever comparing them are named first. Either removal deletes
# ignored files, so run/ and .local/ are checked again right before it: a dev
# client can save a world after the caller's check.
function Remove-WorktreeCheckout([string]$WorktreePath, [string]$WorktreeState) {
  if (Test-WorktreeHoldsLocalFiles $WorktreePath) {
    Write-ErrorLine "Worktree gained run/ or .local/ files while it was being removed: $WorktreePath"
    return $false
  }

  if ($WorktreeState -ne 'dirty') {
    return (Invoke-Git -Arguments @('worktree', 'remove', $WorktreePath) -Output Inherit).ExitCode -eq 0
  }

  $recheck = Test-WorktreeDisposable $WorktreePath
  if (-not $recheck.Disposable) {
    Write-ErrorLine "Worktree became $($recheck.State) while it was being removed: $WorktreePath"
    return $false
  }

  # Git never reports ignored files as changes, so nothing above compared them.
  $ignored = Invoke-Git -Arguments @('-C', $WorktreePath, 'status', '--porcelain=v1', '--untracked-files=no', '--ignored=traditional') -ErrorOutput Discard
  if ($ignored.ExitCode -eq 0 -and @(Split-Lines $ignored.Text | Where-Object { $_.StartsWith('!! ') }).Count -gt 0) {
    Write-Info "Also discarding ignored files that were never compared, such as build output, caches or run/gametest/: $WorktreePath"
  }

  return (Invoke-Git -Arguments @('worktree', 'remove', '--force', $WorktreePath) -Output Inherit).ExitCode -eq 0
}

function Test-RegisteredWorktreePath([string]$CandidatePath) {
  foreach ($preservedPath in $preservedWorktreePaths) {
    if (Test-SamePath $preservedPath $CandidatePath) {
      return $true
    }
  }

  $records = Get-WorktreeRecords
  if ($null -eq $records) {
    Write-ErrorLine "Treating $CandidatePath as registered because current worktree metadata could not be inspected."
    return $true
  }
  foreach ($record in $records) {
    if ($record.StartsWith('worktree ') -and (Test-SamePath $record.Substring(9) $CandidatePath)) {
      return $true
    }
  }
  return $false
}

# A directory in a worktree home can hold nested worktrees instead of being one.
# Its contents are relative to the wrong root for every residue check, so it is
# never a removal candidate while any registered worktree lies below it.
function Test-HoldsRegisteredWorktree([string]$CandidatePath) {
  foreach ($preservedPath in $preservedWorktreePaths) {
    if (Test-PathStartsWith $preservedPath "$CandidatePath/") {
      return $true
    }
  }

  $records = Get-WorktreeRecords
  if ($null -eq $records) {
    Write-ErrorLine "Treating $CandidatePath as holding a worktree because current worktree metadata could not be inspected."
    return $true
  }
  foreach ($record in $records) {
    if ($record.StartsWith('worktree ') -and (Test-PathStartsWith $record.Substring(9) "$CandidatePath/")) {
      return $true
    }
  }
  return $false
}

# Only build output and caches a later run recreates. run/ (dev client and
# server worlds, which may hold rooms built by hand) and .local/ (the owner's
# private inputs) are never recognized, so residue holding them is kept.
function Test-RecognizedRuntimeArtifactPath([string]$RelativePath) {
  return $RelativePath -cmatch '^(\.gradle|build|mod/build|qa-mod/build|\.venv|\.pytest_cache|\.ruff_cache|\.cache)(/.*)?$' -or
    $RelativePath -cmatch '/__pycache__(/.*)?$'
}

# Through a link the scan would reach some other directory, so a home is never
# followed when it, or the directory holding it (.claude/ for the session home),
# is one.
function Test-WorktreeHomeLinked([string]$WorktreeHome) {
  $separator = $WorktreeHome.LastIndexOf('/')
  return (Test-IsLink $WorktreeHome) -or ($separator -ge 0 -and (Test-IsLink $WorktreeHome.Substring(0, $separator)))
}

function Remove-RecognizedOrphanDirectory([string]$CandidatePath, [string]$WorktreeHome, [string]$ExpectedState, [string]$MergedHead = '') {
  $candidateName = $CandidatePath
  if ($CandidatePath.StartsWith("$WorktreeHome/", [StringComparison]::Ordinal)) {
    $candidateName = $CandidatePath.Substring($WorktreeHome.Length + 1)
  }

  if ($candidateName -ceq $CandidatePath -or $candidateName -eq '' -or $candidateName.Contains('/')) {
    Write-ErrorLine "Refusing to remove an orphan path that is not a direct child of ${WorktreeHome}: $CandidatePath"
    return $false
  }

  if (-not (Test-IsRealDirectory $WorktreeHome) -or (Test-WorktreeHomeLinked $WorktreeHome)) {
    Write-ErrorLine "Refusing to remove an orphan path whose worktree home is no longer a real directory: $CandidatePath"
    return $false
  }

  if ((Test-SamePath $CandidatePath $PrimaryWorktreeRoot) -or (Test-SamePath $CandidatePath $RepositoryRoot) -or
    -not (Test-IsRealDirectory $CandidatePath)) {
    Write-ErrorLine "Refusing to remove an invalid orphan worktree path: $CandidatePath"
    return $false
  }

  if (Test-RegisteredWorktreePath $CandidatePath) {
    Write-ErrorLine "Refusing to remove a path that is now a registered worktree: $CandidatePath"
    return $false
  }

  if (Test-HoldsRegisteredWorktree $CandidatePath) {
    Write-ErrorLine "Refusing to remove a path that now holds a registered worktree: $CandidatePath"
    return $false
  }

  switch ($ExpectedState) {
    'generated' {
      if (Test-EntryPresent "$CandidatePath/.git") {
        Write-ErrorLine "Refusing to remove generated residue whose worktree metadata changed: $CandidatePath"
        return $false
      }
      if (-not (Test-GeneratedResidueDisposable $CandidatePath)) {
        Write-ErrorLine "Refusing to remove generated residue whose contents changed: $CandidatePath"
        return $false
      }
    }
    'stale-pointer' {
      if (-not (Test-StalePointerBelongsToRepository $CandidatePath)) {
        Write-ErrorLine "Refusing to remove stale residue whose worktree pointer changed: $CandidatePath"
        return $false
      }
      if ($MergedHead -eq '' -or -not (Test-OrphanResidueMatchesMergedHead $CandidatePath $MergedHead)) {
        Write-ErrorLine "Refusing to remove stale residue whose contents changed: $CandidatePath"
        return $false
      }
    }
    default {
      Write-ErrorLine "Refusing to remove orphan residue with an unknown verification state: $CandidatePath"
      return $false
    }
  }

  if (-not (Remove-TreeNoFollow $CandidatePath)) {
    Write-ErrorLine "Could not remove orphan worktree residue: $CandidatePath"
    return $false
  }

  if ((Get-EntryState $CandidatePath) -ne 'missing') {
    Write-ErrorLine "Orphan worktree residue still exists after cleanup: $CandidatePath"
    return $false
  }
  return $true
}

function Test-GeneratedResidueDisposable([string]$CandidatePath) {
  if (Test-EntryPresent "$CandidatePath/.git") {
    return $false
  }

  $listing = Get-TreeListing $CandidatePath
  if (-not $listing.Ok) {
    return $false
  }

  $recognizedArtifactFound = $false
  foreach ($relativePath in @($listing.Files) + @($listing.EmptyDirectories)) {
    if (-not (Test-RecognizedRuntimeArtifactPath $relativePath)) {
      return $false
    }
    $recognizedArtifactFound = $true
  }
  return $recognizedArtifactFound
}

function Test-StalePointerBelongsToRepository([string]$CandidatePath) {
  $pointerFile = "$CandidatePath/.git"
  $attributes = Get-EntryAttributes $pointerFile
  if ($null -eq $attributes -or
    ($attributes -band ([System.IO.FileAttributes]::Directory -bor [System.IO.FileAttributes]::ReparsePoint)) -ne 0) {
    return $false
  }

  try {
    $content = $script:Utf8.GetString([System.IO.File]::ReadAllBytes($pointerFile))
  } catch {
    return $false
  }
  # One line, its newline optional, and nothing after it.
  if ($content.EndsWith("`n")) {
    $content = $content.Substring(0, $content.Length - 1)
    if ($script:IsWin -and $content.EndsWith("`r")) {
      $content = $content.Substring(0, $content.Length - 1)
    }
  }
  if ($content.Contains("`n") -or -not $content.StartsWith('gitdir: ', [StringComparison]::Ordinal)) {
    return $false
  }

  $pointerPath = ConvertTo-GitPath $content.Substring(8)
  $worktreesPrefix = "$CommonGitDir/worktrees/"
  if (-not (Test-PathStartsWith $pointerPath $worktreesPrefix)) {
    return $false
  }

  $pointerName = $pointerPath.Substring($worktreesPrefix.Length)
  if ($pointerName -eq '' -or $pointerName.Contains('/')) {
    return $false
  }

  return (Get-EntryState $pointerPath) -eq 'missing'
}

# A worktree directory is named after its branch in one of three layouts:
# realmforge-worktrees/feat-rlm-17-feature-skeleton holds the flow branch
# feat/rlm-17-feature-skeleton (every "/" replaced by "-"), a desktop session's
# .claude/worktrees/bold-hawking holds claude/bold-hawking, and
# `claude --worktree bold-hawking` holds worktree-bold-hawking. A branch belongs
# to directory NAME when it is NAME with every "/" replaced by "-", when it is
# worktree-NAME, or when it is NAME or NAME-<slug> under at most one prefix
# segment such as claude/ or fix/.
function Test-BranchMatchesWorktreeName([string]$Branch, [string]$Name) {
  if ($Name -eq '') {
    return $false
  }
  if ($Branch.Replace('/', '-') -ceq $Name -or $Branch -ceq "worktree-$Name") {
    return $true
  }

  $leaf = $Branch
  $slash = $Branch.IndexOf('/')
  if ($slash -ge 0) {
    $leaf = $Branch.Substring($slash + 1)
    if ($leaf.Contains('/')) {
      return $false
    }
  }

  return $leaf -ceq $Name -or $leaf.StartsWith("$Name-", [StringComparison]::Ordinal)
}

function Test-IsAncestor([string]$Commit, [string]$Of) {
  return (Invoke-Git -Arguments @('merge-base', '--is-ancestor', $Commit, $Of)).ExitCode -eq 0
}

function Find-MergedHeadForOrphan([string]$CandidatePath) {
  $candidateName = Get-LeafName $CandidatePath
  if ($candidateName -eq '') {
    return $null
  }

  $matchedHeads = New-Object 'System.Collections.Generic.List[string]'
  $githubMatchFound = $false

  $refs = Invoke-Git -Arguments @('for-each-ref', '--format=%(refname)', 'refs/heads')
  foreach ($branchRef in @(Split-Lines $refs.Text)) {
    $branch = $branchRef
    if ($branchRef.StartsWith('refs/heads/')) {
      $branch = $branchRef.Substring(11)
    }
    if (-not (Test-BranchMatchesWorktreeName $branch $candidateName)) {
      continue
    }
    # An unmerged branch of the same name may own the residue, so it makes the
    # owner ambiguous rather than being passed over.
    $localHead = Invoke-Git -Arguments @('rev-parse', '--verify', '--quiet', "$branchRef^{commit}")
    if ($localHead.ExitCode -ne 0) {
      return $null
    }
    $localHead = $localHead.Text.TrimEnd([char]"`n")
    if (-not (Test-IsAncestor $localHead $TargetRef)) {
      return $null
    }
    $matchedHeads.Add($localHead)
  }

  if ($null -ne $script:GhPath) {
    # Every merged pull request naming this directory must report a local commit
    # already in the target, and every matching head, local or merged, must be
    # the same commit.
    $ghOutput = Invoke-Gh -Arguments @('pr', 'list', '--state', 'merged', '--limit', '1000', '--json', 'number,headRefName,headRefOid,mergedAt,url',
      '--jq', '.[] | select(.mergedAt != null) | [.number, .headRefName, .headRefOid, .url] | join("|")')
    if ($ghOutput.ExitCode -ne 0) {
      return $null
    }

    foreach ($row in @(Split-Lines $ghOutput.Text)) {
      $fields = $row.Split([char]'|', 4)
      if ($fields.Length -lt 3 -or $fields[2] -eq '') {
        continue
      }
      $prHeadName = $fields[1]
      $prHeadOid = $fields[2]
      if (-not (Test-BranchMatchesWorktreeName $prHeadName $candidateName)) {
        continue
      }
      if ((Invoke-Git -Arguments @('cat-file', '-e', "$prHeadOid^{commit}") -ErrorOutput Discard).ExitCode -ne 0) {
        return $null
      }
      if (-not (Test-IsAncestor $prHeadOid $TargetRef)) {
        return $null
      }
      $matchedHeads.Add($prHeadOid)
      $githubMatchFound = $true
    }
  }

  $matchedHead = ''
  foreach ($existingHead in $matchedHeads) {
    if ($matchedHead -eq '') {
      $matchedHead = $existingHead
    } elseif ($matchedHead -cne $existingHead) {
      return $null
    }
  }

  if ($matchedHead -eq '' -or -not $githubMatchFound) {
    return $null
  }
  return $matchedHead
}

function Test-OrphanResidueMatchesMergedHead([string]$CandidatePath, [string]$MergedHead) {
  $temporaryDirectory = New-TemporaryDirectory
  if ($null -eq $temporaryDirectory) {
    return $false
  }
  $environment = @{
    GIT_DIR = $CommonGitDir
    GIT_WORK_TREE = $CandidatePath
    GIT_INDEX_FILE = (Join-Path $temporaryDirectory 'index')
  }

  $result = Test-OrphanResidueAgainstIndex $CandidatePath $MergedHead $environment

  if (-not (Remove-TemporaryDirectory $temporaryDirectory)) {
    return $false
  }
  return $result
}

function Test-OrphanResidueAgainstIndex([string]$CandidatePath, [string]$MergedHead, [hashtable]$Environment) {
  if ((Invoke-Git -Arguments @('read-tree', $MergedHead) -Environment $Environment -Output Inherit).ExitCode -ne 0) {
    return $false
  }

  [void](Invoke-Git -Arguments @('update-index', '--really-refresh') -Environment $Environment -Output Discard -ErrorOutput Discard)

  $diff = Invoke-Git -Arguments @('diff-files', '--name-status', '--ignore-submodules') -Environment $Environment
  if ($diff.ExitCode -ne 0) {
    return $false
  }
  foreach ($line in @(Split-Lines $diff.Text)) {
    $statusCode = $line.Split([char]"`t")[0]
    if ($statusCode -eq '') {
      continue
    }
    if ($statusCode -cne 'D') {
      return $false
    }
  }

  $listing = Get-TreeListing $CandidatePath
  if (-not $listing.Ok) {
    return $false
  }

  # One listing of the index stands in for `git ls-files -- <path>` per file: a
  # path the index holds as a file matches exactly, and a path the index holds
  # files below never does.
  $indexed = Invoke-Git -Arguments @('ls-files', '-z') -Environment $Environment
  if ($indexed.ExitCode -ne 0) {
    return $false
  }
  $indexedFiles = New-StringSet
  $indexedDirectories = New-StringSet
  foreach ($indexedPath in @(Split-NulRecords $indexed.Text)) {
    [void]$indexedFiles.Add($indexedPath)
    $slash = $indexedPath.LastIndexOf('/')
    while ($slash -gt 0) {
      $indexedPath = $indexedPath.Substring(0, $slash)
      if (-not $indexedDirectories.Add($indexedPath)) {
        break
      }
      $slash = $indexedPath.LastIndexOf('/')
    }
  }

  foreach ($relativePath in $listing.Files) {
    if ($relativePath -ceq '.git') {
      continue
    }
    if ($indexedDirectories.Contains($relativePath)) {
      return $false
    }
    if ($indexedFiles.Contains($relativePath)) {
      continue
    }
    if (-not (Test-RecognizedRuntimeArtifactPath $relativePath)) {
      return $false
    }
  }
  return $true
}

function Clear-OrphanedWorktreeDirectories([string]$WorktreeHome) {
  if (Test-WorktreeHomeLinked $WorktreeHome) {
    Write-Labeled 'Not following a symlinked worktree home:' $WorktreeHome Yellow
    return
  }
  if (-not (Test-IsRealDirectory $WorktreeHome)) {
    return
  }

  try {
    $candidatePaths = @([System.IO.Directory]::GetDirectories($WorktreeHome) |
        Where-Object { Test-IsRealDirectory $_ } |
        ForEach-Object { "$WorktreeHome/" + (Get-LeafName $_) } |
        Sort-Object)
  } catch {
    Write-ErrorLine "Could not list the worktree home: $WorktreeHome"
    return
  }

  foreach ($candidatePath in $candidatePaths) {
    if ((Test-RegisteredWorktreePath $candidatePath) -or (Test-HoldsRegisteredWorktree $candidatePath)) {
      continue
    }

    if (Test-GeneratedResidueDisposable $candidatePath) {
      Write-Labeled 'Removing generated worktree residue:' $candidatePath Green
      if (Remove-RecognizedOrphanDirectory $candidatePath $WorktreeHome 'generated') {
        $script:orphanDeletedCount++
      } else {
        $script:orphanSkippedCount++
      }
      continue
    }

    if (Test-StalePointerBelongsToRepository $candidatePath) {
      $mergedHead = Find-MergedHeadForOrphan $candidatePath
      if ($null -eq $mergedHead) {
        Write-ErrorLine "Keeping orphan worktree because its merged HEAD could not be established safely: $candidatePath"
        $script:orphanSkippedCount++
        continue
      }

      if (-not (Test-OrphanResidueMatchesMergedHead $candidatePath $mergedHead)) {
        Write-ErrorLine "Keeping orphan worktree because it contains modified or unrecognized files: $candidatePath"
        $script:orphanSkippedCount++
        continue
      }

      Write-Labeled 'Removing verified merged orphan worktree:' $candidatePath Green
      if (Remove-RecognizedOrphanDirectory $candidatePath $WorktreeHome 'stale-pointer' $mergedHead) {
        $script:orphanDeletedCount++
      } else {
        $script:orphanSkippedCount++
      }
      continue
    }

    Write-Labeled 'Keeping unrecognized directory:' $candidatePath Yellow
    $script:orphanSkippedCount++
  }
}

function Test-ProtectedBranch([string]$Branch) {
  return $Branch -ceq $currentBranch -or $Branch -ceq $TargetBranch -or $Branch.StartsWith('recovery/', [StringComparison]::Ordinal)
}

# --- Rewritten branches ------------------------------------------------------

# Succeeds when merging the given revision into the merge target would change
# nothing; with a merge base, only the changes since that merge base are merged,
# so a single commit can be applied alone. Exit status 1 from `git merge-tree`
# is a conflict; anything higher means the merge could not run, as on Git older
# than 2.40, which is said once instead of every branch silently reading as
# unmerged.
function Test-MergeIntoTargetChangesNothing([string]$Revision, [string]$MergeBase = '') {
  $arguments = @('merge-tree', '--write-tree')
  if ($MergeBase -ne '') {
    $arguments += "--merge-base=$MergeBase"
  }
  $arguments += @($TargetRef, $Revision)
  $merged = Invoke-Git -Arguments $arguments -ErrorOutput Discard
  if ($merged.ExitCode -ne 0) {
    if ($merged.ExitCode -gt 1 -and -not $script:mergeTreeFailureReported) {
      Write-ErrorLine "git merge-tree --write-tree --merge-base could not run (it needs Git 2.40 or newer), so only commits whose patch is already in $TargetRef, or that their merged pull request rewrote, count as merged."
      $script:mergeTreeFailureReported = $true
    }
    return $false
  }
  return (Get-FirstLine $merged.Text) -ceq $TargetTree
}

# The commit subject without its conventional-commit type and scope, so a
# commit a later revision re-scoped (`fix(tools): x` became `fix(art): x`) still
# names the same change.
function Get-SubjectWithoutTypeAndScope([string]$Subject) {
  if ($Subject -cmatch '^[a-z]+(\([^)]*\))?!?: (.+)$') {
    return $Matches[2]
  }
  return $Subject
}

# GitHub's newest pull request for the branch, with its merge commit, when
# every one it lists targets the merge target, none is open, and the newest is
# merged; otherwise $null.
function Get-SettledMergedPullRequest([string]$Branch) {
  if ($null -eq $script:GhPath) {
    return $null
  }
  $prOutput = Invoke-Gh -Arguments @('pr', 'list', '--state', 'all', '--head', $Branch, '--limit', '100', '--json',
    'number,state,baseRefName,headRefName,createdAt,mergeCommit', '--jq',
    'sort_by(.createdAt) | .[] | [.number, .state, .baseRefName, .headRefName, (.mergeCommit.oid // "")] | join("|")')
  if ($prOutput.ExitCode -ne 0) {
    return $null
  }
  $rows = @(Split-Lines $prOutput.Text)
  if ($rows.Count -eq 0) {
    return $null
  }
  $prNumber = ''
  $prState = ''
  $prMergeCommit = ''
  foreach ($row in $rows) {
    $fields = $row.Split([char]'|', 5)
    $values = @('', '', '', '', '')
    for ($index = 0; $index -lt $fields.Length; $index++) {
      $values[$index] = $fields[$index]
    }
    $prNumber = $values[0]
    $prState = $values[1]
    $prBase = $values[2]
    $prHeadName = $values[3]
    $prMergeCommit = $values[4]
    if (-not ($prHeadName -ceq $Branch -and $prBase -ceq $TargetBranch -and $prState -cne 'OPEN')) {
      return $null
    }
  }
  if ($prState -cne 'MERGED' -or $prNumber -eq '' -or $prMergeCommit -eq '') {
    return $null
  }
  return [pscustomobject]@{ Number = $prNumber; MergeCommit = $prMergeCommit }
}

# The set of "<path>TAB<sign><line>" for every line the change between two
# revisions adds (+) or removes (-), trimmed, file by file, so a line counts
# only in the file it changed. $null on a path holding a tab or newline, which
# the format cannot carry, and when any diff cannot be read.
function Get-DiffChangedLines([string]$From, [string]$To) {
  if ((Invoke-Git -Arguments @('diff-tree', '-r', '--no-commit-id', '--name-only', '--no-renames', $From, $To) -Output Discard).ExitCode -ne 0) {
    return $null
  }
  $listing = Invoke-Git -Arguments @('diff-tree', '-r', '-z', '--no-commit-id', '--name-only', '--no-renames', $From, $To)
  if ($listing.ExitCode -ne 0) {
    return $null
  }

  $changed = New-StringSet
  foreach ($path in @(Split-NulRecords $listing.Text)) {
    if ($path.Contains("`t") -or $path.Contains("`n")) {
      return $null
    }
    $diff = Invoke-Git -Arguments @('--literal-pathspecs', 'diff', '--no-color', '--no-ext-diff', '--no-renames', '-U0', $From, $To, '--', $path)
    if ($diff.ExitCode -ne 0) {
      return $null
    }
    $inHunk = $false
    foreach ($line in $script:Latin1.GetString($diff.Bytes).Split([char]"`n")) {
      if ($line.StartsWith('diff --git ', [StringComparison]::Ordinal)) {
        $inHunk = $false
        continue
      }
      if ($line.StartsWith('@@ ', [StringComparison]::Ordinal)) {
        $inHunk = $true
        continue
      }
      if ($inHunk -and ($line.StartsWith('+', [StringComparison]::Ordinal) -or $line.StartsWith('-', [StringComparison]::Ordinal))) {
        $text = $line.Substring(1).Trim([char[]]@(' ', "`t"))
        if ($text -ne '') {
          [void]$changed.Add("$path`t" + $line.Substring(0, 1) + $text)
        }
      }
    }
  }
  return , $changed
}

# The part of a changed-line entry after its path: "<sign><line>".
function Get-ChangedLineText([string]$Entry) {
  return $Entry.Substring($Entry.IndexOf("`t") + 1)
}

# Whether GitHub already holds the exact commit, so it exists outside this
# checkout.
function Test-GithubHasCommit([string]$Commit) {
  if ($null -eq $script:GhPath) {
    return $false
  }
  $known = Invoke-Gh -Arguments @('api', "repos/{owner}/{repo}/commits/$Commit", '--jq', '.sha')
  return $known.ExitCode -eq 0 -and $known.Text.TrimEnd([char]"`n") -ceq $Commit
}

# The number of the given changed lines that origin/master does not bear out,
# file by file: an added line its version of the file does not hold, or a
# removed line it still holds. A file missing from origin/master holds nothing.
# $null when a file could not be read.
function Get-LinesNotInTarget($Changed) {
  $byPath = @{}
  $paths = New-Object 'System.Collections.Generic.List[string]'
  foreach ($entry in $Changed) {
    $tab = $entry.IndexOf("`t")
    $path = $entry.Substring(0, $tab)
    if (-not $byPath.ContainsKey($path)) {
      $byPath[$path] = New-Object 'System.Collections.Generic.List[string]'
      $paths.Add($path)
    }
    $byPath[$path].Add($entry.Substring($tab + 1))
  }

  $missing = 0
  foreach ($path in $paths) {
    $listing = Invoke-Git -Arguments @('--literal-pathspecs', 'ls-tree', '--name-only', $TargetRef, '--', $path)
    if ($listing.ExitCode -ne 0) {
      return $null
    }
    $content = New-StringSet
    if ($listing.Text.TrimEnd([char]"`n") -ne '') {
      $blob = Invoke-Git -Arguments @('cat-file', 'blob', "${TargetRef}:$path")
      if ($blob.ExitCode -ne 0) {
        return $null
      }
      foreach ($line in $script:Latin1.GetString($blob.Bytes).Split([char]"`n")) {
        $trimmed = $line.Trim([char[]]@(' ', "`t"))
        if ($trimmed -ne '') {
          [void]$content.Add($trimmed)
        }
      }
    }
    $counted = New-StringSet
    foreach ($expected in $byPath[$path]) {
      if (-not $counted.Add($expected)) {
        continue
      }
      $line = $expected.Substring(1)
      if ($expected.StartsWith('+') -and -not $content.Contains($line)) {
        $missing++
      } elseif ($expected.StartsWith('-') -and $content.Contains($line)) {
        $missing++
      }
    }
  }
  return $missing
}

# Measures one rewritten commit against its merged pull request's final change
# (the merge commit against its first parent, never the intermediate commits,
# which may add a line a later review commit removes). Returns the number of
# lines the commit adds or removes, and how many of them are uncovered: the pull
# request's final change does not make them in the same file, and origin/master
# does not bear them out there either. $null when the pull request's own
# commits, files aside, made fewer than $RewrittenCommitMinimumShare percent of
# them: that only asks whether the commit belongs to this pull request at all,
# so an unrelated commit is never offered, and never counts a line as covered.
function Get-RewrittenCommitCoverage([string]$Commit, $PullRequestLines, $PullRequestCommitTexts) {
  $commitLines = Get-DiffChangedLines "$Commit^" $Commit
  if ($null -eq $commitLines -or $commitLines.Count -eq 0) {
    return $null
  }

  $commitTexts = New-StringSet
  foreach ($entry in $commitLines) {
    [void]$commitTexts.Add((Get-ChangedLineText $entry))
  }
  $sharedTextCount = 0
  foreach ($text in $commitTexts) {
    if ($PullRequestCommitTexts.Contains($text)) {
      $sharedTextCount++
    }
  }
  if ($sharedTextCount * 100 -lt $commitTexts.Count * $RewrittenCommitMinimumShare) {
    return $null
  }

  $outsideLines = New-Object 'System.Collections.Generic.List[string]'
  foreach ($entry in $commitLines) {
    if (-not $PullRequestLines.Contains($entry)) {
      $outsideLines.Add($entry)
    }
  }
  $uncoveredLineCount = 0
  if ($outsideLines.Count -gt 0) {
    $uncoveredLineCount = Get-LinesNotInTarget $outsideLines
    if ($null -eq $uncoveredLineCount) {
      return $null
    }
  }

  return [pscustomobject]@{ Total = $commitLines.Count; Uncovered = $uncoveredLineCount }
}

# The "<patch id> <commit>" lines of `git log -p <range> | git patch-id --verbatim`,
# or $null when either command failed.
function Get-PatchIds([string]$Range) {
  $log = Invoke-Git -Arguments @('log', '--no-merges', '--no-color', '--no-decorate', '-p', $Range)
  if ($log.ExitCode -ne 0) {
    return $null
  }
  $patchIds = Invoke-Git -Arguments @('patch-id', '--verbatim') -InputBytes $log.Bytes
  if ($patchIds.ExitCode -ne 0) {
    return $null
  }
  return , @(Split-Lines $patchIds.Text)
}

# A branch whose commits were rewritten before they merged (a playtest branch
# `qa1` rebased, a commit a review fix later changed, a re-scoped follow-up) is
# no ancestor of the merge target although its work is there. It counts as
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
#    it adds or removes is covered under Get-RewrittenCommitCoverage, so a
#    subject never decides alone.
#
# A commit made only in this checkout, or a change the merge left out, passes
# none of them and keeps the branch. Status 0 when the branch's work is in the
# target, 2 when it passes everything but the coverage of the third check and
# every commit still reaches $RewrittenCommitMinimumShare (a candidate for
# --rewritten-prs), and 1 otherwise, with the merged pull request's number when
# the third check was needed, and the changed and uncovered line counts of its
# commits.
function Get-BranchWorkInTarget([string]$Branch) {
  $notInTarget = [pscustomobject]@{ Status = 1; PullRequest = ''; Total = 0; Uncovered = 0 }
  $inTarget = [pscustomobject]@{ Status = 0; PullRequest = ''; Total = 0; Uncovered = 0 }
  $branchRef = "refs/heads/$Branch"

  $remote = Invoke-Git -Arguments @('ls-remote', '--heads', $TargetRemote, $branchRef) -ErrorOutput Discard
  if ($remote.ExitCode -ne 0) {
    return $notInTarget
  }
  $remoteText = $remote.Text.TrimEnd([char]"`n")
  if ($remoteText -ne '') {
    $remoteHead = ($remoteText -split '\s', 2)[0]
    if ((Invoke-Git -Arguments @('cat-file', '-e', "$remoteHead^{commit}") -ErrorOutput Discard).ExitCode -ne 0) {
      return $notInTarget
    }
    if (-not (Test-IsAncestor $remoteHead $TargetRef)) {
      return $notInTarget
    }
  }

  if (Test-MergeIntoTargetChangesNothing $branchRef) {
    return $inTarget
  }

  # Each listing is used only after its command succeeded: a listing that
  # failed would otherwise read as nothing pending.
  $forkPoint = Invoke-Git -Arguments @('merge-base', $TargetRef, $branchRef)
  if ($forkPoint.ExitCode -ne 0) {
    return $notInTarget
  }
  $forkPoint = $forkPoint.Text.TrimEnd([char]"`n")

  $targetPatchIds = Get-PatchIds "$forkPoint..$TargetRef"
  if ($null -eq $targetPatchIds) {
    return $notInTarget
  }
  $targetPatches = New-StringSet
  foreach ($line in $targetPatchIds) {
    $patchId = $line.Split([char]' ')[0]
    if ($patchId -ne '') {
      [void]$targetPatches.Add($patchId)
    }
  }
  $branchPatchIds = Get-PatchIds "$TargetRef..$branchRef"
  if ($null -eq $branchPatchIds) {
    return $notInTarget
  }
  $deliveredCommits = New-StringSet
  foreach ($line in $branchPatchIds) {
    $fields = $line.Split([char]' ')
    if ($fields.Length -ge 2 -and $fields[0] -ne '' -and $targetPatches.Contains($fields[0])) {
      [void]$deliveredCommits.Add($fields[1])
    }
  }

  $branchCommits = Invoke-Git -Arguments @('rev-list', '--no-merges', "$TargetRef..$branchRef")
  if ($branchCommits.ExitCode -ne 0) {
    return $notInTarget
  }
  $pendingCommits = New-Object 'System.Collections.Generic.List[string]'
  foreach ($commit in @(Split-Lines $branchCommits.Text)) {
    if ($commit -eq '' -or $deliveredCommits.Contains($commit)) {
      continue
    }
    $parent = Invoke-Git -Arguments @('rev-parse', '--verify', '--quiet', "$commit^1")
    if ($parent.ExitCode -ne 0) {
      return $notInTarget
    }
    if (-not (Test-MergeIntoTargetChangesNothing $commit $parent.Text.TrimEnd([char]"`n"))) {
      $pendingCommits.Add($commit)
    }
  }
  $mergeCommits = Invoke-Git -Arguments @('rev-list', '--merges', "$TargetRef..$branchRef")
  if ($mergeCommits.ExitCode -ne 0) {
    return $notInTarget
  }
  foreach ($commit in @(Split-Lines $mergeCommits.Text)) {
    if ($commit -eq '') {
      continue
    }
    if (-not (Test-MergeIntoTargetChangesNothing $commit "$commit^1")) {
      return $notInTarget
    }
  }

  if ($pendingCommits.Count -eq 0) {
    return $inTarget
  }

  # The pull request's own commits are the ones its merge commit brought into
  # the target; a squash or rebase merge has no second parent and keeps the
  # branch.
  $settled = Get-SettledMergedPullRequest $Branch
  if ($null -eq $settled) {
    return $notInTarget
  }
  $mergeCommit = $settled.MergeCommit
  if ((Invoke-Git -Arguments @('cat-file', '-e', "$mergeCommit^{commit}") -ErrorOutput Discard).ExitCode -ne 0) {
    return $notInTarget
  }
  if (-not (Test-IsAncestor $mergeCommit $TargetRef)) {
    return $notInTarget
  }
  if ((Invoke-Git -Arguments @('rev-parse', '--verify', '--quiet', "$mergeCommit^2")).ExitCode -ne 0) {
    return $notInTarget
  }
  $subjectList = Invoke-Git -Arguments @('log', '--no-merges', '--format=%s', "$mergeCommit^1..$mergeCommit^2")
  if ($subjectList.ExitCode -ne 0) {
    return $notInTarget
  }
  $pullRequestSubjects = New-StringSet
  foreach ($subject in @(Split-Lines $subjectList.Text)) {
    $subject = Get-SubjectWithoutTypeAndScope $subject
    if ($subject -ne '') {
      [void]$pullRequestSubjects.Add($subject)
    }
  }
  $pullRequestLines = Get-DiffChangedLines "$mergeCommit^1" $mergeCommit
  if ($null -eq $pullRequestLines) {
    return $notInTarget
  }
  $pullRequestCommits = Invoke-Git -Arguments @('rev-list', '--no-merges', "$mergeCommit^1..$mergeCommit^2")
  if ($pullRequestCommits.ExitCode -ne 0) {
    return $notInTarget
  }
  $pullRequestCommitTexts = New-StringSet
  foreach ($pullRequestCommit in @(Split-Lines $pullRequestCommits.Text)) {
    if ($pullRequestCommit -eq '') {
      continue
    }
    $commitLines = Get-DiffChangedLines "$pullRequestCommit^" $pullRequestCommit
    if ($null -eq $commitLines) {
      return $notInTarget
    }
    foreach ($entry in $commitLines) {
      [void]$pullRequestCommitTexts.Add((Get-ChangedLineText $entry))
    }
  }

  $branchTotal = 0
  $branchUncovered = 0
  foreach ($commit in $pendingCommits) {
    $subject = Invoke-Git -Arguments @('log', '-1', '--format=%s', $commit)
    if ($subject.ExitCode -ne 0) {
      return $notInTarget
    }
    $subject = Get-SubjectWithoutTypeAndScope ($subject.Text.TrimEnd([char]"`n"))
    if ($subject -eq '' -or -not $pullRequestSubjects.Contains($subject)) {
      return $notInTarget
    }
    if (-not (Test-GithubHasCommit $commit)) {
      return $notInTarget
    }
    $coverage = Get-RewrittenCommitCoverage $commit $pullRequestLines $pullRequestCommitTexts
    if ($null -eq $coverage) {
      return $notInTarget
    }
    $branchTotal += $coverage.Total
    $branchUncovered += $coverage.Uncovered
  }

  $status = 0
  if ($branchUncovered -ne 0) {
    $status = 2
  }
  return [pscustomobject]@{ Status = $status; PullRequest = $settled.Number; Total = $branchTotal; Uncovered = $branchUncovered }
}

$rewrittenCandidates = New-Object 'System.Collections.Generic.List[object]'

# A branch its merged pull request covers only in part is kept by default and
# reported with what is not in the target; under --rewritten-prs it becomes a
# candidate once its worktree is removable, deleted only after confirmation.
function Add-PartlyCoveredBranch([string]$Branch, [string]$PrNumber, [int]$Uncovered, [int]$Total) {
  if (-not $RewrittenPrsMode) {
    Write-Labeled "Keeping rewritten branch merged in PR #${PrNumber}:" "$Branch ($Uncovered of its $Total changed lines are not in $TargetRef; --rewritten-prs deletes it after confirmation)" Yellow
    return
  }

  $worktreePath = Get-WorktreePathForBranch $Branch
  if ($null -ne $worktreePath) {
    $worktree = Test-WorktreeDisposable $worktreePath
    if (-not $worktree.Disposable) {
      Write-PreservedWorktree $Branch $worktreePath $worktree.State
      return
    }
  }

  $head = (Invoke-Git -Arguments @('rev-parse', "refs/heads/$Branch")).Text.TrimEnd([char]"`n")
  $rewrittenCandidates.Add([pscustomobject]@{ Branch = $Branch; Head = $head; PrNumber = $PrNumber; Worktree = $worktreePath })
  Write-Labeled 'Partly covered rewritten branch candidate:' "$Branch (PR #$PrNumber; $Uncovered of its $Total changed lines are not in $TargetRef)" Green
}

function Remove-PartlyCoveredCandidate($Candidate) {
  $branch = $Candidate.Branch
  if (Test-ProtectedBranch $branch) {
    Write-ErrorLine "Keeping $branch because it became protected during cleanup."
    return
  }
  $head = Invoke-Git -Arguments @('rev-parse', '--verify', '--quiet', "refs/heads/$branch")
  if ($head.Text.TrimEnd([char]"`n") -cne $Candidate.Head) {
    Write-ErrorLine "Keeping $branch because it moved after it was checked."
    return
  }

  if ($null -ne $Candidate.Worktree) {
    $worktree = Test-WorktreeDisposable $Candidate.Worktree
    if (-not $worktree.Disposable) {
      Write-PreservedWorktree $branch $Candidate.Worktree $worktree.State
      return
    }

    Write-Labeled 'Removing partly covered rewritten worktree:' $Candidate.Worktree Green
    if (-not (Remove-WorktreeCheckout $Candidate.Worktree $worktree.State)) {
      Write-ErrorLine "Could not remove worktree for $branch; keeping the branch."
      return
    }
  }

  Write-Labeled "Deleting partly covered rewritten branch merged in PR #$($Candidate.PrNumber):" $branch Green
  if ((Invoke-Git -Arguments @('branch', '-D', '--', $branch) -Output Discard).ExitCode -eq 0) {
    $script:rewrittenDeletedCount++
  } else {
    Write-ErrorLine "Could not delete $branch after removing its worktree."
  }
}

# The answer to a DELETE prompt. The prompt shows only on a console, as with
# `read -p`; piped input is read all the same.
function Read-Confirmation([string]$Prompt) {
  if (-not [Console]::IsInputRedirected) {
    Write-Host $Prompt -NoNewline
  }
  $answer = [Console]::In.ReadLine()
  if ($null -eq $answer) {
    return ''
  }
  return $answer.Trim([char[]]@(' ', "`t"))
}

# --- Main --------------------------------------------------------------------

$script:deletedCount = 0
$script:rewrittenDeletedCount = 0
$script:skippedCount = 0
$script:orphanDeletedCount = 0
$script:orphanSkippedCount = 0
$script:closedDeletedCount = 0
$script:mergeTreeFailureReported = $false

if (-not (Save-ExistingWorktreeMappings)) {
  exit 1
}
[void](Invoke-GitOrExit -Arguments @('worktree', 'prune') -Output Inherit)

Write-Host ''
Write-Info "Checking local branches merged into $TargetRef..."

$localBranches = @(Split-Lines (Invoke-Git -Arguments @('for-each-ref', '--format=%(refname:short)', 'refs/heads')).Text)
foreach ($branch in $localBranches) {
  if (Test-ProtectedBranch $branch) {
    Write-Labeled 'Keeping protected branch:' $branch Cyan
    $script:skippedCount++
    continue
  }

  $mergedPullRequest = ''
  $rewrittenBranch = $false
  if (-not (Test-IsAncestor $branch $TargetRef)) {
    $work = Get-BranchWorkInTarget $branch
    if ($work.Status -eq 2) {
      Add-PartlyCoveredBranch $branch $work.PullRequest $work.Uncovered $work.Total
      $script:skippedCount++
      continue
    }
    if ($work.Status -ne 0) {
      Write-Labeled 'Keeping unmerged:' $branch Yellow
      $script:skippedCount++
      continue
    }
    $mergedPullRequest = $work.PullRequest
    $rewrittenBranch = $true
  }

  $worktreePath = Get-WorktreePathForBranch $branch
  if ($null -ne $worktreePath) {
    $worktree = Test-WorktreeDisposable $worktreePath
    if (-not $worktree.Disposable) {
      Write-PreservedWorktree $branch $worktreePath $worktree.State
      $script:skippedCount++
      continue
    }

    $removalReason = 'Removing worktree'
    if ($worktree.State -eq 'dirty') {
      $removalReason = "$removalReason whose changes already match $TargetRef"
    }
    Write-Labeled "${removalReason}:" $worktreePath Green

    if (-not (Remove-WorktreeCheckout $worktreePath $worktree.State)) {
      Write-ErrorLine "Could not remove worktree for $branch; keeping the branch. Resolve any uncommitted changes or worktree lock, then retry."
      $script:skippedCount++
      continue
    }
  }

  if ($mergedPullRequest -ne '') {
    Write-Labeled "Deleting rewritten branch merged in PR #${mergedPullRequest}:" $branch Green
  } elseif ($rewrittenBranch) {
    Write-Labeled "Deleting rewritten branch whose commits are all in ${TargetRef}:" $branch Green
  } else {
    Write-Labeled 'Deleting:' $branch Green
  }
  [void](Invoke-GitOrExit -Arguments @('branch', '-D', '--', $branch) -Output Discard)
  $script:deletedCount++
}

foreach ($worktreeHome in $WorktreeHomes) {
  Write-Host ''
  Write-Info "Checking for orphaned worktree directories under $worktreeHome..."
  Clear-OrphanedWorktreeDirectories $worktreeHome
}

$closedCandidates = New-Object 'System.Collections.Generic.List[object]'

function Add-ClosedPrBranch([string]$Branch) {
  if (Test-ProtectedBranch $Branch) {
    Write-Labeled 'Keeping protected branch:' $Branch Cyan
    return
  }

  $remote = Invoke-Git -Arguments @('ls-remote', '--heads', $TargetRemote, "refs/heads/$Branch") -ErrorOutput Capture
  if ($remote.ExitCode -ne 0) {
    Write-ErrorLine "Keeping $Branch because its remote branch state could not be verified."
    return
  }

  if (($remote.Text + $remote.ErrorText).TrimEnd([char]"`n") -ne '') {
    Write-Labeled 'Keeping remote branch:' $Branch Yellow
    return
  }

  if ($null -eq $script:GhPath) {
    Write-ErrorLine "Keeping $Branch because GitHub CLI metadata is unavailable."
    return
  }

  $prOutput = Invoke-Gh -Arguments @('pr', 'list', '--state', 'closed', '--head', $Branch, '--json', 'number,mergedAt,headRefName,headRefOid,url',
    '--jq', '.[] | [.number, (.mergedAt // "UNMERGED"), .headRefName, .headRefOid, .url] | join("|")')
  if ($prOutput.ExitCode -ne 0) {
    Write-ErrorLine "Keeping $Branch because GitHub pull request metadata could not be established safely."
    return
  }

  $prText = $prOutput.Text.TrimEnd([char]"`n")
  if ($prText -eq '') {
    Write-Labeled 'Keeping without a matching closed pull request:' $Branch Yellow
    return
  }

  $prRows = @($prText.Split([char]"`n"))
  if ($prRows.Count -ne 1) {
    Write-ErrorLine "Keeping $Branch because GitHub returned ambiguous closed pull request metadata."
    return
  }

  $fields = $prRows[0].Split([char]'|', 5)
  $values = @('', '', '', '', '')
  for ($index = 0; $index -lt $fields.Length; $index++) {
    $values[$index] = $fields[$index]
  }
  $prNumber = $values[0]
  if ($values[1] -cne 'UNMERGED' -or $values[2] -cne $Branch -or $values[3] -eq '') {
    Write-ErrorLine "Keeping $Branch because its pull request is not unambiguously closed and unmerged."
    return
  }

  $branchHead = (Invoke-GitOrExit -Arguments @('rev-parse', $Branch)).Text.TrimEnd([char]"`n")
  if ($branchHead -cne $values[3]) {
    Write-ErrorLine "Keeping $Branch because local HEAD differs from closed PR #$prNumber."
    return
  }

  $worktreePath = Get-WorktreePathForBranch $Branch
  if ($null -ne $worktreePath) {
    $worktree = Test-WorktreeDisposable $worktreePath
    if (-not $worktree.Disposable) {
      Write-PreservedWorktree $Branch $worktreePath $worktree.State
      return
    }
  }

  $closedCandidates.Add([pscustomobject]@{ Branch = $Branch; PrNumber = $prNumber; Worktree = $worktreePath })
  Write-Labeled 'Closed PR candidate:' "$Branch (PR #$prNumber, closed and unmerged)" Green
}

function Remove-ClosedPrCandidate($Candidate) {
  $branch = $Candidate.Branch
  if (Test-ProtectedBranch $branch) {
    Write-ErrorLine "Keeping $branch because it became protected during cleanup."
    return
  }

  if ($null -ne $Candidate.Worktree) {
    $worktree = Test-WorktreeDisposable $Candidate.Worktree
    if (-not $worktree.Disposable) {
      Write-PreservedWorktree $branch $Candidate.Worktree $worktree.State
      return
    }

    Write-Labeled 'Removing closed PR worktree:' $Candidate.Worktree Green
    if (-not (Remove-WorktreeCheckout $Candidate.Worktree $worktree.State)) {
      Write-ErrorLine "Could not remove worktree for $branch; keeping the branch."
      return
    }
  }

  Write-Labeled 'Deleting closed PR branch:' "$branch (PR #$($Candidate.PrNumber))" Green
  if ((Invoke-Git -Arguments @('branch', '-D', '--', $branch) -Output Discard).ExitCode -eq 0) {
    $script:closedDeletedCount++
  } else {
    Write-ErrorLine "Could not delete $branch after removing its worktree."
  }
}

if ($ClosedPrsMode) {
  Write-Host ''
  Write-Info 'Checking local branches for closed, unmerged pull requests...'

  foreach ($branch in @(Split-Lines (Invoke-Git -Arguments @('for-each-ref', '--format=%(refname:short)', 'refs/heads')).Text)) {
    Add-ClosedPrBranch $branch
  }

  if ($closedCandidates.Count -gt 0) {
    # Its own answer: a DELETE typed here never confirms the rewritten group.
    $confirmedClosed = $AssumeYes
    if (-not $confirmedClosed) {
      $confirmation = Read-Confirmation "Type DELETE to remove the $($closedCandidates.Count) closed pull request candidate(s): "
      if ($confirmation -cne 'DELETE') {
        Write-Info 'Closed pull request cleanup cancelled; no candidates were removed.'
      } else {
        $confirmedClosed = $true
      }
    }

    if ($confirmedClosed) {
      foreach ($candidate in $closedCandidates) {
        Remove-ClosedPrCandidate $candidate
      }
    }
  } else {
    Write-Info 'No safe closed pull request candidates found.'
  }
}

if ($RewrittenPrsMode) {
  Write-Host ''
  if ($rewrittenCandidates.Count -gt 0) {
    $confirmedRewritten = $false
    if ($AssumeYes) {
      $confirmedRewritten = $true
    } else {
      $confirmation = Read-Confirmation "Type DELETE to remove the $($rewrittenCandidates.Count) partly covered rewritten branch(es) listed above: "
      if ($confirmation -ceq 'DELETE') {
        $confirmedRewritten = $true
      } else {
        Write-Info 'Partly covered rewritten branch cleanup cancelled; no candidates were removed.'
      }
    }

    if ($confirmedRewritten) {
      foreach ($candidate in $rewrittenCandidates) {
        Remove-PartlyCoveredCandidate $candidate
      }
    }
  } else {
    Write-Info 'No partly covered rewritten branches found.'
  }
}

Write-Host ''
Write-Header 'Cleanup Complete'
Write-Success "Deleted merged local branches: $($script:deletedCount)"
Write-Success "Deleted orphan worktree directories: $($script:orphanDeletedCount)"
if ($ClosedPrsMode) {
  Write-Success "Deleted closed pull request branches: $($script:closedDeletedCount)"
}
if ($RewrittenPrsMode) {
  Write-Success "Deleted partly covered rewritten branches: $($script:rewrittenDeletedCount)"
}
Write-Success "Kept local branches: $($script:skippedCount)"
if ($script:orphanSkippedCount -gt 0) {
  Write-Success "Kept ambiguous or modified worktree-home directories: $($script:orphanSkippedCount)"
}
Write-Success "Pruned deleted remote branch references from $TargetRemote"
exit 0
