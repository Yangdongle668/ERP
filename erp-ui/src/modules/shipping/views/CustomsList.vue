<script setup lang="ts">
import DocListBody from '../components/DocListBody.vue'

defineOptions({ name: 'ShpCustomsList' })
</script>

<template>
  <ErpPage description="报关资料：按 HS 编码合并出货行，导出 Excel 导入单一窗口；报关后回填报关单号">
    <DocListBody kind="customs" />
  </ErpPage>
</template>
