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

const metricCards = [
  { label: '代码质量', value: '87', suffix: '/100', trend: '+8.4%', icon: Gauge, tone: 'mint' },
  { label: '本周提交', value: '24', suffix: ' 次', trend: '+12%', icon: GitBranch, tone: 'blue' },
  { label: '有效时长', value: '18.6', suffix: ' 小时', trend: '+2.1h', icon: Clock3, tone: 'gold' },
  { label: '已修复问题', value: '36', suffix: ' 个', trend: '连续 7 天', icon: Check, tone: 'pink' },
]

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
    toast.value = `已使用 ${result.model || '当前模型'} 生成代码。`
  } catch (error) {
    toast.value = '模型暂时不可用，已切换到演示生成模式。'
    window.setTimeout(() => {
    files.value['index.html'] = `<main class="hero hero-generated">\n  <div class="orbit-mark">✦</div>\n  <span class="eyebrow">AI GENERATED EXPERIENCE</span>\n  <h1>${prompt.value || '让每一次创作，都有即时回应。'}</h1>\n  <p>这是由星码空间生成的交互式页面，你可以继续编辑并在右侧实时查看效果。</p>\n  <button id="launch">进入工作台</button>\n</main>`
    activeFile.value = 'index.html'
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
function openModelCenter() { showModelCenter.value = true; loadModelConfigs() }
function editModel(config) { modelForm.value = { id: config.id, name: config.name, baseUrl: config.baseUrl, apiKey: '', model: config.model, active: config.active } }
function resetModelForm() { modelForm.value = { id: '', name: '', baseUrl: 'http://127.0.0.1:11434/v1', apiKey: '', model: 'qwen2.5-coder:7b', active: modelConfigs.value.length === 0 } }
async function saveModel() { if (!modelForm.value.name || !modelForm.value.baseUrl || !modelForm.value.model) { toast.value = '请填写模型名称、接口地址和模型名。'; return } modelBusy.value = true; try { const response = await fetch(`${API_BASE}/api/ai/config`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(modelForm.value) }); if (!response.ok) throw new Error(); const data = await response.json(); modelConfigs.value = data.configs || []; resetModelForm(); toast.value = '模型配置已保存。' } catch { toast.value = '保存失败，请先启动 Java 后端。' } finally { modelBusy.value = false } }
async function activateModel(id) { try { const response = await fetch(`${API_BASE}/api/ai/config/${id}/activate`, { method: 'POST' }); if (!response.ok) throw new Error(); await loadModelConfigs(); toast.value = '已切换当前模型。' } catch { toast.value = '切换失败，请检查后端。' } }
async function testModel(id) { modelTest.value[id] = '测试中'; try { const response = await fetch(`${API_BASE}/api/ai/config/${id}/test`, { method: 'POST' }); const data = await response.json(); modelTest.value[id] = data.ok ? '连接成功' : '连接失败' } catch { modelTest.value[id] = '连接失败' } }
async function removeModel(id) { try { const response = await fetch(`${API_BASE}/api/ai/config/${id}`, { method: 'DELETE' }); if (!response.ok) throw new Error(); await loadModelConfigs(); toast.value = '模型配置已删除。' } catch { toast.value = '至少保留一个模型配置。' } }

function selectNav(label) {
  activeNav.value = label
  if (label !== '总览') toast.value = `${label} 模块将在下一阶段接入真实数据。`
}

onMounted(() => {
  ticker = window.setInterval(() => (now.value = new Date()), 1000)
  loadModelConfigs()
})

onUnmounted(() => window.clearInterval(ticker))
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
      <div class="workspace-switcher">
        <div class="workspace-icon"><Code2 :size="17" /></div>
        <div class="workspace-copy"><span>个人开发空间</span><small>个人工作区</small></div>
        <ChevronRight :size="16" />
      </div>
      <div class="nav-group-label">工作台</div>
      <nav>
        <button v-for="item in navItems" :key="item.label" class="nav-item" :class="{ active: activeNav === item.label }" @click="selectNav(item.label)">
          <component :is="item.icon" :size="17" />
          <span>{{ item.label }}</span>
          <span v-if="item.label === '代码评审'" class="nav-count">3</span>
        </button>
      </nav>
      <div class="sidebar-bottom">
        <button class="nav-item" @click="selectNav('知识库')"><FileCode2 :size="17" /><span>知识库</span></button>
        <button class="nav-item" @click="openModelCenter"><Settings2 :size="17" /><span>模型中心</span></button>
        <div class="profile-chip"><div class="avatar">林</div><div><strong>林同学</strong><small>成长等级 Lv.08</small></div><CircleHelp :size="15" /></div>
      </div>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div class="breadcrumb"><span>工作台</span><ChevronRight :size="14" /><strong>{{ activeNav }}</strong></div>
        <div class="top-actions"><div class="live-status"><span class="status-dot"></span>系统运行正常</div><button class="icon-button" title="搜索"><Search :size="17" /></button><button class="icon-button" title="帮助"><CircleHelp :size="17" /></button><button class="new-project"><Plus :size="16" />新建项目</button></div>
      </header>

      <section class="content-wrap">
        <section class="hero-row">
          <div>
            <p class="eyebrow-text">{{ formattedTime }} · 周四 · 晚间工作区</p>
            <h1>你好，林同学。<br /><em>让代码开始生长。</em></h1>
            <p class="hero-subtitle">把需求交给 AI，把判断权留给你。今天继续完成「星夜登录页」吧。</p>
          </div>
          <div class="ai-orb-card"><div class="orb-glow"></div><div class="orb-core"><Bot :size="31" /></div><div><span>AI 导师在线</span><strong>准备好协助你</strong></div><span class="orb-pulse"></span></div>
        </section>

        <section class="metrics-grid">
          <article v-for="card in metricCards" :key="card.label" class="metric-card">
            <div class="metric-top"><span>{{ card.label }}</span><span class="metric-icon" :class="card.tone"><component :is="card.icon" :size="16" /></span></div>
            <div class="metric-value">{{ card.value }}<small>{{ card.suffix }}</small></div>
            <div class="metric-trend"><span>{{ card.trend }}</span><span>较上周</span></div>
          </article>
        </section>

        <section class="workbench-grid">
          <article class="panel project-panel">
            <div class="panel-heading"><div><span class="panel-kicker">PROJECT SPACE</span><h2>正在进行的项目</h2></div><button class="text-button">查看全部 <ChevronRight :size="15" /></button></div>
            <div class="project-card featured-project"><div class="project-icon"><Rocket :size="19" /></div><div class="project-info"><div class="project-title"><strong>星夜登录页</strong><span class="project-badge">进行中</span></div><p>Vue 3 · HTML/CSS/JavaScript</p><div class="progress-line"><span style="width: 68%"></span></div><div class="project-meta"><span>68% 完成</span><span>最后编辑 12 分钟前</span></div></div><ChevronRight :size="17" /></div>
            <div class="project-card"><div class="project-icon muted"><FolderKanban :size="19" /></div><div class="project-info"><div class="project-title"><strong>个人作品集重构</strong><span class="project-badge gray">规划中</span></div><p>Vue 3 · Tailwind CSS</p><div class="progress-line"><span style="width: 22%"></span></div><div class="project-meta"><span>22% 完成</span><span>3 个待办任务</span></div></div><ChevronRight :size="17" /></div>
            <button class="add-project"><Plus :size="15" /> 创建一个新项目</button>
          </article>

          <article class="panel activity-panel"><div class="panel-heading"><div><span class="panel-kicker">ACTIVITY</span><h2>成长轨迹</h2></div><button class="icon-button small" title="切换视图"><Activity :size="16" /></button></div><div class="heatmap"><div v-for="n in 35" :key="n" class="heat-cell" :class="`level-${(n * 7 + 3) % 5}`"></div></div><div class="heatmap-footer"><span>少</span><i class="legend level-1"></i><i class="legend level-2"></i><i class="legend level-3"></i><i class="legend level-4"></i><span>多</span><strong>连续活跃 7 天</strong></div><div class="activity-highlight"><div class="highlight-icon"><Clock3 :size="17" /></div><div><strong>本周专注了 18.6 小时</strong><span>比上周多出 2 小时 6 分钟</span></div></div></article>
        </section>

        <section class="studio-layout">
          <article class="panel studio-panel"><div class="panel-heading studio-heading"><div><span class="panel-kicker">CODE STUDIO</span><h2>代码工作台</h2></div><div class="studio-actions"><span class="saved-label"><span></span>已自动保存</span><button class="run-button"><Play :size="14" />运行预览</button></div></div><div class="studio-body"><div class="file-tree"><div class="tree-label">项目文件</div><button v-for="name in Object.keys(files)" :key="name" class="file-item" :class="{ selected: activeFile === name }" @click="activeFile = name"><FileCode2 :size="15" /><span>{{ name }}</span></button><div class="tree-add"><Plus :size="14" />新建文件</div></div><div class="editor-side"><div class="editor-tabs"><button v-for="name in Object.keys(files)" :key="name" :class="{ selected: activeFile === name }" @click="activeFile = name">{{ name }}</button></div><textarea :value="files[activeFile]" spellcheck="false" @input="updateFile($event.target.value)"></textarea><div class="editor-footer"><span>Ln 1, Col 1</span><span>UTF-8</span><span>JavaScript</span></div></div><div class="preview-side"><div class="preview-toolbar"><span><span class="preview-dot"></span>实时预览</span><span class="device-label">桌面 · 100%</span></div><iframe title="代码实时预览" :srcdoc="previewDoc"></iframe></div></div></article>
          <aside class="side-column"><article class="panel ai-panel"><div class="panel-heading"><div><span class="panel-kicker">AI COPILOT</span><h2>让 AI 帮你写</h2></div><button class="model-chip" @click="openModelCenter">模型中心</button></div><p class="ai-hint">描述你想实现的页面或功能，AI 会调用当前选中的模型。</p><textarea v-model="prompt" class="prompt-input" placeholder="例如：生成一个带流星动画的登录页..." @keydown.enter.exact.prevent="generateCode"></textarea><button class="generate-button" :disabled="isGenerating" @click="generateCode"><Sparkles :size="16" />{{ isGenerating ? '正在生成...' : '生成代码' }}<span>⌘ ↵</span></button><div class="ai-suggestion"><Sparkles :size="14" /><span>支持 Ollama、DeepSeek、通义和中转站</span></div></article><article class="panel issues-panel"><div class="panel-heading"><div><span class="panel-kicker">REVIEW QUEUE</span><h2>待处理问题</h2></div><span class="issue-total">3</span></div><div v-for="issue in issues" :key="issue.title" class="issue-item"><span class="issue-dot" :class="issue.tone"></span><div><strong>{{ issue.title }}</strong><small>{{ issue.file }}</small></div><ChevronRight :size="14" /></div><button class="review-link" @click="selectNav('代码评审')">进入评审中心 <ChevronRight :size="14" /></button></article></aside>
        </section>
      </section>
    </main>
    <div v-if="showModelCenter" class="modal-backdrop" @click.self="showModelCenter = false"><section class="model-modal"><div class="modal-head"><div><span class="panel-kicker">MODEL SWITCHBOARD</span><h2>自定义模型中心</h2><p>兼容 OpenAI API，可接本地模型、云端模型或第三方中转站。</p></div><button class="icon-button" title="关闭" @click="showModelCenter = false">×</button></div><div class="model-list"><div v-for="config in modelConfigs" :key="config.id" class="model-row" :class="{ active: config.active }"><div class="model-status"><span></span></div><div class="model-main"><strong>{{ config.name }}</strong><small>{{ config.model }} · {{ config.baseUrl }}</small></div><span v-if="config.active" class="active-label">当前使用</span><span v-if="modelTest[config.id]" class="test-label">{{ modelTest[config.id] }}</span><button class="row-action" @click="testModel(config.id)">测试</button><button v-if="!config.active" class="row-action primary" @click="activateModel(config.id)">使用</button><button class="row-action danger" @click="removeModel(config.id)">删除</button></div><div v-if="modelConfigs.length === 0" class="empty-model">还没有模型配置，请在下方添加。</div></div><div class="model-form"><div class="form-title">添加或编辑模型</div><div class="form-grid"><label>配置名称<input v-model="modelForm.name" placeholder="例如：我的 DeepSeek 中转" /></label><label>模型名称<input v-model="modelForm.model" placeholder="例如：deepseek-chat" /></label><label class="wide">接口地址<input v-model="modelForm.baseUrl" placeholder="例如：http://127.0.0.1:11434/v1" /></label><label class="wide">API Key <span class="optional">可选，本地 Ollama 不需要</span><input v-model="modelForm.apiKey" type="password" placeholder="sk-..." /></label></div><div class="form-actions"><button class="row-action" @click="resetModelForm">清空</button><button class="generate-button save-model" :disabled="modelBusy" @click="saveModel">{{ modelBusy ? '保存中...' : '保存配置' }}</button></div></div></section></div>
    <transition name="toast"><div v-if="toast" class="toast-message"><Sparkles :size="16" />{{ toast }}</div></transition>
  </div>
</template>
