@echo off
setlocal

set SCRIPT1=viewer.py
set APPNAME1=impakViewer

uv run --active python -m nuitka ^
    --standalone ^
    --enable-plugin=pyside6 ^
    --windows-console-mode=attach ^
    --include-data-file=.\icon.png=icon.png ^
    --windows-icon-from-ico=.\icon.png ^
    --output-filename=%APPNAME1%.exe ^
    --output-dir=build ^
    --follow-imports ^
    %SCRIPT1%

if errorlevel 1 (
    echo.
    echo Build failed.
    exit /b 1
)

echo.
echo Build complete.

endlocal