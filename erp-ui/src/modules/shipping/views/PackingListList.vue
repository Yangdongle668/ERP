<script setup lang="ts">
import DocListBody from '../components/DocListBody.vue'

defineOptions({ name: 'ShpPackingListList' })
</script>

<template>
  <ErpPage description="Packing List（装箱单）：由出货单生成，箱号区间、每箱数量、毛净重、CBM 与装箱一致">
    <DocListBody kind="packing-list" />
  </ErpPage>
</template>
