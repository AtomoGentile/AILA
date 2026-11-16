# Nothing Glyph SDK 2.0

`nothing-glyph-sdk-2.0.jar` is the unmodified `classes.jar` from the official
Nothing `glyph-matrix-sdk-2.0.aar`. It is used **only for compilation** in shared;
androidApp packages the complete AAR (including resources) from androidApp/libs.

Source: https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit/blob/main/sdk/glyph-matrix-sdk-2.0.aar
Upstream Git blob: 7fdd2e8ec41e6fef2aaa3f31963d06807421dfaf

AILA uses the conventional Glyph Interface on the models enumerated by the SDK.
Glyph Matrix models use the on-screen preview. Android 16+ is required for the
keyless hardware connection. Nothing OS 14/15 use preview only. Connections are
created only when the Nothing widget is composed. Pausing, leaving the view or
finishing the 1.2s sequence turns lights off and closes the session. No background
notifications are intercepted and no device data is uploaded.
