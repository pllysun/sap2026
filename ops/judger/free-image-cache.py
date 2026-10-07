"""Reclaim only unused, registry-backed SAP image cache; never removes containers/data."""
import json,os,subprocess,time,re
run=lambda *a:subprocess.check_output(['docker',*a],encoding='utf-8').strip()
used={c['Image'] for c in json.loads(run('inspect',*run('ps','-aq').splitlines()))}
images={i['Id']:i for i in json.loads(run('image','inspect',*run('images','-q').splitlines()))}
removed=[]
for image in sorted(images.values(),key=lambda i:i['Created']):
    stat=os.statvfs('/');free=stat.f_bavail*stat.f_frsize
    if free>=9*1024**3:break
    tags=image.get('RepoTags') or []
    digests=image.get('RepoDigests') or []
    if image['Id'] in used or not digests or not all('/pllysun/sap@' in '/'+d for d in digests):continue
    def old_app_tag(tag):
        match=re.search(r'(?:^|/)pllysun/sap:v?(\d+)\.(\d+)\.(\d+)$',tag)
        return bool(match and tuple(map(int,match.groups()))<(1,4,114))
    if tags and not all(old_app_tag(t) for t in tags):continue
    subprocess.check_call(['docker','image','rm',*(tags or [image['Id']])],stdout=subprocess.DEVNULL)
    removed.append({'tags':tags,'digests':image['RepoDigests']})
os.makedirs('/tmp/sap-oj-build',exist_ok=True)
with open('/tmp/sap-oj-build/reclaimed-image-cache.json','w') as out:json.dump(removed,out)
stat=os.statvfs('/');print(json.dumps({'unusedRegistryBackedImagesRemoved':len(removed),'freeGiB':round(stat.f_bavail*stat.f_frsize/1024**3,2),'containersAndDataPreserved':True}))
