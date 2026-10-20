import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
const root = fileURLToPath(new URL('../public/', import.meta.url))
function clean(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const file = path.join(dir, entry.name)
    if (entry.isDirectory() && entry.name === '__pycache__') fs.rmSync(file, { recursive: true })
    else if (entry.isDirectory()) clean(file)
    else if (entry.isFile() && entry.name.endsWith('.pyc')) fs.unlinkSync(file)
  }
}
if (fs.existsSync(root)) clean(root)
