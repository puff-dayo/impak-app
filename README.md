# impak-app

In this repo,

## impak-viewer

This is a multi-platform desktop GUI application to browse, view and export from impak files, written with PySide and built using Nuitka.Make sure Nuitka, Pyside6, impakdecoder and other platform-specific dependencies are present.

Built portable EXE binary for Windows x64 can be downloaded from release [impakViewer_msvc_x64.zip](https://github.com/puff-dayo/impak-app/releases/download/v26.03.19/impakViewer_msvc_x64.zip).

## impak-viewer-android

This is an Android application to browse, view and export from impak files.

Navigate frames using the left/right arrow buttons in the toolbar, or quick-nav by swiping horizontally on the canvas. Tap the leftmost or rightmost 10% edge of the canvas to step one frame in either direction. Double-tap the image to zoom in incrementally, and use pinch-to-zoom for finer scale control; panning with two fingers holding is available when zoomed.

Built APK binary for Android 8.0+, armv8 can be downloaded from release [impakViewer_armv8.apk](https://github.com/puff-dayo/impak-app/releases/download/v26.03.19/impakViewer_armv8.apk).


## impak-decoder Python

This is a decoder-only, lightweight Python package for impak, the image collection format. For encoding and more API or CLI tool usage, check the package [impak](https://github.com/puff-dayo/impak).

Build and install the built wheel with [build.bat](/impak-decoder/build.bat).

## impak-decoder Kotlin

For a Kotlin version of impak decoder, see [ImpakReader.kt](/impak-viewer-android/app/src/main/java/dev/setsuna/impakviewer/decoder/ImpakReader.kt).
