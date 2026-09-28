"""Local catalog fixture: delayed loading, forced refresh and retryable errors."""
import json
import time
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

state = dict(delay=3, fail=0)
counts = Counter()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, data):
        body = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_GET(self):
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        if parsed.path == '/control':
            for key in state:
                if key in query:
                    state[key] = int(query[key][0])
            return self.reply(state)
        if parsed.path == '/stats':
            return self.reply(dict(counts))
        counts[self.path] += 1
        if parsed.path.startswith('/api/class-schedule/'):
            snapshot = state.copy()
            time.sleep(snapshot['delay'])
            if snapshot['fail']:
                return self.reply(dict(code=500, msg='目录加载失败，请重试'))
        if parsed.path.endswith('/terms'):
            data = [dict(value=term, label=term) for term in ('2026-2027-1', '2025-2026-2')]
        elif parsed.path.endswith('/classes'):
            data = [dict(term=query['term'][0], college='测试学院', grade='2023', major='示例专业', className=name)
                    for name in ('2023人工智能2班', '2024城乡规划1班')]
        elif parsed.path.endswith('/schedule'):
            data = dict(courses=[dict(name='智慧农林', day=3, sectionIndex=1, weeks='1-6', room='树人楼南501')],
                        remarks=[], semesterStartDate='2026-09-07')
        elif 'academic-calendar' in parsed.path:
            data = {}
        else:
            data = []
        self.reply(dict(code=200, data=data))


print('Class schedule QA server: 127.0.0.1:18892', flush=True)
ThreadingHTTPServer(('127.0.0.1', 18892), Handler).serve_forever()
