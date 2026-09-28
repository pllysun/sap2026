"""Read only public App release fields from the configured DB; never reads credentials."""
import json
import os
import urllib.parse
import pymysql

uri = urllib.parse.urlsplit(os.environ["MYSQL_URL"].removeprefix("jdbc:"))
db = pymysql.connect(host=uri.hostname, port=uri.port or 3306,
    user=os.environ["MYSQL_USER"], password=os.environ["MYSQL_PASSWORD"],
    database=uri.path.lstrip("/"), connect_timeout=15, read_timeout=30)
try:
    with db.cursor() as c:
        c.execute("START TRANSACTION READ ONLY")
        keys = ("app_version_code", "app_version_name", "app_changelog", "app_force_update", "app_min_version_code", "app_apk_sha256", "app_apk_size", "app_download_url")
        c.execute("SELECT setting_key,setting_value FROM sys_setting WHERE setting_key IN (" + ",".join(["%s"]*len(keys)) + ")", keys)
        print(json.dumps(dict(c.fetchall()), ensure_ascii=False))
finally:
    db.rollback()
    db.close()
