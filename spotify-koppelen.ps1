# Koppelt je Spotify-account eenmalig aan Glass Home en schrijft de gegevens in config.js
$ErrorActionPreference = "Stop"
$cfg = Join-Path $PSScriptRoot "config.js"
$clientId = (Read-Host "Plak je Spotify Client ID").Trim()
$redirect = "http://127.0.0.1:8888/callback"
$rng = [Security.Cryptography.RandomNumberGenerator]::Create(); $b = New-Object byte[] 48; $rng.GetBytes($b)
$verifier = [Convert]::ToBase64String($b).TrimEnd('=').Replace('+','-').Replace('/','_')
$hash = [Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::ASCII.GetBytes($verifier))
$challenge = [Convert]::ToBase64String($hash).TrimEnd('=').Replace('+','-').Replace('/','_')
$scope = [uri]::EscapeDataString("user-top-read user-read-recently-played user-read-currently-playing")
$url = "https://accounts.spotify.com/authorize?response_type=code&client_id=$clientId&redirect_uri=$([uri]::EscapeDataString($redirect))&code_challenge_method=S256&code_challenge=$challenge&scope=$scope"
$tcp = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 8888); $tcp.Start()
Start-Process $url
Write-Host "Log in bij Spotify in je browser en klik op Akkoord..."
do {
  $cl = $tcp.AcceptTcpClient(); $st = $cl.GetStream(); $rd = New-Object IO.StreamReader($st)
  $line = $rd.ReadLine()
  if ($line -match 'code=([^& ]+)') {
    $code = $Matches[1]
    $wr = New-Object IO.StreamWriter($st)
    $wr.Write("HTTP/1.1 200 OK`r`nContent-Type: text/html; charset=utf-8`r`nConnection: close`r`n`r`n<h2 style='font-family:sans-serif'>Spotify gekoppeld. Je kunt dit venster sluiten.</h2>"); $wr.Flush()
  }
  $cl.Close()
} until ($code)
$tcp.Stop()
$tok = Invoke-RestMethod -Method Post -Uri "https://accounts.spotify.com/api/token" -ContentType "application/x-www-form-urlencoded" `
  -Body @{ grant_type="authorization_code"; code=$code; redirect_uri=$redirect; client_id=$clientId; code_verifier=$verifier }
$c = Get-Content $cfg -Raw
$c = $c -replace 'spotifyClientId:\s*"[^"]*"', "spotifyClientId: `"$clientId`""
$c = $c -replace 'spotifyRefresh:\s*"[^"]*"', "spotifyRefresh: `"$($tok.refresh_token)`""
Set-Content -Path $cfg -Value $c -Encoding UTF8
Write-Host "Klaar! Spotify is gekoppeld en opgeslagen in config.js." -ForegroundColor Green
