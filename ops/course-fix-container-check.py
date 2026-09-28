"""Read-only post-deploy checks; run over SSH stdin. Never print environment secrets."""
import json
import subprocess


def docker(*args):
    return subprocess.check_output(["docker", *args], encoding="utf-8").strip()


containers = json.loads(docker("inspect", *docker("ps", "-aq").splitlines()))
current = next(c for c in containers if c["Name"] == "/sap")
backups = [c for c in containers if c["Name"].startswith("/sap-rollback-")]
previous = max(backups, key=lambda c: c["Name"])
assert current["Config"]["Image"] == "pllysun/sap:1.4.91"
assert current["Image"] == "sha256:7de2d8e3d5b7aed59cfa235394d2f10fe9ca1d2096ce247cf8ba503ef58c63de"
assert current["State"]["Health"]["Status"] == "healthy"
assert sorted(current["Config"]["Env"]) == sorted(previous["Config"]["Env"])
for field in ("Binds", "PortBindings", "RestartPolicy", "NetworkMode"):
    assert current["HostConfig"][field] == previous["HostConfig"][field], field
jar_hash = docker("exec", "sap", "sha256sum", "/app/app.jar").split()[0]
assert jar_hash == "b2f7af9b9b4093d562b0589f00d341b039a3b79ef17a9cba0b1099b72ee9b7e3"
print(json.dumps({"image": current["Config"]["Image"], "healthy": True,
                  "configurationPreserved": True, "jarVerified": True,
                  "restartCount": current["RestartCount"],
                  "rollbackContainer": previous["Name"].lstrip("/")}))
