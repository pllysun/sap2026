"""Consistent, read-only MySQL snapshot. Run inside sap with a PyMySQL wheel on PYTHONPATH.
Credentials stay in the container environment; only counts/path/hash are printed.
The resulting SQL file is PRIVATE and must never be committed or published.
"""
import argparse
import datetime
import gzip
import hashlib
import json
import os
import pathlib
import re
import urllib.parse
import pymysql

parser = argparse.ArgumentParser()
parser.add_argument("--check", action="store_true")
parser.add_argument("--label", default="manual")
args = parser.parse_args()
if not re.fullmatch(r"[A-Za-z0-9.-]+", args.label):
    raise SystemExit("Invalid backup label")
url = os.environ.get("MYSQL_URL") or os.environ.get("SPRING_DATASOURCE_URL", "")
if not url.startswith("jdbc:mysql://"):
    raise SystemExit("Expected configured MySQL datasource")
uri = urllib.parse.urlsplit(url.removeprefix("jdbc:"))
database = uri.path.lstrip("/")
conn = pymysql.connect(host=uri.hostname, port=uri.port or 3306,
    user=os.environ.get("MYSQL_USER", "root"), password=os.environ.get("MYSQL_PASSWORD", ""),
    database=database, charset="utf8mb4", connect_timeout=15, read_timeout=180)
def ident(value):
    return "`" + value.replace("`", "``") + "`"
try:
    with conn.cursor() as cursor:
        cursor.execute("SELECT COALESCE(SUM(data_length+index_length),0) FROM information_schema.tables WHERE table_schema=%s", (database,))
        estimate = int(cursor.fetchone()[0])
        cursor.execute("SHOW FULL TABLES")
        tables = [row[0] for row in cursor.fetchall() if row[1] == "BASE TABLE"]
        cursor.execute("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=%s AND table_type='BASE TABLE' AND engine<>'InnoDB'", (database,))
        if cursor.fetchone()[0]:
            raise RuntimeError("Non-InnoDB table requires separate snapshot procedure")
        if args.check:
            print(json.dumps({"tables": len(tables), "estimatedBytes": estimate}))
            raise SystemExit(0)
        cursor.execute("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ")
        cursor.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY")
    directory = pathlib.Path("/app/data/ops/backups")
    directory.mkdir(parents=True, exist_ok=True)
    directory.chmod(0o700)
    path = directory / ("before-" + args.label + "-" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S") + ".sql.gz")
    fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    total = 0
    try:
        with os.fdopen(fd, "wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb") as output:
            def write(text): output.write(text.encode("utf8"))
            write("SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n")
            for table in tables:
                with conn.cursor() as cursor:
                    cursor.execute("SHOW CREATE TABLE " + ident(table))
                    ddl = cursor.fetchone()[1]
                write("DROP TABLE IF EXISTS " + ident(table) + ";\n" + ddl + ";\n")
                with conn.cursor(pymysql.cursors.SSCursor) as cursor:
                    cursor.execute("SELECT * FROM " + ident(table))
                    columns = ",".join(ident(col[0]) for col in cursor.description)
                    while True:
                        rows = cursor.fetchmany(200)
                        if not rows: break
                        values = ["(" + ",".join(conn.escape(value) for value in row) + ")" for row in rows]
                        write("INSERT INTO " + ident(table) + " (" + columns + ") VALUES " + ",".join(values) + ";\n")
                        total += len(rows)
            write("SET FOREIGN_KEY_CHECKS=1;\n")
        # Read every gzip block back to verify that the backup is not truncated.
        with gzip.open(path, "rb") as stream:
            while stream.read(1024 * 1024): pass
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""): digest.update(block)
        print(json.dumps({"path": str(path), "tables": len(tables), "rows": total,
            "bytes": path.stat().st_size, "sha256": digest.hexdigest()}))
    except Exception:
        path.unlink(missing_ok=True)
        raise
finally:
    conn.rollback()
    conn.close()
