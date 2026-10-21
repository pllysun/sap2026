import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { verifyAdminBuild } from './verify-build.mjs'

test('管理端产物必须在部署前缀下且实际存在', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sap-admin-build-'))
  try {
    mkdirSync(join(dir, 'assets'))
    writeFileSync(join(dir, 'assets/index.js'), 'export {}')
    writeFileSync(join(dir, 'index.html'), '<script type="module" src="/admin/assets/index.js"></script>')
    assert.deepEqual(verifyAdminBuild(dir), { base: '/admin/', references: 1 })
    writeFileSync(join(dir, 'index.html'), '<script type="module" src="/assets/index.js"></script>')
    assert.throws(() => verifyAdminBuild(dir), /未使用构建前缀/)
    writeFileSync(join(dir, 'index.html'), '<script type="module" src="/admin/assets/missing.js"></script>')
    assert.throws(() => verifyAdminBuild(dir), /ENOENT/)
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})
