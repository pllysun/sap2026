# ============================================
# 软件协会管理系统 - 多阶段 Docker 构建
# ============================================

# ----- Stage 1: 构建前端 -----
# 固定到精确 minor 版本以保证可复现构建
FROM node:20.18-alpine AS frontend-build

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
# 固定到精确 patch 版本以保证可复现构建
FROM maven:3.9.9-eclipse-temurin-21 AS backend-build

WORKDIR /build

# 先拷贝 pom.xml 下载依赖（利用层缓存）
COPY sap-backend/pom.xml .
RUN mvn dependency:go-offline -B

# 拷贝源码并编译
COPY sap-backend/src ./src
RUN mvn package -DskipTests -B

# ----- Stage 3: 运行时 -----
# 使用 eclipse-temurin JRE (Ubuntu jammy 基础镜像)
# 注：本运行时镜像与 maven 构建阶段并不共享基础层
#     （maven 阶段基于 temurin-21 完整 JDK，此处为 jammy JRE），
#     两者层不复用，仅最终 JAR 通过 COPY --from 引入。
# 固定到精确 patch 版本以保证可复现构建
FROM eclipse-temurin:21.0.5_11-jre-jammy

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

# 安全说明：本容器当前以 root 用户运行。
#   原因：同一容器内的 Nginx 与 Certbot 需要 root 权限监听 80/443
#         并写入 /etc/letsencrypt 证书目录。
#   风险：Java 应用进程同样以 root 身份运行，若被攻破将拥有容器内
#         root 权限，横向影响面更大。
#   后续可改进方向：拆分为多容器（Nginx/Certbot 与 Java 应用分离），
#         或在 entrypoint 内用 su-exec/gosu 将 Java 进程降权为非 root 用户。
#   此处不强制改 USER，以免破坏 Nginx/Certbot 对特权端口与证书目录的访问。
ENTRYPOINT ["/app/entrypoint.sh"]
