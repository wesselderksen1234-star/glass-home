# Glass Home tv-brug: verbindt je pc met de LG-tv (officiële LG-netwerkbediening)
# en zet Glass Home automatisch aan als de tv aangaat.
param([switch]$Setup)
$ErrorActionPreference = "Continue"
# IP-adres van de tv staat alleen lokaal in tv-ip.txt (niet online); ontbreekt het, dan zoekt de brug de tv zelf op het netwerk
$ipFile = Join-Path $PSScriptRoot "tv-ip.txt"
$TV = if (Test-Path $ipFile) { ("" + (Get-Content $ipFile -Raw)).Trim() } else { "" }
if (-not $TV) {
  try {
    $u = New-Object Net.Sockets.UdpClient; $u.Client.ReceiveTimeout = 3000
    $msg = [Text.Encoding]::ASCII.GetBytes("M-SEARCH * HTTP/1.1`r`nHOST: 239.255.255.250:1900`r`nMAN: `"ssdp:discover`"`r`nMX: 2`r`nST: urn:lge-com:service:webos-second-screen:1`r`n`r`n")
    $u.Send($msg, $msg.Length, "239.255.255.250", 1900) | Out-Null
    $ep = New-Object Net.IPEndPoint([Net.IPAddress]::Any, 0); $u.Receive([ref]$ep) | Out-Null
    $TV = $ep.Address.ToString(); Set-Content $ipFile $TV -Encoding ASCII
  } catch { $TV = "" }
}
$Dir = $PSScriptRoot
$AppId = "com.custom.glasshome"
$ManifestPlain = '{"appVersion":"1.1","manifestVersion":1,"permissions":["APP_TO_APP","CLOSE","CONTROL_AUDIO","CONTROL_DISPLAY","CONTROL_INPUT_JOYSTICK","CONTROL_INPUT_MEDIA_PLAYBACK","CONTROL_INPUT_MEDIA_RECORDING","CONTROL_INPUT_TEXT","CONTROL_INPUT_TV","CONTROL_MOUSE_AND_KEYBOARD","CONTROL_POWER","CONTROL_TV_SCREEN","LAUNCH","LAUNCH_WEBAPP","READ_APP_STATUS","READ_COUNTRY_INFO","READ_CURRENT_CHANNEL","READ_INPUT_DEVICE_LIST","READ_INSTALLED_APPS","READ_LGE_SDX","READ_LGE_TV_INPUT_EVENTS","READ_NETWORK_STATE","READ_NOTIFICATIONS","READ_POWER_STATE","READ_RUNNING_APPS","READ_SETTINGS","READ_TV_CHANNEL_LIST","READ_TV_CURRENT_TIME","READ_UPDATE_INFO","SEARCH","TEST_OPEN","TEST_PROTECTED","TEST_SECURE","UPDATE_FROM_REMOTE_APP","WRITE_NOTIFICATION_ALERT","WRITE_NOTIFICATION_TOAST","WRITE_SETTINGS"]}'
$Manifest = '{"appVersion":"1.1","manifestVersion":1,"deviceName":"bscpylgtv","permissions":["LAUNCH","LAUNCH_WEBAPP","APP_TO_APP","CLOSE","TEST_OPEN","TEST_PROTECTED","CONTROL_AUDIO","CONTROL_DISPLAY","CONTROL_INPUT_JOYSTICK","CONTROL_INPUT_MEDIA_RECORDING","CONTROL_INPUT_MEDIA_PLAYBACK","CONTROL_INPUT_TV","CONTROL_POWER","CONTROL_TV_SCREEN","READ_APP_STATUS","READ_CURRENT_CHANNEL","READ_INPUT_DEVICE_LIST","READ_NETWORK_STATE","READ_RUNNING_APPS","READ_TV_CHANNEL_LIST","WRITE_NOTIFICATION_TOAST","READ_POWER_STATE","READ_COUNTRY_INFO","CONTROL_INPUT_TEXT","CONTROL_MOUSE_AND_KEYBOARD","READ_INSTALLED_APPS","READ_SETTINGS","READ_STORAGE_DEVICE_LIST"],"signatures":[{"signature":"eyJhbGdvcml0aG0iOiJSU0EtU0hBMjU2Iiwia2V5SWQiOiJ0ZXN0LXNpZ25pbmctY2VydCIsInNpZ25hdHVyZVZlcnNpb24iOjF9.hrVRgjCwXVvE2OOSpDZ58hR+59aFNwYDyjQgKk3auukd7pcegmE2CzPCa0bJ0ZsRAcKkCTJrWo5iDzNhMBWRyaMOv5zWSrthlf7G128qvIlpMT0YNY+n/FaOHE73uLrS/g7swl3/qH/BGFG2Hu4RlL48eb3lLKqTt2xKHdCs6Cd4RMfJPYnzgvI4BNrFUKsjkcu+WD4OO2A27Pq1n50cMchmcaXadJhGrOqH5YmHdOCj5NSHzJYrsW0HPlpuAx/ECMeIZYDh6RMqaFM2DXzdKX9NmmyqzJ3o/0lkk/N97gfVRLW5hA29yeAwaCViZNCP8iC9aO0q9fQojoa7NQnAtw==","signatureVersion":1}],"signed":{"appId":"com.lge.test","created":"20140509","localizedAppNames":{"":"LG Remote App","ko-KR":"\ub9ac\ubaa8\ucee8 \uc571","zxx-XX":"\u041b\u0413 R\u044d\u043cot\u044d A\u041f\u041f"},"localizedVendorNames":{"":"LG Electronics"},"permissions":["TEST_SECURE","CONTROL_INPUT_TEXT","CONTROL_MOUSE_AND_KEYBOARD","READ_INSTALLED_APPS","READ_LGE_SDX","READ_NOTIFICATIONS","SEARCH","WRITE_SETTINGS","WRITE_NOTIFICATION_ALERT","CONTROL_POWER","READ_CURRENT_CHANNEL","READ_RUNNING_APPS","READ_UPDATE_INFO","UPDATE_FROM_REMOTE_APP","READ_LGE_TV_INPUT_EVENTS","READ_TV_CURRENT_TIME"],"serial":"2f930e2d2cfe083771f68e4fe7bb07","vendorId":"com.lge"}}'
$log = Join-Path $Dir "log.txt"
function Log($t){ $l = "[{0:HH:mm:ss}] {1}" -f (Get-Date), $t; Add-Content -Path $log -Value $l -Encoding UTF8; Write-Host $l }

# eenmalig: automatisch starten met Windows (verborgen)
if ($Setup) {
  $startup = [Environment]::GetFolderPath("Startup")
  $vbs = Join-Path $startup "Glass Home tv-brug.vbs"
  $cmd = 'powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "' + (Join-Path $Dir "tv-brug.ps1") + '"'
  Set-Content -Path $vbs -Value ('CreateObject("Wscript.Shell").Run "' + $cmd.Replace('"','""') + '", 0') -Encoding ASCII
  Log "Automatisch starten met Windows ingesteld."
}

# maar één exemplaar tegelijk
$mutex = New-Object Threading.Mutex($false, "GlassHomeTvBrug2")
if (-not $mutex.WaitOne(0)) { Write-Host "De tv-brug draait al."; exit }

# de tv gebruikt een eigen (zelf-ondertekend) certificaat: accepteer dat alleen in deze brug
Add-Type -TypeDefinition @"
using System.Net; using System.Net.Security; using System.Security.Cryptography.X509Certificates;
public static class GHTrust {
  static bool Ok(object s, X509Certificate c, X509Chain ch, SslPolicyErrors e) { return true; }
  public static void Enable() { ServicePointManager.ServerCertificateValidationCallback = Ok; }
}
"@
[GHTrust]::Enable()
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$enc = [Text.Encoding]::UTF8

function Open-Ws($url) {
  $ws = New-Object Net.WebSockets.ClientWebSocket
  $ws.Options.KeepAliveInterval = [TimeSpan]::FromSeconds(20)
  try { $ws.ConnectAsync([Uri]$url, [Threading.CancellationToken]::None).Wait(8000) | Out-Null }
  catch { $e = $_.Exception; while ($e.InnerException) { $e = $e.InnerException }; throw ("geen verbinding met " + $url + ": " + $e.Message) }
  if ($ws.State -ne "Open") { throw ("geen verbinding met " + $url + " (geen antwoord)") }
  return $ws
}
function Send-Ws($ws, [string]$text) {
  $b = $enc.GetBytes($text); $seg = New-Object ArraySegment[byte] -ArgumentList (,$b)
  $ws.SendAsync($seg, "Text", $true, [Threading.CancellationToken]::None).Wait(5000) | Out-Null
}
$script:rxTask = $null; $script:rxBuf = New-Object byte[] 65536; $script:rxText = ""
function Poll-Ws($ws, [int]$ms) {
  # geeft één compleet bericht terug, of $null na $ms milliseconden
  $end = (Get-Date).AddMilliseconds($ms)
  while ($true) {
    if ($script:rxTask -eq $null) { $seg = New-Object ArraySegment[byte] -ArgumentList (,$script:rxBuf); $script:rxTask = $ws.ReceiveAsync($seg, [Threading.CancellationToken]::None) }
    $left = [int]([Math]::Max(0, ($end - (Get-Date)).TotalMilliseconds))
    if (-not $script:rxTask.Wait($left)) { return $null }
    $r = $script:rxTask.Result; $script:rxTask = $null
    if ($r.MessageType -eq "Close") { throw "verbinding gesloten" }
    $script:rxText += $enc.GetString($script:rxBuf, 0, $r.Count)
    if ($r.EndOfMessage) { $t = $script:rxText; $script:rxText = ""; return $t }
  }
}
$script:n = 0
$script:pending = New-Object Collections.ArrayList
function Req($uri, $payloadJson) {
  $script:n++; $id = "c$($script:n)"
  if (-not $payloadJson) { $payloadJson = "{}" }
  Send-Ws $script:ws ('{"type":"request","id":"' + $id + '","uri":"' + $uri + '","payload":' + $payloadJson + '}')
  $end = (Get-Date).AddSeconds(10)
  while ((Get-Date) -lt $end) {
    $m = Poll-Ws $script:ws 500
    if ($m -eq $null) { continue }
    $o = $m | ConvertFrom-Json
    if ($o.id -eq $id) { return $o }
    Handle-Event $o
  }
  return @{ type = "timeout"; id = $id }
}
function Luna($uri, $paramsJson) {
  $lu = "luna://" + $uri
  $p = '{"message":" ","buttons":[{"label":"","onClick":"' + $lu + '","params":' + $paramsJson + '}],"onclose":{"uri":"' + $lu + '","params":' + $paramsJson + '},"onfail":{"uri":"' + $lu + '","params":' + $paramsJson + '}}'
  $r = Req "ssap://system.notifications/createAlert" $p
  if ($r.payload.alertId) { return Req "ssap://system.notifications/closeAlert" ('{"alertId":"' + $r.payload.alertId + '"}') }
  return $r
}
function Button($name) {
  if (-not $script:btn -or $script:btn.State -ne "Open") {
    $r = Req "ssap://com.webos.service.networkinput/getPointerInputSocket" "{}"
    $script:btn = Open-Ws $r.payload.socketPath
  }
  Send-Ws $script:btn ("type:button`nname:" + $name + "`n`n")
}
function Shot {
  $r = Req "ssap://tv/executeOneShot" "{}"
  if ($r.payload.imageUri) {
    $f = Join-Path $Dir "scherm.jpg"
    try { Invoke-WebRequest -UseBasicParsing -Uri $r.payload.imageUri -OutFile $f -TimeoutSec 10; return "scherm.jpg opgeslagen" } catch { return "download mislukt: $_" }
  }
  return ($r | ConvertTo-Json -Depth 10 -Compress)
}
$script:wasActive = $true
function Handle-Event($o) {
  if ($o.id -eq "pwr" -and $o.payload.state) {
    $active = ($o.payload.state -eq "Active")
    if ($active -and -not $script:wasActive) { Log "Tv is aangezet."; Start-GlassHome }
    $script:wasActive = $active
  }
}
function Start-GlassHome {
  $cfg = Join-Path $Dir "autostart-uit.txt"
  if (Test-Path $cfg) { return }
  Start-Sleep -Milliseconds 1500
  $r = Req "ssap://system.launcher/launch" ('{"id":"' + $AppId + '"}')
  Log ("Glass Home gestart: " + ($r.type))
}
function Connect-Tv {
  Log "Verbinden met de tv op $TV..."
  try { $script:ws = Open-Ws ("wss://" + $TV + ":3001") }
  catch { Log $_.Exception.Message; Log "Tweede poging via poort 3000..."; $script:ws = Open-Ws ("ws://" + $TV + ":3000") }
  $script:rxTask = $null; $script:rxText = ""; $script:btn = $null
  $keyFile = Join-Path $Dir "tv-key.txt"
  $key = ""; if (Test-Path $keyFile) { $raw = Get-Content $keyFile -Raw; if ($raw) { $key = "$raw".Trim() } }
  $kp = if ($key) { '"client-key":"' + $key + '",' } else { "" }
  $mf = if (Test-Path (Join-Path $Dir "unsigned.txt")) { $ManifestPlain } else { $Manifest }
  Send-Ws $script:ws ('{"type":"register","id":"register_0","payload":{' + $kp + '"forcePairing":false,"pairingType":"PROMPT","manifest":' + $mf + '}}')
  if (-not $key) { Log "Kijk op je tv en kies 'Toestaan' / 'Accept' met de afstandsbediening." }
  $end = (Get-Date).AddSeconds(90)
  while ((Get-Date) -lt $end) {
    $m = Poll-Ws $script:ws 1000
    if ($m -eq $null) { continue }
    $o = $m | ConvertFrom-Json
    if ($o.type -eq "registered") {
      Set-Content -Path $keyFile -Value $o.payload."client-key" -Encoding ASCII
      Log "Verbonden met de tv."
      Send-Ws $script:ws '{"type":"subscribe","id":"pwr","uri":"ssap://com.webos.service.tvpower/power/getPowerState","payload":{}}'
      return $true
    }
    if ($o.type -eq "error") {
      Log ("Tv weigerde: " + $o.error)
      if ($o.error -match "blacklist" -and -not (Test-Path (Join-Path $Dir "unsigned.txt"))) { Set-Content (Join-Path $Dir "unsigned.txt") "1"; Log "Ik probeer het meteen opnieuw op een andere manier." }
      try { $script:ws.Abort() } catch {}; $script:ws = $null
      return $false }
  }
  Log "Geen toestemming ontvangen op de tv."; return $false
}

# opdrachten van Claude: opdracht.json -> antwoord.json
function Run-Commands {
  $f = Join-Path $Dir "opdracht2.json"
  if (-not (Test-Path $f)) { return }
  $raw = Get-Content $f -Raw -Encoding UTF8; Remove-Item $f -Force
  $job = $raw | ConvertFrom-Json; $out = @()
  foreach ($c in $job.cmds) {
    $res = ""
    try {
      switch ($c.t) {
        "ssap"   { $res = Req $c.uri (($c.p | ConvertTo-Json -Depth 20 -Compress)) }
        "luna"   { $res = Luna $c.uri (($c.p | ConvertTo-Json -Depth 20 -Compress)) }
        "button" { Button $c.name; $res = "ok" }
        "wait"   { Start-Sleep -Milliseconds ([int]($c.s * 1000)); $res = "ok" }
        "shot"   { $res = Shot }
        "autostart" { $af = Join-Path $Dir "autostart-uit.txt"; if ($c.on) { Remove-Item $af -ErrorAction SilentlyContinue } else { Set-Content $af "uit" }; $res = "ok" }
        default  { $res = "onbekend" }
      }
    } catch { $res = "fout: $_" }
    $out += [pscustomobject]@{ cmd = $c; result = $res }
  }
  ($out | ConvertTo-Json -Depth 20) | Set-Content -Path (Join-Path $Dir "antwoord2.json") -Encoding UTF8
  Log ("Opdracht uitgevoerd (" + $out.Count + " stappen).")
}

Log "Tv-brug gestart."
$self = Join-Path $Dir "tv-brug.ps1"; $selfTime = (Get-Item $self).LastWriteTime
while ($true) {
  try {
    if ((Get-Item $self).LastWriteTime -ne $selfTime) {
      Log "Nieuwe versie van de brug gevonden, herstarten..."
      $mutex.ReleaseMutex()
      Start-Process powershell -ArgumentList ('-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "' + $self + '"') -WindowStyle Hidden
      exit
    }
    if (-not $script:ws -or $script:ws.State -ne "Open") {
      $ok = Connect-Tv
      if (-not $ok) { Start-Sleep -Seconds 3; continue }
      if (-not $script:wasActive) { Start-GlassHome }
    }
    $m = Poll-Ws $script:ws 1000
    if ($m) { Handle-Event ($m | ConvertFrom-Json) }
    Run-Commands
  } catch {
    Log ("Fout: " + $_.Exception.Message + " [regel " + $_.InvocationInfo.ScriptLineNumber + ": " + $_.InvocationInfo.Line.Trim() + "] (nieuwe poging over 10 s)")
    $script:ws = $null; $script:wasActive = $false
    Start-Sleep -Seconds 10
  }
}
