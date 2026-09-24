<template>
  <ErpPage title="无权限" no-breadcrumb>
    <ErpPanel>
      <ErpEmpty icon="Lock" description="没有访问该页面的权限，如需使用请联系系统管理员分配角色">
        <el-button @click="$router.push('/')">返回工作台</el-button>
      </ErpEmpty>
    </ErpPanel>
  </ErpPage>
</template>
