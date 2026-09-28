"""Local App registration fixture. No real accounts or email are sent.

Graphic code A7K2, email code 654321. /control?captcha_fail=1&delay=2
exercises loading and retry states. Passwords/codes in requests are never logged.
"""
import base64
import io
import json
import re
import threading
import time
import uuid
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse
from PIL import Image, ImageDraw, ImageFont

state = dict(captcha_fail=0, delay=0)
counts = Counter()
challenges = set()
email_challenges = {}
accounts = set()
lock = threading.Lock()


def captcha_image():
    image = Image.new('RGB', (160, 50), 'white')
    draw = ImageDraw.Draw(image)
    draw.text((27, 8), 'A7K2', font=ImageFont.load_default(size=32), fill='#3564DC')
    output = io.BytesIO()
    image.save(output, format='PNG')
    return 'data:image/png;base64,' + base64.b64encode(output.getvalue()).decode()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, data, code=200, message='success', raw=False):
        body = json.dumps(data if raw else dict(code=code, message=message, data=data), ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == '/stats':
            with lock:
                snapshot = dict(counts, created_accounts=len(accounts))
            return self.reply(snapshot, raw=True)
        if parsed.path == '/control':
            with lock:
                for key, values in parse_qs(parsed.query).items():
                    if key in state:
                        state[key] = max(0, min(10, int(values[0])))
                snapshot = state.copy()
            return self.reply(snapshot, raw=True)
        with lock:
            counts[parsed.path] += 1
            config = state.copy()
        if parsed.path == '/api/ping':
            return self.reply('pong')
        if parsed.path == '/api/auth/captcha':
            time.sleep(config['delay'])
            if config['captcha_fail']:
                return self.reply(None, 500, '验证码暂时不可用，请点击重新获取')
            with lock:
                challenge = 'captcha-' + str(counts[parsed.path])
                challenges.add(challenge)
            return self.reply(dict(captchaId=challenge, image=captcha_image()))
        return self.reply(None, 404, '未找到接口')

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))))
        with lock:
            counts[self.path] += 1
            config = state.copy()
        time.sleep(config['delay'])
        if self.path not in ('/api/auth/app/register', '/api/auth/app/register/email-code'):
            return self.reply(None, 400, '本地验收仅验证 App 注册')
        if not (re.fullmatch(r'[A-Za-z0-9_-]{1,20}', body.get('studentId', ''))
                and 1 <= len(body.get('name', '').strip()) <= 50
                and re.fullmatch(r'[1-9][0-9]{4,14}', body.get('qq', ''))):
            return self.reply(None, 400, '注册字段不符合规则')
        if self.path.endswith('/email-code'):
            if 'password' in body:
                return self.reply(None, 400, '验证码申请不应传送密码')
            if not body.get('captchaId') or not body.get('captchaCode'):
                return self.reply(dict(captchaRequired=True))
            with lock:
                valid = body['captchaId'] in challenges
                challenges.discard(body['captchaId'])
            if not valid or body['captchaCode'].upper() != 'A7K2':
                return self.reply(None, 400, '图形验证码错误或已过期，请刷新后重试')
            request_id = str(uuid.uuid4())
            with lock:
                email_challenges[request_id] = (body['studentId'], body['qq'])
            return self.reply(dict(requestId=request_id, email=body['qq']+'@qq.com', cooldownSeconds=180, expiresInSeconds=900))
        if not (6 <= len(body.get('password', '')) <= 64 and body.get('password', '').strip() and body.get('gender') in (0, 1)):
            return self.reply(None, 400, '注册字段不符合规则')
        with lock:
            identity = (body['studentId'], body['qq'])
            valid = email_challenges.get(body.get('emailRequestId')) == identity and body.get('emailCode') == '654321'
            exists = body['studentId'] in accounts
            if valid and not exists:
                accounts.add(body['studentId'])
                email_challenges.pop(body['emailRequestId'])
        if not valid:
            return self.reply(None, 400, '邮箱验证码错误、已过期或已使用，请核对学号和 QQ 邮箱后重试')
        if exists:
            return self.reply(None, 400, '学号已注册')
        return self.reply('注册成功')


if __name__ == '__main__':
    print('Registration QA server: 127.0.0.1:18893 (graphic A7K2, email 654321)', flush=True)
    ThreadingHTTPServer(('127.0.0.1', 18893), Handler).serve_forever()
