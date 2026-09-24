<template>
  <div class="not-found">
    <div class="code num">404</div>
    <ErpEmpty icon="Search" description="页面不存在或已被移除">
      <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
    </ErpEmpty>
  </div>
</template>

<style scoped>
.not-found { min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; background: var(--erp-color-bg); }
.code { font-size: var(--erp-font-size-metric); font-weight: var(--erp-font-weight-semibold); color: var(--erp-color-text); }
</style>
