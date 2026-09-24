<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PasswordForm from './PasswordForm.vue'
import { useUserStore } from '@/stores/user'

/** 强制修改密码页（首次登录 / 密码过期）：修改成功后清除令牌，回到登录页 */
const store = useUserStore()
const router = useRouter()
const title = computed(() => (store.user?.passwordExpired ? '密码已过期，请修改密码' : '首次登录，请修改密码'))

async function onChanged() {
  ElMessage.success('密码修改成功，请重新登录')
  store.clear()
  await router.replace('/login')
}

async function logout() {
  await store.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="page">
    <section class="box">
      <h1 class="box__title">{{ title }}</h1>
      <p class="box__desc">为了账号安全，修改密码后才能继续使用系统</p>
      <PasswordForm @changed="onChanged">
        <template #extra-buttons><el-button @click="logout">退出登录</el-button></template>
      </PasswordForm>
    </section>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--erp-color-bg); padding: 16px; }
.box { width: 100%; max-width: 560px; padding: 32px; background: var(--erp-color-surface); border: 1px solid var(--erp-color-border); border-radius: var(--erp-radius-dialog); }
.box__title { margin: 0; font-size: var(--erp-font-size-page-title); font-weight: var(--erp-font-weight-semibold); line-height: 28px; }
.box__desc { margin: 4px 0 24px; font-size: var(--erp-font-size-secondary); color: var(--erp-color-text-secondary); }
</style>
