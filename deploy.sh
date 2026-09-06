#!/usr/bin/env bash
#
# 知识星图 Agent v2.0 一键部署脚本
# 适用：Ubuntu 22.04 / Debian 12（CentOS/RHEL 走 yum 分支，尽力支持）
#
# 用法（一条命令）：
#   curl -fsSL https://raw.githubusercontent.com/dongmuyunanfeng/Knowledge-Star-Map/master/deploy.sh -o /tmp/deploy.sh && sudo bash /tmp/deploy.sh
#
# 说明：
#   首次执行会交互式询问数据库密码与 API Key（仅一次），填完自动完成全部部署。
#   重复运行幂等，可安全重跑。
#
# 安全：所有密钥均从 /opt/star-map/.env 读取，脚本本身不含任何密钥。
#
set -euo pipefail

# ---------------- 全局配置 ----------------
APP_NAME="star-map"
INSTALL_DIR="/opt/Knowledge-Star-Map"
ENV_DIR="/opt/star-map"
ENV_FILE="${ENV_DIR}/.env"
UPLOAD_DIR="/data/knowledge-star-map/uploads"
REPO_URL="https://github.com/dongmuyunanfeng/Knowledge-Star-Map.git"
DB_NAME="knowledge_star_map"
DB_USER="star"
BRANCH="master"

# ---------------- 输出辅助 ----------------
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
step() { echo -e "\n${CYAN}==>${NC} $1"; }
ok()   { echo -e "${GREEN}[✓]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
die()  { echo -e "${RED}[✗] $1${NC}" >&2; exit 1; }

# ---------------- 0. 前置检查 ----------------
[ "$(id -u)" -eq 0 ] || die "请以 root 运行：curl ... | sudo bash"

. /etc/os-release
OS_ID="${ID:-unknown}"
case "${OS_ID}" in
  ubuntu|debian)               PKG_MGR="apt" ;;
  centos|rhel|rocky|almalinux) PKG_MGR="yum" ;;
  *) die "暂不支持的发行版：${OS_ID}（脚本针对 Ubuntu/Debian 设计）" ;;
esac

# ---------------- 1. 环境文件准备（密钥唯一来源） ----------------
mkdir -p "${ENV_DIR}"

if [ ! -f "${ENV_FILE}" ]; then
  echo -e "\n${CYAN}首次运行：请配置部署参数（写入 ${ENV_FILE}，之后重跑无需再填）${NC}"
  echo -e "${CYAN}--------------------------------------------------------------${NC}"

  DB_PASSWORD=""
  while [ -z "${DB_PASSWORD}" ]; do
    read -rsp "数据库密码（必填，仅字母数字下划线）: " DB_PASSWORD < /dev/tty || true
    echo ""
  done

  read -rp "API_KEY_1（文件解析/知识点生成，可留空）: " API_KEY_1 < /dev/tty || true
  read -rp "API_KEY_2（Agent对话/简历，可留空）: " API_KEY_2 < /dev/tty || true
  read -rp "API_KEY_3（图片OCR，可留空）: " API_KEY_3 < /dev/tty || true
  read -rp "服务器域名或IP（留空则匹配所有）: " SERVER_NAME < /dev/tty || true

  cat > "${ENV_FILE}" <<EOF
DB_PASSWORD=${DB_PASSWORD}
API_KEY_1=${API_KEY_1}
API_KEY_2=${API_KEY_2}
API_KEY_3=${API_KEY_3}
SERVER_NAME=${SERVER_NAME}
DB_HOST=localhost
DB_PORT=3306
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
JWT_SECRET=
FILE_UPLOAD_DIR=${UPLOAD_DIR}
EOF
  chmod 600 "${ENV_FILE}"
  ok "配置已保存到 ${ENV_FILE}"
fi
chmod 600 "${ENV_FILE}"

# 加载环境变量
set -a
# shellcheck disable=SC1090
. "${ENV_FILE}"
set +a

SERVER_NAME="${SERVER_NAME:-_}"   # 未填则 Nginx 匹配所有 host

# 自动生成 JWT_SECRET
if [ -z "${JWT_SECRET:-}" ]; then
  JWT_SECRET="$(openssl rand -hex 32)"
  echo "JWT_SECRET=${JWT_SECRET}" >> "${ENV_FILE}"
  ok "已自动生成 JWT_SECRET 写入 ${ENV_FILE}"
fi

# ---------------- 2. 安装依赖 ----------------
step "安装基础依赖"
if [ "${PKG_MGR}" = "apt" ]; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y openjdk-17-jdk maven nginx redis-server curl git openssl
  # Node 18+（NodeSource）
  if ! command -v node >/dev/null 2>&1 || [ "$(node -v | sed 's/v//;s/\..*//')" -lt 18 ]; then
    curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
    apt-get install -y nodejs
  fi
  # MySQL 8
  if ! command -v mysql >/dev/null 2>&1; then
    apt-get install -y mysql-server
  fi
else
  yum install -y java-17-openjdk maven nginx redis curl git openssl || true
  curl -fsSL https://rpm.nodesource.com/setup_18.x | bash -
  yum install -y nodejs || true
  yum install -y mysql-server || true
fi

# 启动并开机自启 Redis / MySQL
systemctl enable --now redis-server 2>/dev/null || systemctl enable --now redis 2>/dev/null || true
systemctl enable --now mysql 2>/dev/null || systemctl enable --now mysqld 2>/dev/null || true

# ---------------- 3. 拉取代码 ----------------
step "拉取代码"
if [ -d "${INSTALL_DIR}/.git" ]; then
  cd "${INSTALL_DIR}"
  git fetch origin
  git checkout "${BRANCH}" 2>/dev/null || true
  git reset --hard "origin/${BRANCH}"
  ok "代码已更新到最新"
else
  git clone --branch "${BRANCH}" "${REPO_URL}" "${INSTALL_DIR}"
  ok "代码克隆完成"
fi

# ---------------- 4. 初始化数据库 ----------------
step "初始化数据库"
# 等待 MySQL 就绪
for _ in $(seq 1 30); do
  mysqladmin ping --silent 2>/dev/null && break
  sleep 2
done

mysql <<SQL
CREATE DATABASE IF NOT EXISTS ${DB_NAME} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
ALTER USER '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL

# 导入建表脚本（Flyway 已关闭，需手动导入；用 sort -V 保证 V1<V2<...<V20 数字顺序）
MIGRATION_DIR="${INSTALL_DIR}/星图后端/src/main/resources/db/migration"
while IFS= read -r f; do
  [ -f "$f" ] && mysql "${DB_NAME}" < "$f"
done < <(find "${MIGRATION_DIR}" -maxdepth 1 -name 'V*.sql' | sort -V)
ok "数据库初始化完成"

# ---------------- 5. 构建后端 ----------------
step "构建后端（Maven 打包）"
cd "${INSTALL_DIR}/星图后端"
# .gitignore 忽略了 src/testP2~P9，但 pom.xml 的 build-helper 仍引用，补齐空目录避免报错
mkdir -p src/testP{2,3,4,5,6,7,8,9}
mvn -q clean package -Dmaven.test.skip=true
ok "后端打包完成"

# ---------------- 6. 构建前端 ----------------
step "构建前端（Vite 打包）"
cd "${INSTALL_DIR}/星图前端"
npm install --no-audit --no-fund
npm run build
ok "前端打包完成"

# ---------------- 7. 上传目录 ----------------
mkdir -p "${UPLOAD_DIR}"
chmod -R 777 "${UPLOAD_DIR}"
ok "上传目录就绪：${UPLOAD_DIR}"

# ---------------- 8. 配置 Nginx ----------------
step "配置 Nginx"
DIST_DIR="${INSTALL_DIR}/星图前端/dist"
NGINX_CONF="/etc/nginx/sites-available/${APP_NAME}"
cat > "${NGINX_CONF}" <<NGINX
server {
    listen 80;
    server_name ${SERVER_NAME};

    root ${DIST_DIR};
    index index.html;

    location / {
        try_files \$uri \$uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_read_timeout 300s;
    }

    client_max_body_size 50m;
}
NGINX

if [ "${PKG_MGR}" = "yum" ]; then
  cp "${NGINX_CONF}" "/etc/nginx/conf.d/${APP_NAME}.conf"
else
  ln -sf "${NGINX_CONF}" "/etc/nginx/sites-enabled/${APP_NAME}"
  rm -f /etc/nginx/sites-enabled/default
fi
nginx -t && systemctl reload nginx
ok "Nginx 配置完成"

# ---------------- 9. 后端 systemd 守护 ----------------
step "注册后端服务并启动"
JAR_FILE="$(find "${INSTALL_DIR}/星图后端/target" -maxdepth 1 -name '*.jar' ! -name '*.original' | head -n1)"
[ -n "${JAR_FILE}" ] || die "未找到后端 jar 包"

cat > "/etc/systemd/system/${APP_NAME}.service" <<EOF
[Unit]
Description=Knowledge Star Map Backend
After=network.target mysql.service redis-server.service

[Service]
EnvironmentFile=${ENV_FILE}
ExecStart=/usr/bin/java -jar ${JAR_FILE}
WorkingDirectory=${INSTALL_DIR}/星图后端
Restart=always
RestartSec=5
User=root

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now "${APP_NAME}"
ok "后端服务已启动"

# ---------------- 10. 防火墙 ----------------
if command -v ufw >/dev/null 2>&1; then
  ufw allow 80/tcp  >/dev/null 2>&1 || true
  ufw allow 443/tcp >/dev/null 2>&1 || true
  ok "已放行 80/443（8080/3306/6379 保持内网）"
fi

# ---------------- 完成 ----------------
step "部署完成"
echo -e "  访问地址：  ${GREEN}http://${SERVER_NAME}${NC}"
echo -e "  后端状态：  systemctl status ${APP_NAME}"
echo -e "  后端日志：  journalctl -u ${APP_NAME} -f"
echo -e "  Nginx 日志：tail -f /var/log/nginx/error.log"
