# Contributing to KeyScan

Thanks for your interest in KeyScan.

This project is currently maintained as a noncommercial Android app. Contributions are welcome for bug fixes, translations, documentation, UI polish, and security improvements.

## Development Setup

1. Open the project in Android Studio.
2. Let Gradle sync complete.
3. Build the debug APK with `assembleDebug`.
4. Test changes on a real device when camera, storage, vibration, or WebDAV behavior is involved.

## Code Style

- Keep Java code readable and localized strings in `strings.xml`.
- Do not hardcode user-facing Chinese or English text in Java or XML layouts.
- Keep sensitive values out of source code.
- Do not commit generated build outputs.

## Pull Request Checklist

- The project builds successfully.
- New user-facing text has Chinese and English translations.
- Sensitive files are not included.
- Behavior changes are described clearly.

## License

By contributing, you agree that your contribution will be licensed under the same license as this project: PolyForm Noncommercial License 1.0.0.
