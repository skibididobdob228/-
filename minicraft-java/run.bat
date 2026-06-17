@echo off
REM Launches MiniCraft on Windows.
set DIR=%~dp0
if not exist "%DIR%target\minicraft.jar" (
  echo Build first: mvn -f "%DIR%pom.xml" package
  exit /b 1
)
java -jar "%DIR%target\minicraft.jar" %*
