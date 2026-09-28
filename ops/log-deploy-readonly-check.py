"""Read-only deployment counts, never prints users, credentials or log content.
Run inside the app container with PyMySQL on PYTHONPATH.
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
        c.execute("START TRANSACTION READ ONLY")
        result = {}
        def count(key, sql):
            c.execute(sql)
            result[key] = int(c.fetchone()[0])
        count("detailedLogs", "SELECT COUNT(*) FROM sys_log")
        count("logsOlderThan90Days", "SELECT COUNT(*) FROM sys_log WHERE request_time < NOW()-INTERVAL 90 DAY")
        count("heatmapRows", "SELECT COUNT(*) FROM log_stats")
        c.execute("SHOW COLUMNS FROM log_stats")
        cols = [r[0] for r in c.fetchall()]
        if "count" in cols:
            count("heatmapCalls", "SELECT COALESCE(SUM(`count`),0) FROM log_stats")
        count("eligibleInactiveIssues", """SELECT COUNT(*) FROM app_feedback_issue i
            WHERE i.deleted=0 AND i.status='OPEN' AND i.updated_at < NOW()-INTERVAL 7 DAY
            AND EXISTS (SELECT 1 FROM app_feedback_comment c WHERE c.issue_id=i.id AND c.deleted=0 AND c.admin_reply=1)
            AND NOT EXISTS (SELECT 1 FROM app_feedback_comment c WHERE c.issue_id=i.id AND c.deleted=0 AND c.created_at>=NOW()-INTERVAL 7 DAY)""")
        count("systemClosedIssues", "SELECT COUNT(*) FROM app_feedback_issue WHERE closed_by=0 AND status='CLOSED' AND deleted=0")
        c.execute("SHOW TABLES LIKE 'sys_log_archive'")
        result["archiveTablePresent"] = c.fetchone() is not None
        if result["archiveTablePresent"]:
            count("archiveGroups", "SELECT COUNT(*) FROM sys_log_archive")
            count("archivedCalls", "SELECT COALESCE(SUM(call_count),0) FROM sys_log_archive")
            c.execute("SHOW INDEX FROM sys_log_archive")
            result["archiveIndexes"] = sorted({r[2] for r in c.fetchall()})
        c.execute("SHOW COLUMNS FROM sys_log")
        result["newLogColumns"] = sorted({r[0] for r in c.fetchall()} & {"source", "endpoint", "result_code"})
        if "source" in result["newLogColumns"]:
            c.execute("SELECT COALESCE(source,'LEGACY'),COUNT(*) FROM sys_log GROUP BY source")
            result["sourceCounts"] = {r[0]: int(r[1]) for r in c.fetchall()}
        print(json.dumps(result))
finally:
    db.rollback()
    db.close()
