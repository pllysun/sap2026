"""Create an exact Docker load patch for an already-installed, verified parent.
The receiver MUST have the parent's complete layer chain; then Docker's loader
reuses those layers. Config and newly added layers are preserved byte-for-byte.
"""
import argparse,hashlib,io,json,tarfile
p=argparse.ArgumentParser();p.add_argument('archive');p.add_argument('base_diffids');p.add_argument('config_sha');p.add_argument('tag');p.add_argument('output');args=p.parse_args()
base=json.load(open(args.base_diffids))
with tarfile.open(args.archive) as src:
    manifests=json.load(src.extractfile('manifest.json'))
    manifest=next(m for m in manifests if args.tag in (m.get('RepoTags') or []))
    config_bytes=src.extractfile(manifest['Config']).read()
    assert hashlib.sha256(config_bytes).hexdigest()==args.config_sha.removeprefix('sha256:')
    config=json.loads(config_bytes);diffids=config['rootfs']['diff_ids']
    assert diffids[:len(base)]==base and len(manifest['Layers'])==len(diffids)
    new_layers=manifest['Layers'][len(base):]
    assert new_layers
    with tarfile.open(args.output,'w:gz') as out:
        for name in [manifest['Config'],*new_layers]:
            member=src.getmember(name);assert member.isfile()
            out.addfile(member,src.extractfile(member))
        data=json.dumps([manifest]).encode();member=tarfile.TarInfo('manifest.json');member.size=len(data);out.addfile(member,io.BytesIO(data))
    print(json.dumps({'tag':args.tag,'existingLayers':len(base),'newLayers':len(new_layers),'exactConfigVerified':True}))
