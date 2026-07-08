> Ce document est une traduction AI basée sur la version chinoise simplifiée. Les relectures humaines sont les bienvenues via PR.
> This document is AI-translated from the Simplified Chinese version. Human proofreading PRs are welcome.

<div align="center">

![yaoshu-token](yaoshu-token-web/public/logo.png)

# Yaoshu Token（爻枢 Token）

🍥 **Passerelle API IA & Système de Gestion d'Actifs**

<p align="center">
  <a href="./README.zh_CN.md">简体中文</a> |
  <a href="./README.zh_TW.md">繁體中文</a> |
  <a href="./README.md">English</a> |
  <strong>Français</strong> |
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
  <a href="#-demarrage-rapide">Démarrage</a> •
  <a href="#-fonctionnalites">Fonctionnalités</a> •
  <a href="#-deploiement">Déploiement</a> •
  <a href="#-documentation">Documentation</a> •
  <a href="#-support">Support</a>
</p>

</div>

## 📝 Description

Yaoshu Token（爻枢 Token）est inspiré par [new-api](https://github.com/Calcium-Ion/new-api) et entièrement reconstruit de manière indépendante en Java + Vue3. C'est une passerelle API IA qui agrège plus de 40 fournisseurs IA en amont derrière une API unifiée.

**Stack technique** : Java 17 + SpringBoot 3.3 + MyBatis-Plus 3.5.14 + [yue-library](https://yue.library.dev)

> [!IMPORTANT]
> Ce projet est destiné exclusivement aux scénarios de passerelle API IA autorisée, d'authentification organisationnelle, de gestion multi-modèles, d'analyse d'utilisation, de comptabilité des coûts et de déploiement privé. Les utilisateurs doivent obtenir légalement les clés API, comptes et autorisations en amont et se conformer aux conditions de service applicables.

---

## 🚀 Démarrage Rapide

### Prérequis

- **Java** 17+
- **MySQL** ≥ 8.0.28
- **Redis**

### Avec Docker Compose (Recommandé)

```bash
git clone https://github.com/yaoshu-open/yaoshu-token.git
cd yaoshu-token
docker-compose up -d
```

<details>
<summary><strong>Commande Docker</strong></summary>

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

🎉 Après le déploiement, visitez `http://localhost:9527`

---

## 📚 Documentation

> 📖 Site de documentation à venir. En attendant, consultez le guide de déploiement ci-dessous et [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions).

| Catégorie | Description |
|------|------|
| 🚀 Déploiement | Voir [docker-compose.yml](./docker-compose.yml) et [Dockerfile](./Dockerfile) |
| ⚙️ Configuration | Voir [.env.example](./.env.example) et `application.yml` |
| 📡 API | API compatible OpenAI |
| ❓ FAQ | [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions) |

---

## ✨ Fonctionnalités Clés

### 🎨 Fonctions Principales

| Fonctionnalité | Description |
|------|------|
| 🎨 UI Moderne | Interface Vue3 + Element Plus moderne |
| 🌍 Multilingue | Chinois simplifié/traditionnel, Anglais, Français, Japonais |
| 📈 Tableau de bord | Console visuelle et analyses |
| 🔒 Gestion des droits | Groupes de tokens, restrictions de modèles, gestion utilisateurs |

### 💰 Comptabilité et Facturation

- ✅ Rechargement interne et allocation de quotas (EPay, Stripe, Creem)
- ✅ Comptabilité par requête, par utilisation et par cache
- ✅ Statistiques de facturation cache pour OpenAI, Azure, DeepSeek, Claude, Qwen

### 🔐 Autorisation et Sécurité

- 😈 Connexion Discord
- 🤖 Connexion LinuxDO
- 📱 Connexion Telegram
- 🔑 Authentification OIDC

### 🚀 Fonctionnalités Avancées

- ⚡ OpenAI Responses API / Realtime API / Claude Messages / Google Gemini
- ⚖️ Routage pondéré aléatoire des canaux
- 🔄 Nouvelle tentative automatique en cas d'échec
- 🔄 Conversion de formats (OpenAI ⇄ Claude, OpenAI → Gemini, Gemini → OpenAI)

---

## 🤖 Modèles Supportés

| Type | Description |
|---------|------|
| 🤖 OpenAI-Compatible | Chat Completions, Responses, Images, Audio, Embeddings |
| 🎨 Midjourney-Proxy | Génération d'images Midjourney |
| 🎵 Suno-API | Génération musicale Suno |
| 🔄 Rerank | Cohere, Jina |
| 💬 Claude | Format Messages |
| 🌐 Gemini | Format Google Gemini |
| 🔧 Dify | Mode ChatFlow |
| 🎯 Amont personnalisé | Points de terminaison amont configurables |

---

## 🚢 Déploiement

> [!TIP]
> **Dernière image Docker :** `ylyue/yaoshu-token:latest`

### 📋 Prérequis

| Composant | Exigence |
|------|------|
| **Base de données** | MySQL ≥ 8.0.28 (migrations Flyway) |
| **Cache** | Redis |
| **Java** | 17+ (pour déploiement JAR) |
| **Conteneur** | Docker / Docker Compose |

### 🔧 Méthodes

<details>
<summary><strong>Docker Compose (Recommandé)</strong></summary>

```bash
git clone https://github.com/yaoshu-open/yaoshu-token.git
cd yaoshu-token
docker-compose up -d
```
</details>

<details>
<summary><strong>Compilation depuis les sources</strong></summary>

```bash
git clone https://github.com/yaoshu-open/yaoshu-token.git
cd yaoshu-token
mvn package -DskipTests
java -jar yaoshu-token-server/target/yaoshu-token-server-*.jar --spring.profiles.active=prod
```
</details>

---

## 💬 Support

| Ressource | Lien |
|------|------|
| 💬 Discussions | [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions) |
| 🐛 Rapports de bugs | [GitHub Issues](https://github.com/yaoshu-open/yaoshu-token/issues) |
| 📚 Documentation | À venir — voir [GitHub Discussions](https://github.com/yaoshu-open/yaoshu-token/discussions) |

---

## 📜 Licence

Ce projet est publié sous la [Licence Apache 2.0](./LICENSE).

---

<div align="center">

### 💖 Merci d'utiliser Yaoshu Token

**[GitHub Issues](https://github.com/yaoshu-open/yaoshu-token/issues)** • **[Dernière version](https://github.com/yaoshu-open/yaoshu-token/releases)**

</div>
