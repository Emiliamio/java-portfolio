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

**面试要点**：学生机 / 轻量应用服务器每月 30-60 元，足够跑这三个项目。

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
AI_MODEL=claude-sonnet-5-20251001
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

## 面试怎么聊这套部署

| 面试官问 | 你怎么答 |
|----------|----------|
| "你的项目怎么部署的？" | 我用 Docker Compose 把 MySQL、Redis、两个 Spring Boot 应用编排在一起，一条 `docker compose up -d` 全部启动。配置文件用 `.env` 管理敏感信息，不提交到 Git。 |
| "Dockerfile 怎么写？" | 多阶段构建：`maven:3.9` 镜像编译 → `jre-alpine` 镜像运行，最终镜像只有 200MB 左右。用非 root 用户启动，加了 HEALTHCHECK 探针。 |
| "安全组怎么配的？" | MySQL 端口只对内网开放，8080/8081 对外开放。SSH 用密钥登录，禁用密码。 |
| "数据持久化怎么做的？" | MySQL 和 Redis 数据都挂载了 Docker Volume，容器删了数据还在。建表 SQL 通过 `docker-entrypoint-initdb.d` 在 MySQL 容器首次启动时自动执行。 |
| "怎么更新代码？" | `git pull` → `docker compose up -d --build`，自动重新编译和重启，整个过程不停机。 |

---

## License

MIT
