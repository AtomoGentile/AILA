@echo off
setlocal
cd /d "%~dp0"
echo.
echo Anteprima locale AILA - nessuna installazione della PWA stabile.
echo Apri http://localhost:8765 nel browser dopo l'avvio del server.
echo Per fermarlo, premi Ctrl+C in questa finestra.
echo.
py -3 --version >nul 2>&1
if not errorlevel 1 (
    py -3 -m http.server 8765 --bind 127.0.0.1
    goto fine
)
python --version >nul 2>&1
if not errorlevel 1 (
    python -m http.server 8765 --bind 127.0.0.1
    goto fine
)
echo Python 3 non trovato. Installalo da https://www.python.org/downloads/windows/
echo Poi riapri questo file.
:fine
pause
