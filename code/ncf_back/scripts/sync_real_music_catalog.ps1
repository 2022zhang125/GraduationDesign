param(
    [string]$HostName = "127.0.0.1",
    [int]$Port = 3306,
    [string]$UserName = "root",
    [string]$Password = "123456",
    [string]$Database = "ncf",
    [string]$ApiKey = "bf3f2cd3b61203bfafcc1c4e9c90786a",
    [string]$Platform = "wy",
    [string]$CatalogPath = ".\src\main\resources\music\real_music_catalog.txt",
    [int]$RetryCount = 3,
    [int]$RetryDelayMs = 1500,
    [int]$ThrottleDelayMs = 400
)

$ErrorActionPreference = "Stop"

function Escape-Sql([string]$Value) {
    if ($null -eq $Value) {
        return "NULL"
    }

    $escaped = $Value.Replace("\", "\\").Replace("'", "''")
    return "'" + $escaped + "'"
}

function Invoke-MySql([string]$Sql) {
    & 'F:\SQL\bin\mysql.exe' `
        --default-character-set=utf8mb4 `
        --host=$HostName `
        --port=$Port `
        --user=$UserName `
        --password=$Password `
        $Database `
        -N -B -e $Sql
}

function Invoke-MusicApi([string]$Query) {
    $url = 'https://jkapi.com/api/music?plat=' + $Platform + '&type=json&apiKey=' + $ApiKey + '&name=' + [uri]::EscapeDataString($Query)

    for ($attempt = 1; $attempt -le $RetryCount; $attempt++) {
        try {
            $resp = Invoke-RestMethod -Uri $url
            if ($null -ne $resp -and $resp.code -eq 1 -and -not [string]::IsNullOrWhiteSpace($resp.music_url) -and -not [string]::IsNullOrWhiteSpace($resp.name) -and -not [string]::IsNullOrWhiteSpace($resp.artist)) {
                return $resp
            }
        } catch {
        }

        if ($attempt -lt $RetryCount) {
            Start-Sleep -Milliseconds $RetryDelayMs
        }
    }

    return $null
}

$catalogFullPath = Resolve-Path $CatalogPath
$catalogEntries = [System.IO.File]::ReadAllLines($catalogFullPath, [System.Text.Encoding]::UTF8) | Where-Object { $_.Trim() -and -not $_.Trim().StartsWith("#") }
$itemIds = @(Invoke-MySql "SELECT item_id FROM items ORDER BY item_id;")

if ($itemIds.Count -eq 0) {
    throw "No items found in database."
}

$usedTracks = @{}
$sqlStatements = New-Object System.Collections.Generic.List[string]
$assigned = 0

foreach ($line in $catalogEntries) {
    if ($assigned -ge $itemIds.Count) {
        break
    }

    $parts = $line.Split("|")
    if ($parts.Count -lt 3) {
        continue
    }

    $query = $parts[0].Trim()
    $genre = $parts[1].Trim()
    $language = $parts[2].Trim()
    $resp = Invoke-MusicApi $query
    if ($null -eq $resp) {
        Start-Sleep -Milliseconds $ThrottleDelayMs
        continue
    }

    $trackKey = $resp.name + "|" + $resp.artist
    if ($usedTracks.ContainsKey($trackKey)) {
        continue
    }
    $usedTracks[$trackKey] = $true

    $itemId = [int64]$itemIds[$assigned]
    $artistId = 1000000 + $assigned + 1
    $albumId = 2000000 + $assigned + 1
    $externalItemNo = "JKAPI_QUERY::" + $query
    $lyricSnippet = $resp.name + " - " + $resp.artist + " / " + $resp.album

    $sqlStatements.Add(@"
UPDATE items
SET external_item_no = $(Escape-Sql $externalItemNo),
    title = $(Escape-Sql $resp.name),
    artist_id = $artistId,
    artist_name = $(Escape-Sql $resp.artist),
    album_id = $albumId,
    album_name = $(Escape-Sql $resp.album),
    genre_code = $(Escape-Sql $genre),
    language_code = $(Escape-Sql $language),
    duration_seconds = NULL,
    release_date = NULL,
    item_status = 1,
    updated_at = NOW()
WHERE item_id = $itemId;
"@)

    $sqlStatements.Add(@"
INSERT INTO item_media (
    item_id, preview_url, cover_url, preview_duration_seconds, lyric_snippet, source_platform, updated_at
) VALUES (
    $itemId, $(Escape-Sql $resp.music_url), NULL, 30, $(Escape-Sql $lyricSnippet), 'JKAPI_WY', NOW()
)
ON DUPLICATE KEY UPDATE
    preview_url = VALUES(preview_url),
    cover_url = VALUES(cover_url),
    preview_duration_seconds = VALUES(preview_duration_seconds),
    lyric_snippet = VALUES(lyric_snippet),
    source_platform = VALUES(source_platform),
    updated_at = NOW();
"@)

    $assigned++
    Start-Sleep -Milliseconds $ThrottleDelayMs
}

if ($assigned -lt $itemIds.Count) {
    throw "Only synced $assigned of $($itemIds.Count) items. Increase catalog candidates or inspect API availability."
}

$tempSql = Join-Path $env:TEMP ("ncf-real-music-sync-" + [guid]::NewGuid().ToString() + ".sql")
Set-Content -Path $tempSql -Value ("SET NAMES utf8mb4;`n" + ($sqlStatements -join "`n")) -Encoding UTF8

try {
    & 'F:\SQL\bin\mysql.exe' `
        --default-character-set=utf8mb4 `
        --host=$HostName `
        --port=$Port `
        --user=$UserName `
        --password=$Password `
        $Database `
        -e ("SOURCE " + $tempSql.Replace("\", "/") + ";")
} finally {
    Remove-Item $tempSql -ErrorAction SilentlyContinue
}

Write-Output ("SYNCED_ITEMS=" + $assigned)
