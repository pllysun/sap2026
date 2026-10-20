#!/usr/bin/env python3
"""Authenticated AI/operator CLI. Only the server sandbox executes submitted code."""
from __future__ import annotations
import argparse, concurrent.futures, hashlib, json, os, re, sys, time, urllib.error, urllib.parse, urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
LANGUAGES=('c','cpp','java','python','rust')
SECTION_KEYS=('understand','approach','walkthrough','steps','correctness','complexity','pitfalls','review')
class Error(Exception):pass

def unique(pairs):
    result={}
    for k,v in pairs:
        if k in result:raise Error('JSON 包含重复字段: '+k)
        result[k]=v
    return result

def read(path):
    path=Path(path)
    if path.stat().st_size>4_000_000:raise Error('JSON 文件不能超过 4 MB')
    return json.loads(path.read_text(encoding='utf-8'),object_pairs_hook=unique)

def lint(pack):
    if not isinstance(pack,dict):raise Error('题包必须是 JSON 对象')
    required=('slug','title','difficulty','description','inputFormat','outputFormat','constraints','sourcePlatform','sourceNote','tags','modes','defaultMode','checker','profiles','cases','references')
    for field in required:
        if field not in pack:raise Error('缺少字段: '+field)
    if not isinstance(pack['slug'],str) or not re.fullmatch('[a-z0-9][a-z0-9-]{0,99}',pack['slug']):raise Error('slug 只能包含小写字母、数字和连字符，长度 1–100')
    for field in ('title','description','inputFormat','outputFormat','constraints','sourcePlatform','sourceNote'):
        if not isinstance(pack[field],str) or not pack[field].strip():raise Error(field+' 必须是非空字符串')
    if len(pack['title'])>200 or pack['difficulty'] not in ('EASY','MEDIUM','HARD'):raise Error('标题过长或难度无效')
    if pack['sourcePlatform'] not in ('原创','ORIGINAL'):
        u=urllib.parse.urlsplit(pack.get('sourceUrl',''))
        if u.scheme!='https' or not u.hostname:raise Error('引用题型必须提供 HTTPS 来源链接')
    modes=pack['modes']
    if not isinstance(modes,list) or not modes or len(set(modes))!=len(modes) or not set(modes)<= {'STDIO','FUNCTION'} or pack['defaultMode'] not in modes:raise Error('模式配置无效')
    if pack['checker'] not in ('TOKENS','UNORDERED_TOKENS'):raise Error('比较器只支持 TOKENS 和 UNORDERED_TOKENS')
    if not isinstance(pack['tags'],list) or len(pack['tags'])>20 or any(not isinstance(t,str) or len(t)>50 for t in pack['tags']):raise Error('标签格式无效')
    cases=pack['cases']
    if not isinstance(cases,list) or not 1<=len(cases)<=500:raise Error('测试集须为 1–500 个用例')
    names=set();inputs={};total=0;sample=False
    for c in cases:
        if not isinstance(c,dict) or set(c)!= {'name','input','expectedOutput','sample'}:raise Error('用例必须且只能包含 name/input/expectedOutput/sample')
        if not all(isinstance(c[k],str) for k in ('name','input','expectedOutput')) or not c['name'].strip() or type(c['sample']) is not bool:raise Error('用例字段类型无效')
        if c['name'] in names:raise Error('用例名称不能重复: '+c['name'])
        names.add(c['name']);sample |= c['sample']
        if len(c['input'].encode())>1048576 or len(c['expectedOutput'].encode())>262144:raise Error('单个用例过大')
        total+=len(c['input'].encode())+len(c['expectedOutput'].encode())
        if c['input'] in inputs and inputs[c['input']]!=c['expectedOutput']:raise Error('相同输入存在相互冲突的答案')
        inputs[c['input']]=c['expectedOutput']
    if not sample or total>2_000_000:raise Error('至少一条公开样例；测试数据 UTF-8 总量不超过 2 MB')
    for language in LANGUAGES:
        profile=pack['profiles'].get(language);refs=pack['references'].get(language)
        if not isinstance(profile,dict) or not isinstance(refs,dict):raise Error('五语言必须齐全: '+language)
        for mode in modes:
            starter=profile.get('starterStdio' if mode=='STDIO' else 'starterFunction')
            if not isinstance(starter,str) or not starter.strip() or len(starter)>65536:raise Error(language+'/'+mode+' 缺少合法初始代码')
            code=refs.get(mode)
            if not isinstance(code,str) or not code.strip() or len(code)>65536:raise Error(language+'/'+mode+' 缺少合法参考代码')
        if 'FUNCTION' in modes:
            driver=profile.get('functionDriver','')
            if not isinstance(driver,str) or driver.count('__USER_CODE__')!=1 or len(driver)>131072:raise Error(language+' 函数驱动必须且仅包含一次 __USER_CODE__')
    if len(json.dumps(pack,ensure_ascii=False).encode())>4_000_000:raise Error('题包总量不能超过 4 MB')
    return {'slug':pack['slug'],'languages':5,'modes':len(modes),'cases':len(cases),'sampleCases':sum(c['sample'] for c in cases),'testBytes':total}

def lint_solution(document,pack):
    if document.get('schemaVersion')!=1 or document.get('problemSlug')!=pack['slug']:raise Error('题解版本或 slug 不匹配')
    if document.get('title')!=pack['title'] or not isinstance(document.get('summary'),str) or not document['summary'].strip():raise Error('题解标题或摘要无效')
    sections=document.get('sections',[])
    if [s.get('key') for s in sections]!=list(SECTION_KEYS):raise Error('题解必须依序包含固定八节')
    if sum(len(s.get('markdown','')) for s in sections)<500:raise Error('题解正文至少 500 个字符，面向入门读者逐步解释')
    for section in sections:
        if not section.get('title') or not section.get('markdown'):raise Error('章节标题和正文不能为空')
    for mode in pack['modes']:
        codes=document.get('codes',{}).get(mode,{})
        for language in LANGUAGES:
            code=codes.get(language,'')
            if not isinstance(code,str) or not code.strip():raise Error('题解缺少五语言代码: '+language+'/'+mode)
    return {'slug':pack['slug'],'sections':8,'languages':5}

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):raise Error('API 出现重定向，拒绝转发认证令牌；请使用正确的站点地址')

class Client:
    def __init__(self,url,token_file):
        parts=urllib.parse.urlsplit(url)
        if parts.scheme not in ('http','https') or not parts.hostname or parts.username or parts.password or parts.query or parts.fragment:raise Error('站点地址无效')
        self.url=url.rstrip('/');self.token_file=Path(token_file);self.token=None
        self.http=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect())
        if self.token_file.exists():
            stored=read(self.token_file)
            if stored.get('baseUrl')!=self.url:raise Error('令牌所属站点不匹配，请指定另一个 --token-file 或重新登录')
            self.token=stored['token']
    def request(self,method,path,body=None,login=False):
        if not path.startswith('/api/'):raise Error('只允许同站点 /api/ 接口')
        headers={'Content-Type':'application/json'}
        if not login:
            if not self.token:raise Error('先执行 login；不要把令牌直接写到命令行或公开日志')
            headers['sap-token']=self.token
        request=urllib.request.Request(self.url+path,data=None if body is None else json.dumps(body,ensure_ascii=False).encode(),headers=headers,method=method)
        try:
            with self.http.open(request,timeout=45) as response:raw=response.read(5*1024*1024+1)
        except urllib.error.HTTPError as e:
            try:message=json.loads(e.read(65536)).get('message')
            except Exception:message=None
            raise Error(message or 'HTTP '+str(e.code)) from None
        if len(raw)>5*1024*1024:raise Error('API 响应过大')
        result=json.loads(raw)
        if result.get('code')!=200:raise Error(str(result.get('message','请求失败'))+'（'+str(result.get('code'))+'）')
        return result.get('data')
    def login(self,account,password):
        data=self.request('POST','/api/auth/admin/login',{'studentId':account,'password':password},True)
        self.token=data['token'];self.token_file.parent.mkdir(parents=True,exist_ok=True)
        fd=os.open(self.token_file,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
        with os.fdopen(fd,'w') as file:json.dump({'baseUrl':self.url,'token':self.token},file,ensure_ascii=False)
        self.token_file.chmod(0o600)
        return {'authenticated':True,'tokenFile':str(self.token_file)}
    def problem(self,id):return self.request('GET',f'/api/admin/oj/problems/{id}')
    def validate(self,id,timeout=1200):
        enabled={r['languageKey'] for r in self.request('GET','/api/admin/oj/languages') if r['enabled']}
        if enabled!=set(LANGUAGES):raise Error('完整验证要求 C/C++/Java/Python/Rust 五语言全部启用，不能停用语言绕过验证')
        job=self.request('POST',f'/api/admin/oj/problems/{id}/validate')
        jid=job.get('id',job.get('jobId'))
        if jid is None:raise Error('验证接口没有返回任务 ID')
        started=time.monotonic()
        while time.monotonic()-started<timeout:
            row=self.request('GET',f'/api/admin/oj/jobs/{jid}')
            if row['status'] not in ('QUEUED','RUNNING'):
                result={k:row.get(k) for k in ('id','status','passedCases','totalCases')}
                if row['status']!='AC':raise Error('验证未通过: '+json.dumps(result,ensure_ascii=False)+'；在管理端查看该任务的详细错误')
                return result
            time.sleep(1)
        raise Error('等待验证超时；任务可能仍在运行，请用 job 查询，不能直接发布')
    def find(self,slug):
        # Keywords cover titles/tags/source; exact slug lookup needs paginated reads.
        page=1
        while True:
            data=self.request('GET','/api/admin/oj/problems?'+urllib.parse.urlencode({'page':page,'size':50}))
            records=data.get('records',[])
            found=next((item for item in records if item['slug']==slug),None)
            if found:return found
            if not records or page*int(data.get('size',50))>=int(data['total']):return None
            page+=1

def corpus(client,args):
    manifest=read(args.dir/'manifest.json');items=manifest['items']
    if args.slugs:
        names=set(args.slugs.split(','));items=[item for item in items if item['slug'] in names]
        if {item['slug'] for item in items}!=names:raise Error('选定的 slug 不在 manifest 中')
    elif not args.all:raise Error('明确选择 --slugs 或 --all；不会默认导入所有题目')
    completed=[];failures=[]
    def one(item):
        slug=item['slug'];pack=read(args.dir/'problems'/f'{slug}.json');lint(pack)
        old=client.find(slug)
        if old:
            current=client.problem(old['id']);stored=current['pack']
            if stored!=pack:raise Error(slug+' 已存在且内容不同；请使用带 revision 的 update 进行修改')
            id=old['id']
        else:id=client.request('POST','/api/admin/oj/problems',pack)['id']
        result={'slug':slug,'id':id,'status':'DRAFT'}
        if args.publish:
            if not old or not old.get('validated'):client.validate(id,args.timeout)
            client.request('PUT',f'/api/admin/oj/problems/{id}/status',{'status':'PUBLISHED'});result['status']='PUBLISHED'
        solution=args.dir/'solutions'/f'{slug}.json'
        if solution.exists():
            doc=read(solution);lint_solution(doc,pack);doc['problemRevision']=int(client.problem(id)['revision'])
            client.request('PUT',f'/api/admin/oj/problems/{id}/solution',doc);result['solution']=True
        return result
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.parallel) as pool:
        futures={pool.submit(one,item):item['slug'] for item in items}
        for f in concurrent.futures.as_completed(futures):
            slug=futures[f]
            try:
                result=f.result();completed.append(result);print(json.dumps({'completed':len(completed),'total':len(items),**result},ensure_ascii=False),flush=True)
            except Exception as e:failures.append({'slug':slug,'error':str(e)});print(json.dumps({'failed':slug,'error':str(e)},ensure_ascii=False),file=sys.stderr,flush=True)
    report={'completed':completed,'failures':failures}
    if args.report:
        args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    if failures:raise Error(f'{len(failures)} 题未完成；已成功的题目保留，修复后可以安全重试')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url',default=os.environ.get('OJ_BASE_URL','http://127.0.0.1:8080'))
    parser.add_argument('--token-file',type=Path,default=ROOT/'secrets/admin-api-token.json')
    parser.add_argument('--env-file',type=Path,default=ROOT/'.env')
    sub=parser.add_subparsers(dest='command',required=True)
    sub.add_parser('login');sub.add_parser('health')
    lint_p=sub.add_parser('lint');lint_p.add_argument('file',type=Path)
    create=sub.add_parser('create');create.add_argument('file',type=Path)
    update=sub.add_parser('update');update.add_argument('id');update.add_argument('file',type=Path);update.add_argument('--revision',type=int,required=True)
    validate=sub.add_parser('validate');validate.add_argument('id');validate.add_argument('--timeout',type=int,default=1200)
    job=sub.add_parser('job');job.add_argument('id')
    publish=sub.add_parser('publish');publish.add_argument('id');publish.add_argument('--contest-only',action='store_true')
    sol=sub.add_parser('solution');sol.add_argument('id');sol.add_argument('file',type=Path)
    export=sub.add_parser('export');export.add_argument('id');export.add_argument('file',type=Path)
    seed=sub.add_parser('corpus');seed.add_argument('--dir',type=Path,required=True);seed.add_argument('--all',action='store_true');seed.add_argument('--slugs');seed.add_argument('--publish',action='store_true');seed.add_argument('--parallel',type=int,choices=[1,2],default=1);seed.add_argument('--timeout',type=int,default=1200);seed.add_argument('--report',type=Path)
    args=parser.parse_args()
    if args.command=='lint':print(json.dumps(lint(read(args.file)),ensure_ascii=False));return
    client=Client(args.url,args.token_file)
    if args.command=='login':
        private={}
        if args.env_file.exists():
            for line in args.env_file.read_text().splitlines():
                if not line.strip() or line.lstrip().startswith('#'):continue
                k,sep,v=line.partition('=')
                if sep and k in ('OJ_ADMIN_ACCOUNT','OJ_ADMIN_PASSWORD'):private[k]=v.strip().strip(chr(34)).strip(chr(39))
        account=os.environ.get('OJ_ADMIN_ACCOUNT') or private.get('OJ_ADMIN_ACCOUNT');password=os.environ.get('OJ_ADMIN_PASSWORD') or private.get('OJ_ADMIN_PASSWORD')
        if not account or not password:raise Error('在私有环境配置 OJ_ADMIN_ACCOUNT / OJ_ADMIN_PASSWORD；不要把密码作为 CLI 参数')
        result=client.login(account,password)
    elif args.command=='health':result=client.request('GET','/api/admin/oj/health')
    elif args.command=='create':pack=read(args.file);lint(pack);result=client.request('POST','/api/admin/oj/problems',pack)
    elif args.command=='update':pack=read(args.file);lint(pack);result=client.request('PUT',f'/api/admin/oj/problems/{args.id}',{'pack':pack,'revision':args.revision})
    elif args.command=='validate':result=client.validate(args.id,args.timeout)
    elif args.command=='job':row=client.request('GET',f'/api/admin/oj/jobs/{args.id}');result={k:row.get(k) for k in ('id','kind','status','passedCases','totalCases','problemId')}
    elif args.command=='publish':client.request('PUT',f'/api/admin/oj/problems/{args.id}/status',{'status':'CONTEST_ONLY' if args.contest_only else 'PUBLISHED'});result={'published':args.id,'contestOnly':args.contest_only}
    elif args.command=='solution':
        pack=client.problem(args.id);doc=read(args.file);lint_solution(doc,pack['pack'])
        if int(doc.get('problemRevision',0))!=int(pack['revision']):raise Error('题解 problemRevision 必须匹配当前题目，不能导入过期题解')
        result=client.request('PUT',f'/api/admin/oj/problems/{args.id}/solution',doc)
        result={k:result.get(k) for k in ('id','problemSlug','problemRevision','title')}
    elif args.command=='export':
        pack=client.problem(args.id)['pack'];args.file.parent.mkdir(parents=True,exist_ok=True)
        fd=os.open(args.file,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(fd,'w') as file:json.dump(pack,file,ensure_ascii=False,indent=2)
        result={'privateExport':str(args.file)}
    else:corpus(client,args);return
    print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':
    try:main()
    except (Error,OSError,ValueError,KeyError,urllib.error.URLError) as e:print('操作失败: '+str(e),file=sys.stderr);sys.exit(1)
