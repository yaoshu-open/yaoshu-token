<div align="center">

![yaoshu-token](yaoshu-token-web/public/logo.png)

# Yaoshu Token（爻樞 Token）

🍥 **AI 大模型閘道與資產管理系統**

<p align="center">
  <a href="./README.zh_CN.md">简体中文</a> |
  <strong>繁體中文</strong> |
  <a href="./README.md">English</a> |
  <a href="./README.fr.md">Français</a> |
  <a href="./README.ja.md">日本語</a>
</p>

<p align="center">
  <a href="https://raw.githubusercontent.com/yaoshu-open/yaoshu-token/main/LICENSE">
    <img src="https://img.shields.io/github/license/yaoshu-open/yaoshu-token?color=brightgreen" alt="license">
  </a>
  <a href="https://github.com/yaoshu-open/yaoshu-token/releases/latest">
    <img src="https://img.shields.io/github/v/release/yaoshu-open/yaoshu-token?color=brightgreen&include_prereleases" alt="release">
  </a>
  <a href="https://hub.docker.com/r/ylyue/yaoshu-token">
    <img src="https://img.shields.io/badge/docker-dockerHub-blue" alt="docker">
  </a>
</p>

<p align="center">
  <a href="#-快速開始">快速開始</a> •
  <a href="#-主要特性">主要特性</a> •
  <a href="#-部署">部署</a> •
  <a href="#-文件">文件</a> •
  <a href="#-幫助支援">幫助</a>
</p>

</div>

## 📝 專案說明

爻樞 Token 受 [new-api](https://github.com/Calcium-Ion/new-api) 啟發，已完全獨立重構為 Java + Vue3 實現。聚合 40+ AI 上游供應商，提供統一 API、使用者管理、計費、限流和管理後台。

**技術棧**：Java 17 + SpringBoot 3.3 + MyBatis-Plus 3.5.14 + [yue-library](https://github.com/yl-yue/yue-library)

> [!IMPORTANT]
> - 本專案僅面向合法授權的 AI API 閘道、組織內部鑑權、多模型管理、用量統計、成本核算和私有化部署場景。
> - 使用者必須合法取得上游 API Key、帳號、模型服務或介面權限，並遵守上游服務條款及適用法律法規。
> - 面向公眾提供生成式人工智慧服務時，使用者應遵守適用監管要求，自行完成所在司法轄區要求的備案、許可、內容安全、實名、日誌留存、稅務和上游授權等合規義務。

---

## 🚀 快速開始

### 環境要求

- **Java** 17+
- **MySQL** ≥ 8.0.28
- **Redis**

### 使用 Docker Compose（推薦）

```bash
# 克隆專案
git clone https://github.com/yaoshu-open/yaoshu-token.git
cd yaoshu-token

# 編輯 docker-compose.yml 設定檔（按需修改資料庫/Redis 等連線資訊）
vim docker-compose.yml

# 啟動服務
docker-compose up -d
```

<details>
<summary><strong>使用 Docker 命令</strong></summary>

```bash
# 拉取最新映像檔
docker pull ylyue/yaoshu-token:latest

# 使用 Docker run
docker run --name yaoshu-token -d --restart always \
  -p 9527:9527 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/yaoshu_token" \
  -e SPRING_DATASOURCE_USERNAME=root \
  -e SPRING_DATASOURCE_PASSWORD=123456 \
  -e SPRING_REDIS_HOST=localhost \
  -e TZ=Asia/Shanghai \
  -v ./logs:/app/logs \
  ylyue/yaoshu-token:latest
```

</details>

---

🎉 部署完成後，訪問 `http://localhost:9527` 即可使用！

> [!WARNING]
> 將本專案作為面向公眾的生成式 AI 服務或 API 轉售服務營運時，使用者應先完成備案、內容安全、實名、日誌留存、稅務、支付和上游授權等合規義務。

---

## 📚 文件

> 📖 文件站點即將上線，暫請參考下方部署指南與 [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions)。

**快速導航：**

| 分類 | 說明 |
|------|------|
| 🚀 部署指南 | 參見 [docker-compose.yml](./docker-compose.yml) 和 [Dockerfile](./Dockerfile) |
| ⚙️ 環境配置 | 參見 [.env.example](./.env.example) 和 `application.yml` |
| 📡 API 文件 | OpenAI 相容 API |
| ❓ 常見問題 | [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions) |

---

## ✨ 主要特性

### 🎨 核心功能

| 特性 | 說明 |
|------|------|
| 🎨 全新 UI | 基於 Vue3 + Element Plus 的現代化使用者介面設計 |
| 🌍 多語言 | 支援簡體中文、繁體中文、英文、法語、日語 |
| 📈 資料看板 | 視覺化控制台與統計分析 |
| 🔒 權限管理 | 令牌分組、模型限制、使用者管理 |

### 💰 授權用量與成本管理

- ✅ 合法授權場景下的內部充值與額度分配（易支付、Stripe、Creem）
- ✅ 組織內按次、按量或快取命中成本核算
- ✅ 支援 OpenAI、Azure、DeepSeek、Claude、Qwen 等模型的快取計費統計
- ✅ 面向內部管理或企業客戶的靈活計費策略配置

### 🔐 授權與安全

- 😈 Discord 授權登入
- 🤖 LinuxDO 授權登入
- 📱 Telegram 授權登入
- 🔑 OIDC 統一認證
- 🔍 Key 額度查詢

### 🚀 進階功能

**API 格式支援：**
- ⚡ OpenAI Responses API
- ⚡ OpenAI Realtime API（含 Azure）
- ⚡ Claude Messages 格式
- ⚡ Google Gemini 格式
- 🔄 Rerank 模型（Cohere、Jina）

**智慧路由：**
- ⚖️ 渠道加權隨機
- 🔄 失敗自動重試
- 🚦 使用者級別模型限流

**格式轉換：**
- 🔄 OpenAI Compatible ⇄ Claude Messages
- 🔄 OpenAI Compatible → Google Gemini
- 🔄 Google Gemini → OpenAI Compatible
- 🔄 思考轉內容功能

---

## 🤖 模型支援

| 模型類型 | 說明 |
|---------|------|
| 🤖 OpenAI-Compatible | OpenAI 相容模型（Chat Completions、Responses、Images、Audio、Embeddings） |
| 🎨 Midjourney-Proxy | Midjourney 圖像生成 |
| 🎵 Suno-API | Suno 音樂生成 |
| 🔄 Rerank | Cohere、Jina |
| 💬 Claude | Messages 格式 |
| 🌐 Gemini | Google Gemini 格式 |
| 🔧 Dify | ChatFlow 模式 |
| 🎯 自訂上游 | 支援配置合法授權的上游介面地址 |

---

## 🚢 部署

> [!TIP]
> **最新版 Docker 映像檔：** `ylyue/yaoshu-token:latest`

### 📋 部署要求

| 組件 | 要求 |
|------|------|
| **資料庫** | MySQL ≥ 8.0.28（Flyway 管理遷移） |
| **快取** | Redis |
| **Java** | 17+（JAR 部署需要） |
| **容器** | Docker / Docker Compose |

### 🔧 部署方式

<details>
<summary><strong>方式 1：Docker Compose（推薦）</strong></summary>

```bash
git clone https://github.com/yaoshu-open/yaoshu-token.git
cd yaoshu-token
docker-compose up -d
```
</details>

<details>
<summary><strong>方式 2：原始碼構建</strong></summary>

```bash
git clone https://github.com/yaoshu-open/yaoshu-token.git
cd yaoshu-token
mvn package -DskipTests
java -jar yaoshu-token-server/target/yaoshu-token-server-*.jar --spring.profiles.active=prod
```
</details>

### ⚠️ 多機部署注意事項

> [!WARNING]
> - **必須設定** `SESSION_SECRET` - 否則登入狀態不一致
> - **公用 Redis 必須設定** `CRYPTO_SECRET` - 否則資料無法解密

---

## 💬 幫助支援

### 📖 資源

| 資源 | 連結 |
|------|------|
| 💬 社群討論 | [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions) |
| 🐛 回報問題 | [GitHub Issues](https://github.com/yaoshu-open/yaoshu-token/issues) |
| 📚 文件 | 即將上線，暫見 [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions) |

### 🤝 貢獻指南

歡迎各種形式的貢獻！

- 🐛 回報 Bug
- 💡 提出新功能
- 📝 改進文件
- 🔧 提交程式碼

---

## 📜 授權條款

本專案基於 [Apache License 2.0](./LICENSE) 開源。

---

<div align="center">

### 💖 感謝使用爻樞 Token

如果這個專案對你有幫助，歡迎給我們一個 ⭐️ Star！

**[GitHub Issues](https://github.com/yaoshu-open/yaoshu-token/issues)** • **[最新發布](https://github.com/yaoshu-open/yaoshu-token/releases)**

<sub>Java 重構 by Yaoshu Token contributors</sub>

</div>
