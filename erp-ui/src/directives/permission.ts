import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * 按钮级权限：<el-button v-perm="'eng:material:create'">新建</el-button>
 * 无权限时移除元素（UI 设计规范 8）。值为空时不限制。
 */
function apply(el: HTMLElement, value?: string) {
  if (!useUserStore().hasPermission(value)) {
    el.style.display = 'none'
    el.setAttribute('data-perm-hidden', '1')
    el.parentNode?.removeChild(el)
  }
}

export const vPerm: Directive<HTMLElement, string | undefined> = {
  mounted(el, binding) {
    apply(el, binding.value)
  },
  updated(el, binding) {
    if (binding.value !== binding.oldValue) apply(el, binding.value)
  }
}
