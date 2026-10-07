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
hydrate_oj = '--hydrate-oj-rollback' in sys.argv[3:]
if hydrate_oj:
    import os
    for path in ['/tmp/sap-oj-1513-rollback.py', '/tmp/sap-oj-pymysql.whl']:
        if not os.path.isfile(path):raise SystemExit('OJ rollback prerequisites missing')
judger = '--judger' in sys.argv[3:] or "JUDGER_ENABLED=true" in old["Config"]["Env"]
judge_paths=[]
if judger:
    import os
    exec(open('/usr/local/lib/sap/judger-host.py').read())
    judge_paths=prepare()
    if not os.path.isfile('/etc/sap/judger-seccomp.json'):
        raise SystemExit('Judge seccomp profile missing')
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
    if judger and env.startswith("JUDGER_ENABLED="): continue
    args += ["-e", env]
if judger:
    args += ["-e","JUDGER_ENABLED=true","--cap-add=SYS_ADMIN","--cap-add=SYS_PTRACE",
             "--cap-add=SYS_RESOURCE","--security-opt=systempaths=unconfined",
             "--security-opt=seccomp=/etc/sap/judger-seccomp.json"]
    for path in judge_paths: args += ["-v",path+":"+os.path.dirname(path)+":rw"]
for binding in old["HostConfig"].get("Binds") or []:
    if judger and binding.startswith("/sys/fs/cgroup/"): continue
    args += ["-v", binding]
for target, bindings in old["HostConfig"]["PortBindings"].items():
    for port in bindings:
        ip = port.get("HostIp", "")
        if ":" in ip:
            ip = "[" + ip + "]"
        args += ["-p", (ip + ":" if ip else "") + port["HostPort"] + ":" + target]
health="wget -qO- http://localhost:8081/api/ping >/dev/null"
if judger:
    # Node stops are intentional; only the lightweight controller must stay up.
    health += ' && wget -qO /dev/null --header="Authorization: Bearer $(cat /run/judger/token)" http://127.0.0.1:5051/status'
args += ["--health-cmd", health+" || exit 1",
         "--health-interval", "30s", "--health-timeout", "5s", "--health-retries", "3",
         "--health-start-period", "90s", image]
docker("stop", "--time", "30", "sap")
docker("rename", "sap", backup)
try:
    docker(*args)
    for _ in range(150):
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
    if hydrate_oj:
        # Stop the new worker/compactor before expanding snapshots. Use a separate
        # process so this remains possible even if its application failed to boot.
        subprocess.run(['docker','stop','--time','30','sap'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        restore=['run','--rm','--network',old['HostConfig']['NetworkMode'],
            '--entrypoint','python3','-v','/tmp/sap-oj-1513-rollback.py:/rollback.py:ro',
            '-v','/tmp/sap-oj-pymysql.whl:/pymysql.whl:ro']
        for env in old['Config']['Env']:
            if env.startswith(('MYSQL_URL=','MYSQL_USER=','MYSQL_PASSWORD=')):restore+=['-e',env]
        try:print(docker(*restore,image,'/rollback.py','/pymysql.whl'),flush=True)
        except Exception:
            raise SystemExit('Deployment failed; snapshot expansion failed. Previous container retained and stopped to protect historical jobs.')
    subprocess.run(["docker", "rm", "-f", "sap"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    docker("rename", backup, "sap")
    docker("start", "sap")
    raise SystemExit("Deployment failed; previous container restored")
