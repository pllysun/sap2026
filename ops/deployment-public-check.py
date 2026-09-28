"""Verify public deployment artifacts and anonymous access denial, without login."""
import hashlib
import json
import urllib.request

base = "https://csuftsap.top"
def get(path):
    with urllib.request.urlopen(base + path, timeout=30) as response:
        return response.status, response.read()

checks = {}
for path, sha in (("/admin/", "0b559134191c5f6733d9e2e4e537d877dcfaa6a66ce920fec592d4e2448ceb00"),
                  ("/", "45e06a0fba379d0afcfaf54760ced86686c1e30912056a01ee20af8da1445aa0")):
    status, body = get(path)
    assert status == 200 and hashlib.sha256(body).hexdigest() == sha, path
    checks[path] = "200, matches built artifact"
for path in ("/api/log/explore?dimension=detail", "/api/log/explore?archive=true&dimension=user", "/api/log/calendar-year?year=2026"):
    try:
        _, body = get(path)
        result = json.loads(body)
        assert result["code"] in (401, 403), (path, result.get("code"))
    except urllib.error.HTTPError as error:
        assert error.code in (401, 403), (path, error.code)
    checks[path] = "anonymous denied"
assert get("/api/ping")[0] == 200
checks["/api/ping"] = "200"
print(json.dumps(checks))
