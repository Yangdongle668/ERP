import { computed, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'

export const LEAVE_MESSAGE = '有未保存的修改，确定离开吗？'

/**
 * 未保存离开提醒（UI 设计规范 3.3、6.2）。
 *
 * - 把 source 当前内容作为“已保存”快照；内容变化后标记页签为已修改（标题前显示 ●）。
 * - 关闭页签、刷新浏览器时由框架提示；页面上的“返回/取消”按钮调用 confirmLeave()。
 * - 保存成功后调用 markClean()。
 *
 * const guard = useLeaveGuard(() => form.value)
 * await save(); guard.markClean()
 */
export function useLeaveGuard(source: () => unknown) {
  const route = useRoute()
  const tabs = useTabsStore()
  const key = tabKeyOf(route)
  const snapshot = ref(serialize(source()))
  const current = ref(snapshot.value)
  const dirty = computed(() => current.value !== snapshot.value)

  watch(source, (v) => (current.value = serialize(v)), { deep: true })
  watch(dirty, (d) => tabs.setDirty(key, d))

  function markClean() {
    snapshot.value = serialize(source())
    current.value = snapshot.value
  }

  async function confirmLeave(): Promise<boolean> {
    if (!dirty.value) return true
    return ElMessageBox.confirm(LEAVE_MESSAGE, '提示', { type: 'warning' })
      .then(() => true)
      .catch(() => false)
  }

  const onBeforeUnload = (e: BeforeUnloadEvent) => {
    if (dirty.value) {
      e.preventDefault()
      e.returnValue = LEAVE_MESSAGE
    }
  }
  onMounted(() => window.addEventListener('beforeunload', onBeforeUnload))
  onActivated(() => tabs.setDirty(key, dirty.value))
  onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', onBeforeUnload)
    tabs.setDirty(key, false)
  })

  return { dirty, markClean, confirmLeave }
}

function serialize(v: unknown): string {
  try {
    return JSON.stringify(v)
  } catch {
    return String(Math.random())
  }
}
