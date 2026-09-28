"""Run over SSH stdin: python3 - IMAGE EXPECTED_CURRENT.

Preserves the live container's environment, published ports, bind mounts and
restart policy. Does not print secrets. Keeps the stopped old container for rollback.
"""
import json
import subprocess
import sys
import time


def docker(*args):
    return subprocess.check_output(["docker", *args], encoding="utf-8").strip()


image, expected = sys.argv[1:3]
old = json.loads(docker("inspect", "sap"))[0]
if old["Config"]["Image"] != expected:
    raise SystemExit("Live image changed; aborting deployment")
if json.loads(docker("image", "inspect", image))[0]["Architecture"] != "amd64":
    raise SystemExit("Expected amd64 image")
docker("run", "--rm", "--entrypoint", "/bin/sh", image, "-c",
       "test -x /app/entrypoint.sh && test -r /app/app.jar && test -r /app/static/admin/index.html")
docker("run", "--rm", "--user", "nobody", "--entrypoint", "/bin/sh", image, "-c",
       "test -r /app/static/admin/index.html && test -r /app/static/user/index.html")
backup = "sap-rollback-" + time.strftime("%Y%m%d-%H%M%S")
args = ["run", "-d", "--name", "sap"]
policy = old["HostConfig"]["RestartPolicy"]
restart = policy["Name"] or "no"
if restart == "on-failure" and policy.get("MaximumRetryCount"):
    restart += ":" + str(policy["MaximumRetryCount"])
args += ["--restart", restart]
args += ["--network", old["HostConfig"]["NetworkMode"]]
for env in old["Config"]["Env"]:
    args += ["-e", env]
for binding in old["HostConfig"].get("Binds") or []:
    args += ["-v", binding]
for target, bindings in old["HostConfig"]["PortBindings"].items():
    for port in bindings:
        ip = port.get("HostIp", "")
        if ":" in ip:
            ip = "[" + ip + "]"
        args += ["-p", (ip + ":" if ip else "") + port["HostPort"] + ":" + target]
args += ["--health-cmd", "wget -qO- http://localhost:8081/api/ping >/dev/null || exit 1",
         "--health-interval", "30s", "--health-timeout", "5s", "--health-retries", "3",
         "--health-start-period", "45s", image]
docker("stop", "--time", "30", "sap")
docker("rename", "sap", backup)
try:
    docker(*args)
    for _ in range(90):
        state = json.loads(docker("inspect", "sap"))[0]["State"]
        if state.get("Health", {}).get("Status") == "healthy":
            print(json.dumps({"image": image, "status": "healthy", "rollbackContainer": backup}), flush=True)
            break
        if state.get("Status") in ("exited", "dead"):
            raise RuntimeError("New container exited")
        time.sleep(2)
    else:
        raise RuntimeError("Health check timeout")
except Exception:
    subprocess.run(["docker", "rm", "-f", "sap"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    docker("rename", backup, "sap")
    docker("start", "sap")
    raise SystemExit("Deployment failed; previous container restored")
