// Local, credential-free static preview of the exact release assets.
const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const root=process.argv[2],port=Number(process.argv[3]||4176);
if(!root)throw new Error('Pass the release static directory');
const types={'.html':'text/html; charset=utf-8','.js':'text/javascript','.css':'text/css','.wasm':'application/wasm','.json':'application/json','.svg':'image/svg+xml','.png':'image/png','.ico':'image/x-icon','.woff2':'font/woff2'};
http.createServer((req,res)=>{
  const url=new URL(req.url,'http://localhost'),admin=url.pathname.startsWith('/admin/');
  const base=path.resolve(root,admin?'admin':'user'),relative=decodeURIComponent(admin?url.pathname.slice(7):url.pathname.slice(1));
  if(url.pathname.startsWith('/api/')){res.writeHead(503);res.end('API is supplied only by the test browser');return;}
  let target=path.resolve(base,relative);
  if(!target.startsWith(base+path.sep)&&target!==base){res.writeHead(403);res.end();return;}
  if(!fs.existsSync(target)||!fs.statSync(target).isFile())target=path.join(base,'index.html');
  res.writeHead(200,{'Content-Type':types[path.extname(target)]||'application/octet-stream','Cache-Control':'no-store'});
  fs.createReadStream(target).pipe(res);
}).listen(port,'127.0.0.1',()=>console.log('Release preview on http://127.0.0.1:'+port));
