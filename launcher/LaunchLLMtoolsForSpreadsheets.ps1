# -----------------------------------------------------------------------------
# Launch LLMToolsForSpreadsheets
# -----------------------------------------------------------------------------

# -------------------------------------
# Parameter
# -------------------------------------

$useDebug = 0 # 1(use) or 0 (not use)

$curDir        = Get-Location
$Env:JAVA_HOME = "$curDir\jre"
$javaExe       = "$Env:JAVA_HOME\bin\java.exe"
$confFile      = "$curDir\conf\application.properties"

# -------------------------------------
# Setup JRE
# -------------------------------------

$Env:PATH = "$Env:JAVA_HOME\bin;$Env:PATH"

# -------------------------------------
# Launch application
# -------------------------------------

$appArgs = "-Dspring.config.location=`"$confFile`"","-Dspring.profiles.active=LLMToolbox","-Dfile.encoding=UTF-8","-jar","LLMToolsForSpreadsheets-0.1.1.jar"

if ($useDebug -eq 0) {
    Start-Process -FilePath "$javaExe" -ArgumentList $appArgs -WindowStyle Hidden

} else {
    Write-Host "... Debug mode"
    Start-Process -FilePath "$javaExe" -ArgumentList $appArgs -NoNewWindow
    $host.UI.RawUI.ReadKey()
}

exit 0
