<div align="center">

![yaoshu-token](yaoshu-token-web/public/logo.png)

# Yaoshu Token（爻枢 Token）

🍥 **AI 大模型网关与资产管理系统**

<p align="center">
  <strong>简体中文</strong> |
  <a href="./README.zh_TW.md">繁體中文</a> |
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
  <a href="#-快速开始">快速开始</a> •
  <a href="#-主要特性">主要特性</a> •
  <a href="#-部署">部署</a> •
  <a href="#-文档">文档</a> •
  <a href="#-帮助支持">帮助</a>
</p>

</div>

## 📝 项目说明

爻枢 Token 受 [new-api](https://github.com/Calcium-Ion/new-api) 启发，已完全独立重构为 Java + Vue3 实现。聚合 40+ AI 上游供应商，提供统一 API、用户管理、计费、限流和管理后台。

**技术栈**：Java 17 + SpringBoot 3.3 + MyBatis-Plus 3.5.14 + [yue-library](https://github.com/yl-yue/yue-library)

> [!IMPORTANT]
> - 本项目仅面向合法授权的 AI API 网关、组织内部鉴权、多模型管理、用量统计、成本核算和私有化部署场景。
> - 使用者必须合法取得上游 API Key、账号、模型服务或接口权限，并遵守上游服务条款及适用法律法规。
> - 面向公众提供生成式人工智能服务时，使用者应遵守适用监管要求，自行完成所在司法辖区要求的备案、许可、内容安全、实名、日志留存、税务和上游授权等合规义务。

---

## 🚀 快速开始

### 环境要求

- **Java** 17+
- **MySQL** ≥ 8.0.28
- **Redis**

### 使用 Docker Compose（推荐）

```bash
# 克隆项目
git clone https://github.com/yaoshu-open/yaoshu-token.git
cd yaoshu-token

# 编辑 docker-compose.yml 配置文件（按需修改数据库/Redis 等连接信息）
vim docker-compose.yml

# 启动服务
docker-compose up -d
```

<details>
<summary><strong>使用 Docker 命令</strong></summary>

```bash
# 拉取最新镜像
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

🎉 部署完成后，访问 `http://localhost:9527` 即可使用！

> [!WARNING]
> 将本项目作为面向公众的生成式 AI 服务或 API 转售服务运营时，使用者应先完成备案、内容安全、实名、日志留存、税务、支付和上游授权等合规义务。

---

## 📚 文档

> 📖 文档站点即将上线，暂请参考下方部署指南与 [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions)。

**快速导航：**

| 分类 | 说明 |
|------|------|
| 🚀 部署指南 | 参见 [docker-compose.yml](./docker-compose.yml) 和 [Dockerfile](./Dockerfile) |
| ⚙️ 环境配置 | 参见 [.env.example](./.env.example) 和 `application.yml` |
| 📡 API 文档 | OpenAI 兼容 API |
| ❓ 常见问题 | [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions) |

---

## ✨ 主要特性

### 🎨 核心功能

| 特性 | 说明 |
|------|------|
| 🎨 全新 UI | 基于 Vue3 + Element Plus 的现代化用户界面设计 |
| 🌍 多语言 | 支持简体中文、繁体中文、英文、法语、日语 |
| 📈 数据看板 | 可视化控制台与统计分析 |
| 🔒 权限管理 | 令牌分组、模型限制、用户管理 |

### 💰 授权用量与成本管理

- ✅ 合法授权场景下的内部充值与额度分配（易支付、Stripe、Creem）
- ✅ 组织内按次、按量或缓存命中成本核算
- ✅ 支持 OpenAI、Azure、DeepSeek、Claude、Qwen 等模型的缓存计费统计
- ✅ 面向内部管理或企业客户的灵活计费策略配置

### 🔐 授权与安全

- 😈 Discord 授权登录
- 🤖 LinuxDO 授权登录
- 📱 Telegram 授权登录
- 🔑 OIDC 统一认证
- 🔍 Key 额度查询

### 🚀 高级功能

**API 格式支持：**
- ⚡ OpenAI Responses API
- ⚡ OpenAI Realtime API（含 Azure）
- ⚡ Claude Messages 格式
- ⚡ Google Gemini 格式
- 🔄 Rerank 模型（Cohere、Jina）

**智能路由：**
- ⚖️ 渠道加权随机
- 🔄 失败自动重试
- 🚦 用户级别模型限流

**格式转换：**
- 🔄 OpenAI Compatible ⇄ Claude Messages
- 🔄 OpenAI Compatible → Google Gemini
- 🔄 Google Gemini → OpenAI Compatible
- 🔄 思考转内容功能

---

## 🤖 模型支持

| 模型类型 | 说明 |
|---------|------|
| 🤖 OpenAI-Compatible | OpenAI 兼容模型（Chat Completions、Responses、Images、Audio、Embeddings） |
| 🎨 Midjourney-Proxy | Midjourney 图像生成 |
| 🎵 Suno-API | Suno 音乐生成 |
| 🔄 Rerank | Cohere、Jina |
| 💬 Claude | Messages 格式 |
| 🌐 Gemini | Google Gemini 格式 |
| 🔧 Dify | ChatFlow 模式 |
| 🎯 自定义上游 | 支持配置合法授权的上游接口地址 |

---

## 🚢 部署

> [!TIP]
> **最新版 Docker 镜像：** `ylyue/yaoshu-token:latest`

### 📋 部署要求

| 组件 | 要求 |
|------|------|
| **数据库** | MySQL ≥ 8.0.28（Flyway 管理迁移） |
| **缓存** | Redis |
| **Java** | 17+（JAR 部署需要） |
| **容器** | Docker / Docker Compose |

### 🔧 部署方式

<details>
<summary><strong>方式 1：Docker Compose（推荐）</strong></summary>

```bash
git clone https://github.com/yaoshu-open/yaoshu-token.git
cd yaoshu-token
docker-compose up -d
```
</details>

<details>
<summary><strong>方式 2：源码构建</strong></summary>

```bash
git clone https://github.com/yaoshu-open/yaoshu-token.git
cd yaoshu-token
mvn package -DskipTests
java -jar yaoshu-token-server/target/yaoshu-token-server-*.jar --spring.profiles.active=prod
```
</details>

### ⚠️ 多机部署注意事项

> [!WARNING]
> - **必须设置** `SESSION_SECRET` - 否则登录状态不一致
> - **公用 Redis 必须设置** `CRYPTO_SECRET` - 否则数据无法解密

---

## 💬 帮助支持

### 📖 资源

| 资源 | 链接 |
|------|------|
| 💬 社区讨论 | [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions) |
| 🐛 反馈问题 | [GitHub Issues](https://github.com/yaoshu-open/yaoshu-token/issues) |
| 📚 文档 | 即将上线，暂见 [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions) |

### 🤝 贡献指南

欢迎各种形式的贡献！

- 🐛 报告 Bug
- 💡 提出新功能
- 📝 改进文档
- 🔧 提交代码

---

## 📜 许可证

本项目基于 [Apache License 2.0](./LICENSE) 开源。

---

<div align="center">

### 💖 感谢使用爻枢 Token

如果这个项目对你有帮助，欢迎给我们一个 ⭐️ Star！

**[GitHub Issues](https://github.com/yaoshu-open/yaoshu-token/issues)** • **[最新发布](https://github.com/yaoshu-open/yaoshu-token/releases)**

<sub>Java 重构 by Yaoshu Token contributors</sub>

</div>
