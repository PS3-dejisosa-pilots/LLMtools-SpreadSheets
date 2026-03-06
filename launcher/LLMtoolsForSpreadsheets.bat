@REM -----------------------------------------------------------------------------
@REM Launch LaunchLLMToolsForSpreadsheets.ps1
@REM -----------------------------------------------------------------------------
@echo off
setlocal enabledelayedexpansion

@REM ----------------------------------
@REM Launch LLMTools
@REM ----------------------------------

powershell -ExecutionPolicy RemoteSigned -File "%~dp0LaunchLLMtoolsForSpreadsheets.ps1"

endlocal
exit /b
