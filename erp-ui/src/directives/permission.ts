import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'

/** 按钮级权限：<el-button v-perm="'eng:material:create'">新建</el-button> */
export const vPerm: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    if (!useUserStore().hasPermission(binding.value)) {
      el.parentNode?.removeChild(el)
    }
  }
}
