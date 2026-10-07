"""Expand content-addressed snapshots before rolling back to pre-1.5.13 binaries.
Run INSIDE sap with PyMySQL on PYTHONPATH. Credentials stay in its environment.
Never delete source, judge results, frozen set items or shared snapshots.
"""
import json,os,sys,urllib.parse
if len(sys.argv)>1:sys.path.insert(0,sys.argv[1])
import pymysql
uri=urllib.parse.urlsplit(os.environ['MYSQL_URL'].removeprefix('jdbc:'))
db=pymysql.connect(host=uri.hostname,port=uri.port or 3306,user=os.environ['MYSQL_USER'],password=os.environ['MYSQL_PASSWORD'],database=uri.path.lstrip('/'),charset='utf8mb4',connect_timeout=15)
try:
    with db.cursor() as c:
        c.execute("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='oj_submission' AND column_name='snapshot_key'")
        if not c.fetchone()[0]:
            print(json.dumps({'snapshotsExpanded':0,'migrationNotStarted':True}));sys.exit(0)
        c.execute("SELECT COUNT(*) FROM oj_submission j LEFT JOIN oj_execution_snapshot s ON s.id=j.snapshot_key WHERE j.snapshot_key IS NOT NULL AND s.id IS NULL")
        assert c.fetchone()[0]==0,'Cannot expand a missing snapshot'
        c.execute("""UPDATE oj_submission j JOIN oj_execution_snapshot s ON s.id=j.snapshot_key
            SET j.snapshot_json=JSON_SET(JSON_REMOVE(j.snapshot_json,'$.packRef'),
                '$.pack',JSON_EXTRACT(s.payload_json,'$.pack'),
                '$.languages',JSON_EXTRACT(s.payload_json,'$.languages')),
                j.snapshot_key=NULL WHERE j.snapshot_key IS NOT NULL""")
        count=c.rowcount
    db.commit();print(json.dumps({'snapshotsExpanded':count,'sourceAndResultsPreserved':True}))
finally:db.close()
