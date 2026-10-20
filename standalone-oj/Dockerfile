# syntax=docker/dockerfile:1.7
FROM --platform=$BUILDPLATFORM node:22-bookworm-slim AS frontend
WORKDIR /build
COPY user/package*.json ./user/
COPY admin/package*.json ./admin/
RUN --mount=type=cache,target=/root/.npm cd user && npm ci --no-audit --no-fund && cd ../admin && npm ci --no-audit --no-fund
COPY user/ ./user/
COPY admin/ ./admin/
RUN cd user && npm test && npm run build && cd ../admin && npm test && npm run build

FROM --platform=$BUILDPLATFORM maven:3.9.9-eclipse-temurin-21 AS backend
WORKDIR /build/backend
COPY backend/pom.xml ./
RUN --mount=type=cache,target=/root/.m2 --mount=type=secret,id=maven-settings if [ -f /run/secrets/maven-settings ]; then mvn -s /run/secrets/maven-settings -B -q dependency:go-offline; else mvn -B -q dependency:go-offline; fi
COPY backend/src/ ./src/
COPY examples/ /build/examples/
COPY --from=frontend /build/user/dist/ ./src/main/resources/static/
COPY --from=frontend /build/admin/dist/ ./src/main/resources/static/admin/
RUN --mount=type=cache,target=/root/.m2 --mount=type=secret,id=maven-settings if [ -f /run/secrets/maven-settings ]; then mvn -s /run/secrets/maven-settings -B package; else mvn -B package; fi

FROM eclipse-temurin:21.0.5_11-jre-jammy AS server
ARG VERSION=1.1.1
LABEL org.opencontainers.image.title="Lume server" org.opencontainers.image.version=$VERSION org.opencontainers.image.licenses="MIT" org.opencontainers.image.description="Standalone user frontend, administration and judging backend"
WORKDIR /app
COPY --chown=2000:2000 --from=backend /build/backend/target/sap-oj.jar /app/app.jar
COPY --chown=2000:2000 docker/data/ /app/data/
COPY --chmod=0755 docker/healthcheck.sh /app/healthcheck.sh
ENV TZ=Asia/Shanghai JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Shanghai"
USER 2000:2000
VOLUME ["/app/data"]
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD /app/healthcheck.sh
ENTRYPOINT ["java","-jar","/app/app.jar"]

FROM --platform=$BUILDPLATFORM golang:1.26.0-bookworm AS engine
ENV GOMAXPROCS=2
ARG GOPROXY=https://proxy.golang.org,direct
WORKDIR /src
COPY judge/go-judge/ ./
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build GOPROXY="$GOPROXY" CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -tags seccomp -trimpath -ldflags '-s -w' -o /go-judge ./cmd/go-judge
FROM pllysun/sap-judger-sdk@sha256:ff9484d4ba7b2a510cd49b10e701d8ff2106a86f19cf36ee8fb2049298914ca0 AS sdk
FROM python:3.12-slim-bookworm AS worker
ARG VERSION=1.1.1
LABEL org.opencontainers.image.title="Lume worker" org.opencontainers.image.version=$VERSION org.opencontainers.image.licenses="MIT"
COPY --from=sdk /usr/ /opt/judger/rootfs/usr/
COPY --from=sdk /opt/java27/ /opt/judger/rootfs/opt/java27/
COPY --from=sdk /opt/rust/ /opt/judger/rootfs/opt/rust/
COPY --from=sdk /etc/ld.so.cache /opt/judger/rootfs/etc/ld.so.cache
COPY --from=sdk /etc/alternatives/ /opt/judger/rootfs/etc/alternatives/
COPY --from=engine /go-judge /opt/judger/go-judge
COPY judge/judger-agent.py /opt/judger/agent.py
COPY judge/judger-mount.yaml /opt/judger/mount.yaml
COPY --chmod=0755 judge/entrypoint.sh judge/healthcheck.py /opt/judger/
VOLUME ["/app/data"]
EXPOSE 5051
HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=3 CMD python3 /opt/judger/healthcheck.py
ENTRYPOINT ["/opt/judger/entrypoint.sh"]

FROM worker AS bundle
ARG VERSION=1.1.1
LABEL org.opencontainers.image.title="Lume" org.opencontainers.image.version=$VERSION org.opencontainers.image.description="Single-container website, administration, MySQL-backed sessions and isolated five-language judge"
COPY --from=server /opt/java/openjdk/ /opt/application-java/
COPY --from=server /usr/lib/x86_64-linux-gnu/libstdc++.so.6 /usr/lib/x86_64-linux-gnu/libgcc_s.so.1 /opt/application-libs/
COPY --from=server --chown=2000:2000 /app/app.jar /app/app.jar
COPY --chown=2000:2000 docker/data/ /app/data/
COPY --chmod=0755 docker/bundle-entrypoint.py docker/bundle-healthcheck.py /opt/lume/
WORKDIR /app
ENV PYTHONUNBUFFERED=1
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=8s --start-period=90s --retries=3 CMD python3 /opt/lume/bundle-healthcheck.py
ENTRYPOINT ["python3","/opt/lume/bundle-entrypoint.py"]
