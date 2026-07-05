# KeyScan Release Signing

This folder contains local release-signing materials for app-market builds.

Sensitive files are ignored by Git:

- `keyscan-release.jks`
- `keystore.properties`

Important:

- Back up `keyscan-release.jks` and `keystore.properties` securely.
- If this signing key is used to publish the app, future updates usually must use the same key.
- Do not upload the keystore or passwords to public repositories.

Build signed release APK:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
& 'E:\二维码\work\gradle-8.13\gradle-8.13\bin\gradle.bat' assembleRelease
```

Signed APK output:

```text
app/build/outputs/apk/release/KeyScan-v1.0.0-release.apk
```
