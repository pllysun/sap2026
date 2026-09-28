"""Package freshly built artifacts as an OCI layer for crane append.

Usage: python3 ops/package-runtime-layer.py /external-disk/layer.tar
No credentials or local configuration are included.
"""
import io
import pathlib
import sys
import tarfile

root = pathlib.Path(__file__).resolve().parent.parent
def permissions(info):
    info.uid = info.gid = 0
    info.uname = info.gname = "root"
    info.mode = 0o755 if info.name == "app/entrypoint.sh" else 0o644
    return info

with tarfile.open(sys.argv[1], "w") as layer:
    for app in ("admin", "user"):
        # Hide assets from the lower layer, without deleting new assets.
        marker = tarfile.TarInfo("app/static/" + app + "/.wh..wh..opq")
        marker.mode = 0o644
        layer.addfile(marker, io.BytesIO())
        source = root / ("sap-" + app) / "dist"
        assert (source / "index.html").is_file()
        for f in sorted(source.rglob("*")):
            if f.is_file():
                layer.add(f, arcname="app/static/" + app + "/" + str(f.relative_to(source)), filter=permissions)
    layer.add(root / "sap-backend/target/sap-backend-1.0.0.jar", arcname="app/app.jar", filter=permissions)
    layer.add(root / "docker/nginx.conf", arcname="etc/nginx/nginx.conf", filter=permissions)
    layer.add(root / "docker/entrypoint.sh", arcname="app/entrypoint.sh", filter=permissions)
print("Packaged compiled JAR, frontend assets and runtime configuration only")
