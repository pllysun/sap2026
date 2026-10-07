"""Install the proven judge budget before Docker starts; no Docker restart needed."""
import os,subprocess
os.makedirs('/usr/local/lib/sap',exist_ok=True)
os.makedirs('/etc/sap',exist_ok=True)
os.makedirs('/etc/systemd/system/docker.service.d',exist_ok=True)
source='/tmp/sap-oj-build/host-runtime.py'
with open('/usr/local/lib/sap/judger-host.py','w') as out:
    out.write(open(source).read()+'\nif __name__ == "__main__": prepare()\n')
os.chmod('/usr/local/lib/sap/judger-host.py',0o644)
with open('/etc/sysctl.d/90-sap-judger.conf','w') as out:
    out.write('# Dedicated go-judge namespaces, bounded by its own cgroups.\nuser.max_user_namespaces = 1024\n')
with open('/etc/sap/judger-seccomp.json','w') as out: out.write(open('/tmp/sap-oj-build/seccomp.json').read())
os.chmod('/etc/sap/judger-seccomp.json',0o644)
with open('/etc/systemd/system/sap-judger-host.service','w') as out:
    out.write('''[Unit]
Description=SAP judge dedicated cgroups
After=systemd-sysctl.service
Before=docker.service

[Service]
Type=oneshot
ExecStart=/usr/bin/python3 /usr/local/lib/sap/judger-host.py
RemainAfterExit=yes

[Install]
WantedBy=multi-user.target
''')
with open('/etc/systemd/system/docker.service.d/sap-judger.conf','w') as out:
    out.write('[Unit]\nRequires=sap-judger-host.service\nAfter=sap-judger-host.service\n')
subprocess.check_call(['systemctl','daemon-reload'])
subprocess.check_call(['systemctl','enable','sap-judger-host.service'])
subprocess.check_call(['systemctl','start','sap-judger-host.service'])
print('Judge host service enabled; Docker was not restarted')
