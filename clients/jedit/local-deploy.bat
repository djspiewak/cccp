@echo off

if not defined JEDIT_HOME set JEDIT_HOME=c:\Program Files\jEdit
if not exist "%JEDIT_HOME%" set JEDIT_HOME=%1

if not exist "%JEDIT_HOME%" (
  echo 'Must specify a valid destination for jEdit plugin JARs!'
  exit -1
)

xcopy "%~dp0\dist\lib\cccp-jedit-client_2.9.1-0.1.jar" "%JEDIT_HOME%\jars\CCCP.jar" /y
xcopy "%~dp0\dist\lib\scala-library.jar" "%JEDIT_HOME%\jars" /y
