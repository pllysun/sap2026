# ============================================
# 软件协会管理系统 - 多阶段 Docker 构建
# ============================================

# ----- Stage 1: 构建前端 -----
FROM node:20-alpine AS frontend-build

WORKDIR /build

# 构建用户端
COPY sap-user/package*.json ./sap-user/
RUN cd sap-user && npm ci --silent
COPY sap-user/ ./sap-user/
RUN cd sap-user && npm run build

# 构建管理端（设置 base=/admin/）
COPY sap-admin/package*.json ./sap-admin/
RUN cd sap-admin && npm ci --silent
COPY sap-admin/ ./sap-admin/
ENV VITE_BASE_URL=/admin/
RUN cd sap-admin && npm run build

# ----- Stage 2: 构建后端 -----
FROM maven:3.9-eclipse-temurin-21 AS backend-build

WORKDIR /build

# 先拷贝 pom.xml 下载依赖（利用层缓存）
COPY sap-backend/pom.xml .
RUN mvn dependency:go-offline -B

# 拷贝源码并编译
COPY sap-backend/src ./src
RUN mvn package -DskipTests -B

# ----- Stage 3: 运行时 -----
# 使用 eclipse-temurin JRE + Debian bookworm (和 maven stage 共享基础层)
FROM eclipse-temurin:21-jre-jammy

# 接收代理参数用于 apt-get
ARG HTTP_PROXY=""
ARG HTTPS_PROXY=""

# 安装 Nginx、Certbot、Cron
RUN apt-get update && \
    apt-get install -y --no-install-recommends nginx wget certbot cron && \
    rm -rf /var/lib/apt/lists/* && \
    mkdir -p /run/nginx /app/static/user /app/static/admin /app/data /app/logs /app/uploads /app/ssl/webroot/.well-known/acme-challenge

# 清除代理环境变量（运行时不需要）
ENV HTTP_PROXY="" HTTPS_PROXY="" http_proxy="" https_proxy=""

# 拷贝前端构建产物
COPY --from=frontend-build /build/sap-user/dist/ /app/static/user/
COPY --from=frontend-build /build/sap-admin/dist/ /app/static/admin/

# 拷贝后端 JAR
COPY --from=backend-build /build/target/sap-backend-1.0.0.jar /app/app.jar

# 拷贝 Nginx 配置
COPY docker/nginx.conf /etc/nginx/nginx.conf

# 拷贝启动脚本
COPY docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# 数据卷声明（含 SSL 证书和 Let's Encrypt 数据）
VOLUME ["/app/data", "/app/logs", "/app/uploads", "/etc/letsencrypt"]

# 暴露端口 (HTTP + HTTPS)
EXPOSE 80 443

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:80/ || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
