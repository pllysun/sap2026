import request from '../utils/request'

// ===== 认证 =====
export const adminLogin = (data) => request.post('/api/auth/admin/login', data)
export const getUserInfo = () => request.get('/api/auth/info')
export const logout = () => request.post('/api/auth/logout')

// ===== 仪表盘 =====
export const getDashboardStats = (grade) => request.get('/api/dashboard/stats', { params: grade ? { grade } : {} })
export const getGradeStats = () => request.get('/api/dashboard/grade-stats')

// ===== 用户 =====
export const getUserList = (params) => request.get('/api/user/list', { params })
export const updateUser = (id, data) => request.put(`/api/user/${id}`, data)
export const updateUserRoles = (id, data) => request.put(`/api/user/${id}/roles`, data)
export const getUserRoles = (id) => request.get(`/api/user/${id}/roles`)
export const upgradeUser = (id) => request.post(`/api/user/${id}/upgrade`)
export const batchUpgradeUsers = (ids) => request.post('/api/user/batch-upgrade', ids)
export const getMemberUsers = () => request.get('/api/user/members')
export const resetUserPassword = (data) => request.post('/api/user/reset-password', data)

// ===== 设置 =====
export const getSettings = () => request.get('/api/setting/list')
export const updateSetting = (data) => request.put('/api/setting', data)
export const getSettingValue = (key) => request.get('/api/setting/value', { params: { key } })
export const getPublicSettings = () => request.get('/api/setting/public')

// ===== COS 对象存储配置 =====
export const getCosConfig = () => request.get('/api/setting/cos-config')
export const updateCosConfig = (data) => request.put('/api/setting/cos-config', data)
export const testCosConnection = () => request.post('/api/setting/cos-test')

// ===== App 版本发布（在线升级） =====
export const getAppVersion = () => request.get('/api/app/version')
export const getCosStatus = () => request.get('/api/file/cos-status')
export const publishAppVersion = (formData) => request.post('/api/app/version/publish', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
})

// ===== 身份 =====
export const getPositions = () => request.get('/api/position/list')
export const addPosition = (data) => request.post('/api/position', data)
export const updatePosition = (id, data) => request.put(`/api/position/${id}`, data)
export const deletePosition = (id) => request.delete(`/api/position/${id}`)

// ===== 换届 =====
export const getTermList = (params) => request.get('/api/term/list', { params })
export const getGrades = () => request.get('/api/term/grades')
export const addTerm = (data) => request.post('/api/term', data)
export const deleteTerm = (id) => request.delete(`/api/term/${id}`)
export const doChangeover = (data) => request.post('/api/term/changeover', data)

// ===== 活动 =====
export const getActivities = (grade) => request.get('/api/activity/list', { params: { grade } })
export const addActivity = (data) => request.post('/api/activity', data)
export const updateActivity = (id, data) => request.put(`/api/activity/${id}`, data)
export const deleteActivity = (id) => request.delete(`/api/activity/${id}`)
export const getActivityYears = () => request.get('/api/activity/years')
export const getActivityCount = (grade) => request.get('/api/activity/count', { params: { grade } })

// ===== 财务 =====
export const getBills = (params) => request.get('/api/bill/list', { params })
export const addBill = (data) => request.post('/api/bill', data)
export const updateBill = (id, data) => request.put(`/api/bill/${id}`, data)
export const deleteBill = (id) => request.delete(`/api/bill/${id}`)
export const getBillStats = (grade) => request.get('/api/bill/stats', { params: { grade } })

// ===== 学习小组 =====
export const getStudyActivities = (grade) => request.get('/api/study/activity/list', { params: { grade } })
export const createStudyActivity = (data) => request.post('/api/study/activity', data)
export const getStudyActivityDetail = (id) => request.get(`/api/study/activity/${id}/detail`)
export const closeStudyActivity = (id) => request.put(`/api/study/activity/${id}/close`)
export const getStudyCycleDetail = (params) => request.get('/api/study/cycle/detail', { params })
export const getStudyLeaders = (activityId) => request.get('/api/study/leader/list', { params: { activityId } })
export const addStudyLeader = (data) => request.post('/api/study/leader', data)
export const deleteStudyLeader = (id) => request.delete(`/api/study/leader/${id}`)
export const restoreStudyLeader = (id) => request.put(`/api/study/leader/${id}/restore`)
export const getStudyMembers = (params) => request.get('/api/study/member/list', { params })
export const joinStudy = (data) => request.post('/api/study/member/join', data)
export const batchJoinStudy = (data) => request.post('/api/study/member/batch-join', data)
export const reassignMember = (data) => request.put('/api/study/member/assign', data)
export const nextWeek = (data) => request.post('/api/study/week/next', data)
export const uploadHomework = (data) => request.post('/api/study/homework/upload', data)
export const deleteHomework = (params) => request.delete('/api/study/homework', { params })
export const getHomeworkSchedule = (activityId) => request.get('/api/study/homework/schedule', { params: { activityId } })
export const setActiveWeek = (id, week) => request.put(`/api/study/activity/${id}/active-week`, { week })
export const submitScore = (data) => request.post('/api/study/score', data)
export const getScoreOverview = (params) => request.get('/api/study/score/overview', { params })

// ===== 留言 =====
export const getMessages = (params) => request.get('/api/message/list', { params })
export const deleteMessage = (id) => request.delete(`/api/message/${id}`)

// ===== 文件 =====
export const uploadFile = (formData) => request.post('/api/file/upload', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
})
export const batchUpload = (formData) => request.post('/api/file/upload/batch', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
})

// ===== 优秀成员 =====
export const getOutstandingMembers = (grade) => request.get('/api/outstanding-member/list', { params: { grade } })
export const getAllOutstandingMembers = () => request.get('/api/outstanding-member/all')
export const addOutstandingMember = (data) => request.post('/api/outstanding-member', data)
export const updateOutstandingMember = (id, data) => request.put(`/api/outstanding-member/${id}`, data)
export const deleteOutstandingMember = (id) => request.delete(`/api/outstanding-member/${id}`)

// ===== 日志 =====
export const getLogList = (params) => request.get('/api/log/list', { params })
export const getLogStats = (days) => request.get('/api/log/stats', { params: { days } })

// ===== 入会管理 =====
export const getJoinStatus = () => request.get('/api/join/status')
export const toggleJoin = (enabled) => request.post('/api/join/toggle', { enabled })
export const getJoinManagers = () => request.get('/api/join/managers')
export const addJoinManager = (userId) => request.post('/api/join/manager', { userId })
export const removeJoinManager = (id) => request.delete(`/api/join/manager/${id}`)
export const uploadManagerQr = (data) => request.post('/api/join/manager/qr', data)
export const getJoinApplications = (params) => request.get('/api/join/applications', { params })
export const approveJoinApplication = (id) => request.post(`/api/join/approve/${id}`)
export const directUpgradeMember = (studentId) => request.post('/api/join/direct-upgrade', { studentId })

// ===== 流量统计 =====
export const getStatsOverview = (days) => request.get('/api/stats/overview', { params: { days } })
export const getCosByUser = (days) => request.get('/api/stats/cos/by-user', { params: { days } })
export const getCosTrend = (days) => request.get('/api/stats/cos/trend', { params: { days } })
export const getApiTop = (params) => request.get('/api/stats/api/top', { params })
export const getApiTrend = (days) => request.get('/api/stats/api/trend', { params: { days } })
export const getApiByUser = (params) => request.get('/api/stats/api/by-user', { params })
export const getApiByEndpoint = (params) => request.get('/api/stats/api/by-endpoint', { params })
export const getApiDetail = (params) => request.get('/api/stats/api/detail', { params })
export const getStatsUsers = () => request.get('/api/stats/users')

// ===== 软协笔记 =====
export const getNoteList = (params) => request.get('/api/note/list', { params })
export const getNoteDetail = (id) => request.get(`/api/note/${id}`)
export const addNote = (data) => request.post('/api/note', data)
export const uploadNote = (formData) => request.post('/api/note/upload', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
})
export const updateNote = (id, data) => request.put(`/api/note/${id}`, data)
export const deleteNote = (id) => request.delete(`/api/note/${id}`)
export const getNoteStats = (id) => request.get(`/api/note/${id}/stats`)
export const recordNoteDownload = (id) => request.post(`/api/note/${id}/download`)
