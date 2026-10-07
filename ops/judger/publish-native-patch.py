"""Publish the verified native image without changing either server's daemon.
Uses the user's existing Docker credential helper and local HTTPS proxy; never
prints credentials or signed upload URLs. Parent manifest/config must match.
"""
import base64,hashlib,json,subprocess,sys,time,urllib.error,urllib.parse,urllib.request
from pathlib import Path

root=Path(sys.argv[1]);meta=json.loads((root/'native-metadata.json').read_text())
repo='pllysun/sap';base='https://registry-1.docker.io/v2/'+repo
manifest_type='application/vnd.docker.distribution.manifest.v2+json'
opener=urllib.request.build_opener(urllib.request.ProxyHandler({'https':'http://127.0.0.1:7890'}))
credentials=json.loads(subprocess.check_output(['docker-credential-desktop','get'],input=b'https://index.docker.io/v1/'))
token=None;token_at=0

def authorize():
    global token,token_at
    basic=base64.b64encode((credentials['Username']+':'+credentials['Secret']).encode()).decode()
    url='https://auth.docker.io/token?'+urllib.parse.urlencode({'service':'registry.docker.io','scope':'repository:'+repo+':pull,push'})
    request=urllib.request.Request(url,headers={'Authorization':'Basic '+basic})
    token=json.loads(opener.open(request,timeout=30).read())['token'];token_at=time.monotonic()

def request(path,method='GET',data=None,headers=None):
    if token is None or time.monotonic()-token_at>120:authorize()
    url=path if path.startswith('https://') else base+path
    for attempt in range(3):
        try:return opener.open(urllib.request.Request(url,data=data,method=method,headers={'Authorization':'Bearer '+token,**(headers or {})}),timeout=240)
        except urllib.error.HTTPError as error:
            if error.code==401 and attempt<2:authorize();continue
            raise
        except (OSError,TimeoutError):
            if attempt==2:raise
            time.sleep(2)

def digest(data):return 'sha256:'+hashlib.sha256(data).hexdigest()
def upload(data):
    sha=digest(data)
    try:
        with request('/blobs/'+sha,'HEAD'):return sha
    except urllib.error.HTTPError as error:
        if error.code!=404:raise
    with request('/blobs/uploads/','POST',b'') as response:
        location=urllib.parse.urljoin(base+'/',response.headers['Location'])
    url=location+('&' if '?' in location else '?')+urllib.parse.urlencode({'digest':sha})
    with request(url,'PUT',data,{'Content-Type':'application/octet-stream'}) as response:assert response.status==201
    return sha

try:
    with request('/manifests/1.5.12',headers={'Accept':manifest_type}) as response:parent=json.loads(response.read())
    assert parent['config']['digest']=='sha256:6ae17605e9f7ca4d924786e28f29b618516672cbbbadd1428ae8d2a9a2b8083e'
    with request('/blobs/'+parent['config']['digest']) as response:parent_config=response.read()
    assert digest(parent_config)==parent['config']['digest']
    assert json.loads(parent_config)['rootfs']['diff_ids']==meta['baseDiffIds']
    config=base64.b64decode(meta['config']);assert digest(config)==meta['configId']
    assert json.loads(config)['rootfs']['diff_ids']==meta['baseDiffIds']+[l['diffId'] for l in meta['layers']]
    print(json.dumps({'registryParentVerified':True,'nativeConfig':meta['configId']}),flush=True)
    layers=list(parent['layers'])
    for i,layer in enumerate(meta['layers']):
        data=(root/(layer['diffId'][7:]+'.tar.gz')).read_bytes()
        # build-native-patch already checked every uncompressed layer; verify
        # again while publishing so an altered cache cannot enter the registry.
        import gzip
        assert digest(gzip.decompress(data))==layer['diffId']
        layers.append({'mediaType':'application/vnd.docker.image.rootfs.diff.tar.gzip','digest':upload(data),'size':len(data)})
        print(json.dumps({'verifiedUploadedLayers':i+1,'totalNewLayers':len(meta['layers'])}),flush=True)
    config_sha=upload(config)
    body=json.dumps({'schemaVersion':2,'mediaType':manifest_type,'config':{'mediaType':'application/vnd.docker.container.image.v1+json','digest':config_sha,'size':len(config)},'layers':layers},separators=(',',':')).encode()
    with request('/manifests/1.5.13','PUT',body,{'Content-Type':manifest_type}) as response:assert response.status==201
    with request('/manifests/1.5.13',headers={'Accept':manifest_type}) as response:
        live=response.read();repository_digest=response.headers['Docker-Content-Digest']
    assert digest(live)==repository_digest
    published=json.loads(live);assert published['config']['digest']==meta['configId'];assert published['layers']==layers
    report={'passed':True,'image':repo+':1.5.13','configId':meta['configId'],'digest':repository_digest,'layers':len(layers),'nativeLayerBytesVerified':True}
    (root/'registry-published.json').write_text(json.dumps(report));print(json.dumps(report),flush=True)
except Exception as error:
    print(json.dumps({'published':False,'errorType':type(error).__name__,'httpCode':getattr(error,'code',None)}),flush=True)
    raise SystemExit(1)
