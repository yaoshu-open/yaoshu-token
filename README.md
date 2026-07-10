<div align="center">

![yaoshu-token](yaoshu-token-web/public/logo.png)

# Yaoshu Token（爻枢 Token）

🍥 **AI API Gateway & Asset Management System**

<p align="center">
  <a href="./README.zh_CN.md">简体中文</a> |
  <a href="./README.zh_TW.md">繁體中文</a> |
  <strong>English</strong> |
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
  <a href="#-quick-start">Quick Start</a> •
  <a href="#-key-features">Key Features</a> •
  <a href="#-deployment">Deployment</a> •
  <a href="#-documentation">Documentation</a> •
  <a href="#-help-support">Help</a>
</p>

</div>

## 📝 Project Description

Yaoshu Token (爻枢 Token) is inspired by [new-api](https://github.com/Calcium-Ion/new-api) and independently rebuilt with Java + Vue3. It is an AI API gateway that aggregates 40+ upstream AI providers behind a unified API, providing user management, billing, rate limiting, and an admin dashboard.

**Tech Stack**: Java 17 + SpringBoot 3.3 + MyBatis-Plus 3.5.14 + [yue-library](https://github.com/yl-yue/yue-library)

> [!IMPORTANT]
> - This project is intended solely for lawful and authorized AI API gateway, organization-level authentication, multi-model management, usage analytics, cost accounting, and private deployment scenarios.
> - Users must lawfully obtain upstream API keys, accounts, model services, and interface permissions, and must comply with upstream terms of service and applicable laws and regulations.
> - When providing generative AI services to the public, users should comply with applicable regulatory requirements and fulfill all filing, licensing, content safety, real-name verification, log retention, tax, and upstream authorization obligations required by their jurisdiction.

---

## 🚀 Quick Start

### Prerequisites

- **Java** 17+
- **MySQL** ≥ 8.0.28
- **Redis**

### Using Docker Compose (Recommended)

```bash
# Clone the project
git clone https://github.com/yaoshu-open/yaoshu-token.git
cd yaoshu-token

# Edit docker-compose.yml (adjust DB/Redis connection settings as needed)
vim docker-compose.yml

# Start the service
docker-compose up -d
```

<details>
<summary><strong>Using Docker Commands</strong></summary>

```bash
# Pull the latest image
docker pull ylyue/yaoshu-token:latest

# Using Docker run
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

🎉 After deployment is complete, visit `http://localhost:9527` to start using!

> [!WARNING]
> When operating this project as a public generative AI service or API resale service, users should first complete all required filing, licensing, content safety, real-name verification, log retention, tax, payment, and upstream authorization obligations.

---

## 📚 Documentation

> 📖 Documentation site coming soon. In the meantime, see the deployment guide below and [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions).

**Quick Navigation:**

| Category | Description |
|------|------|
| 🚀 Deployment Guide | See [docker-compose.yml](./docker-compose.yml) and [Dockerfile](./Dockerfile) |
| ⚙️ Environment Configuration | See [.env.example](./.env.example) and `application.yml` |
| 📡 API Documentation | OpenAI-compatible API |
| ❓ FAQ | [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions) |

---

## ✨ Key Features

### 🎨 Core Functions

| Feature | Description |
|------|------|
| 🎨 Modern UI | Vue3 + Element Plus based user interface design |
| 🌍 Multi-language | Supports Simplified Chinese, Traditional Chinese, English, French, Japanese |
| 📈 Data Dashboard | Visual console and statistical analysis |
| 🔒 Permission Management | Token grouping, model restrictions, user management |

### 💰 Authorized Usage Accounting and Billing

- ✅ Internal top-up and quota allocation for lawful authorized scenarios (EPay, Stripe, Creem)
- ✅ Organization-level per-request, usage-based, and cache-hit cost accounting
- ✅ Cache billing statistics for OpenAI, Azure, DeepSeek, Claude, Qwen, and supported models
- ✅ Flexible billing policies for internal management or authorized enterprise customers

### 🔐 Authorization and Security

- 😈 Discord authorization login
- 🤖 LinuxDO authorization login
- 📱 Telegram authorization login
- 🔑 OIDC unified authentication
- 🔍 Key quota query usage

### 🚀 Advanced Features

**API Format Support:**
- ⚡ OpenAI Responses API
- ⚡ OpenAI Realtime API (including Azure)
- ⚡ Claude Messages format
- ⚡ Google Gemini format
- 🔄 Rerank Models (Cohere, Jina)

**Intelligent Routing:**
- ⚖️ Channel weighted random
- 🔄 Automatic retry on failure
- 🚦 User-level model rate limiting

**Format Conversion:**
- 🔄 OpenAI Compatible ⇄ Claude Messages
- 🔄 OpenAI Compatible → Google Gemini
- 🔄 Google Gemini → OpenAI Compatible
- 🔄 Thinking-to-content functionality

---

## 🤖 Model Support

| Model Type | Description |
|---------|------|
| 🤖 OpenAI-Compatible | OpenAI compatible models (Chat Completions, Responses, Images, Audio, Embeddings) |
| 🎨 Midjourney-Proxy | Midjourney image generation |
| 🎵 Suno-API | Suno music generation |
| 🔄 Rerank | Cohere, Jina |
| 💬 Claude | Messages format |
| 🌐 Gemini | Google Gemini format |
| 🔧 Dify | ChatFlow mode |
| 🎯 Custom upstream | Supports configuring legally authorized upstream endpoints |

---

## 🚢 Deployment

> [!TIP]
> **Latest Docker image:** `ylyue/yaoshu-token:latest`

### 📋 Deployment Requirements

| Component | Requirement |
|------|------|
| **Database** | MySQL ≥ 8.0.28 (Flyway managed migrations) |
| **Cache** | Redis |
| **Java** | 17+ (for JAR deployment) |
| **Container** | Docker / Docker Compose |

### 🔧 Deployment Methods

<details>
<summary><strong>Method 1: Docker Compose (Recommended)</strong></summary>

```bash
git clone https://github.com/yaoshu-open/yaoshu-token.git
cd yaoshu-token
docker-compose up -d
```
</details>

<details>
<summary><strong>Method 2: Build from Source</strong></summary>

```bash
git clone https://github.com/yaoshu-open/yaoshu-token.git
cd yaoshu-token
mvn package -DskipTests
java -jar yaoshu-token-server/target/yaoshu-token-server-*.jar --spring.profiles.active=prod
```
</details>

### ⚠️ Multi-machine Deployment Considerations

> [!WARNING]
> - **Must set** `SESSION_SECRET` - Otherwise login status inconsistent
> - **Shared Redis must set** `CRYPTO_SECRET` - Otherwise data cannot be decrypted

---

## 💬 Help Support

### 📖 Resources

| Resource | Link |
|------|------|
| 💬 Discussions | [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions) |
| 🐛 Issue Feedback | [GitHub Issues](https://github.com/yaoshu-open/yaoshu-token/issues) |
| 📚 Documentation | Coming soon — see [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions) |

### 🤝 Contribution Guide

Welcome all forms of contribution!

- 🐛 Report Bugs
- 💡 Propose New Features
- 📝 Improve Documentation
- 🔧 Submit Code

---

## 📜 License

This project is licensed under the [Apache License 2.0](./LICENSE).

---

<div align="center">

### 💖 Thank you for using Yaoshu Token

If this project is helpful to you, welcome to give us a ⭐️ Star!

**[GitHub Issues](https://github.com/yaoshu-open/yaoshu-token/issues)** • **[Latest Release](https://github.com/yaoshu-open/yaoshu-token/releases)**

<sub>Java reconstruction by Yaoshu Token contributors</sub>

</div>
