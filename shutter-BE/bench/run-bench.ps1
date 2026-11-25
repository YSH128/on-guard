param(
    [int]$VUs = 100,
    [int]$IntervalMs = 3000,
    [int]$DurationSec = 60,
    [int]$WarmupMs = 5000
)

$ErrorActionPreference = "Stop"
$BenchDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "=== [1/2] Redis Benchmark (VUs=$VUs, Interval=${IntervalMs}ms, Duration=${DurationSec}s) ==="
docker run --rm --name shutter-bench-k6-redis --network shutter-bench-net `
  -v "${BenchDir}:/scripts:ro" `
  -e MODE=redis `
  -e VUS=$VUs `
  -e INTERVAL_MS=$IntervalMs `
  -e DURATION_SEC=$DurationSec `
  -e WARMUP_MS=$WarmupMs `
  grafana/k6:latest run /scripts/k6-gps-bench.js

Write-Host ""
Write-Host "=== [2/2] MySQL Direct Write Benchmark (VUs=$VUs, Interval=${IntervalMs}ms, Duration=${DurationSec}s) ==="
docker run --rm --name shutter-bench-k6-db --network shutter-bench-net `
  -v "${BenchDir}:/scripts:ro" `
  -e MODE=db `
  -e VUS=$VUs `
  -e INTERVAL_MS=$IntervalMs `
  -e DURATION_SEC=$DurationSec `
  -e WARMUP_MS=$WarmupMs `
  grafana/k6:latest run /scripts/k6-gps-bench.js
