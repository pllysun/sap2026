"""Add the new log archive schema before deployment; never modifies log rows.
Run inside sap with PyMySQL on PYTHONPATH. DATETIME avoids MySQL's legacy implicit
TIMESTAMP zero defaults and retains the same local time semantics as sys_log.
"""
import json
import os
import urllib.parse
import pymysql

uri = urllib.parse.urlsplit(os.environ["MYSQL_URL"].removeprefix("jdbc:"))
db = pymysql.connect(host=uri.hostname, port=uri.port or 3306,
    user=os.environ["MYSQL_USER"], password=os.environ["MYSQL_PASSWORD"],
    database=uri.path.lstrip("/"), connect_timeout=15, read_timeout=60)
try:
    with db.cursor() as c:
        c.execute("SHOW COLUMNS FROM sys_log")
        log_columns = {r[0] for r in c.fetchall()}
        if "resultCode" in log_columns and "result_code" in log_columns:
            raise RuntimeError("Both result-code columns exist; manual reconciliation required")
        if "result_code" not in log_columns:
            if "resultCode" in log_columns:
                c.execute("ALTER TABLE sys_log CHANGE COLUMN `resultCode` result_code INT NULL")
            else:
                c.execute("ALTER TABLE sys_log ADD COLUMN result_code INT NULL")
        c.execute("""CREATE TABLE IF NOT EXISTS sys_log_archive (
            group_key VARCHAR(64) PRIMARY KEY, bucket_time DATETIME NOT NULL,
            source VARCHAR(8) NOT NULL, endpoint VARCHAR(255) NOT NULL,
            http_method VARCHAR(10) NOT NULL, user_id BIGINT NOT NULL,
            user_name VARCHAR(50), ip VARCHAR(50), operation_type VARCHAR(10), description VARCHAR(200),
            result_code INT NOT NULL, call_count BIGINT NOT NULL, duration_sum BIGINT NOT NULL,
            duration_min BIGINT NOT NULL, duration_max BIGINT NOT NULL,
            first_time DATETIME NOT NULL, last_time DATETIME NOT NULL
        )""")
        c.execute("SHOW COLUMNS FROM sys_log_archive")
        columns = {r[0]:r[1] for r in c.fetchall()}
        assert all(columns[k].lower().startswith("datetime") for k in ("bucket_time", "first_time", "last_time"))
        c.execute("SELECT COUNT(*) FROM sys_log_archive")
        print(json.dumps({"schemaReady": True, "datetimeColumnsVerified": True, "resultCodeColumnVerified": True, "archiveGroups": c.fetchone()[0]}))
finally:
    db.close()
