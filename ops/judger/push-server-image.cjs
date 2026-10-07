// Push a tested server-built image with an ephemeral registry config. Never log credentials.
const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),cp=require('node:child_process')
const image=process.argv[2];if(!/^pllysun\/sap:\d+\.\d+\.\d+$/.test(image||''))throw Error('Expected immutable SAP image tag')
const buildNode=process.argv.includes('--build-node');
const cfg=JSON.parse(fs.readFileSync(path.join(os.homedir(),'.docker/config.json'))),registry='https://index.docker.io/v1/'
let auth=cfg.auths?.[registry]?.auth
if(!auth){const helper=cfg.credHelpers?.[registry]||cfg.credsStore;if(!/^[a-z0-9-]+$/.test(helper||''))throw Error('Registry credential helper unavailable');const result=cp.spawnSync('/Applications/Docker.app/Contents/Resources/bin/docker-credential-'+helper,['get'],{input:registry+'\n',encoding:'utf8'});if(result.status!==0)throw Error('Registry credentials unavailable');const {Username,Secret}=JSON.parse(result.stdout);auth=Buffer.from(Username+':'+Secret).toString('base64')}
const config=JSON.stringify({auths:{[registry]:{auth}}})
const docker=buildNode?['docker','-H','unix:///run/sap-judger-docker.sock']:['docker'];
const script=`import os,subprocess,tempfile,shutil,json\nfolder=tempfile.mkdtemp(prefix='sap-image-push-')\ntry:\n os.chmod(folder,0o700)\n with open(folder+'/config.json','w') as out: out.write(${JSON.stringify(config)})\n os.chmod(folder+'/config.json',0o600)\n env=dict(os.environ);env['DOCKER_CONFIG']=folder\n result=subprocess.call(${JSON.stringify([...docker,'push',image])},env=env)\n if result: raise SystemExit('Image push failed')\nfinally:\n shutil.rmtree(folder)\n`
const connection=buildNode?['ssh',['-i',path.join(os.homedir(),'Library/Caches/sap-judger-pllysun/identity'),'-o','IdentitiesOnly=yes','-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','UserKnownHostsFile='+path.join(os.homedir(),'Library/Caches/sap-judger-pllysun/known_hosts'),'root@pllysun.top','python3 -']]:[process.execPath,[path.join(__dirname,'../server-ssh.cjs'),'python3 -']];
const child=cp.spawn(connection[0],connection[1],{stdio:['pipe','inherit','inherit']});child.stdin.end(script);child.on('exit',code=>{process.exitCode=code||0})
