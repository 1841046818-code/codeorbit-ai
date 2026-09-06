<script setup>
import { ref } from 'vue'
import { ArrowRight, Bot, CircleAlert, Sparkles } from 'lucide-vue-next'
import { useRouter, useRoute, RouterLink } from 'vue-router'
import { setSession } from '../auth'

const API_BASE = 'http://127.0.0.1:8080'
const router = useRouter()
const route = useRoute()
const email = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')

async function submit() {
  if (!email.value.trim() || !password.value) {
    error.value = '请输入邮箱和密码。'
    return
  }
  busy.value = true
  error.value = ''
  try {
    const response = await fetch(`${API_BASE}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: email.value, password: password.value }) })
    const data = await response.json()
    if (!response.ok || data.message) throw new Error(data.message || '登录失败')
    setSession(data)
    router.push(route.query.redirect || '/workspace')
  } catch (cause) {
    error.value = cause.message || '登录失败，请检查后端服务。'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="auth-shell">
    <div class="auth-stars auth-stars-one" /><div class="auth-stars auth-stars-two" /><div class="auth-meteor" />
    <section class="auth-panel">
      <div class="auth-brand"><div class="auth-orbit"><span /></div><div><strong>星码空间</strong><small>CODEORBIT AI</small></div></div>
      <div class="auth-copy"><span class="auth-kicker">WELCOME BACK</span><h1>让代码，<em>开始生长。</em></h1><p>登录你的个人开发空间，继续构建下一个想法。</p></div>
      <form class="auth-form" @submit.prevent="submit"><label>邮箱地址<input v-model="email" type="email" autocomplete="email" placeholder="name@example.com" /></label><label>密码<input v-model="password" type="password" autocomplete="current-password" placeholder="输入密码" /></label><p v-if="error" class="auth-error"><CircleAlert :size="15" />{{ error }}</p><button class="auth-submit" :disabled="busy"><Bot :size="17" />{{ busy ? '正在登录...' : '登录工作台' }}<ArrowRight :size="16" /></button></form>
      <p class="auth-switch">还没有账号？<RouterLink to="/register">创建一个账号 <Sparkles :size="13" /></RouterLink></p>
    </section>
  </main>
</template>
