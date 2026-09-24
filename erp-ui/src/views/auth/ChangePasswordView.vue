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
    <el-card class="box">
      <h3>{{ title }}</h3>
      <PasswordForm @changed="onChanged">
        <template #extra-buttons><el-button @click="logout">退出登录</el-button></template>
      </PasswordForm>
    </el-card>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--el-bg-color-page); padding: 16px; }
.box { width: 100%; max-width: 560px; }
h3 { margin: 0 0 20px; }
</style>
