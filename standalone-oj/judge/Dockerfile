# syntax=docker/dockerfile:1.7
FROM --platform=$BUILDPLATFORM golang:1.26.0-bookworm AS engine
ENV GOMAXPROCS=2
ARG GOPROXY=https://proxy.golang.org,direct
WORKDIR /src
COPY judge/go-judge/ ./
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build GOPROXY="$GOPROXY" CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -tags seccomp -trimpath -ldflags '-s -w' -o /go-judge ./cmd/go-judge
FROM pllysun/sap-judger-sdk@sha256:ff9484d4ba7b2a510cd49b10e701d8ff2106a86f19cf36ee8fb2049298914ca0 AS sdk
FROM python:3.12-slim-bookworm
ARG VERSION=1.1.1
LABEL org.opencontainers.image.title="Lume worker" org.opencontainers.image.version=$VERSION org.opencontainers.image.licenses="MIT" org.opencontainers.image.description="Dedicated authenticated five-language sandbox; no application credentials"
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
