"""Remote fallback: reconstruct the exact pushed image from verified existing layers."""
import base64,gzip,hashlib,io,json,pathlib,shutil,subprocess,sys,tarfile,tempfile
with gzip.open(sys.argv[1],'rb') as f: p=json.loads(f.read())
w=pathlib.Path(tempfile.mkdtemp(prefix='sap-access-1.4.87-'))
try:
    obj=json.loads(subprocess.check_output(['docker','inspect','sap']))[0]
    assert obj['Config']['Image']=='pllysun/sap:1.4.86','Live version changed'
    subprocess.check_call(['docker','cp','sap:/app/app.jar',str(w/'base.jar')])
    base=(w/'base.jar').read_bytes()
    assert hashlib.sha256(base).hexdigest()==p['baseJarSha256']
    with (w/'layer.tar').open('wb') as f:
        for s in p['segments']: f.write(base[s['offset']:s['offset']+s['size']] if 'offset' in s else base64.b64decode(s['data']))
    assert hashlib.sha256((w/'layer.tar').read_bytes()).hexdigest()==p['layerSha256']
    config=base64.b64decode(p['config']); parsed=json.loads(config)
    assert hashlib.sha256(config).hexdigest()==p['configSha256']
    assert parsed['rootfs']['diff_ids'][-1]=='sha256:'+p['layerSha256']
    print('Transport checksums verified',flush=True)
    subprocess.check_call(['docker','save','-o',str(w/'base.tar'),'pllysun/sap:1.4.86'])
    with tarfile.open(w/'base.tar') as src:
        entries=json.loads(src.extractfile('manifest.json').read()); assert len(entries)==1
        entry=entries[0]; old=json.loads(src.extractfile(entry['Config']).read())
        assert old['rootfs']['diff_ids']==parsed['rootfs']['diff_ids'][:-1]
        entry['Config']=p['configSha256']+'.json'; entry['RepoTags']=['pllysun/sap:1.4.87']; entry['Layers'].append('access-layer/layer.tar')
        with tarfile.open(w/'release.tar','w') as dest:
            for member in src.getmembers():
                if member.name not in ('manifest.json','repositories','index.json','oci-layout'):
                    dest.addfile(member,src.extractfile(member) if member.isfile() else None)
            for name,data in [(entry['Config'],config),('manifest.json',json.dumps([entry]).encode())]:
                item=tarfile.TarInfo(name); item.size=len(data); item.mode=0o644; dest.addfile(item,io.BytesIO(data))
            dest.add(w/'layer.tar',arcname='access-layer/layer.tar')
    subprocess.check_call(['docker','load','-i',str(w/'release.tar')])
    loaded=json.loads(subprocess.check_output(['docker','inspect','pllysun/sap:1.4.87']))[0]
    assert loaded['Id']=='sha256:'+p['configSha256']
    print(json.dumps({'image': 'pllysun/sap:1.4.87','matchesPublishedImage':True}),flush=True)
finally:
    shutil.rmtree(w)
