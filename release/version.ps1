# Change the version of the line this checkout is on, and report the artifact name that follows.
#
# docs/VERSIONING.md makes this the only way a version is changed: the jar version is
# "<MAJOR>.<MINOR>.<PATCH>+mc<Minecraft version>", the base lives in gradle.properties, and the
# three lines (1.20.x, 1.21.x, 26.x) version themselves independently - so this edits the file in
# the branch it is run from and nothing else.
#
# ASCII-only on purpose: Windows PowerShell 5.1 reads a UTF-8 file without BOM as ANSI.
#
# Usage:
#   .\release\version.ps1 show                 # what the version is now, and the jar it builds
#   .\release\version.ps1 patch                # 0.1.0 -> 0.1.1
#   .\release\version.ps1 minor -DryRun        # say what would change, write nothing
#   .\release\version.ps1 patch -Base 1.0.0    # set the base outright (first run in a real game)
param(
	[Parameter(Mandatory=$true)][ValidateSet('show', 'major', 'minor', 'patch')][string]$Bump,
	[string]$Base = '',
	[switch]$DryRun,
	[string]$Repo = (Split-Path -Parent $PSScriptRoot)
)
$ErrorActionPreference = 'Stop'
$properties = Join-Path $Repo 'gradle.properties'
if(-not (Test-Path $properties)){ throw "no gradle.properties under $Repo" }

$text = [System.IO.File]::ReadAllText($properties)
$baseMatch = [regex]::Match($text, '(?m)^mod_version_base=(.+)$')
$minecraftMatch = [regex]::Match($text, '(?m)^minecraft_version=(.+)$')
if(-not $baseMatch.Success){ throw "gradle.properties has no mod_version_base" }
if(-not $minecraftMatch.Success){ throw "gradle.properties has no minecraft_version" }
$current = $baseMatch.Groups[1].Value.Trim()
$minecraft = $minecraftMatch.Groups[1].Value.Trim()

# A pre-release suffix ("0.2.0-rc.1") is kept out of the arithmetic and put back afterwards, so a
# bump always lands on a release version and never on a pre-release of one.
$suffix = ''
$core = $current
$suffixMatch = [regex]::Match($current, '^(\d+\.\d+\.\d+)(-.+)$')
if($suffixMatch.Success){ $core = $suffixMatch.Groups[1].Value; $suffix = $suffixMatch.Groups[2].Value }

function Core-Of([string]$value){
	$match = [regex]::Match($value, '^(\d+)\.(\d+)\.(\d+)$')
	if(-not $match.Success){ throw "'$value' is not MAJOR.MINOR.PATCH" }
	return @([int]$match.Groups[1].Value, [int]$match.Groups[2].Value, [int]$match.Groups[3].Value)
}

if($Base){
	$parts = Core-Of $Base
	$next = $Base
} else {
	if($Bump -eq 'show'){
		$next = $core
	} else {
		$parts = Core-Of $core
		switch($Bump){
			'major' { $next = "$($parts[0] + 1).0.0" }
			'minor' { $next = "$($parts[0]).$($parts[1] + 1).0" }
			'patch' { $next = "$($parts[0]).$($parts[1]).$($parts[2] + 1)" }
		}
	}
}

# 0.x means "the loader does not run in the game yet" (docs/VERSIONING.md), so nothing may bump past
# 0.0.x or back into it: 1.0.0 is set deliberately with -Base, when that has actually happened.
if($next -match '^0\.0\.' -and $Bump -ne 'show'){ throw "$next would claim nothing works; use -Base 0.1.0 or higher" }

$written = "$next$suffix"
$artifact = "OptifiNeoforge-$written+mc$minecraft.jar"
if($Bump -eq 'show'){
	Write-Host "line           $Repo"
	Write-Host "minecraft      $minecraft"
	Write-Host "version        $written"
	Write-Host "artifact       $artifact"
	return
}

if($written -eq $current){
	Write-Host "version already $current ($artifact); nothing to do"
	return
}
if($DryRun){
	Write-Host "would change $current -> $written"
	Write-Host "artifact would be $artifact"
	return
}

$updated = $text.Substring(0, $baseMatch.Groups[1].Index) + $written +
	$text.Substring($baseMatch.Groups[1].Index + $baseMatch.Groups[1].Length)
[System.IO.File]::WriteAllText($properties, $updated, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "version $current -> $written"
Write-Host "artifact        $artifact"
Write-Host "commit gradle.properties together with the change that justified the bump"
