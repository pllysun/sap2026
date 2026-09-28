"""Verify App-only QQ email registration in the disposable, network-disabled image.

Run init, inspect /tmp/app-registration-captcha.png, then finish GRAPHIC_CODE.
Requires SAP_REGISTRATION_PREFLIGHT=1, no MYSQL_URL, and the local SMTP fixture.
Never use on production: this configures test SMTP and creates two synthetic users.
"""
import base64
import copy
import json
import os
import re
import sys
import time
import urllib.request
from pathlib import Path

assert os.environ.get('SAP_REGISTRATION_PREFLIGHT') == '1'
assert not os.environ.get('MYSQL_URL')
BASE = 'http://127.0.0.1:8081'
state_file = Path('/tmp/app-registration-state.json')

def api(path, body=None, method=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['sap-token'] = token
    request = urllib.request.Request(BASE + path, headers=headers,
        data=json.dumps(body).encode() if body is not None else None,
        method=method or ('POST' if body is not None else 'GET'))
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)

def ok(result):
    assert result['code'] == 200, result.get('message')
    return result.get('data')

def rejected(result):
    assert result['code'] != 200, 'Request unexpectedly succeeded'

if sys.argv[1] == 'init':
    token = ok(api('/api/auth/admin/login', json.loads(Path(os.environ['SAP_TEST_AUTH_FILE']).read_text())))['token']
    hooks = ok(api('/api/email/hooks', token=token))
    assert any(h.get('eventKey') == 'APP_REGISTRATION_CODE' for h in hooks)
    binding = next(h for h in ok(api('/api/email/bindings', token=token)) if h['eventKey'] == 'APP_REGISTRATION_CODE')
    assert binding['templateId'] and binding['compatible']
    template = ok(api('/api/email/templates/' + str(binding['templateId']), token=token))
    assert template['enabled'] and '{{email}}' in template['htmlContent']
    cfg = ok(api('/api/setting/registration-protection', token=token))
    policy = copy.deepcopy(cfg['config'])
    policy['captcha']['enabled'] = False
    policy['quotas']['enabled'] = False
    policy['requests']['enabled'] = False
    ok(api('/api/setting/registration-protection', {'revision':cfg['revision'], 'config':policy}, 'PUT', token))
    ok(api('/api/email/config', {'host':'127.0.0.1', 'port':'2525','username':'fixture@qq.com',
        'password':'isolated-fixture-only', 'ssl':'false', 'starttls':'false', 'enabled':'true'}, 'PUT', token))
    suffix = str(int(time.time()))
    Path('/tmp/smtp-capture.json').unlink(missing_ok=True)
    form = {'studentId':'app-' + suffix, 'name':'手机注册隔离验证','qq':suffix,'gender':0,'password':'Isolated-Password-Only'}
    # Web remains independently controlled and needs no email challenge.
    ok(api('/api/auth/register', dict(form, studentId='web-' + suffix)))
    rejected(api('/api/auth/app/register', form))
    assert ok(api('/api/auth/app/register/email-code', {k:form[k] for k in ('studentId','name','qq')}))['captchaRequired']
    challenge = ok(api('/api/auth/captcha'))
    Path('/tmp/app-registration-captcha.png').write_bytes(base64.b64decode(challenge['image'].split(',')[1]))
    state_file.write_text(json.dumps({'form':form, 'token':token, 'captchaId':challenge['captchaId']}))
    state_file.chmod(0o600)
    print(json.dumps({'phase':'init', 'passed':['template_auto_install','compatible_hook_binding','web_without_email','app_requires_email','app_graphic_required_even_when_web_disabled']}))
else:
    state = json.loads(state_file.read_text()); form = state['form']; token = state['token']
    request = {k:form[k] for k in ('studentId','name','qq')}
    request.update(captchaId=state['captchaId'],captchaCode=sys.argv[2])
    issued = ok(api('/api/auth/app/register/email-code', request))
    state['issued']=issued
    state_file.write_text(json.dumps(state))
    assert issued['email']==form['qq']+'@qq.com' and int(issued['expiresInSeconds'])==900 and int(issued['cooldownSeconds'])==180
    assert 'code' not in issued
    for _ in range(20):
        if Path('/tmp/smtp-capture.json').exists():
            break
        time.sleep(1)
    mail = json.loads(Path('/tmp/smtp-capture.json').read_text())
    from email import policy
    from email.parser import BytesParser
    msg = BytesParser(policy=policy.default).parsebytes(base64.b64decode(mail['message']))
    html = msg.get_content()
    code = re.search(r'>\s*([0-9]{6})\s*<',html).group(1)
    assert form['qq']+'@qq.com' in html and 'App' in html and '{{' not in html
    assert code not in str(msg['Subject']) and form['qq']+'@qq.com' in str(msg['To'])
    verified = dict(form, emailRequestId=issued['requestId'], emailCode=code)
    rejected(api('/api/auth/app/register', dict(verified, qq='100000002')))
    rejected(api('/api/auth/app/register', dict(verified, emailCode='wrong')))
    ok(api('/api/auth/app/register',verified))
    rejected(api('/api/auth/app/register',verified))
    ok(api('/api/auth/login', {k:form[k] for k in ('studentId','password')}))
    logs = ok(api('/api/email/delivery/logs?size=100',token=token))['records']
    assert any(r['event_key']=='APP_REGISTRATION_CODE' and r['status']=='SUCCESS' for r in logs)
    assert code not in json.dumps(logs)
    assert any(r['event_key']=='ACCOUNT_REGISTERED' for r in logs)
    print(json.dumps({'phase':'finish','passed':['real_smtp_fixture','rendered_email','qq_identity_binding','valid_code_creates_account','one_time_consumption','new_account_login','registration_success_hook','secret_free_logs']}))
