// Run an authorized verification with ignored local credentials, without logging them.
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process')
const env={...process.env}
for(const name of ['server.local.env','app-release.local.env'])for(const line of fs.readFileSync(path.join(__dirname,name),'utf8').split(/\r?\n/)){const m=line.match(/^\s*(?:export\s+)?([A-Z_][A-Z_0-9]*)=(.*)$/);if(m)env[m[1]]=m[2].trim().replace(/^(["'])(.*)\1$/,'$2')}
const [script,...args]=process.argv.slice(2);if(!script)throw Error('Verification script required')
const child=cp.spawn(process.execPath,[script,...args],{env,stdio:'inherit'});child.on('exit',code=>{process.exitCode=code||0})
