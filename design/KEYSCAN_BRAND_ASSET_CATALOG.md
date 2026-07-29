# KeyScan 品牌图标素材盘点

## 素材来源

### `F:\logos-main\logos-main`

- 通用品牌 SVG：约 1,860 个
- 定位：网站、软件、开发与互联网品牌补充库
- 许可文件：`LICENSE.txt`（CC0）
- 注意：CC0 不代表品牌商标权被放弃；Logo 仅用于用户数据识别

### `F:\KeyScan_Icons\KeyScan_Icons`

成品目录共 480 个 SVG：

| 分类 | 数量 | 用途 |
|---|---:|---|
| `technology` | 200 | 网站、软件、互联网服务 |
| `banking` | 200 | 银行与金融机构 |
| `payment` | 50 | 卡组织与支付品牌 |
| `email` | 30 | 邮箱服务 |

`_sources` 包含约 7,200 个 SVG 以及上游源码和构建文件，不直接加入 APK。

## 重复与授权

- `technology` 中约 196 个图标与 `logos-main` 重叠。
- 全部来源中约有 859 组同名文件。
- 成品 `index.json` 共 480 条，可作为品牌注册表的初始索引。
- 上游来源混合 CC0、MIT 等许可；正式使用时保留来源与许可记录。
- 每个品牌只选择一个正式图标，不同时打包多个版本。

## 首批建议采用的品牌

### 网站与服务

Google、Apple、Microsoft、GitHub、GitLab、Facebook、Instagram、X、LinkedIn、
Netflix、Spotify、Adobe、Dropbox、Steam、Discord、Telegram、YouTube、Reddit、
Slack、Notion、OpenAI、Zoom、WordPress、Shopify、Cloudflare。

### 邮箱

Gmail、Yahoo Mail、iCloud Mail、QQ Mail、Proton Mail、Zoho Mail。

### 支付与卡组织

PayPal、Visa、Mastercard、UnionPay、JCB、American Express、Alipay、Apple Pay、
Google Pay。

## Android 素材检查

首批 40 个候选图标中：

- 37 个不含明显的 SVG 滤镜、遮罩、字体或渐变结构。
- Telegram 包含 `linearGradient`。
- Visa 包含 `linearGradient`。
- JCB 包含 `linearGradient`。

为保持官方颜色、透明背景和跨 Android 版本一致性，建议统一渲染成经过校验的透明
WebP/PNG，而不是直接批量转换成 Android VectorDrawable。

## 当前缺失或需要补齐

### 网站与邮箱

- Amazon 主品牌 Logo
- Outlook 主品牌 Logo

Amazon Pay、Amazon Chime 等不能替代 Amazon 主品牌 Logo。

### 中国常用银行

当前成品库确认存在：

- 中国农业银行：`abchina.svg`
- 交通银行：`bank-of-communications-logo.svg`
- 平安银行：`ping-an-bank-logo.svg`

仍需补齐并核对官方版本：

- 中国工商银行
- 中国建设银行
- 中国银行
- 招商银行
- 中国邮政储蓄银行
- 中信银行
- 中国光大银行
- 中国民生银行
- 浦发银行
- 华夏银行
- 兴业银行
- 北京银行
- 上海银行

## 接入约束

1. 仅使用成品目录中已审核的素材，不直接引用 `_sources`。
2. 网站匹配注册域名或明确别名，不使用简单字符串包含判断。
3. Amazon 所有国家/地区域名统一映射到 `amazon`。
4. 银行优先根据明确银行名称匹配，BIN 只作为辅助。
5. 未匹配品牌时使用稳定的“首字母 + 背景色”备用图标。
6. Logo 使用固定圆角容器、`centerInside`、透明背景，不染色、不裁切。
7. 品牌图标不存入数据库；数据库和备份格式保持不变。
8. 不联网请求第三方 favicon 或 Logo 服务，避免泄露用户保存的网站。

## 推荐接入顺序

1. 补齐 Amazon、Outlook 和中国主要银行。
2. 为首批素材建立唯一 `brandId`。
3. 建立网站域名别名表、邮箱别名表、银行名称别名表和卡组织规则。
4. 将审核后的 SVG 批量渲染成统一画布的透明 WebP/PNG。
5. 实现离线品牌匹配器和统一 Logo 容器。
6. 先接入密码账本，再接入 TOTP，最后接入银行卡。
