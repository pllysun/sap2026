// Use the ignored local deployment credentials without logging private key material.
// Usage: node ops/server-ssh.cjs 'read-only or approved deployment command'
// Remote scripts may be supplied on stdin. Temporary key files are always removed.
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { spawn } = require('node:child_process')
const root = path.resolve(__dirname, '..')
function readEnv(file) {
  return Object.fromEntries(fs.readFileSync(file, 'utf8').split(/\r?\n/).flatMap(line => {
    const match = line.match(/^\s*(?:export\s+)?([A-Z_][A-Z_0-9]*)=(.*)$/)
    if (!match) return []
    return [[match[1], match[2].trim().replace(/^(["'])(.*)\1$/, '$2')]]
  }))
}
const env = readEnv(path.join(root, 'ops/server.local.env'))
const command = process.argv[2]
if (!command) throw new Error('A remote command is required')
let key = (env.SAP_SERVER_KEY_PATH || '').replace(/^~/, os.homedir()), temporary
if (!key || !fs.existsSync(key)) {
  const document = fs.readFileSync(path.join(root, 'ops/REMOTE_DEBUG.local.md'), 'utf8')
  const pem = document.match(/-----BEGIN ([A-Z ]*PRIVATE KEY)-----[\s\S]*?-----END \1-----/)
  if (!pem) throw new Error('No local SSH identity available')
  temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'sap-deployment-identity-'))
  fs.chmodSync(temporary, 0o700)
  key = path.join(temporary, 'identity')
  fs.writeFileSync(key, pem[0] + '\n', { mode: 0o600, flag: 'wx' })
}
const cleanup = () => { if (temporary) fs.rmSync(temporary, { recursive: true, force: true }) }
const forwardIndex = process.argv.indexOf('--forward')
const forward = forwardIndex < 0 ? null : process.argv[forwardIndex + 1]
const reverseIndex = process.argv.indexOf('--reverse')
const reverse = reverseIndex < 0 ? null : process.argv[reverseIndex + 1]
if (forward && !/^\d{1,5}:127\.0\.0\.1:\d{1,5}$/.test(forward)) {
  cleanup(); throw new Error('Forward must be LOCAL_PORT:127.0.0.1:REMOTE_PORT')
}
if (reverse && !/^\d{1,5}:127\.0\.0\.1:\d{1,5}$/.test(reverse)) {
  cleanup(); throw new Error('Reverse must be REMOTE_PORT:127.0.0.1:LOCAL_PORT')
}
const child = spawn('ssh', ['-i', key, '-p', env.SAP_SERVER_PORT || '22',
  '-o', 'IdentitiesOnly=yes', '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes',
  '-o', 'ConnectTimeout=15', '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=4',
  ...(forward ? ['-o', 'ExitOnForwardFailure=yes', '-L', '127.0.0.1:' + forward] : []),
  ...(reverse ? ['-o', 'ExitOnForwardFailure=yes', '-R', '127.0.0.1:' + reverse] : []),
  `${env.SAP_SERVER_USER}@${env.SAP_SERVER_HOST}`, command], { stdio: 'inherit' })
child.on('error', error => { cleanup(); console.error(error.message); process.exitCode = 1 })
child.on('exit', code => { cleanup(); process.exitCode = code ?? 1 })
process.on('SIGINT', () => child.kill('SIGINT'))
process.on('SIGTERM', () => child.kill('SIGTERM'))
process.on('exit', cleanup)
