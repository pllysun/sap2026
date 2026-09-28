"""Build the access-only release over the verified production 1.4.86 artifacts."""
import hashlib, io, json, pathlib, shutil, subprocess, tarfile, zipfile

root = pathlib.Path(__file__).resolve().parent.parent
old = pathlib.Path('/Volumes/Newsmy/sap-president-parity-zy9ux83n')
apfs = pathlib.Path('/Volumes/SAPAndroidBuild/sap-president-parity-zy9ux83n')
work = pathlib.Path('/Volumes/Newsmy/sap-access-1.4.87')
work.mkdir(exist_ok=True)
javac = '/Users/pllysun/Library/Java/JavaVirtualMachines/temurin-21/Contents/Home/bin/javac'
cp = str(apfs/'base-extracted/BOOT-INF/classes') + ':' + str(apfs/'base-extracted/BOOT-INF/lib/*')
names = ['AuthService', 'AppAccessService']
base = old/'release.jar'
assert hashlib.sha256(base.read_bytes()).hexdigest() == '11dac31b386b563499dae5ae1bd453aeb13df9f8c71c13c60fa8b9fe511371d1'
for label, source in [('before', apfs/'backend'), ('after', root/'sap-backend')]:
    out = work/label
    out.mkdir(exist_ok=True)
    subprocess.run([javac, '--release', '21', '-parameters', '-g', '-proc:none', '-cp', cp, '-d', str(out)] +
                   [str(source/f'src/main/java/com/sap/service/{n}.java') for n in names], check=True)
with zipfile.ZipFile(base) as src, zipfile.ZipFile(work/'release.jar', 'w') as dest:
    changed = []
    for info in src.infolist():
        data = src.read(info)
        for name in names:
            if info.filename == f'BOOT-INF/classes/com/sap/service/{name}.class':
                assert data == (work/f'before/com/sap/service/{name}.class').read_bytes(), f'Baseline mismatch: {name}'
                data = (work/f'after/com/sap/service/{name}.class').read_bytes()
                changed.append(info.filename)
        dest.writestr(info, data)
    assert len(changed) == 2
assets = work/'admin-dist'
if assets.exists(): shutil.rmtree(assets)
shutil.copytree(old/'admin-dist', assets, dirs_exist_ok=True)
replacements = {
    '控制游客账号能否登录，以及登录后可使用的软协课表能力。真实会员不受此设置影响。': 'Web、班级课表默认开放；此处控制游客是否拥有完整教务能力。真实会员不受此设置影响。',
    '完全关闭': '基础能力（兼容旧配置）',
    '游客账号无法登录 App': 'Web、班级课表默认开放，不开放教务模式',
    '游客登录后使用 Web 本地课表': 'Web、班级课表默认开放，不开放教务模式',
}
hits = {s: 0 for s in replacements}
for file in assets.rglob('*.js'):
    if file.name.startswith('._'): continue
    text = file.read_text()
    for a,b in replacements.items():
        hits[a] += text.count(a)
        text = text.replace(a,b)
    file.write_text(text)
assert all(n == 1 for n in hits.values()), hits
# Give every JS entry a new URL, including all inter-chunk references.
renames = {f.name: f.stem+'-access87.js' for f in assets.rglob('*.js') if not f.name.startswith('._')}
for file in assets.rglob('*'):
    if file.suffix in ('.js','.html','.css') and not file.name.startswith('._'):
        text = file.read_text()
        for a,b in renames.items(): text = text.replace(a,b)
        file.write_text(text)
for file in list(assets.rglob('*.js')):
    if file.name in renames: file.rename(file.with_name(renames[file.name]))
def permissions(info):
    info.uid = info.gid = 0
    info.uname = info.gname = 'root'
    info.mode = 0o644
    return info
with tarfile.open(work/'layer.tar','w') as layer:
    marker=tarfile.TarInfo('app/static/admin/.wh..wh..opq'); marker.mode=0o644
    layer.addfile(marker,io.BytesIO())
    for file in sorted(assets.rglob('*')):
        if file.is_file() and not file.name.startswith('._'):
            layer.add(file,arcname='app/static/admin/'+str(file.relative_to(assets)),filter=permissions)
    layer.add(work/'release.jar',arcname='app/app.jar',filter=permissions)
manifest={'version':'1.4.87','changedClasses':changed,'jarSha256':hashlib.sha256((work/'release.jar').read_bytes()).hexdigest()}
(work/'release-manifest.json').write_text(json.dumps(manifest,indent=2))
print(json.dumps(manifest))
