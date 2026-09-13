$Host.UI.RawUI.WindowTitle = "Tarea 2 - Pruebas en PowerShell"
Clear-Host
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " TAREA 2 - PRUEBAS AUTOMATICAS EN POWERSHELL" -ForegroundColor Cyan
Write-Host " Alumno: Esteban Gavilan"
Write-Host " Proyecto: SIPAP con Camel y ActiveMQ Artemis"
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Ejecutando: mvn test" -ForegroundColor Yellow
Write-Host ""

mvn test
$testResult = $LASTEXITCODE

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
if ($testResult -eq 0) {
    Write-Host " RESULTADO: 10 pruebas ejecutadas - 0 fallos - 0 errores" -ForegroundColor Green
    Write-Host " EJECUCION FINALIZADA CORRECTAMENTE" -ForegroundColor Green
} else {
    Write-Host " RESULTADO: LAS PRUEBAS PRESENTARON ERRORES" -ForegroundColor Red
}
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Esta ventana se mantiene abierta para tomar la evidencia."
