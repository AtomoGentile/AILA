# Prova una chiave Gemini (anche AQ.) su piu' modelli e stampa cosa risponde Google.
# La chiave si digita qui e resta su questo PC: non viene salvata ne' stampata.
$secure = Read-Host "Incolla la chiave Gemini" -AsSecureString
$key = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
$key = ($key -split '\s+' | Where-Object { $_ })[0]
Write-Host "Chiave: inizia con '$($key.Substring(0, [Math]::Min(3, $key.Length)))', lunga $($key.Length) caratteri"

$base = 'https://generativelanguage.googleapis.com/v1beta/models'
$body = Join-Path $env:TEMP 'gemini-prova-body.json'
'{"contents":[{"parts":[{"text":"Rispondi solo con: ok"}]}]}' | Set-Content -Encoding ascii $body
$out = Join-Path $env:TEMP 'gemini-prova-out.json'

function Show($label, $code) {
    $text = if (Test-Path $out) { (Get-Content $out -Raw) -replace '\s+', ' ' } else { '' }
    if ($text.Length -gt 220) { $text = $text.Substring(0, 220) + '...' }
    Write-Host ("{0,-28} HTTP {1}  {2}" -f $label, $code, $(if ($code -eq '200') { 'OK' } else { $text }))
}

$code = curl.exe -s -o $out -w '%{http_code}' -H "x-goog-api-key: $key" "$base`?pageSize=5"
Show 'elenco modelli' $code

foreach ($m in 'gemini-flash-latest', 'gemini-flash-lite-latest', 'gemini-pro-latest',
                'gemini-3.8-flash', 'gemini-3.6-flash', 'gemini-3.5-flash-lite') {
    $code = curl.exe -s -o $out -w '%{http_code}' -X POST -H 'Content-Type: application/json' `
        -H "x-goog-api-key: $key" --data-binary "@$body" "$base/$m`:generateContent"
    Show $m $code
}
Remove-Item $body, $out -ErrorAction SilentlyContinue
