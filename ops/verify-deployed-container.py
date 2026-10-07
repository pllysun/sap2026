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
clean_env=lambda c: sorted(e for e in c["Config"]["Env"] if not e.startswith("JUDGER_ENABLED="))
assert clean_env(current) == clean_env(previous)
assert "JUDGER_ENABLED=true" in current["Config"]["Env"]
business_binds=lambda c: sorted(b for b in c["HostConfig"].get("Binds") or [] if not b.startswith("/sys/fs/cgroup/"))
assert business_binds(current) == business_binds(previous)
assert not current["HostConfig"]["Privileged"]
assert set(current["HostConfig"]["CapAdd"] or []) == {"SYS_ADMIN","SYS_PTRACE","SYS_RESOURCE"}
assert not any(p.startswith("5050/") for p in current["HostConfig"]["PortBindings"])
for field in ("PortBindings", "RestartPolicy", "NetworkMode"):
    assert current["HostConfig"][field] == previous["HostConfig"][field], field
for path, expected in (("/app/app.jar", jar), ("/app/static/admin/index.html", admin), ("/app/static/user/index.html", user)):
    assert docker("exec", "sap", "sha256sum", path).split()[0] == expected, path
print(json.dumps({"image": image, "healthy": True, "configurationPreserved": True,
    "artifactsVerified": True, "restartCount": current["RestartCount"],
    "rollbackContainer": previous["Name"].lstrip("/")}))
