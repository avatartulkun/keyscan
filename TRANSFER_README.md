# KeyScan 当前开发迁移包

整理时间：2026-07-29（Asia/Shanghai）

本目录只保留继续开发所需的当前文件，不包含旧版本、历史备份、Git 历史、构建缓存、调试截图、对话记录或临时测试文件。

## 当前版本

- 应用 ID：`com.secureqr.scanner`
- 版本名：`1.0.0`
- versionCode：`30`
- 最新正式 APK：`Artifacts/KeyScan-v1.0.0-keyscanRelease-latest.apk`
- APK 构建时间：2026-07-29 12:12:44
- APK SHA-256：`1CCC1E8C1B3416559F8918873B88D70398889E143C0844A939E6DCBBD7074CDA`

## 换机器后

1. 使用 Android Studio 打开本目录。
2. 安装 JDK 17 和 Android SDK；项目使用 compileSdk 36、minSdk 26、targetSdk 33。
3. 等待 Gradle 同步。Android Studio 会为新机器生成 `local.properties`，不要从旧机器复制该文件。
4. Debug 构建：`.\gradlew.bat assembleKeyscanDebug`
5. Release 构建：`.\gradlew.bat assembleKeyscanRelease`

Release 构建依赖 `release` 目录中的原始签名材料。该目录含敏感密钥和口令，不应公开上传或分享；请额外离线备份。只有使用这套原签名生成的 APK 才能覆盖安装已有正式版本。

当前构建输出被配置到 `build-release-output/app`。首次构建后该目录会自动生成，不属于源代码。
