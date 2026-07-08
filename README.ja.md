> このドキュメントは中国語簡体字版をAI翻訳したものです。校正PRを歓迎します。
> This document is AI-translated from the Simplified Chinese version. Human proofreading PRs are welcome.

<div align="center">

![yaoshu-token](yaoshu-token-web/public/logo.png)

# Yaoshu Token（爻枢 Token）

🍥 **AI API ゲートウェイ & アセット管理システム**

<p align="center">
  <a href="./README.zh_CN.md">简体中文</a> |
  <a href="./README.zh_TW.md">繁體中文</a> |
  <a href="./README.md">English</a> |
  <a href="./README.fr.md">Français</a> |
  <strong>日本語</strong>
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
  <a href="#-クイックスタート">クイックスタート</a> •
  <a href="#-主な機能">主な機能</a> •
  <a href="#-デプロイ">デプロイ</a> •
  <a href="#-ドキュメント">ドキュメント</a> •
  <a href="#-サポート">サポート</a>
</p>

</div>

## 📝 プロジェクト概要

Yaoshu Token（爻枢 Token）は [new-api](https://github.com/Calcium-Ion/new-api) にインスピレーションを受けており、Java + Vue3 で完全に独立して再構築されています。40以上の AI プロバイダーを統合 API の背後に集約する AI API ゲートウェイです。

**技術スタック**：Java 17 + SpringBoot 3.3 + MyBatis-Plus 3.5.14 + [yue-library](https://yue.library.dev)

> [!IMPORTANT]
> 本プロジェクトは、合法的に認可された AI API ゲートウェイ、組織内認証、マルチモデル管理、使用量統計、コスト計算、プライベートデプロイメントシナリオにのみ使用されます。ユーザーは合法的に上流の API キー、アカウント、モデルサービスを取得し、該当する利用規約を遵守する必要があります。

---

## 🚀 クイックスタート

### 前提条件

- **Java** 17+
- **MySQL** ≥ 8.0.28
- **Redis**

### Docker Compose（推奨）

```bash
git clone https://github.com/yaoshu-open/yaoshu-token.git
cd yaoshu-token
docker-compose up -d
```

<details>
<summary><strong>Docker コマンド</strong></summary>

```bash
docker pull ylyue/yaoshu-token:latest

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

🎉 デプロイ後、`http://localhost:9527` にアクセス

---

## 📚 ドキュメント

> 📖 ドキュメントサイトは近日公開予定です。それまでは以下のデプロイガイドと [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions) をご参照ください。

| カテゴリ | 説明 |
|------|------|
| 🚀 デプロイ | [docker-compose.yml](./docker-compose.yml) と [Dockerfile](./Dockerfile) を参照 |
| ⚙️ 設定 | [.env.example](./.env.example) と `application.yml` を参照 |
| 📡 API | OpenAI 互換 API |
| ❓ FAQ | [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions) |

---

## ✨ 主な機能

### 🎨 コア機能

| 機能 | 説明 |
|------|------|
| 🎨 モダン UI | Vue3 + Element Plus ベースのユーザーインターフェース |
| 🌍 多言語 | 簡体字中国語、繁体字中国語、英語、フランス語、日本語 |
| 📈 ダッシュボード | 可視化コンソールと統計分析 |
| 🔒 権限管理 | トークングループ、モデル制限、ユーザー管理 |

### 💰 課金とコスト管理

- ✅ 内部チャージとクォータ割り当て（EPay、Stripe、Creem）
- ✅ リクエスト単位、使用量単位、キャッシュヒットのコスト計算
- ✅ OpenAI、Azure、DeepSeek、Claude、Qwen のキャッシュ課金統計

### 🔐 認証とセキュリティ

- 😈 Discord ログイン
- 🤖 LinuxDO ログイン
- 📱 Telegram ログイン
- 🔑 OIDC 統一認証

### 🚀 高度な機能

- ⚡ OpenAI Responses API / Realtime API / Claude Messages / Google Gemini
- ⚖️ チャネル加重ランダムルーティング
- 🔄 失敗時の自動リトライ
- 🔄 フォーマット変換（OpenAI ⇄ Claude、OpenAI → Gemini、Gemini → OpenAI）

---

## 🤖 対応モデル

| タイプ | 説明 |
|---------|------|
| 🤖 OpenAI-Compatible | Chat Completions、Responses、Images、Audio、Embeddings |
| 🎨 Midjourney-Proxy | Midjourney 画像生成 |
| 🎵 Suno-API | Suno 音楽生成 |
| 🔄 Rerank | Cohere、Jina |
| 💬 Claude | Messages 形式 |
| 🌐 Gemini | Google Gemini 形式 |
| 🔧 Dify | ChatFlow モード |
| 🎯 カスタム上流 | 合法的に認可された上流エンドポイントの設定をサポート |

---

## 🚢 デプロイ

> [!TIP]
> **最新 Docker イメージ：** `ylyue/yaoshu-token:latest`

### 📋 要件

| コンポーネント | 要件 |
|------|------|
| **データベース** | MySQL ≥ 8.0.28（Flyway マイグレーション管理） |
| **キャッシュ** | Redis |
| **Java** | 17+（JAR デプロイ用） |
| **コンテナ** | Docker / Docker Compose |

### 🔧 方法

<details>
<summary><strong>Docker Compose（推奨）</strong></summary>

```bash
git clone https://github.com/yaoshu-open/yaoshu-token.git
cd yaoshu-token
docker-compose up -d
```
</details>

<details>
<summary><strong>ソースからビルド</strong></summary>

```bash
git clone https://github.com/yaoshu-open/yaoshu-token.git
cd yaoshu-token
mvn package -DskipTests
java -jar yaoshu-token-server/target/yaoshu-token-server-*.jar --spring.profiles.active=prod
```
</details>

---

## 💬 サポート

| リソース | リンク |
|------|------|
| 💬 ディスカッション | [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions) |
| 🐛 バグ報告 | [GitHub Issues](https://github.com/yaoshu-open/yaoshu-token/issues) |
| 📚 ドキュメント | 近日公開 — [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions) 参照 |

---

## 📜 ライセンス

本プロジェクトは [Apache License 2.0](./LICENSE) に基づいて公開されています。

---

<div align="center">

### 💖 Yaoshu Token をご利用いただきありがとうございます

**[GitHub Issues](https://github.com/yaoshu-open/yaoshu-token/issues)** • **[最新リリース](https://github.com/yaoshu-open/yaoshu-token/releases)**

</div>
