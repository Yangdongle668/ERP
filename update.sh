#!/usr/bin/env bash
# ERP 更新（Docker）：拉取最新代码 → 备份数据库 → 构建新镜像 → 滚动重启 → 健康检查。
# 数据库结构由各模块的 Flyway 脚本在启动时自动升级（只增不改）。
#
# 用法：
#   ./update.sh                 拉取当前分支最新代码并更新
#   ./update.sh --no-pull       不拉取代码（已手动切换版本 / 修改了代码）
#   ./update.sh --no-backup     跳过数据库备份（不建议）
#   ./update.sh --rollback      回滚到上一次更新前的镜像（数据库需用备份手动恢复，见 README）
#   ./update.sh --help
set -euo pipefail
cd "$(dirname "$0")"

PULL=1; BACKUP=1; ROLLBACK=0
for arg in "$@"; do
  case "$arg" in
    --no-pull) PULL=0 ;;
    --no-backup) BACKUP=0 ;;
    --rollback) ROLLBACK=1 ;;
    -h|--help) sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "未知参数：$arg（--help 查看用法）"; exit 1 ;;
  esac
done

info() { printf '\033[1;34m[ERP]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[ERP]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[ERP]\033[0m %s\n' "$*" >&2; exit 1; }

docker info >/dev/null 2>&1 || fail "无法连接 Docker 守护进程"
[ -f .env ] || fail "未找到 .env，请先运行 ./deploy.sh 完成首次部署"
set -a; . ./.env; set +a
COMPOSE="docker compose"
TAG=${ERP_IMAGE_TAG:-latest}
IMAGES="erp-server erp-ui"

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

# ---------- 回滚 ----------
if [ "$ROLLBACK" = 1 ]; then
  for img in $IMAGES; do
    docker image inspect "$img:previous" >/dev/null 2>&1 || fail "没有可回滚的镜像 $img:previous"
    docker tag "$img:previous" "$img:$TAG"
  done
  $COMPOSE up -d --no-build || true
  wait_healthy erp-server 600 || fail "回滚后后端未能启动（docker compose logs erp-server）"
  info "已回滚到上一版本镜像。"
  warn "如果新版本已升级了数据库结构，请用 backups/ 中更新前的备份恢复数据库（见 README“更新与回滚”）。"
  exit 0
fi

# ---------- 拉取代码 ----------
OLD=$(git rev-parse --short HEAD 2>/dev/null || echo unknown)
if [ "$PULL" = 1 ]; then
  git diff --quiet && git diff --cached --quiet || fail "工作区有未提交的修改，请先处理（或使用 --no-pull）"
  info "拉取最新代码…"
  git pull --ff-only
fi
NEW=$(git rev-parse --short HEAD 2>/dev/null || echo unknown)
if [ "$OLD" = "$NEW" ] && [ "$PULL" = 1 ]; then
  info "代码已是最新（$NEW），仍将重新构建并重启。"
else
  info "版本：$OLD → $NEW"
fi

# ---------- 备份数据库 ----------
if [ "$BACKUP" = 1 ]; then
  if [ -n "$($COMPOSE ps -q mysql 2>/dev/null)" ]; then
    ./backup.sh --db-only --tag "$OLD" || fail "数据库备份失败"
  else
    warn "MySQL 未运行，跳过备份"
  fi
fi

# ---------- 保留当前镜像用于回滚 ----------
for img in $IMAGES; do
  docker image inspect "$img:$TAG" >/dev/null 2>&1 && docker tag "$img:$TAG" "$img:previous"
done

# ---------- 构建与重启 ----------
info "构建新镜像（逐个构建以降低内存占用）…"
$COMPOSE build erp-server
$COMPOSE build erp-ui
info "重启服务…"
if ! $COMPOSE up -d; then
  $COMPOSE logs --tail=100 erp-server || true
  fail "新版本未能正常启动。可执行 ./update.sh --rollback 回滚到上一版本。"
fi
info "等待后端启动（将自动执行数据库升级脚本）…"
if ! wait_healthy erp-server 600; then
  $COMPOSE logs --tail=100 erp-server || true
  fail "新版本后端未能正常启动。可执行 ./update.sh --rollback 回滚到上一版本。"
fi
wait_healthy erp-ui 120 || fail "前端未能正常启动（docker compose logs erp-ui）"
docker image prune -f >/dev/null 2>&1 || true
info "更新完成：$NEW"
