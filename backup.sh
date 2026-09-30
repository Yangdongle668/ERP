#!/usr/bin/env bash
# ERP 备份（Docker）：数据库（mysqldump）+ 附件（erp-data 数据卷），按份数保留，可选复制到异地。
#
# 用法：
#   ./backup.sh                    立即备份数据库和附件
#   ./backup.sh --db-only          只备份数据库（update.sh 更新前调用）
#   ./backup.sh --tag v1.2         备份文件名附加标签
#   ./backup.sh --install-cron     安装每日定时备份（时间取 .env 的 BACKUP_CRON，默认 "30 2 * * *"）
#   ./backup.sh --uninstall-cron   移除定时备份
#   ./backup.sh --restore-db FILE      用备份恢复数据库（会覆盖当前数据，需输入 yes 确认）
#   ./backup.sh --restore-files FILE   用备份恢复附件（会覆盖当前附件，需输入 yes 确认）
#   ./backup.sh --help
#
# .env 配置：BACKUP_KEEP（每类保留份数，默认 10）、BACKUP_DIR（默认 ./backups）、
#            BACKUP_REMOTE（异地目标：rsync/scp 地址如 user@host:/data/erp-backup，或 rclone:远端名:路径）
set -euo pipefail
cd "$(dirname "$0")"
ROOT=$(pwd)

MODE=backup; DB_ONLY=0; TAG=""; RESTORE_FILE=""
while [ $# -gt 0 ]; do
  case "$1" in
    --db-only) DB_ONLY=1 ;;
    --tag) TAG=${2:-}; shift ;;
    --install-cron) MODE=install ;;
    --uninstall-cron) MODE=uninstall ;;
    --restore-db) MODE=restore-db; RESTORE_FILE=${2:-}; shift ;;
    --restore-files) MODE=restore-files; RESTORE_FILE=${2:-}; shift ;;
    -h|--help) sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "未知参数：$1（--help 查看用法）"; exit 1 ;;
  esac
  shift
done

info() { printf '\033[1;34m[ERP]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[ERP]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[ERP]\033[0m %s\n' "$*" >&2; exit 1; }

[ -f .env ] || fail "未找到 .env，请先运行 ./deploy.sh 完成首次部署"
set -a; . ./.env; set +a
COMPOSE="docker compose"
DIR=${BACKUP_DIR:-$ROOT/backups}
KEEP=${BACKUP_KEEP:-10}
CRON_MARK="# erp-backup"

# ---------- 定时任务 ----------
if [ "$MODE" = install ] || [ "$MODE" = uninstall ]; then
  command -v crontab >/dev/null 2>&1 || fail "未找到 crontab，请先安装 cron"
  CURRENT=$(crontab -l 2>/dev/null | grep -v "$CRON_MARK" || true)
  if [ "$MODE" = uninstall ]; then
    printf '%s\n' "$CURRENT" | sed '/^$/d' | crontab -
    info "已移除定时备份"
    exit 0
  fi
  SCHEDULE=${BACKUP_CRON:-30 2 * * *}
  mkdir -p "$DIR"
  { printf '%s\n' "$CURRENT" | sed '/^$/d'; echo "$SCHEDULE cd $ROOT && ./backup.sh >> $DIR/backup.log 2>&1 $CRON_MARK"; } | crontab -
  info "已安装定时备份：$SCHEDULE（日志 $DIR/backup.log）"
  exit 0
fi

docker info >/dev/null 2>&1 || fail "无法连接 Docker 守护进程"

confirm() {
  printf '\033[1;33m[ERP]\033[0m %s 输入 yes 继续：' "$1"
  read -r ans
  [ "$ans" = yes ] || fail "已取消"
}

# ---------- 恢复 ----------
if [ "$MODE" = restore-db ]; then
  [ -f "$RESTORE_FILE" ] || fail "备份文件不存在：$RESTORE_FILE"
  [ -n "$($COMPOSE ps -q mysql 2>/dev/null)" ] || fail "MySQL 未运行"
  confirm "将用 $RESTORE_FILE 覆盖数据库 ${ERP_DB_NAME:-erp}，建议先停止后端（docker compose stop erp-server）。"
  gunzip -c "$RESTORE_FILE" | $COMPOSE exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'
  info "数据库已恢复，请执行 docker compose up -d 启动后端"
  exit 0
fi
if [ "$MODE" = restore-files ]; then
  [ -f "$RESTORE_FILE" ] || fail "备份文件不存在：$RESTORE_FILE"
  [ -n "$($COMPOSE ps -q erp-server 2>/dev/null)" ] || fail "后端未运行（附件数据卷挂载在后端容器）"
  confirm "将用 $RESTORE_FILE 覆盖附件目录 /app/data。"
  $COMPOSE exec -T erp-server sh -c 'rm -rf /app/data/* && tar xzf - -C /app' < "$RESTORE_FILE"
  info "附件已恢复"
  exit 0
fi

# ---------- 备份 ----------
mkdir -p "$DIR"
STAMP=$(date +%Y%m%d-%H%M%S)${TAG:+-$TAG}
FILES=()

[ -n "$($COMPOSE ps -q mysql 2>/dev/null)" ] || fail "MySQL 未运行，无法备份"
DB_FILE="$DIR/erp-$STAMP.sql.gz"
info "备份数据库 → $DB_FILE"
$COMPOSE exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --databases "$MYSQL_DATABASE"' \
  | gzip > "$DB_FILE.tmp"
[ -s "$DB_FILE.tmp" ] || { rm -f "$DB_FILE.tmp"; fail "数据库备份失败"; }
gzip -t "$DB_FILE.tmp" || { rm -f "$DB_FILE.tmp"; fail "数据库备份文件损坏"; }
mv "$DB_FILE.tmp" "$DB_FILE"
FILES+=("$DB_FILE")

if [ "$DB_ONLY" = 0 ]; then
  if [ -n "$($COMPOSE ps -q erp-server 2>/dev/null)" ]; then
    FILE_BAK="$DIR/erp-files-$STAMP.tar.gz"
    info "备份附件 → $FILE_BAK"
    $COMPOSE exec -T erp-server sh -c 'tar czf - -C /app data' > "$FILE_BAK.tmp" || { rm -f "$FILE_BAK.tmp"; fail "附件备份失败"; }
    mv "$FILE_BAK.tmp" "$FILE_BAK"
    FILES+=("$FILE_BAK")
  else
    warn "后端未运行，跳过附件备份"
  fi
fi

# 保留最近 KEEP 份（数据库、附件分别计数）
{ ls -1t "$DIR"/erp-[0-9]*.sql.gz 2>/dev/null || true; } | tail -n +$((KEEP + 1)) | xargs -r rm -f
{ ls -1t "$DIR"/erp-files-*.tar.gz 2>/dev/null || true; } | tail -n +$((KEEP + 1)) | xargs -r rm -f

# 异地复制
if [ -n "${BACKUP_REMOTE:-}" ]; then
  info "复制到异地：$BACKUP_REMOTE"
  case "$BACKUP_REMOTE" in
    rclone:*)
      command -v rclone >/dev/null 2>&1 || fail "未安装 rclone"
      for f in "${FILES[@]}"; do rclone copy "$f" "${BACKUP_REMOTE#rclone:}"; done ;;
    *)
      if command -v rsync >/dev/null 2>&1; then rsync -a "${FILES[@]}" "$BACKUP_REMOTE/"
      else scp -q "${FILES[@]}" "$BACKUP_REMOTE/"; fi ;;
  esac
fi

info "备份完成：${FILES[*]}"
