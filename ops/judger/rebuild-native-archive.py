"""Reproduce exact native Docker layer bytes from tar-split headers + local artifacts.
Every uncompressed layer and the image config MUST match the native build hashes.
This avoids transferring the same tested JAR back from a bandwidth-limited server.
"""
import argparse,base64,gzip,hashlib,io,json,tarfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('metadata');p.add_argument('context');p.add_argument('parent_archive');p.add_argument('output')
p.add_argument('--parent-tag',required=True);p.add_argument('--tag',required=True);a=p.parse_args()
meta=json.loads(Path(a.metadata).read_text());context=Path(a.context).resolve();output=Path(a.output);layers=output.parent/('native-rebuilt-layers-'+meta['configId'][7:19]);layers.mkdir(exist_ok=True)
config=base64.b64decode(meta['config']);assert 'sha256:'+hashlib.sha256(config).hexdigest()==meta['configId']
renames={'opt/judger/agent.py':'judger-agent.py','opt/judger/start.sh':'start-judger.sh','opt/judger/node-start.sh':'node-start.sh','opt/judger/health.sh':'judger-health.sh'}
new=[]
for layer in meta['layers']:
    digest=layer['diffId'][7:];target=layers/(digest+'.tar.gz');check=hashlib.sha256()
    with target.open('wb') as out,gzip.GzipFile(fileobj=out,mode='wb',filename='',mtime=0,compresslevel=1) as zipped:
        def write(data):check.update(data);zipped.write(data)
        for row in layer['entries']:
            if row['type']==2:write(base64.b64decode(row['payload']))
            elif row['type']==1:
                size=row.get('size') or 0
                if not size:continue
                name=row['name'];relative=renames.get(name,name[4:] if name.startswith('app/') else None)
                assert relative is not None,name
                source=(context/relative).resolve();source.relative_to(context)
                assert source.is_file() and source.stat().st_size==size,name
                with source.open('rb') as src:
                    while chunk:=src.read(1048576):write(chunk)
            else:raise AssertionError('Unknown tar-split entry')
    assert check.hexdigest()==digest,'Native layer differs: '+digest
    new.append(target)
with tarfile.open(a.parent_archive) as src:
    parents=json.load(src.extractfile('manifest.json'));parent=next(m for m in parents if a.parent_tag in m.get('RepoTags',[]))
    old=json.load(src.extractfile(parent['Config']));assert old['rootfs']['diff_ids']==meta['baseDiffIds']
    final=json.loads(config);assert final['rootfs']['diff_ids']==meta['baseDiffIds']+[x['diffId'] for x in meta['layers']]
    with tarfile.open(output,'w') as out:
        for name in parent['Layers']:out.addfile(src.getmember(name),src.extractfile(name))
        for target in new:out.add(target,arcname='native/'+target.name,recursive=False)
        configName=meta['configId'][7:]+'.json'
        manifest=[{'Config':configName,'RepoTags':[a.tag],'Layers':parent['Layers']+['native/'+x.name for x in new]}]
        for name,data in [(configName,config),('manifest.json',json.dumps(manifest).encode())]:
            entry=tarfile.TarInfo(name);entry.size=len(data);out.addfile(entry,io.BytesIO(data))
print(json.dumps({'passed':True,'configId':meta['configId'],'nativeLayersVerified':len(new),'archive':str(output)}))
