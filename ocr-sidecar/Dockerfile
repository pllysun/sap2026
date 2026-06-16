# 教务验证码 OCR 边车：ddddocr + FastAPI。
# 注：正式部署已把 OCR 打进主镜像 docker/Dockerfile（同容器 127.0.0.1:9000），本文件仅作
#     "未来拆分为独立容器" 的备用方案，正式流程用不到。独立部署时后端用 JW_OCRURL 指向它。
FROM python:3.11-slim

# onnxruntime 运行依赖 OpenMP
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py .

EXPOSE 9000

# 容器内必须监听 0.0.0.0（跨容器可达）。切勿用 -p 暴露公网：/ocr 无鉴权。
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "9000"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:9000/health').getcode()==200 else 1)"
