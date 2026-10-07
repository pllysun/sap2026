export const ojStatuses = {
  AC:{label:'通过',tone:'success',icon:'check',help:'所有测试用例均通过'},
  WA:{label:'答案错误',tone:'warning',icon:'close',help:'输出与期望答案不一致'},
  TLE:{label:'运行超时',tone:'danger',icon:'clock',help:'执行时间超过题目限制'},
  MLE:{label:'内存超限',tone:'danger',icon:'cpu',help:'内存使用超过题目限制'},
  OLE:{label:'输出超限',tone:'danger',icon:'terminal',help:'程序输出超过限制'},
  RE:{label:'运行错误',tone:'danger',icon:'warning',help:'程序异常退出'},
  CE:{label:'编译失败',tone:'warning',icon:'code',help:'代码未能成功编译'},
  SYSTEM_ERROR:{label:'判题异常',tone:'danger',icon:'warning',help:'判题服务发生异常'},
  VALIDATION_FAILED:{label:'验证失败',tone:'danger',icon:'warning',help:'参考解或测试集未通过验证'},
  QUEUED:{label:'排队中',tone:'waiting',icon:'clock',help:'已进入判题队列，等待执行'},
  RUNNING:{label:'运行中',tone:'working',icon:'refresh',help:'正在编译或执行测试用例'},
  PUBLISHED:{label:'已发布',tone:'success',icon:'check',help:'题目已发布'},
  DRAFT:{label:'草稿',tone:'neutral',icon:'edit',help:'题目尚未发布'},
  CONTEST_ONLY:{label:'仅管理端可见',tone:'neutral',icon:'lock',help:'普通题库不展示，只能在已开赛且有权限的比赛题单中访问'},
  ARCHIVED:{label:'已归档',tone:'neutral',icon:'file-save',help:'保留历史记录，停止新提交'},
  DISABLED:{label:'已禁用',tone:'neutral',icon:'close',help:'题目已停用'}
}
export const languageNames={c:'C',cpp:'C++',java:'Java',python:'Python',rust:'Rust',all:'全部语言'}
export const modeNames={STDIO:'完整程序',FUNCTION:'核心函数',all:'全部模式'}
export const kindNames={RUN:'代码运行',SUBMIT:'正式提交',VALIDATE:'题目验证'}
export const eventNames={REJUDGE:'管理员重判',QUEUED:'进入队列',STARTED:'开始运行',FINISHED:'运行结束',NODE_ERROR:'节点故障',REASSIGNED:'切换运行节点',RECOVERED:'任务恢复',STARTUP:'服务启动',QUEUE_ERROR:'队列异常'}
export function formatOjDate(value){
  if(!value)return '—'
  if(/^\d+$/.test(String(value)))return new Date(Number(value)).toLocaleString('zh-CN',{hour12:false})
  return String(value).replace('T',' ').slice(0,19)
}
