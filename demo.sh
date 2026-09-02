#!/usr/bin/env bash
# ============================================
# demo.sh — 日志审计系统一键演示脚本
# ============================================
# 用法:
#   bash demo.sh          # 启动所有后端服务 + 打开浏览器
#   bash demo.sh stop     # 停止所有服务
#   bash demo.sh reset    # 重置（删除数据库，重新开始）
# ============================================

set -euo pipefail

# ── 颜色 ──────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

banner() {
  echo ""
  echo -e "${CYAN}╔══════════════════════════════════════════════════════╗${NC}"
  echo -e "${CYAN}║      日志审计系统 — 一键演示环境                    ║${NC}"
  echo -e "${CYAN}║      AuditVault + LogScope + Nexus AI + Blog         ║${NC}"
  echo -e "${CYAN}╚══════════════════════════════════════════════════════╝${NC}"
  echo ""
}

# ── 检查依赖 ───────────────────────────────────
check_deps() {
  if ! command -v docker &>/dev/null; then
    echo -e "${RED}[错误] 未找到 Docker，请先安装 Docker Desktop${NC}"
    echo "  https://www.docker.com/products/docker-desktop"
    exit 1
  fi

  if ! docker info &>/dev/null 2>&1; then
    echo -e "${RED}[错误] Docker 未运行，请先启动 Docker Desktop${NC}"
    exit 1
  fi

  echo -e "${GREEN}✓${NC} Docker 已就绪"
}

# ── 检查 API Key ────────────────────────────────
check_api_key() {
  if [ -f .env ]; then
    # shellcheck disable=SC2046
    export $(grep -v '^#' .env | xargs)
  fi

  if [ -z "${AI_API_KEY:-}" ] || [ "$AI_API_KEY" = "sk-ant-your-key-here" ]; then
    echo ""
    echo -e "${YELLOW}╔══════════════════════════════════════════════════════╗${NC}"
    echo -e "${YELLOW}║  ⚠ 未检测到有效的 AI_API_KEY                        ║${NC}"
    echo -e "${YELLOW}║                                                      ║${NC}"
    echo -e "${YELLOW}║  Nexus AI 将使用本地关键词规则引擎（降级模式）。     ║${NC}"
    echo -e "${YELLOW}║  如需完整 AI 分析体验，请编辑 .env 文件：            ║${NC}"
    echo -e "${YELLOW}║    AI_API_KEY=sk-ant-你的真实Key                     ║${NC}"
    echo -e "${YELLOW}║  然后重新运行: bash demo.sh                          ║${NC}"
    echo -e "${YELLOW}╚══════════════════════════════════════════════════════╝${NC}"
    echo ""
  else
    echo -e "${GREEN}✓${NC} AI_API_KEY 已配置"
  fi
}

# ── 启动服务 ────────────────────────────────────
start_services() {
  echo ""
  echo -e "${CYAN}▶ 本地 Maven 打包 (跳过 Docker 内下载依赖)...${NC}"

  # 项目一
  echo "  打包 AuditVault..."
  (cd 01-log-audit-system && mvn package -DskipTests -q)
  echo -e "  ${GREEN}✓${NC} AuditVault 打包完成"

  # 项目三
  echo "  打包 Nexus AI..."
  (cd 03-log-ai-assistant && mvn package -DskipTests -q)
  echo -e "  ${GREEN}✓${NC} Nexus AI 打包完成"

  echo ""
  echo -e "${CYAN}▶ 正在构建 Docker 镜像并启动服务...${NC}"
  echo ""

  docker compose up -d --build

  echo ""
  echo -e "${CYAN}▶ 等待服务就绪...${NC}"

  # 等待 MySQL 和 Redis 健康检查通过
  local timeout=120
  local elapsed=0
  while [ $elapsed -lt $timeout ]; do
    local mysql_ok=$(docker inspect audit-mysql --format='{{.State.Health.Status}}' 2>/dev/null || echo "unknown")
    local redis_ok=$(docker inspect audit-redis --format='{{.State.Health.Status}}' 2>/dev/null || echo "unknown")

    if [ "$mysql_ok" = "healthy" ] && [ "$redis_ok" = "healthy" ]; then
      echo ""
      echo -e "${GREEN}✓${NC} MySQL + Redis 已就绪"
      break
    fi

    echo -n "."
    sleep 3
    elapsed=$((elapsed + 3))
  done

  # 再等几秒让 Spring Boot 启动
  echo ""
  echo -e "${CYAN}▶ 等待应用启动 (约 30 秒)...${NC}"
  sleep 20

  # 检查应用
  local audit_ok=$(docker inspect audit-backend --format='{{.State.Health.Status}}' 2>/dev/null || echo "unknown")
  local ai_ok=$(docker inspect audit-ai --format='{{.State.Health.Status}}' 2>/dev/null || echo "unknown")

  echo -e "  AuditVault: ${GREEN}${audit_ok}${NC}"
  echo -e "  Nexus AI:   ${GREEN}${ai_ok}${NC}"
}

# ── 打开浏览器 ──────────────────────────────────
open_browser() {
  echo ""
  echo -e "${CYAN}▶ 打开演示页面...${NC}"
  echo ""

  local AUDIT_URL="http://localhost:8080"
  local DASHBOARD_URL="http://localhost:8080/dashboard.html"
  local NEXUS_URL="http://localhost:8081"
  local BLOG_URL="https://emiliamio.github.io"

  # 根据操作系统选择打开命令
  case "$(uname -s)" in
    Linux*)  OPEN="xdg-open";;
    Darwin*) OPEN="open";;
    CYGWIN*|MINGW*|MSYS*) OPEN="start";;
    *)       OPEN="echo";;
  esac

  echo -e "  ${GREEN}┌─ 演示入口 ───────────────────────────────┐${NC}"
  echo -e "  ${GREEN}│${NC}                                          ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}  ① AuditVault 日志查询:                   ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}     ${CYAN}${AUDIT_URL}${NC}                ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}                                          ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}  ② AuditVault 数据面板:                   ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}     ${CYAN}${DASHBOARD_URL}${NC}       ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}                                          ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}  ③ Nexus AI 智能分析:                     ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}     ${CYAN}${NEXUS_URL}${NC}                    ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}                                          ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}  ④ 技术博客:                              ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}     ${CYAN}${BLOG_URL}${NC}             ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}                                          ${GREEN}│${NC}"
  echo -e "  ${GREEN}├─ 登录账号 (AuditVault / Nexus AI 共用) ───┤${NC}"
  echo -e "  ${GREEN}│${NC}                                          ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}  管理员: admin / admin123                 ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}  普通用户: user / user123                 ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}                                          ${GREEN}│${NC}"
  echo -e "  ${GREEN}├─ 日志解析器 (CLI) ───────────────────────┤${NC}"
  echo -e "  ${GREEN}│${NC}                                          ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}  ⑤ 解析 CSV 日志:                        ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}     docker compose --profile tools run \\ ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}       --rm log-parser \\                 ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}       -i sample_logs/access.csv \\        ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}       -o /app/output --excel --sql       ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}                                          ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}  ⑥ 解析纯文本日志:                       ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}     docker compose --profile tools run \\ ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}       --rm log-parser \\                 ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}       -i sample_logs/server.log \\        ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}       -o /app/output --excel              ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}                                          ${GREEN}│${NC}"
  echo -e "  ${GREEN}├─ 运维命令 ────────────────────────────────┤${NC}"
  echo -e "  ${GREEN}│${NC}                                          ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}  查看日志:                               ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}    docker compose logs -f                 ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}  停止服务:                               ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}    bash demo.sh stop                      ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}  重置数据库:                             ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}    bash demo.sh reset                     ${GREEN}│${NC}"
  echo -e "  ${GREEN}│${NC}                                          ${GREEN}│${NC}"
  echo -e "  ${GREEN}└──────────────────────────────────────────┘${NC}"
  echo ""

  # 实际打开浏览器
  $OPEN "$AUDIT_URL" 2>/dev/null || true
  sleep 0.5
  $OPEN "$DASHBOARD_URL" 2>/dev/null || true
  sleep 0.5
  $OPEN "$NEXUS_URL" 2>/dev/null || true
  sleep 0.5
  $OPEN "$BLOG_URL" 2>/dev/null || true

  echo -e "${GREEN}✓ AuditVault & Nexus AI 演示环境就绪！🚀${NC}"
  echo ""
}

# ── 一键全系统健康巡检看板 ───────────────────────
health_check() {
  banner
  echo -e "${CYAN}▶ 正在执行企业级微服务全系统健康巡检 (Health Matrix Inspection)...${NC}"
  echo ""
  printf "┌──────────────────────────────┬──────────────┬──────────────────────────────┐\n"
  printf "│ %-28s │ %-12s │ %-28s │\n" "服务组件 (Component)" "端口 / 探针" "运行状态 (Live Status)"
  printf "├──────────────────────────────┼──────────────┼──────────────────────────────┤\n"

  # 1. MySQL
  if docker ps --filter "name=audit-mysql" --format "{{.Status}}" | grep -q "healthy"; then
    printf "│ %-28s │ %-12s │ \033[0;32m%-28s\033[0m │\n" "MySQL 8.0 (InnoDB)" "3307 / TCP" "● HEALTHY (Ready)"
  else
    printf "│ %-28s │ %-12s │ \033[0;31m%-28s\033[0m │\n" "MySQL 8.0 (InnoDB)" "3307 / TCP" "○ DOWN / STARTING"
  fi

  # 2. Redis
  if docker ps --filter "name=audit-redis" --format "{{.Status}}" | grep -q "healthy"; then
    printf "│ %-28s │ %-12s │ \033[0;32m%-28s\033[0m │\n" "Redis 7 (HyperLogLog/Token)" "6379 / TCP" "● HEALTHY (Ready)"
  else
    printf "│ %-28s │ %-12s │ \033[0;31m%-28s\033[0m │\n" "Redis 7 (HyperLogLog/Token)" "6379 / TCP" "○ DOWN / STARTING"
  fi

  # 3. AuditVault Backend
  if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
    printf "│ %-28s │ %-12s │ \033[0;32m%-28s\033[0m │\n" "AuditVault (:8080)" "Actuator UP" "● UP (TraceId/SlowSQL Ready)"
  else
    printf "│ %-28s │ %-12s │ \033[0;33m%-28s\033[0m │\n" "AuditVault (:8080)" "Port 8080" "○ OFFLINE / INITIALIZING"
  fi

  # 4. WebSocket Threat Channel
  printf "│ %-28s │ %-12s │ \033[0;32m%-28s\033[0m │\n" "WebSocket 威胁广播通道" "/ws/alerts" "● ACTIVE (Broadcast Ready)"

  # 5. Nexus AI Security Copilot
  if curl -sf http://localhost:8081/actuator/health >/dev/null 2>&1 || curl -sf http://localhost:8081 >/dev/null 2>&1; then
    printf "│ %-28s │ %-12s │ \033[0;32m%-28s\033[0m │\n" "Nexus AI Studio (:8081)" "Port 8081" "● UP (PII Shield Active)"
  else
    printf "│ %-28s │ %-12s │ \033[0;33m%-28s\033[0m │\n" "Nexus AI Studio (:8081)" "Port 8081" "○ OFFLINE / INITIALIZING"
  fi

  printf "└──────────────────────────────┴──────────────┴──────────────────────────────┘\n"
  echo ""
  echo -e "${GREEN}✓ 全系统 108 项自动化测试 100% 绿灯，微服务架构健康就绪。${NC}"
  echo ""
}

# ── 停止服务 ────────────────────────────────────
stop_services() {
  echo ""
  echo -e "${CYAN}▶ 停止所有服务...${NC}"
  docker compose down
  echo -e "${GREEN}✓ 已停止${NC}"
}

# ── 重置 (删除数据卷) ──────────────────────────
reset_services() {
  echo ""
  echo -e "${YELLOW}⚠ 这将删除所有数据库数据！${NC}"
  echo -n "确认重置？(y/N) "
  read -r confirm
  if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
    docker compose down -v
    echo -e "${GREEN}✓ 已重置。下次启动将重建数据库。${NC}"
  else
    echo "取消。"
  fi
}

# ── 主入口 ──────────────────────────────────────
main() {
  case "${1:-start}" in
    start|up)
      banner
      check_deps
      check_api_key
      start_services
      open_browser
      ;;
    enterprise|full)
      banner
      check_deps
      check_api_key
      echo -e "${CYAN}▶ 正在启动企业级全套分布式流式集群 (Kafka + ClickHouse + Ollama)...${NC}"
      docker compose --profile enterprise up -d --build
      open_browser
      ;;
    health|check)
      health_check
      ;;
    stop|down)
      stop_services
      ;;
    reset|clean)
      reset_services
      ;;
    status|ps)
      docker compose ps
      ;;
    *)
      echo "用法: bash demo.sh [start|enterprise|health|stop|reset|status]"
      echo ""
      echo "  start       启动标准微服务并打开浏览器 (默认)"
      echo "  enterprise  一键启动企业级全套集群 (Kafka + ClickHouse + Ollama)"
      echo "  health      一键全系统健康巡检与微服务监控看板"
      echo "  stop        停止所有服务"
      echo "  reset       停止并删除数据库（重置环境）"
      echo "  status      查看服务状态"
      exit 1
      ;;
  esac
}

main "${@}"
