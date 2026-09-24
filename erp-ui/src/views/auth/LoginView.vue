<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi, type LoginFailData } from '@/api/auth'
import { BizError } from '@/api/http'
import { systemCommonApi } from '@/api/system'
import { useUserStore } from '@/stores/user'

/** 登录页（01-13 登录与个人中心 3.1） */
const REMEMBER = 'erp.rememberUsername'
const store = useUserStore()
const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const loading = ref(false)
const error = ref('')
const capsLock = ref(false)
const systemName = ref('ERP 系统')
const remember = ref(false)
const captcha = reactive({ required: false, id: '', image: '' })
const form = reactive({ username: '', password: '', captchaCode: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ validator: (_r, v, cb) => (captcha.required && !v ? cb(new Error('请输入验证码')) : cb()), trigger: 'blur' }]
}

onMounted(async () => {
  try {
    const saved = localStorage.getItem(REMEMBER)
    if (saved) {
      form.username = saved
      remember.value = true
      checkCaptcha()
    }
  } catch {
    /* ignore */
  }
  const p = await systemCommonApi.publicParams().catch(() => undefined)
  if (p?.systemName) {
    systemName.value = p.systemName
    document.title = p.systemName
  }
})

async function checkCaptcha() {
  if (!form.username) return
  const required = await authApi.captchaRequired(form.username).catch(() => false)
  if (required && !captcha.required) {
    captcha.required = true
    refreshCaptcha()
  }
}

async function refreshCaptcha() {
  const c = await authApi.captcha(form.username)
  captcha.id = c.captchaId
  captcha.image = c.image
  form.captchaCode = ''
}

function onKey(e: Event) {
  capsLock.value = (e as KeyboardEvent).getModifierState?.('CapsLock') ?? false
}

async function submit() {
  error.value = ''
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    const resp = await store.login({
      username: form.username.trim(),
      password: form.password,
      captchaId: captcha.required ? captcha.id : undefined,
      captchaCode: captcha.required ? form.captchaCode : undefined
    })
    try {
      if (remember.value) localStorage.setItem(REMEMBER, form.username.trim())
      else localStorage.removeItem(REMEMBER)
    } catch {
      /* ignore */
    }
    if (resp.mustChangePassword || resp.passwordExpired) {
      await router.replace('/change-password')
      return
    }
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (e) {
    error.value = (e as Error).message
    const data = e instanceof BizError ? (e.data as LoginFailData | undefined) : undefined
    if (data?.captchaRequired) {
      captcha.required = true
      await refreshCaptcha().catch(() => undefined)
    } else if (captcha.required) {
      await refreshCaptcha().catch(() => undefined)
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <el-card class="box">
      <h2>{{ systemName }}</h2>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" autocomplete="username" @blur="checkCaptcha" />
        </el-form-item>
        <el-form-item prop="password" :class="{ caps: capsLock }">
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password autocomplete="current-password" @keyup="onKey" @keydown="onKey" />
          <div v-if="capsLock" class="caps-tip">大写锁定已打开</div>
        </el-form-item>
        <el-form-item v-if="captcha.required" prop="captchaCode">
          <div class="captcha">
            <el-input v-model="form.captchaCode" placeholder="验证码" prefix-icon="Key" maxlength="4" />
            <img v-if="captcha.image" :src="captcha.image" alt="验证码" title="看不清？点击刷新" @click="refreshCaptcha" />
          </div>
        </el-form-item>
        <el-checkbox v-model="remember" class="remember">记住用户名</el-checkbox>
        <div v-if="error" class="error">{{ error }}</div>
        <el-button type="primary" :loading="loading" class="full" @click="submit">登 录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--el-bg-color-page); padding: 16px; }
.box { width: 100%; max-width: 380px; }
h2 { text-align: center; margin: 8px 0 24px; }
.full { width: 100%; }
.remember { margin-bottom: 12px; }
.error { color: var(--el-color-danger); font-size: 13px; margin-bottom: 8px; }
.caps-tip { font-size: 12px; color: var(--el-color-warning); line-height: 1.4; margin-top: 2px; }
.captcha { display: flex; gap: 8px; width: 100%; }
.captcha img { height: 40px; cursor: pointer; border: 1px solid var(--el-border-color); border-radius: 4px; }
</style>
