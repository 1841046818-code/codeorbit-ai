<script setup>
import { ref } from 'vue'
import { ArrowRight, Bot, CircleAlert, Sparkles } from 'lucide-vue-next'
import { useRouter, RouterLink } from 'vue-router'
import { setSession } from '../auth'

const API_BASE = 'http://127.0.0.1:8080'
const router = useRouter()
const name = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const busy = ref(false)
const error = ref('')

async function submit() {
  if (!name.value.trim() || !email.value.trim() || !password.value) { error.value = '请完整填写昵称、邮箱和密码。'; return }
  if (password.value.length < 6) { error.value = '密码至少需要 6 位。'; return }
  if (password.value !== confirmPassword.value) { error.value = '两次输入的密码不一致。'; return }
  busy.value = true; error.value = ''
  try {
    const response = await fetch(`${API_BASE}/api/auth/register`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: name.value, email: email.value, password: password.value }) })
    const data = await response.json()
    if (!response.ok || data.message) throw new Error(data.message || '注册失败')
    setSession(data); router.push('/workspace')
  } catch (cause) { error.value = cause.message || '注册失败，请检查后端服务。' } finally { busy.value = false }
}
</script>

<template>
  <main class="auth-shell"><div class="auth-stars auth-stars-one" /><div class="auth-stars auth-stars-two" /><div class="auth-meteor" /><section class="auth-panel"><div class="auth-brand"><div class="auth-orbit"><span /></div><div><strong>星码空间</strong><small>CODEORBIT AI</small></div></div><div class="auth-copy"><span class="auth-kicker">START YOUR ORBIT</span><h1>把想法，<em>放进空间。</em></h1><p>创建账号，建立你的个人开发工作区。</p></div><form class="auth-form" @submit.prevent="submit"><label>你的昵称<input v-model="name" autocomplete="name" placeholder="例如：林同学" /></label><label>邮箱地址<input v-model="email" type="email" autocomplete="email" placeholder="name@example.com" /></label><label>设置密码<input v-model="password" type="password" autocomplete="new-password" placeholder="至少 6 位字符" /></label><label>确认密码<input v-model="confirmPassword" type="password" autocomplete="new-password" placeholder="再次输入密码" /></label><p v-if="error" class="auth-error"><CircleAlert :size="15" />{{ error }}</p><button class="auth-submit" :disabled="busy"><Bot :size="17" />{{ busy ? '正在创建...' : '创建开发空间' }}<ArrowRight :size="16" /></button></form><p class="auth-switch">已经有账号？<RouterLink to="/login">返回登录 <Sparkles :size="13" /></RouterLink></p></section></main>
</template>
