# KeyScan 密扫

[![Donate](https://img.shields.io/badge/Donate-Buy%20me%20a%20coffee-07C160?style=for-the-badge)](DONATE.md)

KeyScan 是一款非商业开源的 Android 安全工具箱，围绕二维码扫描、二维码生成、密码账本、OTP 双因子认证、随机密码生成和加密 WebDAV 备份构建。

本项目使用 Java + AndroidX 开发，所有敏感数据均以本地加密存储为核心设计目标。

## 功能特性

- 扫描二维码/条形码：基于 CameraX 与 ML Kit，支持连续扫描和历史记录。
- 相册导入识别：从本地图片解析二维码内容。
- 生成二维码：支持文本/链接生成、自定义颜色、圆点、圆角与 Logo。
- 密码账本：保存网站账号和密码，支持搜索、强密码生成和扫码填充。
- OTP 认证器：支持扫码添加、手动添加、30 秒刷新和一键复制验证码。
- 随机密码生成器：支持长度、大小写字母、数字、特殊符号配置。
- 历史记录：支持搜索、编辑、删除、复制和转存到密码账本。
- 数据保险：支持加密导出、恢复和完整性检查。
- WebDAV 同步：支持主/备双 WebDAV 通道，加密备份和恢复数据。
- 外观设置：支持亮色、深色和跟随系统主题。
- 隐藏小游戏：推推拼图、推箱子、俄罗斯方块。

## 技术栈

- 语言：Java
- 最低系统：Android 8.0，API 26
- 目标 SDK：33
- 数据库：Room
- 本地加密：SQLCipher
- 二维码扫描：CameraX + ML Kit Barcode Scanning
- 二维码生成：ZXing
- 网络同步：OkHttp + WebDAV
- 备份加密：AES-256-GCM

## 项目信息

- 应用名：KeyScan
- 包名：`com.secureqr.scanner`
- 当前版本：`1.0.0`
- 当前 `versionCode`：`1`
- 联系邮箱：`tulkun@foxmail.com`

## 构建方式

使用 Android Studio 打开项目根目录，等待 Gradle 同步完成后运行 `app`。

命令行 debug 构建：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
& 'E:\二维码\work\gradle-8.13\gradle-8.13\bin\gradle.bat' assembleDebug
```

命令行 release 构建：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
& 'E:\二维码\work\gradle-8.13\gradle-8.13\bin\gradle.bat' assembleRelease
```

release 签名配置位于 `release/` 目录。签名密钥和密码配置属于敏感文件，已通过 `.gitignore` 排除，不应上传到公开仓库。

## 安全与隐私

- WebDAV 备份数据在本地加密后再上传。
- 密码账本、OTP 密钥等敏感数据本地加密保存。
- 项目不会要求提交 WebDAV 账号、密码、私有服务器地址或签名密钥。
- 隐私说明见 [PRIVACY.md](PRIVACY.md)。
- 安全反馈见 [SECURITY.md](SECURITY.md)。

## 上传 GitHub 前检查

请确认以下文件不会上传：

- `release/keyscan-release.jks`
- `release/keystore.properties`
- `local.properties`
- `.gradle/`
- `build/`
- `app/build/`

当前 `.gitignore` 已经覆盖这些路径。

## 支持项目

如果 KeyScan 对你有帮助，欢迎 [请开发者喝杯咖啡](DONATE.md)。

## 许可

本项目采用 PolyForm Noncommercial License 1.0.0，仅允许非商业用途使用、复制、修改和分发。

任何商业使用、付费服务集成、商业再分发或用于商业产品，均需获得版权所有者单独书面授权。

详情见 [LICENSE](LICENSE)。
