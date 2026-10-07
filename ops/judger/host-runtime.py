"""Dedicated cgroup v1 budget for the single-container judge. Python 3.6 compatible."""
import os

def prepare(prefix='sapjudger'):
    paths=[]
    for controller in ['memory','pids','cpu','cpuacct','cpuset']:
        parent=os.path.realpath('/sys/fs/cgroup/'+controller)
        if not os.path.isdir(parent):
            raise RuntimeError('Required cgroup controller unavailable: '+controller)
        path=parent+'/'+prefix
        if path in paths:
            continue
        os.makedirs(path,exist_ok=True)
        paths.append(path)
        limits={'memory.limit_in_bytes':str(640*1024*1024),'pids.max':'256',
                'cpu.cfs_period_us':'100000','cpu.cfs_quota_us':'100000'}
        for name,value in limits.items():
            if os.path.isfile(path+'/'+name):
                with open(path+'/'+name,'w') as target: target.write(value)
        if controller=='cpuset':
            for name in ['cpuset.cpus','cpuset.mems']:
                with open(path+'/'+name,'w') as target: target.write(open(parent+'/'+name).read())
    with open('/proc/sys/user/max_user_namespaces','w') as target: target.write('1024')
    return paths
