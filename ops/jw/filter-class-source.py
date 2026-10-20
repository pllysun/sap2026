#!/usr/bin/env python3
"""Read-only, server-side filtering of one term's stored school source formats.

No default whole-database mode. A date or an explicit current-snapshot selection
is mandatory. Database credentials stay inside the running service container.
Only grouped time fields leave MySQL; complete raw_json rows are never exported.
"""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import subprocess
import sys

SOURCES = ("class", "teacher", "room", "course")


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--term", required=True, help="Exact term, e.g. 2026-2027-1")
    scope = parser.add_mutually_exclusive_group(required=True)
    scope.add_argument("--date", type=dt.date.fromisoformat,
                       help="Stored row date in the database's +08:00 time zone")
    scope.add_argument("--current", action="store_true",
                       help="Explicitly inspect the currently stored term snapshot")
    parser.add_argument("--source", choices=SOURCES, action="append",
                        help="Repeat to include another source; default: class only")
    parser.add_argument("--format-limit", type=int, default=5000)
    parser.add_argument("--summary-only", action="store_true",
                        help="Return database counts only, without reading JSON fields")
    parser.add_argument("--inspect-id", type=int, action="append", default=[],
                        help="Inspect at most 12 known row ids from one selected source")
    parser.add_argument("--container", default="sap")
    parser.add_argument("--output", type=Path, required=True,
                        help="New local private JSON file; existing files are not overwritten")
    args = parser.parse_args(argv)
    if not re.fullmatch(r"\d{4}-\d{4}-[12]", args.term):
        parser.error("--term must be an exact academic term")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", args.container):
        parser.error("Invalid container name")
    if not 1 <= args.format_limit <= 20000:
        parser.error("--format-limit must be between 1 and 20000")
    if len(args.inspect_id) > 12 or any(row_id < 1 for row_id in args.inspect_id):
        parser.error("--inspect-id accepts at most 12 positive row ids")
    if args.inspect_id and len(set(args.source or ["class"])) != 1:
        parser.error("--inspect-id requires exactly one source")
    if args.output.exists():
        parser.error("--output already exists; choose a new path")
    return args


def selection(args):
    return {
        "term": args.term,
        "date": args.date.isoformat() if args.date else None,
        "sources": list(dict.fromkeys(args.source or ["class"])),
        "formatLimit": args.format_limit,
        "inspectIds": list(dict.fromkeys(args.inspect_id)),
        "summaryOnly": args.summary_only,
    }


def database_program(config):
    # This code runs inside the service. Table names come from the fixed allowlist.
    return r'''
import os,sys,json
from urllib.parse import urlsplit
from datetime import date,timedelta
sys.path.insert(0,'/app/data/ops/backups/pymysql-1.1.2.whl')
import pymysql
CONFIG = ''' + repr(config) + r'''
u=urlsplit(os.environ['MYSQL_URL'][5:])
db=pymysql.connect(host=u.hostname,port=u.port or 3306,
 user=os.environ['MYSQL_USER'],password=os.environ['MYSQL_PASSWORD'],
 database=u.path.lstrip('/'),charset='utf8mb4',connect_timeout=10,
 read_timeout=45,cursorclass=pymysql.cursors.DictCursor)
try:
 with db.cursor() as q:
  q.execute('SET SESSION MAX_EXECUTION_TIME=30000')
  q.execute("SET SESSION time_zone='+08:00'")
  q.execute('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ')
  q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
  q.execute('SELECT UTC_TIMESTAMP() AS snapshot_utc,@@session.time_zone AS timezone')
  result={'selection':CONFIG,'clock':q.fetchone(),'sources':{},'batchLogs':[]}
  q.execute('SELECT term_value,last_collected_at,class_count,teacher_count,room_count,course_count FROM jw_class_schedule_term WHERE term_value=%s',(CONFIG['term'],))
  result['termMetadata']=q.fetchone()
  where='term_value=%s'
  params=[CONFIG['term']]
  if CONFIG['date']:
   start=CONFIG['date']+' 00:00:00'
   end=(date.fromisoformat(CONFIG['date'])+timedelta(days=1)).isoformat()+' 00:00:00'
   where+=' AND updated_at >= %s AND updated_at < %s'
   params.extend([start,end])
   q.execute('SELECT batch_id,term_value,source_type,status,row_count,started_at,finished_at FROM jw_class_schedule_pull_log WHERE started_at >= %s AND started_at < %s AND (term_value=%s OR term_value IS NULL) ORDER BY id LIMIT 501',(start,end,CONFIG['term']))
   result['batchLogs']=q.fetchall()
   result['batchLogsTruncated']=len(result['batchLogs'])>500
  if CONFIG['inspectIds']:
   where+=' AND id IN ('+','.join(['%s']*len(CONFIG['inspectIds']))+')'
   params.extend(CONFIG['inspectIds'])
  for source in CONFIG['sources']:
   if source not in ('class','teacher','room','course'):
    raise ValueError('Invalid source')
   table='jw_class_schedule_'+source
   q.execute("SELECT COUNT(*) AS row_count,MIN(updated_at) AS first_updated,MAX(updated_at) AS last_updated,SUM(group_name IS NOT NULL AND TRIM(group_name)<>'') AS grouped_rows,SUM(course_name IS NULL OR TRIM(course_name)='') AS missing_course_names,SUM(class_name IS NULL OR TRIM(class_name)='') AS missing_class_names FROM "+table+' WHERE '+where,params)
   summary=q.fetchone()
   result['sources'][source]=summary
   summary['formats']=[]
   if not summary['row_count'] or CONFIG['summaryOnly']:
    continue
   # Group at the database: preserve exact spelling (including trailing spaces)
   # while returning only time fields and one sample id, never entire source rows.
   exprs=[
    'week_range','weekday','section',
    'JSON_VALID(raw_json)',
    "IF(JSON_VALID(raw_json),JSON_UNQUOTE(JSON_EXTRACT(raw_json,'$.kkzc')),NULL)",
    "IF(JSON_VALID(raw_json),JSON_UNQUOTE(JSON_EXTRACT(raw_json,'$.zzdweek')),NULL)",
    "IF(JSON_VALID(raw_json),JSON_UNQUOTE(JSON_EXTRACT(raw_json,'$.jc')),NULL)",
   ]
   aliases=['weeks','day','section','raw_valid','raw_weeks','raw_day','raw_section']
   columns=', '.join('MIN('+expr+') AS '+alias for expr,alias in zip(exprs,aliases))
   groups=', '.join('BINARY ('+expr+')' for expr in exprs)
   sql='SELECT '+columns+',COUNT(*) AS count,MIN(id) AS sample_id FROM '+table+' WHERE '+where+' GROUP BY '+groups+' ORDER BY count DESC LIMIT %s'
   q.execute(sql,params+[CONFIG['formatLimit']+1])
   formats=q.fetchall()
   if len(formats)>CONFIG['formatLimit']:
    raise ValueError('Format limit exceeded for '+source+'; narrow the selected term/date/source')
   summary['formats']=formats
   if sum(int(f['count']) for f in formats)!=summary['row_count']:
    raise ValueError('Incomplete grouped result')
   if CONFIG['inspectIds']:
    q.execute('SELECT id,term_value,course_name,class_name,week_range,weekday,section,group_name,updated_at,IF(JSON_VALID(raw_json),JSON_KEYS(raw_json),NULL) AS source_keys FROM '+table+' WHERE '+where+' ORDER BY id LIMIT 12',params)
    summary['samples']=q.fetchall()
  result['status']='ready' if any(s['row_count'] for s in result['sources'].values()) else 'no_stored_rows_for_selection'
  result['limitations']=[
   'The source tables replace a term snapshot on collection; absent historical rows cannot prove that the original batch was valid.',
   'This output contains grouped source time fields; parser replay is required before drawing compatibility conclusions.',
   'Rows lost before persistence or during source deduplication cannot be recovered from the stored snapshot.'
  ]
  print(json.dumps(result,ensure_ascii=True,default=str))
except Exception as error:
 if isinstance(error,ValueError):
  message=str(error)
 else:
  message=type(error).__name__
  if getattr(error,'args',None) and isinstance(error.args[0],int):
   message+=' code='+str(error.args[0])
 sys.stderr.write('SOURCE_AUDIT_ERROR: '+message+'\n')
 sys.exit(2)
finally:
 db.rollback();db.close()
'''


def remote_wrapper(program, container):
    # ascii() keeps the remote host's ASCII locale from corrupting argv.
    command = ["docker", "exec", container, "python3", "-c", program]
    return ("import subprocess,sys\n"
            "p=subprocess.run(" + ascii(command) + ",stdout=subprocess.PIPE,stderr=subprocess.PIPE)\n"
            "if p.returncode:\n"
            " for line in p.stderr.decode('utf-8',errors='replace').splitlines():\n"
            "  if line.startswith('SOURCE_AUDIT_ERROR: '): sys.stderr.write(line+'\\n')\n"
            " sys.stderr.write('Scoped read-only source query failed. No data exported.\\n')\n"
            " sys.exit(p.returncode)\n"
            "sys.stdout.buffer.write(p.stdout)\n")


def main(argv=None):
    args = parse_args(argv)
    config = selection(args)
    root = Path(__file__).resolve().parents[2]
    result = subprocess.run(
        ["node", str(root / "ops/server-ssh.cjs"), "python3 -"],
        input=remote_wrapper(database_program(config), args.container),
        text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, cwd=root,
        timeout=240,
    )
    if result.returncode:
        for line in result.stderr.splitlines():
            if line.startswith("SOURCE_AUDIT_ERROR: "):
                print(line, file=sys.stderr)
        raise SystemExit("Scoped source query failed; no report written")
    report = json.loads(result.stdout)
    args.output.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as output:
        json.dump(report, output, ensure_ascii=False, indent=2)
        output.write("\n")
    print(json.dumps({"status": report["status"], "selection": config,
                      "rowCounts": {k: v["row_count"] for k, v in report["sources"].items()},
                      "output": str(args.output.resolve())}, ensure_ascii=False))


if __name__ == "__main__":
    main()
