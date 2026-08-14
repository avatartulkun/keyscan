<p align="center">
  <img src="assets/keyscan-icon-512.png" alt="KeyScan" width="120" height="120">
</p>

<h1 align="center">KeyScan</h1>

<p align="center">
  <b>本地优先 · 纯离线可用 · 数据自主掌控</b><br>
  一款安全工具箱：智能扫描、密码账本、TOTP 认证器、安全保险箱、自动填充与加密备份
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-支持-2563EB" alt="Android">
  <img src="https://img.shields.io/badge/iOS-支持-2563EB" alt="iOS">
  <img src="https://img.shields.io/badge/语言-12%20种-25B96F" alt="12 languages">
  <img src="https://img.shields.io/badge/模式-纯离线-FF9800" alt="offline-first">
  <img src="https://img.shields.io/badge/加密-AES--GCM-25B96F" alt="AES-GCM">
</p>

---

## 这是什么

KeyScan 是本地优先的数据安全工具箱，**不需要注册账号、不依赖开发者服务器**，核心功能**纯离线可用**。您的密码、OTP、保险箱资料和备份全部加密保存在您自己的设备上。

> 您的数据，只属于您自己。

## 功能亮点

| | 功能 | 说明 |
|---|---|---|
| 📷 | **智能扫描** | 二维码 / 条形码 / 文字识别（OCR）一键完成，自动识别类型 |
| 🔑 | **密码账本** | 加密保存账号密码，支持分组、排序、过期提醒、Bitwarden 格式导入导出 |
| ⏱️ | **TOTP 认证器** | 动态验证码，支持批量导入，登录页自动填充 OTP 候选 |
| 🛡️ | **安全保险箱** | 证件、银行卡、许可证、恢复密码等敏感资料的加密档案库，支持附件 |
| ✨ | **自动填充** | 登录 / 注册 / 改密全场景，本地匹配凭据，永不自动提交表单 |
| ☁️ | **WebDAV 加密备份** | 坚果云、Koofr、NAS 等，备份恢复全程加密，可验证备份完整性 |
| 📡 | **局域网传输** | 同局域网设备间加密备份迁移、凭据分享 |
| 🌍 | **12 种语言** | 简体中文、繁体中文、English、Deutsch、Français、Español、Italiano、Nederlands、Português(BR)、Русский、日本語、한국어 |

## 安全设计

- **本地加密存储**：数据库与备份使用 AES-GCM 加密，密钥由 PIN 与数据保护密钥共同派生
- **系统级密钥保护**：Android Keystore / iOS Keychain + Secure Enclave
- **生物识别**：指纹 / Face ID / Touch ID 解锁与敏感操作确认
- **敏感内容防护**：复制自动清除、自动锁定、安全窗口保护
- **无广告 · 无追踪**：不集成广告系统，不做行为统计与画像

## 下载

| 平台 | 状态 | 链接 |
|---|---|---|
| Android | 内部测试中 | [Google Play](https://play.google.com/) |
| iOS | 开发中 | 敬请期待 |

## 隐私政策

我们认真对待您的数据：[KeyScan 用户协议与隐私政策](https://avatartulkun.github.io/keyscan/KeyScan-privacy-policy.html)（中英双语）

## 联系与反馈

- 产品主页：[avatartulkun.github.io/keyscan](https://avatartulkun.github.io/keyscan)
- Telegram 群组：[KeyScan Users](https://t.me/keyScanUsers)
- 联系邮箱：keyscan.feedback@zohomail.com

## 许可证

代码许可证待定，敬请关注。
