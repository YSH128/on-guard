[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$file = 'C:/S14P21A404/shutter-BE/grafana/provisioning/dashboards/ai_telemetry.json'
$bytes = [System.IO.File]::ReadAllBytes($file)
$content = [System.Text.Encoding]::UTF8.GetString($bytes)

# Replace '${device}' (with manual quotes) -> ${device:sqlstring} (Grafana handles quoting)
$content = $content.Replace("LIKE '`${device}'", "LIKE `${device:sqlstring}")

# Bump version
$content = $content -replace '"version": \d+', '"version": 7'

# Write WITHOUT BOM
$outBytes = [System.Text.Encoding]::UTF8.GetBytes($content)
[System.IO.File]::WriteAllBytes($file, $outBytes)

$count = ([regex]::Matches($content, 'sqlstring')).Count
Write-Host "sqlstring occurrences: $count"
$remaining = ([regex]::Matches($content, "LIKE '`\`${device}'")).Count
Write-Host "Old pattern remaining: $remaining"
