"""Exercise the packaged server in a disposable H2 container with networking disabled.

Requires SAP_REGISTRATION_PREFLIGHT=1 and a mounted SAP_TEST_AUTH_FILE.
Never run against production: this changes protection settings and creates one
synthetic account. The caller removes the container and its anonymous volumes.
"""
import copy
import json
import os
import subprocess
import sys
import time
import urllib.request

assert os.environ.get("SAP_REGISTRATION_PREFLIGHT") == "1", "Isolated preflight only"
assert not os.environ.get("MYSQL_URL"), "Preflight must use its own H2 database"
BASE = "http://127.0.0.1:8081"
PATH = "/api/setting/registration-protection"


def api(path, body=None, method=None, token=None, headers=None):
    request_headers = {"Content-Type": "application/json"}
    if token:
        request_headers["sap-token"] = token
    request_headers.update(headers or {})
    request = urllib.request.Request(
        BASE + path, data=json.dumps(body).encode() if body is not None else None,
        headers=request_headers, method=method or ("POST" if body is not None else "GET"))
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)


def expect(result, code, label):
    assert result.get("code") == code, "%s: code=%s, message=%s" % (
        label, result.get("code"), result.get("message"))
    return result.get("data")


with open(os.environ["SAP_TEST_AUTH_FILE"]) as file:
    credentials = json.load(file)
admin_token = expect(api("/api/auth/admin/login", credentials), 200, "admin login")["token"]


def read():
    return expect(api(PATH, token=admin_token), 200, "read configuration")


def save(config, revision=None):
    return expect(api(PATH, {"revision": revision or read()["revision"], "config": config},
                      method="PUT", token=admin_token), 200, "save configuration")


if len(sys.argv) > 1 and sys.argv[1] == "persisted":
    assert read()["config"]["quotas"]["ipHourlyLimit"] == 7
    print(json.dumps({"status": "passed", "check": "configuration_survives_container_restart"}))
    sys.exit(0)

expect(api(PATH), 401, "anonymous configuration access")
assert expect(api("/api/email/config", token=admin_token), 200, "isolated SMTP configuration")["configured"] is False
# Make a repeated check deterministic without touching login sessions or production data.
save(read()["defaults"])
keys = subprocess.check_output(["redis-cli", "--raw", "--scan", "--pattern", "rl:*"], text=True).splitlines()
if keys:
    subprocess.check_output(["redis-cli", "DEL", *keys])
initial = read()
baseline = copy.deepcopy(initial["config"])
assert baseline["captcha"]["enabled"] and baseline["captcha"]["freeLimit"] == 0

invalid = copy.deepcopy(baseline)
invalid["requests"]["registerPerMinute"] = 0
expect(api(PATH, {"revision": initial["revision"], "config": invalid}, "PUT", admin_token),
       400, "invalid rate")
invalid = copy.deepcopy(baseline)
invalid["quotas"]["maxConcurrentPerIp"] = invalid["quotas"]["maxConcurrent"] + 1
expect(api(PATH, {"revision": initial["revision"], "config": invalid}, "PUT", admin_token),
       400, "invalid concurrency")
assert read()["revision"] == initial["revision"]

policy = copy.deepcopy(baseline)
policy["captcha"]["enabled"] = False
policy["quotas"]["enabled"] = False
policy["requests"].update(registerCapacity=2, registerPerMinute=1, captchaCapacity=2, captchaPerMinute=1)
policy["trustedProxies"] = ""
saved = save(policy)
expect(api(PATH, {"revision": initial["revision"], "config": baseline}, "PUT", admin_token),
       409, "stale administrator revision")
assert read()["revision"] == saved["revision"]

# Existing isolated administrator: admission consumes quota but never creates an account or email.
registration = {"studentId": credentials["studentId"], "password": "Preflight-Only-Password",
                "name": "注册防护隔离验证", "gender": 0, "qq": "100000001"}
for index in range(3):
    result = api("/api/auth/register", registration, headers={"X-Real-IP": "198.51.100." + str(index + 1),
                 "X-Forwarded-For": "198.51.100." + str(index + 10)})
    expect(result, 500 if index < 2 else 429, "registration request quota with forged headers")
    if index < 2:
        assert "已注册" in result.get("message", "")
for index in range(3):
    result = api("/api/auth/captcha")
    data = expect(result, 200 if index < 2 else 429, "captcha image request quota")
    if index < 2:
        assert data["captchaId"] and data["image"].startswith("data:image/")

policy["requests"]["enabled"] = False
policy["quotas"].update(enabled=True, cooldownSeconds=1, ipHourlyLimit=1)
save(policy)
assert "已注册" in api("/api/auth/register", registration).get("message", "")
time.sleep(1.1)
expect(api("/api/auth/register", registration), 429, "database configured hourly quota")
policy["quotas"]["ipHourlyLimit"] = 2
save(policy)
time.sleep(1.1)
assert "已注册" in api("/api/auth/register", registration).get("message", "")

policy["quotas"]["enabled"] = False
policy["captcha"]["enabled"] = True
save(policy)
assert expect(api("/api/auth/register", registration), 200, "mandatory captcha")["captchaRequired"]
expect(api("/api/auth/register", dict(registration, captchaId="missing-image", captchaCode="wrong")),
       400, "invalid captcha")
expect(api("/api/setting/value?key=REGISTRATION_PROTECTION_QUOTAS", token=admin_token),
       403, "generic settings read bypass")
expect(api("/api/setting", {"settingKey": "REGISTRATION_PROTECTION_QUOTAS", "settingValue": "{}"},
           "PUT", admin_token), 403, "generic settings write bypass")

# The isolated server has no external network or SMTP credentials.
policy["captcha"]["enabled"] = False
save(policy)
synthetic = dict(registration, studentId="guard-" + os.urandom(4).hex(), name="隔离环境测试账号")
expect(api("/api/auth/register", synthetic), 200, "isolated normal registration")
ordinary_token = expect(api("/api/auth/login", {"studentId": synthetic["studentId"],
                       "password": synthetic["password"]}), 200, "ordinary user login")["token"]
expect(api(PATH, token=ordinary_token), 403, "ordinary user cannot read policy")
expect(api(PATH, {"revision": read()["revision"], "config": baseline}, "PUT", ordinary_token),
       403, "ordinary user cannot write policy")
assert int(expect(api("/api/email/delivery/status", token=admin_token), 200, "final mail status")["queue"]) == 0

baseline["quotas"]["ipHourlyLimit"] = 7
save(baseline)
assert read()["config"] == baseline
print(json.dumps({"status": "passed", "checks": ["anonymous_access", "parameter_validation",
    "version_conflict", "request_quota", "forged_ip_headers", "captcha_quota", "live_hourly_quota",
    "captcha_switch", "generic_settings_bypass", "normal_registration", "role_permissions",
    "no_email_queued", "configuration_persisted"], "syntheticAccounts": 1}))
