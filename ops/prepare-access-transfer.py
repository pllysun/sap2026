"""Build a verified small transport package using unchanged live JAR bytes."""
import base64,gzip,hashlib,json,pathlib,struct,subprocess,tarfile,zipfile
w=pathlib.Path('/Volumes/Newsmy/sap-access-1.4.87')
basepath=pathlib.Path('/Volumes/Newsmy/sap-president-parity-zy9ux83n/release.jar')
base=basepath.read_bytes(); new=(w/'release.jar').read_bytes(); layer=(w/'layer.tar').read_bytes()
with tarfile.open(w/'layer.tar') as t: jaroffset=t.getmember('app/app.jar').offset_data
def offset(data,info):
    name,extra=struct.unpack_from('<HH',data,info.header_offset+26)
    return info.header_offset+30+name+extra
matches=[]
with zipfile.ZipFile(basepath) as a, zipfile.ZipFile(w/'release.jar') as b:
    for info in b.infolist():
        if info.compress_size<8192: continue
        old=a.getinfo(info.filename)
        x=offset(base,old); y=offset(new,info); n=info.compress_size
        if n==old.compress_size and base[x:x+n]==new[y:y+n]: matches.append((jaroffset+y,x,n))
segments=[]; pos=0
for target,source,n in sorted(matches):
    if target>pos: segments.append({'data':base64.b64encode(layer[pos:target]).decode()})
    segments.append({'offset':source,'size':n}); pos=target+n
if pos<len(layer): segments.append({'data':base64.b64encode(layer[pos:]).decode()})
assert b''.join(base[s['offset']:s['offset']+s['size']] if 'offset' in s else base64.b64decode(s['data']) for s in segments)==layer
config=subprocess.check_output(['crane','config','pllysun/sap:1.4.87'])
# crane prints a trailing newline that is not part of the image config blob.
config=config.rstrip(b'\n')
manifest=json.loads(subprocess.check_output(['crane','manifest','pllysun/sap:1.4.87']))
assert 'sha256:'+hashlib.sha256(config).hexdigest()==manifest['config']['digest']
patch={'baseJarSha256':hashlib.sha256(base).hexdigest(),'layerSha256':hashlib.sha256(layer).hexdigest(),
       'configSha256':hashlib.sha256(config).hexdigest(),'config':base64.b64encode(config).decode(),'segments':segments}
with gzip.open(w/'transfer.json.gz','wb') as f: f.write(json.dumps(patch).encode())
(w/'registry-manifest.json').write_text(json.dumps(manifest,indent=2))
print(json.dumps({'transportBytes':(w/'transfer.json.gz').stat().st_size,'configDigest':manifest['config']['digest']}))
