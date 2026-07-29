# KeyScan English-baseline audit

Audit date: 2026-07-20

## Verified results

- User-visible hard-coded Chinese in `res/layout`, `res/menu`, and `res/xml`: **0**.
- Base resources: **2199** names.
- English resources: **2199** names.
- Base resource names missing from `values-en`: **0**.
- String format-placeholder mismatches between base and English: **0**.
- Vault schema explicitly defines 45 field keys and all 45 are mapped to localized labels.
- `compileKeyscanDebugJavaWithJavac`: **BUILD SUCCESSFUL**.
- `assembleKeyscanDebug`: **BUILD SUCCESSFUL**.
- `testKeyscanDebugUnitTest`: **NO-SOURCE** (the project has no unit-test sources for this variant).

## Deliberately retained Chinese literals

The remaining Java matches are not direct user-interface copy. They are retained to preserve behavior and compatibility:

- `VaultFormSchema`: raw legacy schema labels and hints. Rendering goes through `sectionTitle`, `fieldLabel`, and `fieldHint`; all explicitly defined field keys have localized labels.
- `PasswordNoteFragment`, `MainActivity`, `AppearanceFragment`, `QRGenerator`: aliases for previously saved Chinese labels and settings. Removing them would break old local data/preferences.
- `OcrScanFragment`, `KeyScanAutofillService`, `VaultRecordIcons`: Chinese OCR, form-recognition, and provider-name keywords. They are input recognition vocabulary, not fixed interface text.
- `FieldMappingEngine`, `FieldMappingFragment`, `VaultImportPreviewMapper`, and `VaultImportPreviewItem`: stable migration mapping keys and legacy fallback values. The migration UI renders localized labels.
- `MigrationDemoDataProvider`: development/demo fixture content; it is not referenced by the production UI.
- `SecurityAuditLog`, `SecuritySettings`, `SecurityBackupPasswordFragment`, `LegacyWebDavBackupAdapter`, and selected `SettingsFragment` calls: local internal audit-event text. No current screen reads or displays this preference store; keeping old event wording preserves existing log continuity.
- `AppDatabase`: one legacy SQL migration fallback title. It is embedded in an old schema migration and is retained to avoid changing migration semantics.
- `SettingsFragment`: Chinese `文件` is an error-classification keyword used to recognize older exception messages.

Escaped Unicode was audited separately. Remaining escapes are BOM handling, legacy algorithm aliases, or recognition/compatibility keywords; escaped user-visible clipboard and password-menu text was localized.

## APK

- Variant: `keyscanDebug`
- Package: `com.secureqr.scanner`
- Version: `1.0.0` (`versionCode 26`)
- Output: `app/build/outputs/apk/keyscan/debug/KeyScan-v1.0.0-keyscanDebug.apk`
- Size: 88,346,723 bytes
- SHA-256: `01304B34348971431A7622C1A5D253650AA45637E7A2265924480351D30AA01B`
- APK metadata confirms both `en` and `zh-CN` resource configurations.
- APK signature verification: passed (v2 and v3; one KeyScan RSA signer).
