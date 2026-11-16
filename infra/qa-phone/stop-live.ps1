param([Parameter(Mandatory)][string]$RuntimeDirectory)
$ErrorActionPreference = 'Stop'
$config = Get-Content -Raw -LiteralPath (Join-Path $RuntimeDirectory 'control-config.json') | ConvertFrom-Json
if ($config.origin -cne 'http://127.0.0.1:18785') { throw 'Unexpected QA control origin' }
Invoke-RestMethod -Method Post -Uri ($config.origin + '/shutdown') -Headers @{Origin=$config.origin;'X-QA-Token'=$config.token} -ContentType 'application/json' -Body '{}' | Out-Null
Write-Output 'QA runtime cleanup requested. Only this runtime owns its containers.'
