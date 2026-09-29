<script setup lang="ts">
import InspectionListBody from '../components/InspectionListBody.vue'

defineOptions({ name: 'QcIpqcList' })
</script>

<template>
  <ErpPage description="系统按事件自动生成检验单：入库待检仓（IQC / FQC）、退货仓（退货检验）、报工检验点（IPQC）、出货通知装箱（OQC）">
    <InspectionListBody types="IPQC" perm="ipqc" />
  </ErpPage>
</template>
