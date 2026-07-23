# Generic Markdown file splitter by line-range JSON.
# Usage: powershell -File .split-doc.ps1 -SourcePath <md> -DataPath <json>
#
# JSON format: [{ "N": "out-file.md", "S": <start-line>, "E": <end-line>, "T": "<caption>" }, ...]
#   S/E are 1-based, inclusive. Lines before first batch's S become INDEX preamble.
#   Each batch E - S + 1 must be <= 300.
#
# SAFETY: snapshot source -> .bak before writes; UTF-8 no BOM; LF newlines.

param(
    [Parameter(Mandatory=$true)][string]$SourcePath,
    [Parameter(Mandatory=$true)][string]$DataPath
)
$ErrorActionPreference = 'Stop'

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$lines     = [System.IO.File]::ReadAllLines($SourcePath, $utf8NoBom)
$dir       = Split-Path $SourcePath -Parent
$baseName  = [System.IO.Path]::GetFileNameWithoutExtension($SourcePath)

Write-Host "[split] source: $SourcePath"
Write-Host "[split] source line count: $($lines.Count)"

# Snapshot
$bak = "$SourcePath.bak"
if (Test-Path -LiteralPath $bak) { Remove-Item -LiteralPath $bak -Force }
[System.IO.File]::WriteAllLines($bak, $lines, $utf8NoBom)

$batches = Get-Content -LiteralPath $DataPath -Raw -Encoding UTF8 | ConvertFrom-Json
Write-Host "[split] batch count: $($batches.Count)"

$preambleEnd   = $batches[0].S - 1
$totalCoverage = $preambleEnd
foreach ($b in $batches) { $totalCoverage += ($b.E - $b.S + 1) }
Write-Host "[split] coverage: INDEX($preambleEnd) + sub-files(sum) = $totalCoverage (expected $($lines.Count))"
if ($totalCoverage -ne $lines.Count) { throw "[split] coverage mismatch: $totalCoverage != $($lines.Count)" }

# Write sub-files
foreach ($b in $batches) {
    $count = $b.E - $b.S + 1
    if ($count -gt 300) { throw "[split] batch $($b.N) has $count lines (>300)" }
    $slice = $lines[($b.S - 1)..($b.E - 1)]
    $body  = $slice -join "`n"
    if (-not $body.EndsWith("`n")) { $body += "`n" }
    $path  = Join-Path $dir $b.N
    [System.IO.File]::WriteAllText($path, $body, $utf8NoBom)
    Write-Host ("[split] wrote {0,-65} lines={1}" -f $b.N, $count)
}

# Build INDEX = preamble + TOC table
$idxBody = ($lines[0..($preambleEnd - 1)] -join "`n")
$idxBody += "`n---`n`n## 子文件索引`n`n"
$idxBody += "| 子文件 | 章节标签 | 行号 | 说明 |`n"
$idxBody += "|---|---|---|---|`n"
$prefix = $baseName + '-'
foreach ($b in $batches) {
    $count = $b.E - $b.S + 1
    $short = $b.N.Substring($prefix.Length)
    $idxBody += "| ``$($b.N)`` | $short | $($b.S)..$($b.E) ($count 行) | $($b.T) |`n"
}
$idxBody += "`n> 共 $($batches.Count) 个子文件 + 本 INDEX（$preambleEnd 行 preamble） = 源文件 $($lines.Count) 行 1:1 完整覆盖。`n"
$idxBody += "`n## 拆分规则（CLAUDE.md §3 + 用户硬约束）`n`n"
$idxBody += "- 每子文件 <=300 行`n"
$idxBody += "- 按原章节顺序，单文件内保持原顺序`n"
$idxBody += "- INDEX 保留原 preamble + 子文件 TOC`n"
$idxBody += "- 不修改任何正文；只按行切片`n"

if (-not $idxBody.EndsWith("`n")) { $idxBody += "`n" }
[System.IO.File]::WriteAllText($SourcePath, $idxBody, $utf8NoBom)
$idxLineCount = ($idxBody -split "`n").Count
Write-Host "[split] INDEX written: $SourcePath ($idxLineCount lines)"
Write-Host "[split] DONE. Backup at: $bak"