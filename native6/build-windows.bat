@echo off
setlocal
cd /d "%~dp0"
echo ============================================
echo AlSafa Native 6 - Windows Debug APK
echo ============================================
if not exist "gradlew.bat" (
  echo ERROR: Gradle wrapper is missing.
  exit /b 1
)
if not exist "gradle\wrapper\gradle-wrapper.jar" (
  echo ERROR: Gradle wrapper JAR is missing.
  exit /b 1
)
if not defined JAVA_HOME (
  if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
  )
)
if defined JAVA_HOME (
  echo Using JDK: %JAVA_HOME%
) else (
  echo JAVA_HOME not set. Android Studio embedded JDK or JDK 17 is required.
)
if not defined ANDROID_HOME (
  if exist "%LOCALAPPDATA%\Android\Sdk\platforms\android-34\android.jar" (
    set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
  )
)
if not defined ANDROID_HOME (
  echo ERROR: Android SDK 34 not found. Open Android Studio SDK Manager.
  echo Install Android SDK Platform 34 and Android SDK Build-Tools.
  exit /b 1
)
if not exist "%ANDROID_HOME%\platforms\android-34\android.jar" (
  echo ERROR: Android SDK Platform 34 is missing from %ANDROID_HOME%
  exit /b 1
)
echo Android SDK: %ANDROID_HOME%
echo Building APK first... log: build-output.log
call gradlew.bat --no-daemon --console=plain :app:assembleDebug > build-output.log 2>&1
set "BUILD_EXIT=%ERRORLEVEL%"
if not "%BUILD_EXIT%"=="0" (
  type build-output.log
  echo.
  echo APK BUILD FAILED. Send build-output.log for diagnosis.
  exit /b 1
)
if not exist "app\build\outputs\apk\debug\app-debug.apk" (
  echo ERROR: Gradle finished without the expected debug APK.
  exit /b 1
)
echo.
echo DEBUG APK COMPILED: %CD%\app\build\outputs\apk\debug\app-debug.apk
echo Running accounting unit tests...
call gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest >> build-output.log 2>&1
set "TEST_EXIT=%ERRORLEVEL%"
if not "%TEST_EXIT%"=="0" (
  type build-output.log
  echo.
  echo UNIT TESTS FAILED. Debug APK is NOT approved for store use.
  echo Send build-output.log and app\build\reports\tests\testDebugUnitTest\index.html
  exit /b 2
)
echo.
echo APK BUILD AND UNIT TESTS PASSED.
echo APK: %CD%\app\build\outputs\apk\debug\app-debug.apk
echo Test report: %CD%\app\build\reports\tests\testDebugUnitTest\index.html
exit /b 0
