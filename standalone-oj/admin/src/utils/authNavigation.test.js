import test from 'node:test'
import assert from 'node:assert/strict'
import {safeRedirect} from './authNavigation.js'
test('登录后可以恢复题目和学习路径',()=>{assert.equal(safeRedirect('/oj/3?tab=solution'),'/oj/3?tab=solution');assert.equal(safeRedirect('/oj/learning'),'/oj/learning')})
test('外部地址和异常输入不成为登录跳转目的地',()=>{for(const value of ['https://evil.test','//evil.test','/\\evil.test','/login',undefined,['/oj'],'javascript:alert(1)'])assert.equal(safeRedirect(value),'/oj')})
