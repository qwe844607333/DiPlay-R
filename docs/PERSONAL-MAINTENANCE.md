# Personal maintenance distribution

Version 0.3.10 (versionCode 130) starts a user-owned signing identity. The package remains
`com.shihab.diplay`. The user approved a one-time migration from the differently signed upstream APK.
Later same-key maintenance builds can update this personal installation.

Pinned personal certificate SHA-256:
`7fb6498ca90f59269943ae7627f6759580672a1d4091029ce43a48e11793288b`

The build still checks an unlocked private-key entry and that exact certificate. Never remove the
certificate check or substitute a debug key. Keep all signing credentials and the CarPlay identity
outside source control. The Android signing key and CarPlay MFi identity are different credentials.

Use ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD and
DIPLAY_AUTH_ASSETS_DIR for release builds. Increase versionCode for future updates. Upstream releases
use a different certificate; disable automatic upstream update checks in the personal installation.

The video fixes preserve queued frames on a repeated Surface binding and preserve a healthy decoder
reference chain on picture refresh. Build and JVM test success do not establish physical CarPlay
stability or sustained 60 fps on the head unit.
