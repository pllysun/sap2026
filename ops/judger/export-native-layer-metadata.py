"""Read-only: export exact Docker tar-split metadata and config, never image payloads.
Run on the native release host via SSH stdin, redirect stdout to a private file.
"""
import base64,gzip,hashlib,json,subprocess,sys
from pathlib import Path

def inspect(tag):
    return json.loads(subprocess.check_output(['docker','image','inspect',tag],universal_newlines=True))[0]
image_tag,parent_tag,expected_image,expected_parent=sys.argv[1:5]
image=inspect(image_tag);parent=inspect(parent_tag)
assert image['Id']==expected_image
assert parent['Id']==expected_parent
root=Path('/var/lib/docker/image/overlay2')
config=(root/'imagedb/content/sha256'/image['Id'][7:]).read_bytes()
assert hashlib.sha256(config).hexdigest()==image['Id'][7:]
ids=image['RootFS']['Layers'];base=parent['RootFS']['Layers'];assert ids[:len(base)]==base
chain=None;layers=[]
for i,diff in enumerate(ids):
    chain=diff if chain is None else 'sha256:'+hashlib.sha256((chain+' '+diff).encode()).hexdigest()
    if i<len(base):continue
    metadata=(root/'layerdb/sha256'/chain[7:]/'tar-split.json.gz').read_bytes()
    layers.append({'diffId':diff,'entries':[json.loads(line) for line in gzip.decompress(metadata).splitlines()]})
print(json.dumps({'configId':image['Id'],'config':base64.b64encode(config).decode(),'baseDiffIds':base,'layers':layers}))
