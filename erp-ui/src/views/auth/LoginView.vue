<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi, type LoginFailData } from '@/api/auth'
import { BizError } from '@/api/http'
import { useUserStore } from '@/stores/user'
import ParticleField from './ParticleField.vue'

/**
 * 登录页（01-13 登录与个人中心 3.1）：左侧品牌区（维界ERP、标语、粒子联动背景），右侧登录卡片。
 * 登录页只显示产品品牌「维界ERP」，不显示公司名称 / 公司 Logo。
 */
const PRODUCT = '维界ERP'
const FEATURES = [
  { icon: 'Opportunity', title: '研发工程', desc: '物料编码 · BOM · ECN' },
  { icon: 'Van', title: '供应链协同', desc: '采购 · 仓储 · 发运' },
  { icon: 'SetUp', title: '智能生产', desc: 'PMC · 工单 · 品质追溯' },
  { icon: 'DataAnalysis', title: '经营洞察', desc: '财务 · BI · 实时汇率' }
]
const FLOW = ['研发', '采购', '生产', '品质', '交付', '财务']
const year = new Date().getFullYear()
const REMEMBER = 'erp.rememberUsername'
const store = useUserStore()
const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const loading = ref(false)
const error = ref('')
const capsLock = ref(false)
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
  document.title = `${PRODUCT} · 登录`
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
    <section class="hero">
      <div class="hero__glow" />
      <div class="hero__grid" />
      <ParticleField />
      <div class="hero__inner">
        <div class="brand">
          <svg class="brand__mark" viewBox="0 0 40 40" fill="none" aria-hidden="true">
            <path d="M20 3 35 11.5v17L20 37 5 28.5v-17L20 3Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
            <path d="M20 3v17m0 0 15-8.5M20 20 5 11.5M20 20v17" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round" opacity=".55" />
            <circle cx="20" cy="20" r="3.2" fill="currentColor" />
          </svg>
          <div>
            <div class="brand__name">维界<span class="brand__erp">ERP</span></div>
            <div class="brand__en">WEIJIE ENTERPRISE RESOURCE PLANNING</div>
          </div>
        </div>

        <div class="hero__copy">
          <div class="eyebrow"><span class="eyebrow__dot" />智能制造 · 企业资源计划</div>
          <h1 class="slogan">数联万维<br><span class="slogan__accent">智控无界</span></h1>
          <p class="slogan__en">Beyond Dimensions, Built for Manufacturing.</p>
          <p class="slogan__desc">研发、供应链、生产、品质、财务一体化协同，让每一份数据都有来处、每一个决策都有依据，驱动企业精益增长。</p>

          <div class="features">
            <div v-for="f in FEATURES" :key="f.title" class="feature">
              <el-icon class="feature__icon"><component :is="f.icon" /></el-icon>
              <div>
                <div class="feature__title">{{ f.title }}</div>
                <div class="feature__desc">{{ f.desc }}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="hero__foot">
          <div class="flow">
            <template v-for="(n, i) in FLOW" :key="n">
              <span class="flow__node">{{ n }}</span>
              <span v-if="i < FLOW.length - 1" class="flow__line" />
            </template>
          </div>
          <div class="copyright">© {{ year }} {{ PRODUCT }} · 全流程数字化管理平台</div>
        </div>
      </div>
    </section>

    <section class="side">
      <div class="box">
        <div class="box__brand">
          <svg class="box__mark" viewBox="0 0 40 40" fill="none" aria-hidden="true">
            <path d="M20 3 35 11.5v17L20 37 5 28.5v-17L20 3Z" stroke="currentColor" stroke-width="2.4" stroke-linejoin="round" />
            <circle cx="20" cy="20" r="4" fill="currentColor" />
          </svg>
          {{ PRODUCT }}
        </div>
        <h2 class="box__title">欢迎登录</h2>
        <p class="box__desc">使用管理员分配的账号登录{{ PRODUCT }}</p>
        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" autocomplete="username" @blur="checkCaptcha" />
          </el-form-item>
          <el-form-item prop="password" :class="{ caps: capsLock }">
            <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password autocomplete="current-password" @keyup="onKey" @keydown="onKey" />
            <div v-if="capsLock" class="caps-tip"><el-icon><Warning /></el-icon>大写锁定已打开</div>
          </el-form-item>
          <el-form-item v-if="captcha.required" prop="captchaCode">
            <div class="captcha">
              <el-input v-model="form.captchaCode" placeholder="验证码" prefix-icon="Key" maxlength="4" />
              <img v-if="captcha.image" :src="captcha.image" alt="验证码" title="看不清？点击刷新" @click="refreshCaptcha" />
            </div>
          </el-form-item>
          <el-checkbox v-model="remember" class="remember">记住用户名</el-checkbox>
          <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="error" />
          <el-button type="primary" :loading="loading" class="full" @click="submit">登 录</el-button>
        </el-form>
        <div class="box__foot">忘记密码请联系系统管理员重置</div>
      </div>
      <div class="side__copy">© {{ year }} {{ PRODUCT }}</div>
    </section>
  </div>
</template>

<style scoped>
.login { min-height: 100vh; display: flex; background: var(--erp-color-surface); }

/* ---------- 品牌区 ---------- */
.hero {
  position: relative; flex: 1 1 60%; min-width: 0; overflow: hidden; color: var(--erp-color-on-brand);
  background: radial-gradient(120% 90% at 15% 10%, var(--erp-color-brand-mid) 0%, var(--erp-color-brand-deep) 45%, var(--erp-color-brand-deeper) 100%);
}
.hero__glow {
  position: absolute; width: 720px; height: 720px; right: -220px; bottom: -260px; border-radius: 50%; pointer-events: none;
  background: radial-gradient(circle, var(--erp-color-brand-glow) 0%, transparent 65%);
}
.hero__grid {
  position: absolute; inset: 0; pointer-events: none; opacity: .5;
  background-image: linear-gradient(var(--erp-color-on-brand-fill) 1px, transparent 1px), linear-gradient(90deg, var(--erp-color-on-brand-fill) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(ellipse at 30% 40%, var(--erp-color-on-brand) 0%, transparent 75%);
  -webkit-mask-image: radial-gradient(ellipse at 30% 40%, var(--erp-color-on-brand) 0%, transparent 75%);
}
.hero__inner {
  position: relative; z-index: 1; height: 100%; min-height: 100vh; box-sizing: border-box; display: flex; flex-direction: column;
  padding: 48px 64px 40px; pointer-events: none;
}
.brand { display: flex; align-items: center; gap: 14px; }
.brand__mark { width: 40px; height: 40px; color: var(--erp-color-brand-accent); flex-shrink: 0; }
.brand__name { font-size: var(--erp-font-size-page-title); font-weight: var(--erp-font-weight-semibold); letter-spacing: 2px; line-height: 24px; }
.brand__erp { margin-left: 2px; color: var(--erp-color-brand-accent); letter-spacing: 1px; }
.brand__en { margin-top: 2px; font-size: var(--erp-font-size-caption); color: var(--erp-color-on-brand-tertiary); letter-spacing: 2px; transform: scale(.85); transform-origin: left; white-space: nowrap; }

.hero__copy { flex: 1; display: flex; flex-direction: column; justify-content: center; max-width: 620px; padding: 48px 0; }
.eyebrow {
  align-self: flex-start; display: inline-flex; align-items: center; gap: 8px; padding: 6px 14px; border-radius: 100vmax;
  border: 1px solid var(--erp-color-on-brand-border); background: var(--erp-color-on-brand-fill);
  font-size: var(--erp-font-size-caption); color: var(--erp-color-on-brand-secondary); letter-spacing: 1px;
}
.eyebrow__dot { width: 6px; height: 6px; border-radius: 50%; background: var(--erp-color-brand-accent); box-shadow: 0 0 0 4px var(--erp-color-brand-glow); }
.slogan { margin: 28px 0 0; font-size: var(--erp-font-size-display); font-weight: var(--erp-font-weight-semibold); line-height: 1.25; letter-spacing: 6px; }
.slogan__accent {
  background: linear-gradient(90deg, var(--erp-color-on-brand) 0%, var(--erp-color-brand-accent) 100%);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.slogan__en { margin: 16px 0 0; font-size: var(--erp-font-size-display-sub); color: var(--erp-color-on-brand-secondary); letter-spacing: 1px; }
.slogan__desc { margin: 20px 0 0; max-width: 520px; font-size: var(--erp-font-size-body); line-height: 1.9; color: var(--erp-color-on-brand-tertiary); }

.features { margin-top: 40px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; max-width: 560px; }
.feature {
  display: flex; align-items: center; gap: 12px; padding: 14px 16px; border-radius: var(--erp-radius-dialog);
  border: 1px solid var(--erp-color-on-brand-border); background: var(--erp-color-on-brand-fill); backdrop-filter: blur(6px);
}
.feature__icon {
  width: 36px; height: 36px; flex-shrink: 0; border-radius: var(--erp-radius-card); font-size: var(--erp-font-size-section-title);
  color: var(--erp-color-brand-accent); background: var(--erp-color-on-brand-fill); border: 1px solid var(--erp-color-on-brand-border);
}
.feature__title { font-size: var(--erp-font-size-body); font-weight: var(--erp-font-weight-medium); }
.feature__desc { margin-top: 2px; font-size: var(--erp-font-size-caption); color: var(--erp-color-on-brand-tertiary); }

.hero__foot { display: flex; align-items: center; justify-content: space-between; gap: 24px; flex-wrap: wrap; }
.flow { display: flex; align-items: center; gap: 8px; font-size: var(--erp-font-size-caption); color: var(--erp-color-on-brand-secondary); }
.flow__node { white-space: nowrap; }
.flow__line { width: 24px; height: 1px; background: linear-gradient(90deg, var(--erp-color-on-brand-border), var(--erp-color-brand-accent)); }
.copyright { font-size: var(--erp-font-size-caption); color: var(--erp-color-on-brand-tertiary); }

/* ---------- 登录区 ---------- */
.side { flex: 0 0 480px; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 32px; box-sizing: border-box; background: var(--erp-color-bg); }
.box {
  width: 100%; max-width: 400px; padding: 40px 36px 28px; box-sizing: border-box; background: var(--erp-color-surface);
  border: 1px solid var(--erp-color-border); border-radius: var(--erp-radius-dialog); box-shadow: var(--erp-shadow-login);
}
.box__brand { display: flex; align-items: center; gap: 8px; font-size: var(--erp-font-size-body); font-weight: var(--erp-font-weight-semibold); color: var(--erp-color-primary); letter-spacing: 1px; }
.box__mark { width: 20px; height: 20px; }
.box__title { margin: 20px 0 0; font-size: var(--erp-font-size-page-title); font-weight: var(--erp-font-weight-semibold); line-height: 28px; }
.box__desc { margin: 4px 0 28px; font-size: var(--erp-font-size-secondary); color: var(--erp-color-text-secondary); }
.box__foot { margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--erp-color-border-light); text-align: center; font-size: var(--erp-font-size-caption); color: var(--erp-color-text-tertiary); }
.side__copy { margin-top: 24px; font-size: var(--erp-font-size-caption); color: var(--erp-color-text-tertiary); }
.full { width: 100%; letter-spacing: 4px; }
.remember { margin-bottom: 16px; }
.error { margin-bottom: 16px; }
.caps-tip { display: flex; align-items: center; gap: 4px; font-size: var(--erp-font-size-caption); color: var(--el-color-warning); line-height: 18px; margin-top: 4px; }
.captcha { display: flex; gap: 8px; width: 100%; }
.captcha img { height: 40px; cursor: pointer; border: 1px solid var(--erp-color-border); border-radius: var(--erp-radius-control); }

@media (max-width: 1200px) {
  .hero__inner { padding: 40px 40px 32px; }
  .side { flex-basis: 420px; }
}
@media (max-width: 960px) {
  .login { flex-direction: column; }
  .hero { flex: 0 0 auto; }
  .hero__inner { min-height: 0; padding: 24px 16px; }
  .hero__copy { padding: 24px 0 8px; }
  .features, .hero__foot, .slogan__desc { display: none; }
  .slogan { margin-top: 16px; font-size: var(--erp-font-size-metric); letter-spacing: 3px; }
  .slogan__en { font-size: var(--erp-font-size-secondary); }
  .side { flex: 1 0 auto; padding: 24px 16px; }
  .side__copy { display: none; }
}
</style>
