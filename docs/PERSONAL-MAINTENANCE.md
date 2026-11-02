# Personal maintenance distribution

Version 0.3.10 (versionCode 130) starts a user-owned signing identity for this local maintenance copy.
The package remains `com.shihab.diplay`. It cannot overwrite an installation signed by the upstream
Private Beta certificate. The user approved a one-time migration; back up settings before removing
the old installation. Later versions signed by this same personal key can update the personal build.

Pinned personal release certificate SHA-256:

```text
7fb6498ca90f59269943ae7627f6759580672a1d4091029ce43a48e11793288b
```

`verifyReleaseSigningIdentity` still requires an unlocked private-key entry and this exact certificate.
Do not remove that check or substitute a debug keystore. Keep the release key, its password and runtime
CarPlay identity outside the source tree. The Android signing key and the CarPlay MFi identity serve
different purposes.

Use the documented `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD` and `DIPLAY_AUTH_ASSETS_DIR` environment variables. Increase versionCode for
future maintenance updates and preserve this signing identity. Upstream GitHub release APKs use a
different certificate; turn off their automatic update checks in the personal installation and update
with locally maintained, same-key APKs.

The initial maintenance change preserves pending video on a repeated Surface binding and keeps a
healthy decoder reference chain usable during picture refresh. JVM and build checks do not establish
physical CarPlay stability or a sustained 60 fps on the head unit.
