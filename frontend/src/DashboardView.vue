<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  Activity,
  Bot,
  Check,
  ChevronRight,
  CircleHelp,
  Clock3,
  Code2,
  FileCode2,
  FolderKanban,
  Gauge,
  GitBranch,
  LayoutDashboard,
  Play,
  Plus,
  Rocket,
  Search,
  Settings2,
  Sparkles,
  TimerReset,
  TriangleAlert,
  Users,
} from 'lucide-vue-next'
import { getCurrentUser, getToken, clearSession } from './auth'
import { useRouter } from 'vue-router'

const activeNav = ref('总览')
const activeFile = ref('index.html')
const isGenerating = ref(false)
const prompt = ref('')
const toast = ref('')
const showModelCenter = ref(false)
const modelConfigs = ref([])
const modelForm = ref({ id: '', name: '', baseUrl: 'http://127.0.0.1:11434/v1', apiKey: '', model: 'qwen2.5-coder:7b', active: true })
const modelBusy = ref(false)
const modelTest = ref({})
const now = ref(new Date())
let ticker
const API_BASE = 'http://127.0.0.1:8080'
const router = useRouter()
const currentUser = ref(getCurrentUser() || { name: '开发者', email: '' })
const workspaces = ref([])
const activeWorkspace = ref({ name: '个人开发空间', description: '个人工作区' })
const workspaceOpen = ref(false)
const projects = ref([])
const selectedProject = ref(null)
const projectTasks = ref([])
const activeSession = ref(null)
const timerSeconds = ref(0)
const stats = ref({ projectCount: 0, taskCount: 0, completedTaskCount: 0, focusSeconds: 0 })
const checkin = ref({ checkedInToday: false, streak: 0, total: 0, dates: [] })
const reviewIssues = ref([])
const projectQuery = ref('')
const projectStatusFilter = ref('ALL')
const knowledgeEntries = ref([])
const knowledgeQuery = ref('')
let timerTicker
let fileSaveTimer

const files = ref({
  'index.html': `<main class="hero">\n  <span class="eyebrow">CODEORBIT AI</span>\n  <h1>把想法，变成可运行的代码。</h1>\n  <p>在星码空间中生成、编辑、预览和评审你的下一个项目。</p>\n  <button id="launch">开始探索</button>\n</main>`,
  'style.css': `:root {\n  font-family: Inter, system-ui, sans-serif;\n  color: #f3fbf8;\n  background: #0b171d;\n}\nbody { margin: 0; min-height: 100vh; }\n.hero { max-width: 720px; margin: 10vh auto; padding: 48px; }\n.eyebrow { color: #55e6ae; letter-spacing: .16em; font-size: 12px; }\nh1 { font-size: clamp(32px, 6vw, 68px); line-height: 1.02; margin: 18px 0; }\np { color: #a5bdb7; font-size: 18px; line-height: 1.6; }\nbutton { border: 0; border-radius: 999px; padding: 14px 22px; background: #55e6ae; color: #06110f; font-weight: 700; }`,
  'script.js': `document.querySelector('#launch')?.addEventListener('click', () => {\n  document.querySelector('.hero p').textContent = '预览已连接，开始你的创作。'\n})`,
})

const navItems = [
  { label: '总览', icon: LayoutDashboard },
  { label: '我的项目', icon: FolderKanban },
  { label: 'AI 生成', icon: Sparkles },
  { label: '代码评审', icon: TriangleAlert },
  { label: '专注空间', icon: TimerReset },
  { label: '成长数据', icon: Activity },
]

const metricCards = computed(() => [
  { label: '项目总数', value: stats.value.projectCount, suffix: ' 个', trend: '数据库实时', icon: FolderKanban, tone: 'mint' },
  { label: '待办任务', value: Math.max(0, stats.value.taskCount - stats.value.completedTaskCount), suffix: ' 个', trend: `${stats.value.taskCount} 个任务`, icon: GitBranch, tone: 'blue' },
  { label: '有效时长', value: (stats.value.focusSeconds / 3600).toFixed(1), suffix: ' 小时', trend: '已记录', icon: Clock3, tone: 'gold' },
  { label: '已完成任务', value: stats.value.completedTaskCount, suffix: ' 个', trend: stats.value.taskCount ? `${Math.round(stats.value.completedTaskCount / stats.value.taskCount * 100)}% 完成率` : '暂无任务', icon: Check, tone: 'pink' },
])
const filteredProjects = computed(() => projects.value.filter(project => {
  const matchesQuery = !projectQuery.value.trim() || `${project.name} ${project.description} ${project.techStack}`.toLowerCase().includes(projectQuery.value.trim().toLowerCase())
  const matchesStatus = projectStatusFilter.value === 'ALL' || project.status === projectStatusFilter.value
  return matchesQuery && matchesStatus
}))
const openReviewCount = computed(() => reviewIssues.value.filter(issue => issue.status === 'OPEN').length)
const filteredKnowledge = computed(() => knowledgeEntries.value.filter(entry => `${entry.title} ${entry.content} ${entry.tags}`.toLowerCase().includes(knowledgeQuery.value.trim().toLowerCase())))

const issues = [
  { level: '高风险', title: '按钮事件缺少异常处理', file: 'script.js:1', tone: 'danger' },
  { level: '规范', title: '建议抽离重复颜色变量', file: 'style.css:1', tone: 'warn' },
  { level: '优化', title: '可以增加移动端断点', file: 'style.css:8', tone: 'info' },
]

const previewDoc = computed(() => {
  const html = files.value['index.html']
  const css = files.value['style.css']
  const js = files.value['script.js']
  const endTag = '<' + '/script>'
  const safeJs = js.split(endTag).join('<' + '\\/script>')
  return '<!doctype html><html><head><meta charset="utf-8"><style>' + css + '</style></head><body>' + html + '<script>' + safeJs + '<' + '/script></body></html>'
})

const formattedTime = computed(() => now.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }))

function updateFile(value) {
  files.value[activeFile.value] = value
  if (selectedProject.value) {
    window.clearTimeout(fileSaveTimer)
    fileSaveTimer = window.setTimeout(() => saveFile(activeFile.value), 500)
  }
}

function showComingSoon(label) {
  if (label === '新建文件') { createFile(); return }
  if (label === '知识库条目') { createKnowledge(); return }
  toast.value = `${label} 模块将在下一阶段接入真实数据。`
}

function runPreview() {
  toast.value = '预览已刷新。'
  window.setTimeout(() => (toast.value = ''), 2200)
}

function createProject() {
  const name = window.prompt('项目名称', '新项目')
  if (!name?.trim()) return
  createProjectRecord(name.trim())
}
function editProject(project) {
  const name = window.prompt('项目名称', project.name)
  if (!name?.trim()) return
  updateProject(project, name.trim(), project.status)
}
async function updateProject(project, name, status = project.status) {
  try {
    const response = await fetch(`${API_BASE}/api/projects/${project.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}` }, body: JSON.stringify({ name, description: project.description || '', techStack: project.techStack || 'Vue 3 · HTML/CSS/JavaScript', status }) })
    const data = await response.json()
    if (!response.ok || data.message) throw new Error(data.message || '项目更新失败')
    Object.assign(project, data.project)
    toast.value = '项目已更新。'
  } catch (error) { toast.value = error.message || '项目更新失败。' }
}
async function updateProjectStatus(status) {
  if (!selectedProject.value) return
  await updateProject(selectedProject.value, selectedProject.value.name, status)
}
async function removeProject(project) {
  if (!window.confirm(`删除项目“${project.name}”？`)) return
  const response = await fetch(`${API_BASE}/api/projects/${project.id}`, { method: 'DELETE', headers: { Authorization: `Bearer ${getToken()}` } })
  if (!response.ok) { toast.value = '项目删除失败。'; return }
  projects.value = projects.value.filter(item => item.id !== project.id)
  selectedProject.value = projects.value[0] || null
  projectTasks.value = []
  if (selectedProject.value) { await loadTasks(selectedProject.value.id); await loadProjectFiles(selectedProject.value.id); await loadReviews(selectedProject.value.id) }
  await loadStats()
  toast.value = '项目已删除。'
}

async function createProjectRecord(name) {
  try {
    const response = await fetch(`${API_BASE}/api/projects`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}` }, body: JSON.stringify({ workspaceId: activeWorkspace.value.id, name, description: '由星码空间创建的项目' }) })
    const data = await response.json()
    if (!response.ok || data.message) throw new Error(data.message || '创建失败')
    projects.value.unshift(data.project)
    selectedProject.value = data.project
    projectTasks.value = []
    await persistAllFiles(data.project.id)
    await loadReviews(data.project.id)
    await loadStats()
    toast.value = '项目已创建。'
  } catch (error) { toast.value = error.message || '项目创建失败。' }
}

async function generateCode() {
  if (isGenerating.value) return
  isGenerating.value = true
  toast.value = '正在调用当前模型...'
  try {
    const response = await fetch(`${API_BASE}/api/ai/code/generate`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ prompt: prompt.value || '生成一个简洁的科技感首页' }) })
    if (!response.ok) throw new Error(`模型接口返回 HTTP ${response.status}`)
    const result = await response.json()
    files.value['index.html'] = result.content || files.value['index.html']
    activeFile.value = 'index.html'
    if (selectedProject.value) await saveFile('index.html')
    toast.value = `已使用 ${result.model || '当前模型'} 生成代码。`
  } catch (error) {
    toast.value = '模型暂时不可用，已切换到演示生成模式。'
    window.setTimeout(async () => {
    files.value['index.html'] = `<main class="hero hero-generated">\n  <div class="orbit-mark">✦</div>\n  <span class="eyebrow">AI GENERATED EXPERIENCE</span>\n  <h1>${prompt.value || '让每一次创作，都有即时回应。'}</h1>\n  <p>这是由星码空间生成的交互式页面，你可以继续编辑并在右侧实时查看效果。</p>\n  <button id="launch">进入工作台</button>\n</main>`
    activeFile.value = 'index.html'
    if (selectedProject.value) await saveFile('index.html')
    toast.value = '代码已生成，实时预览已同步更新。'
    window.setTimeout(() => (toast.value = ''), 3200)
    }, 500)
  } finally {
    isGenerating.value = false
  }
}

async function loadModelConfigs() {
  try { const response = await fetch(`${API_BASE}/api/ai/config`); if (!response.ok) return; const data = await response.json(); modelConfigs.value = data.configs || []; } catch { /* 后端未启动时保持本地界面可用 */ }
}
async function loadWorkspaces() {
  try {
    const response = await fetch(`${API_BASE}/api/workspaces`, { headers: { Authorization: `Bearer ${getToken()}` } })
    if (!response.ok) return
    const data = await response.json()
    workspaces.value = data.workspaces || []
    activeWorkspace.value = data.active || activeWorkspace.value
    await loadProjects()
    await loadStats()
    await loadCheckin()
    await loadKnowledge()
  } catch { /* 后端未启动时保留本地工作台 */ }
}
async function switchWorkspace(workspace) {
  workspaceOpen.value = false
  if (!workspace || workspace.id === activeWorkspace.value.id) return
  activeWorkspace.value = workspace
  projects.value = []
  selectedProject.value = null
  projectTasks.value = []
  await loadProjects()
  await loadStats()
  await loadCheckin()
  await loadKnowledge()
}
async function loadProjects() {
  if (!activeWorkspace.value.id) return
  try {
    const response = await fetch(`${API_BASE}/api/projects?workspaceId=${encodeURIComponent(activeWorkspace.value.id)}`, { headers: { Authorization: `Bearer ${getToken()}` } })
    if (!response.ok) return
    const data = await response.json()
    projects.value = data.projects || []
    selectedProject.value = projects.value[0] || null
    if (selectedProject.value) { await loadTasks(selectedProject.value.id); await loadProjectFiles(selectedProject.value.id); await loadReviews(selectedProject.value.id) }
  } catch { /* 后端未启动时保持演示内容 */ }
}
async function loadTasks(projectId) {
  try { const response = await fetch(`${API_BASE}/api/projects/${projectId}/tasks`, { headers: { Authorization: `Bearer ${getToken()}` } }); const data = await response.json(); projectTasks.value = data.tasks || [] } catch { projectTasks.value = [] }
}
async function loadProjectFiles(projectId) {
  try {
    const response = await fetch(`${API_BASE}/api/projects/${projectId}/files`, { headers: { Authorization: `Bearer ${getToken()}` } })
    if (!response.ok) return
    const data = await response.json()
    if (data.files?.length) {
      const loaded = {}
      data.files.forEach(file => { loaded[file.path] = file.content })
      files.value = { ...files.value, ...loaded }
      activeFile.value = Object.keys(loaded).includes(activeFile.value) ? activeFile.value : Object.keys(loaded)[0]
    } else await persistAllFiles(projectId)
  } catch { /* 后端未启动时保留编辑器默认文件 */ }
}
async function loadReviews(projectId) {
  try { const response = await fetch(`${API_BASE}/api/projects/${projectId}/reviews`, { headers: { Authorization: `Bearer ${getToken()}` } }); const data = await response.json(); reviewIssues.value = data.issues || [] } catch { reviewIssues.value = [] }
}
async function resolveReview(issue) {
  if (!selectedProject.value) return
  const response = await fetch(`${API_BASE}/api/projects/${selectedProject.value.id}/reviews/${issue.id}/resolve`, { method: 'POST', headers: { Authorization: `Bearer ${getToken()}` } })
  if (response.ok) { issue.status = 'RESOLVED'; toast.value = '问题已标记为已处理。' }
}
async function saveFile(path) {
  if (!selectedProject.value || !path) return
  try { await fetch(`${API_BASE}/api/projects/${selectedProject.value.id}/files`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}` }, body: JSON.stringify({ path, content: files.value[path] || '' }) }) } catch { /* 后端未启动时允许本地编辑 */ }
}
async function persistAllFiles(projectId) {
  await Promise.all(Object.entries(files.value).map(([path, content]) => fetch(`${API_BASE}/api/projects/${projectId}/files`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}` }, body: JSON.stringify({ path, content }) })))
}
async function loadStats() {
  if (!activeWorkspace.value.id) return
  try {
    const response = await fetch(`${API_BASE}/api/stats?workspaceId=${encodeURIComponent(activeWorkspace.value.id)}`, { headers: { Authorization: `Bearer ${getToken()}` } })
    if (response.ok) stats.value = await response.json()
  } catch { /* 后端未启动时保留零值 */ }
}
async function loadCheckin() {
  if (!activeWorkspace.value.id) return
  try { const response = await fetch(`${API_BASE}/api/checkins?workspaceId=${encodeURIComponent(activeWorkspace.value.id)}`, { headers: { Authorization: `Bearer ${getToken()}` } }); if (response.ok) checkin.value = await response.json() } catch { /* 后端未启动时保留默认状态 */ }
}
async function loadKnowledge() {
  if (!activeWorkspace.value.id) return
  try {
    const response = await fetch(`${API_BASE}/api/knowledge?workspaceId=${encodeURIComponent(activeWorkspace.value.id)}`, { headers: { Authorization: `Bearer ${getToken()}` } })
    if (response.ok) { const data = await response.json(); knowledgeEntries.value = data.entries || [] }
  } catch { knowledgeEntries.value = [] }
}
async function createKnowledge() {
  const title = window.prompt('条目标题', '新的开发笔记')?.trim()
  if (!title) return
  const content = window.prompt('条目内容', '记录一个可复用的开发经验。') || ''
  const tags = window.prompt('标签（可选）', '开发,规范') || ''
  try {
    const response = await fetch(`${API_BASE}/api/knowledge`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}` }, body: JSON.stringify({ workspaceId: activeWorkspace.value.id, title, content, tags }) })
    const data = await response.json()
    if (!response.ok || data.message) throw new Error(data.message || '创建失败')
    knowledgeEntries.value.unshift(data.entry)
    toast.value = '知识库条目已创建。'
  } catch (error) { toast.value = error.message || '知识库保存失败。' }
}
async function editKnowledge(entry) {
  const title = window.prompt('条目标题', entry.title)?.trim()
  if (!title) return
  const content = window.prompt('条目内容', entry.content) ?? entry.content
  const tags = window.prompt('标签（可选）', entry.tags || '') ?? entry.tags
  try {
    const response = await fetch(`${API_BASE}/api/knowledge/${entry.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}` }, body: JSON.stringify({ workspaceId: activeWorkspace.value.id, title, content, tags }) })
    const data = await response.json()
    if (!response.ok || data.message) throw new Error(data.message || '更新失败')
    Object.assign(entry, data.entry)
    toast.value = '知识库条目已更新。'
  } catch (error) { toast.value = error.message || '知识库更新失败。' }
}
async function removeKnowledge(entry) {
  if (!window.confirm(`删除条目“${entry.title}”？`)) return
  const response = await fetch(`${API_BASE}/api/knowledge/${entry.id}?workspaceId=${encodeURIComponent(activeWorkspace.value.id)}`, { method: 'DELETE', headers: { Authorization: `Bearer ${getToken()}` } })
  if (response.ok) { knowledgeEntries.value = knowledgeEntries.value.filter(item => item.id !== entry.id); toast.value = '知识库条目已删除。' }
}
async function doCheckin() {
  if (checkin.value.checkedInToday) return
  try { const response = await fetch(`${API_BASE}/api/checkins`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}` }, body: JSON.stringify({ workspaceId: activeWorkspace.value.id }) }); if (response.ok) { checkin.value = await response.json(); toast.value = '今日签到成功。' } } catch { toast.value = '签到失败，请检查后端服务。' }
}
async function addTask() {
  if (!selectedProject.value) return
  const title = window.prompt('任务名称', '实现首页交互')
  if (!title?.trim()) return
  const response = await fetch(`${API_BASE}/api/projects/${selectedProject.value.id}/tasks`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}` }, body: JSON.stringify({ title: title.trim(), priority: 'MEDIUM' }) })
  const data = await response.json(); if (response.ok && data.task) { projectTasks.value.unshift(data.task); await loadStats() }
}
async function editTask(task) {
  const title = window.prompt('任务名称', task.title)
  if (!title?.trim()) return
  const response = await fetch(`${API_BASE}/api/projects/${selectedProject.value.id}/tasks/${task.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}` }, body: JSON.stringify({ title: title.trim(), priority: task.priority }) })
  if (response.ok) { task.title = title.trim(); toast.value = '任务已更新。' }
}
async function removeTask(task) {
  if (!window.confirm(`删除任务“${task.title}”？`)) return
  const response = await fetch(`${API_BASE}/api/projects/${selectedProject.value.id}/tasks/${task.id}`, { method: 'DELETE', headers: { Authorization: `Bearer ${getToken()}` } })
  if (response.ok) { projectTasks.value = projectTasks.value.filter(item => item.id !== task.id); await loadProjects(); await loadStats(); toast.value = '任务已删除。' }
}
async function createFile() {
  if (!selectedProject.value) { toast.value = '请先选择项目。'; return }
  const path = window.prompt('文件名', 'component.js')?.trim()
  if (!path || files.value[path]) return
  files.value[path] = ''
  activeFile.value = path
  await saveFile(path)
}
async function deleteFile(path) {
  if (!selectedProject.value || Object.keys(files.value).length <= 1 || !window.confirm(`删除文件“${path}”？`)) return
  const response = await fetch(`${API_BASE}/api/projects/${selectedProject.value.id}/files/${encodeURIComponent(path)}`, { method: 'DELETE', headers: { Authorization: `Bearer ${getToken()}` } })
  if (response.ok) { const next = { ...files.value }; delete next[path]; files.value = next; activeFile.value = Object.keys(next)[0]; toast.value = '文件已删除。' }
}
async function completeTask(task) {
  if (!selectedProject.value) return
  await fetch(`${API_BASE}/api/projects/${selectedProject.value.id}/tasks/${task.id}/complete`, { method: 'POST', headers: { Authorization: `Bearer ${getToken()}` } })
  task.status = 'DONE'; const project = projects.value.find(item => item.id === selectedProject.value.id); if (project) project.progress = Math.min(100, Math.round(projectTasks.value.filter(item => item.status === 'DONE').length / projectTasks.value.length * 100)); await loadStats()
}
async function cycleTaskStatus(task) {
  const next = task.status === 'TODO' ? 'IN_PROGRESS' : task.status === 'IN_PROGRESS' ? 'DONE' : 'TODO'
  try {
    const response = await fetch(`${API_BASE}/api/projects/${selectedProject.value.id}/tasks/${task.id}/status`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}` }, body: JSON.stringify({ status: next }) })
    if (!response.ok) return
    task.status = next
    const project = projects.value.find(item => item.id === selectedProject.value.id)
    if (project) { const done = projectTasks.value.filter(item => item.status === 'DONE').length; project.progress = projectTasks.value.length ? Math.round(done / projectTasks.value.length * 100) : 0 }
    await loadStats()
  } catch { toast.value = '任务状态更新失败。' }
}
async function loadActiveSession() {
  try { const response = await fetch(`${API_BASE}/api/time-sessions/active`, { headers: { Authorization: `Bearer ${getToken()}` } }); const data = await response.json(); activeSession.value = data.session; updateTimer() } catch { activeSession.value = null }
}
function updateTimer() { timerSeconds.value = activeSession.value ? Math.max(0, Math.floor((Date.now() - new Date(activeSession.value.startedAt).getTime()) / 1000)) : 0 }
async function toggleTimer() {
  if (activeSession.value) { await fetch(`${API_BASE}/api/time-sessions/${activeSession.value.id}/stop`, { method: 'POST', headers: { Authorization: `Bearer ${getToken()}` } }); activeSession.value = null; await loadStats(); toast.value = '专注计时已结束。'; return }
  if (!selectedProject.value) { toast.value = '请先创建或选择项目。'; return }
  const response = await fetch(`${API_BASE}/api/time-sessions/start`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}` }, body: JSON.stringify({ projectId: selectedProject.value.id }) }); const data = await response.json(); if (response.ok) activeSession.value = data.session
}
const timerLabel = computed(() => `${String(Math.floor(timerSeconds.value / 3600)).padStart(2, '0')}:${String(Math.floor(timerSeconds.value / 60) % 60).padStart(2, '0')}:${String(timerSeconds.value % 60).padStart(2, '0')}`)
function openModelCenter() { showModelCenter.value = true; loadModelConfigs() }
function editModel(config) { modelForm.value = { id: config.id, name: config.name, baseUrl: config.baseUrl, apiKey: '', model: config.model, active: config.active } }
function resetModelForm() { modelForm.value = { id: '', name: '', baseUrl: 'http://127.0.0.1:11434/v1', apiKey: '', model: 'qwen2.5-coder:7b', active: modelConfigs.value.length === 0 } }
async function saveModel() { if (!modelForm.value.name || !modelForm.value.baseUrl || !modelForm.value.model) { toast.value = '请填写模型名称、接口地址和模型名。'; return } modelBusy.value = true; try { const response = await fetch(`${API_BASE}/api/ai/config`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(modelForm.value) }); if (!response.ok) throw new Error(); const data = await response.json(); modelConfigs.value = data.configs || []; resetModelForm(); toast.value = '模型配置已保存。' } catch { toast.value = '保存失败，请先启动 Java 后端。' } finally { modelBusy.value = false } }
async function activateModel(id) { try { const response = await fetch(`${API_BASE}/api/ai/config/${id}/activate`, { method: 'POST' }); if (!response.ok) throw new Error(); await loadModelConfigs(); toast.value = '已切换当前模型。' } catch { toast.value = '切换失败，请检查后端。' } }
async function testModel(id) { modelTest.value[id] = '测试中'; try { const response = await fetch(`${API_BASE}/api/ai/config/${id}/test`, { method: 'POST' }); const data = await response.json(); modelTest.value[id] = data.ok ? '连接成功' : '连接失败' } catch { modelTest.value[id] = '连接失败' } }
async function removeModel(id) { try { const response = await fetch(`${API_BASE}/api/ai/config/${id}`, { method: 'DELETE' }); if (!response.ok) throw new Error(); await loadModelConfigs(); toast.value = '模型配置已删除。' } catch { toast.value = '至少保留一个模型配置。' } }

function selectNav(label) {
  activeNav.value = label
  if (label === '成长数据') toast.value = `已加载 ${stats.value.completedTaskCount} 个完成任务和 ${checkin.value.total} 天签到记录。`
  else if (label !== '总览') toast.value = `${label} 模块正在使用当前项目数据。`
}

function logout() {
  clearSession()
  router.push('/login')
}

onMounted(() => {
  ticker = window.setInterval(() => (now.value = new Date()), 1000)
  timerTicker = window.setInterval(updateTimer, 1000)
  loadModelConfigs()
  loadWorkspaces()
  loadActiveSession()
})

onUnmounted(() => { window.clearInterval(ticker); window.clearInterval(timerTicker) })
</script>

<template>
  <div class="app-shell">
    <div class="stars stars-one" />
    <div class="stars stars-two" />
    <div class="meteor meteor-one" />
    <div class="meteor meteor-two" />
    <aside class="sidebar">
      <div class="brand-lockup">
        <div class="brand-orbit"><span></span></div>
        <div>
          <strong>星码空间</strong>
          <small>CODEORBIT AI</small>
        </div>
      </div>
      <button class="workspace-switcher" title="切换工作空间" @click="workspaceOpen = !workspaceOpen">
        <div class="workspace-icon"><Code2 :size="17" /></div>
        <div class="workspace-copy"><span>{{ activeWorkspace.name }}</span><small>{{ activeWorkspace.description || '工作空间' }}</small></div>
        <ChevronRight :size="16" />
      </button>
      <div v-if="workspaceOpen" class="workspace-menu"><button v-for="workspace in workspaces" :key="workspace.id" :class="{ selected: workspace.id === activeWorkspace.id }" @click="switchWorkspace(workspace)"><Code2 :size="14" /><span>{{ workspace.name }}</span><Check v-if="workspace.id === activeWorkspace.id" :size="14" /></button></div>
      <div class="nav-group-label">工作台</div>
      <nav>
        <button v-for="item in navItems" :key="item.label" class="nav-item" :class="{ active: activeNav === item.label }" @click="selectNav(item.label)">
          <component :is="item.icon" :size="17" />
          <span>{{ item.label }}</span>
          <span v-if="item.label === '代码评审'" class="nav-count">{{ openReviewCount }}</span>
        </button>
      </nav>
      <div class="sidebar-bottom">
        <button class="nav-item" :class="{ active: activeNav === '知识库' }" @click="selectNav('知识库')"><FileCode2 :size="17" /><span>知识库</span></button>
        <button class="nav-item" @click="openModelCenter"><Settings2 :size="17" /><span>模型中心</span></button>
        <button class="profile-chip" title="退出登录" @click="logout"><div class="avatar">{{ currentUser.name.slice(0, 1) }}</div><div><strong>{{ currentUser.name }}</strong><small>{{ currentUser.email || '成长等级 Lv.08' }}</small></div><CircleHelp :size="15" /></button>
      </div>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div class="breadcrumb"><span>工作台</span><ChevronRight :size="14" /><strong>{{ activeNav }}</strong></div>
        <div class="top-actions"><div class="live-status"><span class="status-dot"></span>系统运行正常</div><button class="icon-button" title="搜索" @click="showComingSoon('搜索')"><Search :size="17" /></button><button class="icon-button" title="帮助" @click="showComingSoon('帮助')"><CircleHelp :size="17" /></button><button class="new-project" @click="createProject"><Plus :size="16" />新建项目</button></div>
      </header>

      <section class="content-wrap">
        <section v-if="activeNav === '总览'" class="hero-row">
          <div>
            <p class="eyebrow-text">{{ formattedTime }} · 周四 · 晚间工作区</p>
            <h1>你好，{{ currentUser.name }}。<br /><em>让代码开始生长。</em></h1>
            <p class="hero-subtitle">把需求交给 AI，把判断权留给你。今天继续完成「星夜登录页」吧。</p>
          </div>
          <div class="ai-orb-card"><div class="orb-glow"></div><div class="orb-core"><Bot :size="31" /></div><div><span>AI 导师在线</span><strong>准备好协助你</strong></div><span class="orb-pulse"></span></div>
        </section>

        <section v-if="activeNav === '成长数据'" class="data-overview panel">
          <div class="panel-heading"><div><span class="panel-kicker">GROWTH DATA</span><h2>成长数据</h2></div><button class="run-button" @click="doCheckin"><Check :size="14" />{{ checkin.checkedInToday ? '今日已签到' : '立即签到' }}</button></div>
          <div class="growth-grid"><div><strong>{{ checkin.streak }}</strong><span>连续签到天数</span></div><div><strong>{{ checkin.total }}</strong><span>累计签到天数</span></div><div><strong>{{ stats.completedTaskCount }}</strong><span>完成任务数</span></div><div><strong>{{ (stats.focusSeconds / 3600).toFixed(1) }}</strong><span>专注小时数</span></div></div>
          <div class="checkin-calendar"><span v-for="date in checkin.dates.slice(0, 30)" :key="date" class="checkin-day" :title="date"></span><span v-for="n in Math.max(0, 30 - checkin.dates.length)" :key="`empty-${n}`" class="checkin-day empty"></span></div>
        </section>
        <section v-if="activeNav === '代码评审'" class="review-overview panel">
          <div class="panel-heading"><div><span class="panel-kicker">REVIEW CENTER</span><h2>代码评审中心</h2></div><span class="issue-total">{{ openReviewCount }}</span></div>
          <p class="review-summary">{{ selectedProject ? `当前项目：${selectedProject.name}` : '请先选择一个项目' }} · 问题会保存到数据库。</p>
          <div v-if="reviewIssues.length === 0" class="task-empty">当前项目暂无评审问题。</div>
          <div v-for="issue in reviewIssues" :key="`center-${issue.id}`" class="review-card" :class="{ resolved: issue.status === 'RESOLVED' }"><span class="issue-dot" :class="issue.level === 'HIGH' ? 'danger' : issue.level === 'STYLE' ? 'warn' : 'info'"></span><div><strong>{{ issue.title }}</strong><small>{{ issue.filePath }} · {{ issue.status === 'OPEN' ? '待处理' : '已处理' }}</small></div><button v-if="issue.status === 'OPEN'" class="row-action primary" @click="resolveReview(issue)">标记已处理</button></div>
        </section>
        <section v-if="activeNav === '总览'" class="metrics-grid">
          <article v-for="card in metricCards" :key="card.label" class="metric-card">
            <div class="metric-top"><span>{{ card.label }}</span><span class="metric-icon" :class="card.tone"><component :is="card.icon" :size="16" /></span></div>
            <div class="metric-value">{{ card.value }}<small>{{ card.suffix }}</small></div>
            <div class="metric-trend"><span>{{ card.trend }}</span><span>较上周</span></div>
          </article>
        </section>

        <section v-if="activeNav === '我的项目'" class="page-view panel">
          <div class="page-view-head"><div><span class="panel-kicker">PROJECT MANAGEMENT</span><h2>我的项目</h2><p>管理项目、任务和进度。</p></div><button class="new-project" @click="createProject"><Plus :size="16" />新建项目</button></div>
          <div class="project-toolbar"><div class="project-search"><Search :size="14" /><input v-model="projectQuery" placeholder="搜索项目" /></div><select v-model="projectStatusFilter"><option value="ALL">全部状态</option><option value="IN_PROGRESS">进行中</option><option value="DONE">已完成</option></select></div>
          <div class="project-page-grid"><button v-for="project in filteredProjects" :key="project.id" class="project-card" :class="{ 'featured-project': selectedProject?.id === project.id }" @click="selectedProject = project; loadTasks(project.id); loadProjectFiles(project.id); loadReviews(project.id)"><div class="project-icon"><FolderKanban :size="19" /></div><div class="project-info"><div class="project-title"><strong>{{ project.name }}</strong><span class="project-badge">{{ project.status === 'DONE' ? '已完成' : '进行中' }}</span></div><p>{{ project.description || project.techStack }}</p><div class="progress-line"><span :style="{ width: `${project.progress}%` }"></span></div><div class="project-meta"><span>{{ project.progress }}% 完成</span><span>{{ project.taskCount }} 个任务</span></div></div><ChevronRight :size="17" /></button></div>
          <div v-if="selectedProject" class="project-detail-card"><div class="page-view-head"><div><span class="panel-kicker">PROJECT DETAIL</span><h2>{{ selectedProject.name }}</h2><p>{{ selectedProject.description || '暂无项目描述' }}</p></div><div class="project-actions"><button class="row-action" @click="editProject(selectedProject)">编辑</button><button class="row-action danger" @click="removeProject(selectedProject)">删除</button></div></div><div class="detail-progress"><span :style="{ width: `${selectedProject.progress}%` }"></span></div><div class="project-detail-stats"><span>进度 <strong>{{ selectedProject.progress }}%</strong></span><span>任务 <strong>{{ projectTasks.length }}</strong></span><span>待处理评审 <strong>{{ openReviewCount }}</strong></span></div></div>
        </section>
        <section v-if="activeNav === 'AI 生成'" class="page-view panel ai-page-view"><div class="page-view-head"><div><span class="panel-kicker">AI GENERATOR</span><h2>AI 生成</h2><p>描述需求，生成代码并保存到当前项目。</p></div><button class="model-chip" @click="openModelCenter">模型中心</button></div><textarea v-model="prompt" class="prompt-input large-prompt" placeholder="例如：生成一个带流星动画的登录页..." @keydown.enter.exact.prevent="generateCode"></textarea><button class="generate-button" :disabled="isGenerating" @click="generateCode"><Sparkles :size="16" />{{ isGenerating ? '正在生成...' : '生成代码' }}</button><div class="ai-result"><div class="result-head"><strong>当前项目：{{ selectedProject?.name || '未选择项目' }}</strong><span>{{ selectedProject ? '生成内容会自动保存' : '请先在我的项目中选择项目' }}</span></div><pre>{{ files['index.html'] }}</pre></div></section>
        <section v-if="activeNav === '专注空间'" class="page-view panel focus-page-view"><div class="page-view-head"><div><span class="panel-kicker">FOCUS SPACE</span><h2>专注空间</h2><p>为当前项目记录专注时长。</p></div><TimerReset :size="21" /></div><div class="focus-clock">{{ timerLabel }}</div><p class="focus-caption">{{ activeSession ? '正在记录当前项目专注时长' : selectedProject ? `当前项目：${selectedProject.name}` : '请先选择项目' }}</p><button class="run-button focus-button" @click="toggleTimer"><TimerReset :size="15" />{{ activeSession ? '结束计时' : '开始计时' }}</button><div class="focus-stats"><div><strong>{{ (stats.focusSeconds / 3600).toFixed(1) }}</strong><span>累计专注小时</span></div><div><strong>{{ checkin.streak }}</strong><span>连续签到天数</span></div><div><strong>{{ stats.completedTaskCount }}</strong><span>已完成任务</span></div></div></section>
        <section v-if="activeNav === '知识库'" class="page-view panel knowledge-page-view"><div class="page-view-head"><div><span class="panel-kicker">KNOWLEDGE BASE</span><h2>知识库</h2><p>保存常用开发资料和项目规范。</p></div><button class="new-project" @click="showComingSoon('知识库条目')"><Plus :size="16" />新建条目</button></div><div class="project-toolbar"><div class="project-search"><Search :size="14" /><input v-model="knowledgeQuery" placeholder="搜索标题、内容或标签" /></div></div><div v-if="filteredKnowledge.length === 0" class="task-empty">还没有知识库条目，先创建一条开发笔记。</div><div class="knowledge-grid"><article v-for="entry in filteredKnowledge" :key="entry.id"><FileCode2 :size="18" /><strong>{{ entry.title }}</strong><span>{{ entry.content || '暂无内容' }}</span><small v-if="entry.tags"># {{ entry.tags }}</small><div class="knowledge-actions"><button class="row-action" @click="editKnowledge(entry)">编辑</button><button class="row-action danger" @click="removeKnowledge(entry)">删除</button></div></article></div></section>

        <section v-if="activeNav === '总览'" class="workbench-grid">
          <article class="panel project-panel">
            <div class="panel-heading"><div><span class="panel-kicker">PROJECT SPACE</span><h2>正在进行的项目</h2></div><button class="text-button" @click="createProject"><Plus :size="15" /> 新建</button></div>
            <div v-if="projects.length === 0" class="empty-model">还没有项目，先创建一个项目。</div>
          <div class="project-toolbar"><div class="project-search"><Search :size="14" /><input v-model="projectQuery" placeholder="搜索项目" /></div><select v-model="projectStatusFilter" aria-label="项目状态"><option value="ALL">全部状态</option><option value="IN_PROGRESS">进行中</option><option value="DONE">已完成</option></select></div>
          <div v-if="filteredProjects.length === 0" class="task-empty">没有匹配的项目。</div>
          <button v-for="project in filteredProjects" :key="project.id" class="project-card" :class="{ 'featured-project': selectedProject?.id === project.id }" @click="selectedProject = project; loadTasks(project.id); loadProjectFiles(project.id); loadReviews(project.id)"><div class="project-icon" :class="{ muted: selectedProject?.id !== project.id }"><Rocket v-if="selectedProject?.id === project.id" :size="19" /><FolderKanban v-else :size="19" /></div><div class="project-info"><div class="project-title"><strong>{{ project.name }}</strong><span class="project-badge">{{ project.status === 'DONE' ? '已完成' : '进行中' }}</span></div><p>{{ project.description || project.techStack }}</p><div class="progress-line"><span :style="{ width: `${project.progress}%` }"></span></div><div class="project-meta"><span>{{ project.progress }}% 完成</span><span>{{ project.taskCount }} 个任务</span></div></div><ChevronRight :size="17" /></button>
            <button class="add-project" @click="createProject"><Plus :size="15" /> 创建一个新项目</button>
            <div v-if="selectedProject" class="project-actions"><button class="row-action" @click="editProject(selectedProject)">编辑项目</button><button class="row-action danger" @click="removeProject(selectedProject)">删除项目</button></div>
            <div v-if="selectedProject" class="task-list"><div class="task-list-head"><strong>{{ selectedProject.name }} · 任务</strong><div class="task-list-tools"><select :value="selectedProject.status" @change="updateProjectStatus($event.target.value)" aria-label="项目状态"><option value="IN_PROGRESS">进行中</option><option value="DONE">已完成</option></select><button class="text-button" @click="addTask"><Plus :size="14" /> 添加任务</button></div></div><div class="project-detail-meta"><span>{{ selectedProject.description || '暂无项目描述' }}</span><strong>{{ openReviewCount }} 个待处理问题</strong></div><div v-if="projectTasks.length === 0" class="task-empty">还没有任务。</div><div v-for="task in projectTasks" :key="task.id" class="task-row" @click="cycleTaskStatus(task)"><span class="task-check" :class="{ done: task.status === 'DONE', active: task.status === 'IN_PROGRESS' }"><Check v-if="task.status === 'DONE'" :size="12" /></span><span @dblclick.stop="editTask(task)">{{ task.title }}</span><small>{{ task.status === 'DONE' ? '已完成' : task.status === 'IN_PROGRESS' ? '进行中' : '待办' }}</small><button class="task-action" @click.stop="editTask(task)">编辑</button><button class="task-action danger" @click.stop="removeTask(task)">删除</button></div></div>
          </article>

          <article class="panel activity-panel"><div class="panel-heading"><div><span class="panel-kicker">FOCUS TIMER</span><h2>专注空间</h2></div><TimerReset :size="18" /></div><div class="focus-clock">{{ timerLabel }}</div><p class="focus-caption">{{ activeSession ? '正在记录当前项目专注时长' : '选择项目后开始一次专注计时' }}</p><button class="run-button focus-button" @click="toggleTimer"><TimerReset :size="15" />{{ activeSession ? '结束计时' : '开始计时' }}</button><div class="checkin-strip"><div><strong>连续签到 {{ checkin.streak }} 天</strong><span>累计签到 {{ checkin.total }} 天</span></div><button class="text-button" :disabled="checkin.checkedInToday" @click="doCheckin">{{ checkin.checkedInToday ? '今日已签' : '今日签到' }}</button></div><div class="activity-highlight"><div class="highlight-icon"><Clock3 :size="17" /></div><div><strong>项目数据已保存到本地数据库</strong><span>完成任务后项目进度会自动更新</span></div></div></article>
        </section>

        <section v-if="activeNav === '总览'" class="studio-layout">
          <article class="panel studio-panel"><div class="panel-heading studio-heading"><div><span class="panel-kicker">CODE STUDIO</span><h2>代码工作台</h2></div><div class="studio-actions"><span class="saved-label"><span></span>已自动保存</span><button class="run-button" @click="runPreview"><Play :size="14" />运行预览</button></div></div><div class="studio-body"><div class="file-tree"><div class="tree-label">项目文件</div><button v-for="name in Object.keys(files)" :key="name" class="file-item" :class="{ selected: activeFile === name }" @click="activeFile = name"><FileCode2 :size="15" /><span>{{ name }}</span></button><button class="tree-add" @click="showComingSoon('新建文件')"><Plus :size="14" />新建文件</button></div><div class="editor-side"><div class="editor-tabs"><button v-for="name in Object.keys(files)" :key="name" :class="{ selected: activeFile === name }" @click="activeFile = name">{{ name }}</button></div><textarea :value="files[activeFile]" spellcheck="false" @input="updateFile($event.target.value)"></textarea><div class="editor-footer"><span>Ln 1, Col 1</span><span>UTF-8</span><span>{{ activeFile.endsWith('.js') ? 'JavaScript' : activeFile.endsWith('.css') ? 'CSS' : 'HTML' }}</span></div></div><div class="preview-side"><div class="preview-toolbar"><span><span class="preview-dot"></span>实时预览</span><span class="device-label">桌面 · 100%</span></div><iframe title="代码实时预览" :srcdoc="previewDoc"></iframe></div></div></article>
          <aside class="side-column"><article class="panel ai-panel"><div class="panel-heading"><div><span class="panel-kicker">AI COPILOT</span><h2>让 AI 帮你写</h2></div><button class="model-chip" @click="openModelCenter">模型中心</button></div><p class="ai-hint">描述你想实现的页面或功能，AI 会调用当前选中的模型。</p><textarea v-model="prompt" class="prompt-input" placeholder="例如：生成一个带流星动画的登录页..." @keydown.enter.exact.prevent="generateCode"></textarea><button class="generate-button" :disabled="isGenerating" @click="generateCode"><Sparkles :size="16" />{{ isGenerating ? '正在生成...' : '生成代码' }}<span>⌘ ↵</span></button><div class="ai-suggestion"><Sparkles :size="14" /><span>支持 Ollama、DeepSeek、通义和中转站</span></div></article><article v-if="activeNav !== '代码评审'" class="panel issues-panel"><div class="panel-heading"><div><span class="panel-kicker">REVIEW QUEUE</span><h2>待处理问题</h2></div><span class="issue-total">{{ openReviewCount }}</span></div><div v-if="reviewIssues.length === 0" class="task-empty">选择项目后加载评审问题。</div><div v-for="issue in reviewIssues" :key="issue.id" class="issue-item" :class="{ resolved: issue.status === 'RESOLVED' }"><span class="issue-dot" :class="issue.level === 'HIGH' ? 'danger' : issue.level === 'STYLE' ? 'warn' : 'info'"></span><div><strong>{{ issue.title }}</strong><small>{{ issue.filePath }}</small></div><button v-if="issue.status === 'OPEN'" class="task-action" @click="resolveReview(issue)">处理</button><span v-else class="test-label">已处理</span></div><button class="review-link" @click="selectNav('代码评审')">进入评审中心 <ChevronRight :size="14" /></button></article></aside>
        </section>
      </section>
    </main>
    <div v-if="showModelCenter" class="modal-backdrop" @click.self="showModelCenter = false"><section class="model-modal"><div class="modal-head"><div><span class="panel-kicker">MODEL SWITCHBOARD</span><h2>自定义模型中心</h2><p>兼容 OpenAI API，可接本地模型、云端模型或第三方中转站。</p></div><button class="icon-button" title="关闭" @click="showModelCenter = false">×</button></div><div class="model-list"><div v-for="config in modelConfigs" :key="config.id" class="model-row" :class="{ active: config.active }"><div class="model-status"><span></span></div><div class="model-main"><strong>{{ config.name }}</strong><small>{{ config.model }} · {{ config.baseUrl }}</small></div><span v-if="config.active" class="active-label">当前使用</span><span v-if="modelTest[config.id]" class="test-label">{{ modelTest[config.id] }}</span><button class="row-action" @click="editModel(config)">编辑</button><button class="row-action" @click="testModel(config.id)">测试</button><button v-if="!config.active" class="row-action primary" @click="activateModel(config.id)">使用</button><button class="row-action danger" @click="removeModel(config.id)">删除</button></div><div v-if="modelConfigs.length === 0" class="empty-model">还没有模型配置，请在下方添加。</div></div><div class="model-form"><div class="form-title">添加或编辑模型</div><div class="form-grid"><label>配置名称<input v-model="modelForm.name" placeholder="例如：我的 DeepSeek 中转" /></label><label>模型名称<input v-model="modelForm.model" placeholder="例如：deepseek-chat" /></label><label class="wide">接口地址<input v-model="modelForm.baseUrl" placeholder="例如：http://127.0.0.1:11434/v1" /></label><label class="wide">API Key <span class="optional">可选，本地 Ollama 不需要</span><input v-model="modelForm.apiKey" type="password" placeholder="留空以保留当前 Key" /></label></div><div class="form-actions"><button class="row-action" @click="resetModelForm">清空</button><button class="generate-button save-model" :disabled="modelBusy" @click="saveModel">{{ modelBusy ? '保存中...' : '保存配置' }}</button></div></div></section></div>
    <transition name="toast"><div v-if="toast" class="toast-message"><Sparkles :size="16" />{{ toast }}</div></transition>
  </div>
</template>
