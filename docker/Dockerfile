# Source-built engine and pinned, isolated SDK (only one SAP container runs).
FROM --platform=$BUILDPLATFORM golang:1.26.0-bookworm AS judger-build
WORKDIR /source
COPY third_party/go-judge/ ./
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -tags seccomp -trimpath -ldflags '-s -w' -o /go-judge ./cmd/go-judge
FROM pllysun/sap-judger-sdk@sha256:ff9484d4ba7b2a510cd49b10e701d8ff2106a86f19cf36ee8fb2049298914ca0 AS judger-sdk

# ============================================
# 软件协会管理系统 - 多阶段 Docker 构建
# ============================================

# ----- Stage 1: 构建前端 -----
# Node 22（Debian glibc）：Vite 8 要求 Node ≥20.19/≥22.12；glibc 比 alpine/musl
# 对 rolldown 原生绑定兼容性更好。用 npm install 以便按构建平台拉取对应原生绑定，
# 规避 npm 可选依赖缺失导致的 "Cannot find native binding" 问题。
FROM node:22-bookworm-slim AS frontend-build

WORKDIR /build

# 构建用户端
COPY sap-user/package*.json ./sap-user/
RUN cd sap-user && npm install --no-audit --no-fund
COPY sap-user/ ./sap-user/
RUN cd sap-user && npm run build

# 构建管理端（设置 base=/admin/）
COPY sap-admin/package*.json ./sap-admin/
RUN cd sap-admin && npm install --no-audit --no-fund
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
# Maven Central 偶尔会在大依赖传输途中断开连接。把重试放在同一个镜像层内，
# 这样已成功下载的依赖会被复用，失败的文件则由 Maven 在下一轮继续解析。
RUN set -eu; \
    attempt=1; \
    while ! mvn package -DskipTests -B -Dmaven.wagon.http.retryHandler.count=5; do \
        if [ "$attempt" -ge 5 ]; then \
            echo "Maven build failed after ${attempt} attempts" >&2; \
            exit 1; \
        fi; \
        echo "Maven download interrupted; retrying build (${attempt}/5)..." >&2; \
        find /root/.m2/repository -name '*.lastUpdated' -delete; \
        attempt=$((attempt + 1)); \
        sleep $((attempt * 2)); \
    done

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

# 安装 Nginx、Certbot、Cron、Redis（登录态持久化），以及 OCR 边车所需 Python 运行时（libgomp1 供 onnxruntime）
RUN apt-get update && \
    apt-get install -y --no-install-recommends nginx wget certbot cron redis-server python3 python3-pip libgomp1 && \
    rm -rf /var/lib/apt/lists/* && \
    mkdir -p /run/nginx /app/static/user /app/static/admin /app/ocr /app/data /app/logs /app/uploads /app/ssl/webroot/.well-known/acme-challenge

# ----- 教务验证码 OCR 边车（ddddocr，与主应用同镜像，本机 127.0.0.1:9000）-----
# 先装依赖（利用层缓存），再拷源码。代理由 Docker 预置 build-arg (HTTP_PROXY 等) 自动透传给 pip。
COPY ocr-sidecar/requirements.txt /app/ocr/requirements.txt
RUN python3 -m pip install --no-cache-dir --upgrade pip && \
    python3 -m pip install --no-cache-dir -r /app/ocr/requirements.txt
COPY ocr-sidecar/main.py /app/ocr/main.py

# 清除代理环境变量（运行时不需要）
ENV HTTP_PROXY="" HTTPS_PROXY="" http_proxy="" https_proxy=""

# Runtime toolchains are visible only inside the sandbox, apart from business Java21/OCR Python.
COPY --from=judger-sdk /usr/ /opt/judger/rootfs/usr/
COPY --from=judger-sdk /opt/java27/ /opt/judger/rootfs/opt/java27/
COPY --from=judger-sdk /opt/rust/ /opt/judger/rootfs/opt/rust/
COPY --from=judger-sdk /etc/ld.so.cache /opt/judger/rootfs/etc/ld.so.cache
COPY --from=judger-sdk /etc/alternatives/ /opt/judger/rootfs/etc/alternatives/
COPY --from=judger-build /go-judge /opt/judger/go-judge
COPY third_party/go-judge/ /opt/judger/source/
COPY docker/judger-mount.yaml /opt/judger/mount.yaml
COPY docker/start-judger.sh /opt/judger/start.sh
RUN groupadd -g 2000 sapapp && useradd -u 2000 -g sapapp -M -s /usr/sbin/nologin sapapp && \
    chmod 755 /opt/judger/start.sh


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
    CMD wget -qO- http://localhost:8081/api/ping >/dev/null && \
    { [ "${JUDGER_ENABLED:-false}" != true ] || wget -qO /dev/null --header="Authorization: Bearer $(cat /run/judger/token)" http://127.0.0.1:5050/config; } || exit 1

# 安全说明：本容器当前以 root 用户运行。
#   原因：同一容器内的 Nginx 与 Certbot 需要 root 权限监听 80/443
#         并写入 /etc/letsencrypt 证书目录。
#   风险：Java 应用进程同样以 root 身份运行，若被攻破将拥有容器内
#         root 权限，横向影响面更大。
#   后续可改进方向：拆分为多容器（Nginx/Certbot 与 Java 应用分离），
#         或在 entrypoint 内用 su-exec/gosu 将 Java 进程降权为非 root 用户。
#   此处不强制改 USER，以免破坏 Nginx/Certbot 对特权端口与证书目录的访问。
COPY --chmod=0755 docker/judger-agent.py /opt/judger/agent.py
COPY --chmod=0755 docker/node-start.sh /opt/judger/node-start.sh
COPY --chmod=0755 docker/judger-health.sh /opt/judger/health.sh
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD /opt/judger/health.sh
ENTRYPOINT ["/app/entrypoint.sh"]
