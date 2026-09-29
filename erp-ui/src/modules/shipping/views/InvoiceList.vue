<script setup lang="ts">
import DocListBody from '../components/DocListBody.vue'

defineOptions({ name: 'ShpInvoiceList' })
</script>

<template>
  <ErpPage description="Commercial Invoice：金额与数量与出货单一致，只能修改描述、HS 编码等对外字段">
    <DocListBody kind="invoice" />
  </ErpPage>
</template>
