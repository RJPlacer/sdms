@echo off
setlocal
set "ATHLETE_TRACK_APP=%~dp0AthleteTrack-1.7.0\Athlete Track\Athlete Track.exe"
if exist "%ATHLETE_TRACK_APP%" (
    start "" /d "%~dp0AthleteTrack-1.7.0\Athlete Track" "%ATHLETE_TRACK_APP%"
    exit /b 0
)
echo The standalone Athlete Track 1.7.0 app has not been built in this checkout.
echo Run the Windows installer from dist, or use mvn javafx:run with JDK 17 or newer.
pause
