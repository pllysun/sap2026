"""Local HTTP fixture for avatar caching, delayed updates and failed downloads."""
import json
import struct
import threading
import time
import zlib
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

state = dict(version=1, delay=0, fail=False, color='red', remove=False)
images = {1: state.copy()}
counts = Counter()
lock = threading.Lock()

def png(color):
    rgb = {'red': (216, 48, 72), 'blue': (30, 105, 218), 'green': (30, 165, 82)}[color]
    def chunk(kind, body):
        return struct.pack('!I', len(body)) + kind + body + struct.pack('!I', zlib.crc32(kind + body))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('!2I5B', 64, 64, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress((b'\0' + bytes(rgb) * 64) * 64)) + chunk(b'IEND', b''))

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, body, code=200, kind='application/json', cache='no-store'):
        if not isinstance(body, bytes):
            body = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header('Content-Type', kind)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', cache)
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        if parsed.path == '/control':
            with lock:
                for key in ('version', 'delay'):
                    if key in query:
                        state[key] = int(query[key][0])
                for key in ('fail', 'remove'):
                    if key in query:
                        state[key] = query[key][0] == '1'
                if 'color' in query:
                    state['color'] = query['color'][0]
                images[state['version']] = state.copy()
            return self.reply(state)
        if parsed.path == '/stats':
            return self.reply(dict(counts))
        with lock:
            counts[self.path] += 1
            current = state.copy()
        if parsed.path == '/avatar.png':
            image = images.get(int(query.get('v', ['1'])[0]), current).copy()
            time.sleep(image['delay'])
            if image['fail']:
                return self.reply({}, code=503)
            return self.reply(png(image['color']), kind='image/png', cache='public, max-age=86400')
        if parsed.path.startswith('/api/auth/info'):
            avatar = None if parsed.path.endswith('/light') or current['remove'] else 'http://10.0.2.2:18891/avatar.png'
            return self.reply(dict(code=200, data=dict(
                user=dict(id=42, studentId='avatar-qa', name='头像缓存验收', avatar=avatar),
                roles=[4], identities=[], appAccessLevel=1, updatedAt=current['version'])))
        self.reply(dict(code=200, data=[]))

print('Avatar QA server listening on 127.0.0.1:18891', flush=True)
ThreadingHTTPServer(('127.0.0.1', 18891), Handler).serve_forever()
