# KeyScan GitHub 上传步骤

建议仓库名：

```text
keyscan
```

建议仓库简介：

```text
Noncommercial encrypted QR scanner, password ledger, OTP authenticator, and WebDAV backup toolbox for Android.
```

建议仓库地址：

```text
https://github.com/avatartulkun/keyscan
```

## 1. 创建 GitHub 仓库

在 GitHub 新建仓库：

```text
avatartulkun/keyscan
```

建议不要勾选自动创建 README、LICENSE 或 `.gitignore`，因为本项目已经包含这些文件。

## 2. 本机安装 Git 后上传

在项目根目录执行：

```bash
git init
git add .
git commit -m "Initial open-source release of KeyScan"
git branch -M main
git remote add origin https://github.com/avatartulkun/keyscan.git
git push -u origin main
```

如果仓库已经存在远端：

```bash
git remote set-url origin https://github.com/avatartulkun/keyscan.git
git push -u origin main
```

## 3. 上传前必须确认

不要提交以下敏感或构建文件：

- `release/keyscan-release.jks`
- `release/keystore.properties`
- `local.properties`
- `.gradle/`
- `.idea/`
- `build/`
- `app/build/`
- `*.apk`
- `*.aab`

当前 `.gitignore` 已经配置排除这些文件。

## 4. Release 建议

Release 名称：

```text
KeyScan Android v1.0.0
```

Release APK 文件：

```text
app/build/outputs/apk/release/KeyScan-v1.0.0-release.apk
```

如果只是公开源码，不建议把签名密钥或密码配置作为 Release 附件上传。

## 5. 许可说明

本项目使用 PolyForm Noncommercial License 1.0.0。

允许非商业用途使用、复制、修改和分发；商业用途需要单独书面授权。
