"""Reproduce verified native layers from tar-split metadata and public artifacts.
Receiver must already have the exact verified parent's complete layer chain.
"""
import argparse,base64,gzip,hashlib,io,json,tarfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('metadata');p.add_argument('context');p.add_argument('output');p.add_argument('--tag',required=True);a=p.parse_args()
meta=json.loads(Path(a.metadata).read_text());context=Path(a.context).resolve();output=Path(a.output)
config=base64.b64decode(meta['config']);assert 'sha256:'+hashlib.sha256(config).hexdigest()==meta['configId']
renames={'opt/judger/agent.py':'judger-agent.py','opt/judger/start.sh':'start-judger.sh','opt/judger/node-start.sh':'node-start.sh','opt/judger/health.sh':'judger-health.sh'}
layers=[]
for layer in meta['layers']:
    digest=layer['diffId'][7:];target=output.parent/(digest+'.tar.gz');check=hashlib.sha256()
    with target.open('wb') as out,gzip.GzipFile(fileobj=out,mode='wb',filename='',mtime=0,compresslevel=1) as zipped:
        def write(data):check.update(data);zipped.write(data)
        for row in layer['entries']:
            if row['type']==2:write(base64.b64decode(row['payload']))
            elif row['type']==1:
                size=row.get('size') or 0
                if not size:continue
                name=row['name'];relative=renames.get(name,name[4:] if name.startswith('app/') else None);assert relative is not None,name
                source=(context/relative).resolve();source.relative_to(context);assert source.is_file() and source.stat().st_size==size,name
                with source.open('rb') as src:
                    while chunk:=src.read(1048576):write(chunk)
            else:raise AssertionError('Unknown metadata entry')
    assert check.hexdigest()==digest,'Native layer mismatch: '+digest
    layers.append(target)
expected=meta['baseDiffIds']+[x['diffId'] for x in meta['layers']]
assert json.loads(config)['rootfs']['diff_ids']==expected
with tarfile.open(output,'w:gz',compresslevel=1) as out:
    paths=['cached/'+x[7:]+'/layer.tar' for x in meta['baseDiffIds']]+['native/'+x.name for x in layers]
    for target in layers:out.add(target,arcname='native/'+target.name,recursive=False)
    configName=meta['configId'][7:]+'.json'
    manifest=[{'Config':configName,'RepoTags':[a.tag],'Layers':paths}]
    for name,data in [(configName,config),('manifest.json',json.dumps(manifest).encode())]:
        entry=tarfile.TarInfo(name);entry.size=len(data);out.addfile(entry,io.BytesIO(data))
print(json.dumps({'passed':True,'configId':meta['configId'],'cachedLayers':len(meta['baseDiffIds']),'verifiedNewLayers':len(layers),'bytes':output.stat().st_size}))
