"""Read-only: python3 - IMAGE CONFIG_DIGEST JAR_SHA ADMIN_SHA USER_SHA over SSH.
Checks deployment against the retained previous container without exposing secrets.
"""
import json
import subprocess
import sys

def docker(*args):
    return subprocess.check_output(["docker", *args], encoding="utf-8").strip()

image, config, jar, admin, user = sys.argv[1:]
containers = json.loads(docker("inspect", *docker("ps", "-aq").splitlines()))
current = next(c for c in containers if c["Name"] == "/sap")
previous = max((c for c in containers if c["Name"].startswith("/sap-rollback-")), key=lambda c: c["Name"])
assert current["Config"]["Image"] == image
assert current["Image"] == config
assert current["State"]["Health"]["Status"] == "healthy"
assert sorted(current["Config"]["Env"]) == sorted(previous["Config"]["Env"])
assert sorted(current["HostConfig"].get("Binds") or []) == sorted(previous["HostConfig"].get("Binds") or [])
for field in ("PortBindings", "RestartPolicy", "NetworkMode"):
    assert current["HostConfig"][field] == previous["HostConfig"][field], field
for path, expected in (("/app/app.jar", jar), ("/app/static/admin/index.html", admin), ("/app/static/user/index.html", user)):
    assert docker("exec", "sap", "sha256sum", path).split()[0] == expected, path
print(json.dumps({"image": image, "healthy": True, "configurationPreserved": True,
    "artifactsVerified": True, "restartCount": current["RestartCount"],
    "rollbackContainer": previous["Name"].lstrip("/")}))
