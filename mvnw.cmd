@ECHO OFF
SETLOCAL

SET BASE_DIR=%~dp0
SET WRAPPER_DIR=%BASE_DIR%.mvn\wrapper
SET WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar
SET WRAPPER_PROPS=%WRAPPER_DIR%\maven-wrapper.properties

IF NOT EXIST "%WRAPPER_JAR%" (
  FOR /F "tokens=1,* delims==" %%A IN ('findstr "wrapperUrl" "%WRAPPER_PROPS%"') DO SET WRAPPER_URL=%%B
  ECHO Downloading Maven Wrapper from %WRAPPER_URL% ...
  powershell -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"
)

IF DEFINED JAVA_HOME (
  SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
) ELSE (
  SET JAVA_EXE=java
)

"%JAVA_EXE%" -Dmaven.multiModuleProjectDirectory="%BASE_DIR%" -cp "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*

ENDLOCAL
