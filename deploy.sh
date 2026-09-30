#!/usr/bin/env bash
# ERP 一键部署（Docker）：生成 .env（随机密码与密钥）→ 构建镜像 → 启动 MySQL / 后端 / 前端 → 等待健康检查。
#
# 用法：
#   ./deploy.sh          首次部署（已部署时相当于按当前代码重新构建并启动）
#   ./deploy.sh --cn     使用国内镜像源构建（Maven 阿里云、npm npmmirror），写入 .env
#   ./deploy.sh --help
set -euo pipefail
cd "$(dirname "$0")"

CN=0
for arg in "$@"; do
  case "$arg" in
    --cn) CN=1 ;;
    -h|--help) sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "未知参数：$arg（--help 查看用法）"; exit 1 ;;
  esac
done

info() { printf '\033[1;34m[ERP]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[ERP]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[ERP]\033[0m %s\n' "$*" >&2; exit 1; }

# ---------- 环境检查 ----------
command -v docker >/dev/null 2>&1 || fail "未安装 Docker，请先安装：https://docs.docker.com/engine/install/"
docker info >/dev/null 2>&1 || fail "无法连接 Docker 守护进程（请启动 Docker，或使用 sudo / 将当前用户加入 docker 组）"
docker compose version >/dev/null 2>&1 || fail "未安装 Docker Compose v2（docker compose 命令）"
COMPOSE="docker compose"

# ---------- 生成 .env ----------
rand() { LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c "$1"; }
set_env() { # set_env KEY VALUE：已有则替换，没有则追加
  if grep -q "^$1=" .env; then sed -i.bak "s|^$1=.*|$1=$2|" .env && rm -f .env.bak; else echo "$1=$2" >> .env; fi
}
if [ ! -f .env ]; then
  [ -f .env.example ] || fail "缺少 .env.example"
  cp .env.example .env
  set_env ERP_DB_PASSWORD "$(rand 24)"
  set_env ERP_DB_ROOT_PASSWORD "$(rand 24)"
  set_env ERP_JWT_SECRET "$(rand 64)"
  chmod 600 .env
  info "已生成 .env（数据库密码、JWT 密钥为随机值，请妥善保管）"
fi
if [ "$CN" = 1 ]; then
  set_env MAVEN_MIRROR "https://maven.aliyun.com/repository/public"
  set_env NPM_REGISTRY "https://registry.npmmirror.com"
  info "已启用国内镜像源（写入 .env）"
fi
set -a; . ./.env; set +a
[ -n "${ERP_DB_PASSWORD:-}" ] && [ -n "${ERP_DB_ROOT_PASSWORD:-}" ] && [ -n "${ERP_JWT_SECRET:-}" ] \
  || fail ".env 中 ERP_DB_PASSWORD / ERP_DB_ROOT_PASSWORD / ERP_JWT_SECRET 不能为空"
[ "${#ERP_JWT_SECRET}" -ge 32 ] || fail "ERP_JWT_SECRET 至少 32 位"

# ---------- 构建并启动 ----------
info "构建镜像（首次需要下载依赖，约 5～15 分钟）…"
$COMPOSE build
info "启动服务…"
if ! $COMPOSE up -d; then
  $COMPOSE logs --tail=100 erp-server || true
  fail "服务启动失败，请检查上面的日志（docker compose logs -f erp-server）"
fi

# ---------- 等待健康 ----------
wait_healthy() { # wait_healthy 服务名 超时秒数
  local svc=$1 timeout=$2 waited=0 id status
  id=$($COMPOSE ps -q "$svc")
  while [ "$waited" -lt "$timeout" ]; do
    status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id" 2>/dev/null || echo unknown)
    case "$status" in
      healthy|running) return 0 ;;
      unhealthy|exited|dead) return 1 ;;
    esac
    sleep 5; waited=$((waited + 5))
  done
  return 1
}
info "等待后端启动（首次启动会自动建表）…"
if ! wait_healthy erp-server 600; then
  $COMPOSE logs --tail=100 erp-server || true
  fail "后端未能正常启动，请检查上面的日志（docker compose logs -f erp-server）"
fi
wait_healthy erp-ui 120 || fail "前端未能正常启动（docker compose logs erp-ui）"

PORT=${ERP_HTTP_PORT:-80}
HOST=$(hostname -I 2>/dev/null | awk '{print $1}')
info "部署完成！"
echo "  访问地址：http://${HOST:-localhost}$([ "$PORT" = 80 ] || echo ":$PORT")"
echo "  初始账号：admin / admin123（首次登录后请立即修改密码）"
echo "  查看日志：docker compose logs -f erp-server"
echo "  后续更新：./update.sh"
