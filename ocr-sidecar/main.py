"""教务验证码 OCR 边车：ddddocr 识别 4 位字符验证码。
Spring 后端通过 HTTP POST 图片字节到 /ocr，返回识别文本。
启动：. .venv/bin/activate && uvicorn main:app --host 127.0.0.1 --port 9000
"""
import re

import ddddocr
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

app = FastAPI(title="jw-captcha-ocr")
_ocr = ddddocr.DdddOcr(show_ad=False)


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/ocr")
async def ocr(request: Request):
    img = await request.body()
    if not img:
        return JSONResponse({"code": "", "error": "empty"}, status_code=400)
    try:
        text = _ocr.classification(img)
    except Exception as e:  # noqa: BLE001
        return JSONResponse({"code": "", "error": str(e)}, status_code=500)
    # 强智/深澜验证码一般为 4 位字母数字，做一次清洗
    cleaned = re.sub(r"[^0-9a-zA-Z]", "", text or "")
    return {"code": cleaned, "raw": text}
