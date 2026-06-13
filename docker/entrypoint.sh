#!/bin/sh
set -e

echo "========================================="
echo "  CSUFTSAP - 软件协会管理系统"
echo "========================================="

# 确保目录存在
mkdir -p /app/data /app/logs /app/uploads /app/ssl/webroot/.well-known/acme-challenge

# ===== 数据库模式检测 =====
# 非敏感配置仍走命令行参数；数据源连接信息（含密码）改为通过环境变量传递，
# 避免出现在进程列表 (ps) 与命令行参数中。Spring 的 relaxed binding 会自动
# 将 SPRING_DATASOURCE_* 映射到 spring.datasource.* 属性。
JAVA_OPTS=""

if [ -n "$MYSQL_URL" ]; then
    echo "[DB] 使用外部 MySQL 数据库"
    echo "[DB] URL: $MYSQL_URL"
    export SPRING_DATASOURCE_URL="$MYSQL_URL"
    export SPRING_DATASOURCE_USERNAME="${MYSQL_USER:-root}"
    export SPRING_DATASOURCE_PASSWORD="${MYSQL_PASSWORD:-}"
    export SPRING_DATASOURCE_DRIVER_CLASS_NAME="com.mysql.cj.jdbc.Driver"
    export SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT="org.hibernate.dialect.MySQLDialect"
else
    echo "[DB] 使用内置 H2 数据库 (MySQL 兼容模式)"
    echo "[DB] 数据文件: /app/data/sap.mv.db"
fi

# ===== SSL / HTTPS 配置 =====
setup_ssl() {
    DOMAIN="$1"
    CERT_PATH="/etc/letsencrypt/live/$DOMAIN"
    EMAIL="${SSL_EMAIL:-admin@$DOMAIN}"

    echo "[SSL] 域名: $DOMAIN"
    echo "[SSL] 证书路径: $CERT_PATH"
    echo "[SSL] 联系邮箱: $EMAIL"

    # 先用纯 HTTP 启动 Nginx（用于 ACME 验证）
    echo "[SSL] 启动临时 Nginx (仅 HTTP，用于 ACME 验证)..."
    nginx

    # 检查证书是否已存在
    if [ -d "$CERT_PATH" ] && [ -f "$CERT_PATH/fullchain.pem" ]; then
        echo "[SSL] 检测到已有证书，尝试续期..."
        certbot renew --non-interactive --quiet || echo "[SSL] 续期未执行（可能未到期）"
    else
        echo "[SSL] 首次申请证书..."
        certbot certonly \
            --webroot \
            --webroot-path=/app/ssl/webroot \
            --domain "$DOMAIN" \
            --email "$EMAIL" \
            --agree-tos \
            --no-eff-email \
            --non-interactive || {
                echo "[SSL] =================================="
                echo "[SSL] ⚠️ 证书申请失败！"
                echo "[SSL] 可能原因："
                echo "[SSL]   1. 域名 $DOMAIN 未解析到本服务器"
                echo "[SSL]   2. 服务器 80 端口未对外开放"
                echo "[SSL]   3. Let's Encrypt 速率限制"
                echo "[SSL] 将以 HTTP 模式继续运行..."
                echo "[SSL] =================================="
                return 1
            }
    fi

    # 证书申请成功，停止临时 Nginx
    echo "[SSL] 证书就绪，配置 HTTPS..."
    nginx -s stop 2>/dev/null || true
    sleep 1

    # 生成包含 HTTPS 的 Nginx 配置
    generate_https_config "$DOMAIN"

    # 设置自动续期 (每天凌晨3点检查)
    echo "0 3 * * * certbot renew --quiet --webroot --webroot-path=/app/ssl/webroot --post-hook 'nginx -s reload' >> /app/logs/certbot-renew.log 2>&1" > /etc/cron.d/certbot-renew
    chmod 0644 /etc/cron.d/certbot-renew
    crontab /etc/cron.d/certbot-renew

    echo "[SSL] 启动 cron 守护进程 (自动续期)..."
    cron

    return 0
}

generate_https_config() {
    DOMAIN="$1"
    CERT_PATH="/etc/letsencrypt/live/$DOMAIN"

    # 修改 HTTP server：添加 HTTPS 重定向
    # 替换 SSL_INCLUDE_MARKER 为 HTTPS server 块
    cat > /tmp/ssl_server.conf << SSLEOF
    server {
        listen 443 ssl http2;
        server_name $DOMAIN;

        ssl_certificate     $CERT_PATH/fullchain.pem;
        ssl_certificate_key $CERT_PATH/privkey.pem;

        # SSL 安全配置
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers HIGH:!aNULL:!MD5;
        ssl_prefer_server_ciphers on;
        ssl_session_cache shared:SSL:10m;
        ssl_session_timeout 10m;

        # HSTS
        add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

        # 安全响应头
        add_header X-Content-Type-Options "nosniff" always;
        add_header X-Frame-Options "SAMEORIGIN" always;
        add_header Referrer-Policy "strict-origin-when-cross-origin" always;

        # /admin 精确匹配 → 301 重定向到 /admin/
        location = /admin {
            return 301 /admin/;
        }

        # 屏蔽 API 文档与调试端点
        location ~ ^/api/(doc\.html|swagger-ui|v3/api-docs|webjars|swagger-resources) {
            return 404;
        }

        # 用户端 SPA
        location / {
            root /app/static/user;
            index index.html;
            try_files \$uri \$uri/ /index.html;

            location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)\$ {
                expires 30d;
                add_header Cache-Control "public, immutable";
            }
        }

        # 管理端 SPA
        location /admin/ {
            alias /app/static/admin/;
            index index.html;
            try_files \$uri \$uri/ /admin/index.html;

            location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)\$ {
                expires 30d;
                add_header Cache-Control "public, immutable";
            }
        }

        # API 反向代理
        location /api/ {
            proxy_pass http://127.0.0.1:8081;
            proxy_set_header Host \$host;
            proxy_set_header X-Real-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto \$scheme;
            client_max_body_size 50M;
        }
    }
SSLEOF

    # 将 HTTPS 重定向添加到 HTTP server，将 SSL server 块插入配置
    sed -i "s|server_name _;|server_name $DOMAIN;|" /etc/nginx/nginx.conf

    # 在 HTTP server 的 location / 之前添加 HTTPS 重定向
    # 找到 ACME location 后面，添加重定向规则
    sed -i '/# ===== 用户端 SPA =====/i\        # HTTPS 重定向（保留 ACME 验证路径）\n        location / {\n            return 301 https://$DOMAIN$request_uri;\n        }\n    }\n\n    # 原始 location 块已被重定向替代，以下为 HTTPS server' /etc/nginx/nginx.conf 2>/dev/null || true

    # 更简洁的方案：直接重写 HTTP server 为重定向 + 生成完整配置
    cat > /etc/nginx/nginx.conf << NGINXEOF
worker_processes auto;
pid /run/nginx.pid;

events {
    worker_connections 1024;
}

http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;

    sendfile    on;
    tcp_nopush  on;
    keepalive_timeout 65;

    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript image/svg+xml;
    gzip_min_length 1024;
    gzip_vary on;

    log_format main '\$remote_addr - \$remote_user [\$time_local] "\$request" '
                    '\$status \$body_bytes_sent "\$http_referer" '
                    '"\$http_user_agent"';

    access_log /app/logs/nginx-access.log main;
    error_log  /app/logs/nginx-error.log warn;

    # HTTP → HTTPS 重定向
    server {
        listen 80;
        server_name $DOMAIN;

        # 安全响应头
        add_header X-Content-Type-Options "nosniff" always;
        add_header X-Frame-Options "SAMEORIGIN" always;
        add_header Referrer-Policy "strict-origin-when-cross-origin" always;

        # Let's Encrypt ACME 验证
        location /.well-known/acme-challenge/ {
            root /app/ssl/webroot;
        }

        # 其他所有请求重定向到 HTTPS
        location / {
            return 301 https://\$host\$request_uri;
        }
    }

    # HTTPS Server
    server {
        listen 443 ssl http2;
        server_name $DOMAIN;

        ssl_certificate     $CERT_PATH/fullchain.pem;
        ssl_certificate_key $CERT_PATH/privkey.pem;

        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers HIGH:!aNULL:!MD5;
        ssl_prefer_server_ciphers on;
        ssl_session_cache shared:SSL:10m;
        ssl_session_timeout 10m;

        add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

        # 安全响应头
        add_header X-Content-Type-Options "nosniff" always;
        add_header X-Frame-Options "SAMEORIGIN" always;
        add_header Referrer-Policy "strict-origin-when-cross-origin" always;

        location = /admin {
            return 301 /admin/;
        }

        # 屏蔽 API 文档与调试端点
        location ~ ^/api/(doc\.html|swagger-ui|v3/api-docs|webjars|swagger-resources) {
            return 404;
        }

        location / {
            root /app/static/user;
            index index.html;
            try_files \$uri \$uri/ /index.html;

            location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)\$ {
                expires 30d;
                add_header Cache-Control "public, immutable";
            }
        }

        location /admin/ {
            alias /app/static/admin/;
            index index.html;
            try_files \$uri \$uri/ /admin/index.html;

            location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)\$ {
                expires 30d;
                add_header Cache-Control "public, immutable";
            }
        }

        location /api/ {
            proxy_pass http://127.0.0.1:8081;
            proxy_set_header Host \$host;
            proxy_set_header X-Real-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto https;
            client_max_body_size 50M;
        }
    }
}
NGINXEOF

    echo "[SSL] HTTPS 配置已生成"
}

# ===== 主流程 =====

if [ -n "$DOMAIN" ]; then
    echo "[SSL] 检测到域名变量: $DOMAIN"
    if setup_ssl "$DOMAIN"; then
        echo "[SSL] ✅ HTTPS 配置成功"
        echo "[SSL] 访问地址: https://$DOMAIN/"
        echo "[SSL] 管理端:   https://$DOMAIN/admin/"
    else
        echo "[SSL] ⚠️ 回退到 HTTP 模式"
    fi
else
    echo "[SSL] 未设置 DOMAIN 变量，使用 HTTP 模式"
fi

# ===== 启动 Nginx =====
echo "[NGINX] 启动 Nginx..."
# 先测试配置
nginx -t
# 如果 nginx 已在运行（SSL 模式下），先停止再重启
nginx -s stop 2>/dev/null || true
sleep 1
nginx

# ===== 启动 Spring Boot =====
# 敏感的数据源连接信息通过 SPRING_DATASOURCE_* 环境变量传入（见上），
# 不再拼进命令行，避免泄露到进程列表。
echo "[APP] 启动 Spring Boot..."
# JAVA_OPTS 仅承载非敏感的额外 JVM/Spring 参数；为空时不传入空参数。
if [ -n "$JAVA_OPTS" ]; then
    # 此处依赖按空白拆分以传递多个参数，故 JAVA_OPTS 不加引号。
    # shellcheck disable=SC2086
    exec java -jar /app/app.jar \
        --spring.profiles.active=docker \
        --file.upload.path=/app/uploads/ \
        $JAVA_OPTS
else
    exec java -jar /app/app.jar \
        --spring.profiles.active=docker \
        --file.upload.path=/app/uploads/
fi
