# Smoke test of git_clean_worktrees.ps1 on Windows (RLM-239): the code the suite
# tests/test_git_clean_worktrees.sh cannot reach on Linux. That is the command
# line quoting of Windows PowerShell 5.1, the case-insensitive path comparison,
# the .exe-only program lookup and the removal of a junction.
#
# It starts the port under the PowerShell running this script, which must be the
# named edition (Desktop is 5.1, Core is 7), so a CI step cannot test the wrong
# one unnoticed. It needs Git for Windows and the .NET Framework's csc.exe, and
# stays pure ASCII for the same reason as the port.
#
#   powershell -ExecutionPolicy Bypass -File tests\test_git_clean_worktrees_windows.ps1 -Edition Desktop
#   pwsh -File tests\test_git_clean_worktrees_windows.ps1 -Edition Core

param(
  [Parameter(Mandatory = $true)]
  [ValidateSet('Desktop', 'Core')]
  [string]$Edition
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'

function Assert-That([bool]$Condition, [string]$Message) {
  if (-not $Condition) {
    throw "FAIL: $Message"
  }
  Write-Host "ok: $Message"
}

# Native stderr must not count as a PowerShell error here: Git reports progress
# on it, and only the exit code says whether a command failed.
function Invoke-FixtureGit([string[]]$Arguments) {
  $ErrorActionPreference = 'Continue'
  & git @Arguments 2>&1 | Out-Null
  if ($LASTEXITCODE -ne 0) {
    throw "git $($Arguments -join ' ') exited with $LASTEXITCODE"
  }
}

function Test-BranchExists([string]$Repository, [string]$Branch) {
  $ErrorActionPreference = 'Continue'
  & git -C $Repository show-ref --verify --quiet "refs/heads/$Branch" 2>&1 | Out-Null
  return $LASTEXITCODE -eq 0
}

function New-UnmergedBranch([string]$Repository, [string]$Branch) {
  Invoke-FixtureGit @('-C', $Repository, 'checkout', '--quiet', '-b', $Branch)
  [System.IO.File]::WriteAllText((Join-Path $Repository "$Branch.txt"), "$Branch work`n")
  Invoke-FixtureGit @('-C', $Repository, 'add', "$Branch.txt")
  Invoke-FixtureGit @('-C', $Repository, 'commit', '--quiet', '-m', "$Branch work")
  Invoke-FixtureGit @('-C', $Repository, 'checkout', '--quiet', 'master')
}

function New-FakeGh([string]$Root) {
  # The port takes only an .exe for gh, so this script comes first on PATH and
  # must never run.
  $scriptDirectory = Join-Path $Root 'fake-gh-script'
  [void][System.IO.Directory]::CreateDirectory($scriptDirectory)
  [System.IO.File]::WriteAllText((Join-Path $scriptDirectory 'gh.cmd'), "@echo ran>>`"%FAKE_GH_LOG%.cmd`"`r`n")

  # Writes each argument it received on a line of its own and reports no pull
  # requests. The .NET Framework's compiler builds a real executable under either
  # PowerShell, and its C runtime splits a command line the way gh does.
  $programDirectory = Join-Path $Root 'fake-gh-program'
  [void][System.IO.Directory]::CreateDirectory($programDirectory)
  $source = Join-Path $programDirectory 'FakeGh.cs'
  [System.IO.File]::WriteAllText($source, @'
class FakeGh {
  static int Main(string[] arguments) {
    System.IO.File.AppendAllText(System.Environment.GetEnvironmentVariable("FAKE_GH_LOG"),
      string.Join("\n", arguments) + "\n");
    return 0;
  }
}
'@)
  $compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
  $ErrorActionPreference = 'Continue'
  & $compiler /nologo "/out:$(Join-Path $programDirectory 'gh.exe')" $source | Out-Null
  if ($LASTEXITCODE -ne 0) {
    throw "csc.exe exited with $LASTEXITCODE"
  }
  return "$scriptDirectory;$programDirectory"
}

Assert-That ($PSVersionTable.PSEdition -ceq $Edition) "runs under PowerShell $Edition ($($PSVersionTable.PSVersion))"
$portSource = Join-Path $PSScriptRoot '..\git_clean_worktrees.ps1'

# The space reaches every path the port hands to Git, so 5.1's quoting is in use.
$root = Join-Path ([System.IO.Path]::GetTempPath()) ('git clean smoke ' + [guid]::NewGuid().ToString('N'))
[void][System.IO.Directory]::CreateDirectory($root)
try {
  $remote = Join-Path $root 'remote.git'
  $repo = Join-Path $root 'repo'
  Invoke-FixtureGit @('init', '--quiet', '--bare', '--initial-branch=master', $remote)
  Invoke-FixtureGit @('clone', '--quiet', $remote, $repo)
  Invoke-FixtureGit @('-C', $repo, 'config', 'user.email', 'cleanup-test@example.test')
  Invoke-FixtureGit @('-C', $repo, 'config', 'user.name', 'Cleanup Test')
  Invoke-FixtureGit @('-C', $repo, 'config', 'maintenance.auto', 'false')
  Invoke-FixtureGit @('-C', $repo, 'config', 'gc.auto', '0')
  $port = Join-Path $repo 'git_clean_worktrees.ps1'
  [System.IO.File]::Copy($portSource, $port)
  Invoke-FixtureGit @('-C', $repo, 'add', 'git_clean_worktrees.ps1')
  Invoke-FixtureGit @('-C', $repo, 'commit', '--quiet', '-m', 'Add cleanup helper')
  Invoke-FixtureGit @('-C', $repo, 'push', '--quiet', 'origin', 'master')
  [System.IO.File]::AppendAllText((Join-Path $repo '.git\info\exclude'), ".claude/worktrees/`n")
  $sessionHome = Join-Path $repo '.claude\worktrees'
  $flowHome = Join-Path $root 'realmforge-worktrees'

  # A merged branch with its worktree goes.
  New-UnmergedBranch $repo 'merged-work'
  Invoke-FixtureGit @('-C', $repo, 'merge', '--quiet', '--ff-only', 'merged-work')
  Invoke-FixtureGit @('-C', $repo, 'push', '--quiet', 'origin', 'master')
  $mergedWorktree = Join-Path $flowHome 'merged-work'
  Invoke-FixtureGit @('-C', $repo, 'worktree', 'add', '--quiet', $mergedWorktree, 'merged-work')

  # An unmerged branch stays.
  New-UnmergedBranch $repo 'unmerged-work'

  # A worktree Git registered in another case stays. Seen from its home it looks
  # exactly like generated residue, and without its .git pointer the prune drops
  # the record, so only the mapping captured before the prune, compared without
  # case, says the directory is live.
  New-UnmergedBranch $repo 'case-kept'
  $caseWorktree = Join-Path $sessionHome 'case-kept'
  Invoke-FixtureGit @('-C', $repo, 'worktree', 'add', '--quiet', $caseWorktree, 'case-kept')
  $caseRecord = Join-Path $repo '.git\worktrees\case-kept\gitdir'
  $registered = [System.IO.File]::ReadAllText($caseRecord)
  [System.IO.File]::WriteAllText($caseRecord, $registered.Replace('/case-kept/.git', '/CASE-KEPT/.git'))
  Get-ChildItem -LiteralPath $caseWorktree -Force | Remove-Item -Recurse -Force
  $caseArtifact = Join-Path $caseWorktree 'build\output.txt'
  [void][System.IO.Directory]::CreateDirectory((Split-Path $caseArtifact))
  [System.IO.File]::WriteAllText($caseArtifact, "generated`n")

  # A junction inside generated residue goes, and what it points at stays.
  $outside = Join-Path $root 'outside target'
  [void][System.IO.Directory]::CreateDirectory($outside)
  $outsideFile = Join-Path $outside 'keep.txt'
  [System.IO.File]::WriteAllText($outsideFile, "keep me`n")
  $junctionResidue = Join-Path $sessionHome 'junction-residue'
  [void][System.IO.Directory]::CreateDirectory((Join-Path $junctionResidue 'build'))
  [void](New-Item -ItemType Junction -Path (Join-Path $junctionResidue 'build\link') -Target $outside)

  $fakeGhLog = Join-Path $root 'fake-gh.log'
  $env:FAKE_GH_LOG = $fakeGhLog
  $env:PATH = "$(New-FakeGh $root);$env:PATH"

  $powershell = (Get-Process -Id $PID).Path
  $previousPreference = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  & $powershell -NoProfile -ExecutionPolicy Bypass -File $port
  $exitCode = $LASTEXITCODE
  $ErrorActionPreference = $previousPreference

  Assert-That ($exitCode -eq 0) "the port exits with 0 (was $exitCode)"
  Assert-That (-not (Test-BranchExists $repo 'merged-work')) 'the merged branch is removed'
  Assert-That (-not (Test-Path -LiteralPath $mergedWorktree)) 'the merged worktree is removed'
  Assert-That (Test-BranchExists $repo 'unmerged-work') 'the unmerged branch is kept'
  Assert-That (Test-BranchExists $repo 'case-kept') 'the branch of the worktree registered in another case is kept'
  Assert-That (Test-Path -LiteralPath $caseArtifact) 'the worktree registered in another case is kept'
  Assert-That (-not (Test-Path -LiteralPath $junctionResidue)) 'the residue holding a junction is removed'
  Assert-That (Test-Path -LiteralPath $outsideFile) 'the target of the junction survives'
  Assert-That (-not (Test-Path -LiteralPath "$fakeGhLog.cmd")) 'gh is looked up as an .exe only'
  Assert-That (Test-Path -LiteralPath $fakeGhLog) 'the port calls gh'
  # Broken quoting still hands gh a filter, one that lost its quotes, so it must
  # read exactly as the port's source spells it.
  $ghArguments = [System.IO.File]::ReadAllLines($fakeGhLog)
  $filter = $ghArguments[[array]::IndexOf($ghArguments, '--jq') + 1]
  Assert-That ($filter.Contains('"')) "gh gets a --jq filter with quotes: $filter"
  Assert-That ([System.IO.File]::ReadAllText($portSource).Contains("'$filter'")) 'the filter reaches gh as the port wrote it'
} finally {
  # rmdir never follows a junction a failed run left behind.
  $ErrorActionPreference = 'Continue'
  & cmd.exe /c rmdir /s /q "$root" 2>&1 | Out-Null
}
exit 0
