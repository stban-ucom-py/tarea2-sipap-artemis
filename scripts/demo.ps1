$ErrorActionPreference = "Stop"
$baseUrl = if ($env:SIPAP_API_URL) { $env:SIPAP_API_URL } else { "http://localhost:8080" }
$today = Get-Date -Format "yyyy-MM-dd"
$validQr = "00020101021232400014py.gov.bcp.sip01040015021012345678905204573153036005405150005802PY5914TIENDA EJEMPLO6008ASUNCION6304A1B2"
$highQr = "00020101021232400014py.gov.bcp.sip01040015021012345678905204573153036005408100000015802PY5914TIENDA EJEMPLO6008ASUNCION6304A1B2"

function Send-Transfer($id, $date, $qr) {
    $body = @{
        id_transaccion = $id
        fecha_transaccion = $date
        qr = $qr
        monto = 10
    } | ConvertTo-Json
    try {
        Invoke-RestMethod -Method Post -Uri "$baseUrl/api/transferencias" `
            -ContentType "application/json" -Body $body
    } catch {
        $_.ErrorDetails.Message
    }
}

Write-Host "1. Transferencia válida"
Send-Transfer "TX-DEMO-OK" $today $validQr
Start-Sleep -Seconds 2
Invoke-RestMethod -Uri "$baseUrl/api/resultados?id=TX-DEMO-OK"

Write-Host "2. Monto superior al máximo"
Send-Transfer "TX-DEMO-MONTO" $today $highQr

Write-Host "3. Fecha anterior (aceptada por API, rechazada por consumidor)"
Send-Transfer "TX-DEMO-FECHA" ([datetime]::Today.AddDays(-1).ToString("yyyy-MM-dd")) $validQr
Start-Sleep -Seconds 2
Invoke-RestMethod -Uri "$baseUrl/api/resultados?id=TX-DEMO-FECHA"

Write-Host "4. Respuesta rechazada del banco mock"
Send-Transfer "TX-REJECT-DEMO" $today $validQr
Start-Sleep -Seconds 2
Invoke-RestMethod -Uri "$baseUrl/api/resultados?id=TX-REJECT-DEMO"

Write-Host "5. Duplicado"
Send-Transfer "TX-DEMO-OK" $today $validQr
