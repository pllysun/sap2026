<template>
  <div class="page">
    <div class="page-header"><h1 class="page-title">学习任务</h1></div>

    <div v-if="loading" class="loading"><div class="loading__spinner"></div></div>

    <div v-else-if="!status.hasActivity" class="empty">
      <div class="empty__text">当前暂无进行中的学习活动</div>
      <p class="t-caption mt-2">请等待管理员创建新的学习活动</p>
    </div>

    <template v-else>
      <!-- Status Banner -->
      <div class="status-banner mb-4 anim-in">
        <div>
          <div class="status-banner__title">
            第{{ status.activity.activeWeek }}周匹配周期
            <span class="badge badge--gradient" style="margin-left:8px;">进行中</span>
          </div>
          <div class="status-banner__desc">{{ status.activity.title || `第${status.activity.seqNum}次学习活动` }}</div>
        </div>
        <button v-if="!status.joined" class="btn btn--primary btn--pill" @click="joinActivity" :disabled="joining">
          {{ joining ? '加入中…' : '＋ 加入学习小组' }}
        </button>
      </div>

      <div v-if="joinError" class="error-text mb-3">{{ joinError }}</div>

      <!-- Split: Task + Submit -->
      <template v-if="status.joined">
        <div class="card anim-in" style="animation-delay:0.05s;">
          <h3 class="t-heading mb-3">学习任务与作业管理</h3>
          <div class="split">
            <!-- Left: Task -->
            <div>
              <div class="split__title">📄 本周学习任务</div>
              <div v-if="status.homework" class="card card--gradient" style="padding: var(--s4);">
                <div class="flex gap-2" style="align-items:center;">
                  <div style="width:40px;height:40px;background:rgba(59,130,246,0.1);border-radius:10px;display:flex;align-items:center;justify-content:center;font-size:1.2rem;">📘</div>
                  <div>
                    <div style="font-weight:600;color:var(--ink-800);">{{ status.homework.title || status.homework.fileName || '学习资料' }}</div>
                    <div class="t-caption">
                      {{ status.homework.fileName ? (Math.round((status.homework.fileSize||0)/1024*100)/100) + ' KB' : '' }}
                      <span v-if="status.homework.createdAt"> · {{ formatTime(status.homework.createdAt) }}</span>
                    </div>
                  </div>
                </div>
                <a
                  v-if="status.homework.fileUrl"
                  :href="'/api/file/download?url=' + encodeURIComponent(status.homework.fileUrl) + '&name=' + encodeURIComponent(status.homework.fileName || 'file')"
                  target="_blank"
                  class="btn btn--primary btn--sm btn--pill mt-3"
                >↓ 下载文件</a>
              </div>
              <p v-else class="t-caption">本周暂无学习任务</p>
            </div>

            <!-- Right: Submit -->
            <div>
              <div class="split__title">⬆ 我的作业提交</div>
              <div v-if="status.submitted">
                <span class="badge badge--success mb-2">已提交</span>
                <div v-if="status.submissions && status.submissions.length">
                  <p class="t-caption" v-for="s in status.submissions" :key="s.id">{{ s.fileName }} · {{ formatTime(s.createdAt) }}</p>
                </div>
              </div>
              <div v-else>
                <div class="form-group">
                  <label class="form-label">作业标题</label>
                  <input v-model="hwTitle" type="text" class="input" placeholder="输入标题" />
                </div>
                <div class="form-group">
                  <label class="form-label">上传文件</label>
                  <input type="file" ref="fileInput" @change="handleFileSelect" class="input" style="padding-top:8px;" />
                </div>
                <button class="btn btn--primary btn--sm btn--pill" @click="submitHomework" :disabled="submitting || !selectedFile">
                  {{ submitting ? '提交中…' : '提交作业' }}
                </button>
                <div v-if="submitError" class="error-text mt-1">{{ submitError }}</div>
                <div v-if="submitSuccess" class="success-text mt-1">{{ submitSuccess }}</div>
              </div>
            </div>
          </div>
        </div>
      </template>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '@/utils/request'

const loading = ref(true)
const status = ref({})
const joining = ref(false); const joinError = ref('')
const hwTitle = ref(''); const selectedFile = ref(null); const fileInput = ref(null)
const submitting = ref(false); const submitError = ref(''); const submitSuccess = ref('')

onMounted(() => loadStatus())

async function loadStatus() { loading.value = true; try { const r = await request.get('/api/study/my-status'); status.value = r.data } catch {} finally { loading.value = false } }

async function joinActivity() { joining.value = true; joinError.value = ''; try { await request.post('/api/study/member/auto-join'); await loadStatus() } catch (e) { joinError.value = e.message || '加入失败' } finally { joining.value = false } }

function handleFileSelect(e) { selectedFile.value = e.target.files[0] || null }

async function submitHomework() {
  if (!selectedFile.value) return; submitting.value = true; submitError.value = ''; submitSuccess.value = ''
  try {
    const fd = new FormData(); fd.append('file', selectedFile.value)
    const up = await request.post('/api/file/upload', fd)
    await request.post('/api/study/homework/upload', { activityId: status.value.activity.id, week: status.value.activity.activeWeek, title: hwTitle.value || up.data.originalName || selectedFile.value.name, fileUrl: up.data.url, fileName: up.data.originalName || selectedFile.value.name })
    submitSuccess.value = '提交成功'; await loadStatus()
  } catch (e) { submitError.value = e.message || '提交失败' } finally { submitting.value = false }
}

function formatTime(t) { return t ? new Date(t).toLocaleString('zh-CN') : '' }
</script>
