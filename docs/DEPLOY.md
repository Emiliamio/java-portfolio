# 部署指南 — 从零到云服务器上线

本指南覆盖从购买服务器到 Docker Compose 一键启动的全流程。

---

## 前置要求

- 一台云服务器（阿里云/腾讯云/HUAWEI CLOUD，最低 1 核 2G）
- 操作系统：Ubuntu 22.04 LTS（推荐）或 CentOS 7+
- 你的本地电脑已安装 `git`

---

## 第一步：购买与配置云服务器

### 1.1 购买服务器

| 云厂商 | 入口 | 推荐配置 |
|--------|------|----------|
| 阿里云 | [ecs.console.aliyun.com](https://ecs.console.aliyun.com) | 共享型 1vCPU 2GB，CentOS/Ubuntu |
| 腾讯云 | [console.cloud.tencent.com/cvm](https://console.cloud.tencent.com/cvm) | 轻量应用服务器 2 核 2G |

**配置建议**：轻量应用服务器或标准云服务器每月 30-60 元，足够平稳运行本系统。

### 1.2 配置安全组（防火墙）

在云控制台 **安全组** 中添加入方向规则：

| 端口 | 协议 | 用途 |
|------|------|------|
| 22 | TCP | SSH 远程登录 |
| 8080 | TCP | 项目一：AuditVault 日志审计系统 |
| 8081 | TCP | 项目三：Nexus AI 分析助手 |
| 3306 | TCP | MySQL（建议只对 127.0.0.1 开放）|

### 1.3 SSH 登录

```bash
ssh root@你的服务器公网IP
```

---

## 第二步：服务器环境安装

### 2.1 更新系统 + 安装 Docker

```bash
# Ubuntu
sudo apt update && sudo apt upgrade -y

# 安装 Docker（官方脚本，一行搞定）
curl -fsSL https://get.docker.com | sudo bash

# 把当前用户加入 docker 组（避免每次加 sudo）
sudo usermod -aG docker $USER

# 重新登录使生效
exit
ssh root@你的服务器公网IP
```

### 2.2 安装 Docker Compose

```bash
sudo apt install docker-compose-plugin -y

# 验证
docker compose version
```

### 2.3 安装 Git + 克隆项目

```bash
git clone https://github.com/你的用户名/java-portfolio.git
cd java-portfolio
```

---

## 第三步：配置环境变量

```bash
# 复制模板
cp .env.example .env

# 编辑 .env，填入真实的 API Key
vim .env
```

`.env` 文件内容：
```ini
MYSQL_ROOT_PASSWORD=替换成安全密码
AI_API_KEY=sk-ant-你的真实Key
AI_API_URL=https://api.anthropic.com/v1/messages
AI_MODEL=claude-sonnet-5
```

---

## 第四步：一键启动所有服务

```bash
docker compose up -d
```

**第一次启动** Docker 需要下载基础镜像（约 5-10 分钟），后续启动只需要几十秒。

### 查看运行状态

```bash
docker compose ps
```

应该看到 4 个容器都是 `Up` 状态：

```
NAME            STATUS
audit-mysql     Up (healthy)
audit-redis     Up (healthy)
audit-backend   Up
audit-ai        Up
```

### 查看日志

```bash
# 所有服务
docker compose logs -f

# 只看某个服务
docker compose logs -f log-audit
docker compose logs -f log-ai
```

---

## 第五步：验证部署

在浏览器访问：

| 服务 | 地址 |
|------|------|
| AuditVault 日志查询 | `http://公网IP:8080` |
| AuditVault 统计面板 | `http://公网IP:8080/dashboard.html` |
| Nexus AI 分析助手 | `http://公网IP:8081` |

---

## 常用运维命令

```bash
# 停止所有服务
docker compose down

# 停止并删除数据卷（重置数据库！）
docker compose down -v

# 重新构建某个服务（代码更新后）
docker compose up -d --build log-audit

# 进入 MySQL 容器
docker exec -it audit-mysql mysql -u root -p log_audit

# 查看 MySQL 数据
docker volume ls | grep audit

# 查看容器资源占用
docker stats
```

---

## 进阶：Jenkins / GitHub Actions 自动部署（选做）

如果你想让每次 push 代码自动部署到服务器，可以用 GitHub Actions：

```yaml
# .github/workflows/deploy.yml
name: Deploy
on:
  push:
    branches: [main]
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Deploy to server
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: root
          key: ${{ secrets.SERVER_SSH_KEY }}
          script: |
            cd /opt/java-portfolio
            git pull
            docker compose up -d --build
```

---

## 生产级容器化部署的核心设计考量

| 场景与考量 | 架构设计与实现方案 |
|----------|----------|
| 容器编排与服务拓扑 | 使用 Docker Compose 将 MySQL 8、Redis 7、AuditVault 与 Nexus AI 微服务编排在统一隔离网络中，一条 `docker compose up -d` 即可拉起完整拓扑。敏感配置通过 `.env` 注入，严格不纳入版本控制。 |
| Dockerfile 瘦身与安全 | 基于 Amazon Corretto Alpine 镜像构建，使用非 root 用户运行，内嵌 HEALTHCHECK 容器探针，镜像体积保持极致轻量。 |
| 网络隔离与安全组 | 数据库与缓存端口严格限制在 Docker 内网中，外部仅暴露业务入口端口与反向代理端口，SSH 强制使用 ED25519/RSA 密钥认证并禁用密码。 |
| 数据高可用与持久化 | MySQL 与 Redis 均挂载命名 Docker Volume，确保容器生命周期与数据解耦。初始建表脚本通过 `docker-entrypoint-initdb.d` 在首次启动时自动执行。 |
| 持续交付与滚动更新 | 支持 CI/CD 自动化流水线，通过 `docker compose up -d --build` 实现无缝容器重构与更新。 |

---

## License

MIT
