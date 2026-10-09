<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 登录页粒子联动背景（Canvas）：粒子漂移、相近相连；鼠标靠近时粒子被轻推开并与指针连线，点击散开一圈粒子。
 * 粒子数按面积自适应；标签页隐藏时暂停；系统开启「减少动态效果」时只绘制静态一帧。颜色取自 --erp-color-brand-particle。
 */
interface P { x: number; y: number; vx: number; vy: number; r: number; life?: number }

const el = ref<HTMLCanvasElement>()
const LINK = 130
const MOUSE = 170
let ctx: CanvasRenderingContext2D | null = null
let w = 0
let h = 0
let raf = 0
let rgb = '120, 170, 255'
let reduced = false
let ps: P[] = []
const mouse = { x: -9999, y: -9999, active: false }
let resizeObs: ResizeObserver | undefined

const rand = (a: number, b: number) => a + Math.random() * (b - a)

function spawn(x = rand(0, w), y = rand(0, h), speed = 0.25): P {
  const a = rand(0, Math.PI * 2)
  const s = rand(0.3, 1) * speed
  return { x, y, vx: Math.cos(a) * s, vy: Math.sin(a) * s, r: rand(0.8, 2.2) }
}

function resize() {
  const c = el.value
  if (!c || !ctx) return
  const rect = c.getBoundingClientRect()
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  w = rect.width
  h = rect.height
  c.width = Math.round(w * dpr)
  c.height = Math.round(h * dpr)
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  const target = Math.min(140, Math.max(36, Math.round((w * h) / 9000)))
  ps = ps.filter((p) => p.life === undefined && p.x <= w && p.y <= h)
  while (ps.length < target) ps.push(spawn())
  ps.length = Math.min(ps.length, target)
  if (reduced) draw()
}

function step() {
  for (const p of ps) {
    if (mouse.active) {
      const dx = p.x - mouse.x
      const dy = p.y - mouse.y
      const d = Math.hypot(dx, dy)
      if (d < MOUSE && d > 0.1) {
        const f = ((MOUSE - d) / MOUSE) * 0.06
        p.vx += (dx / d) * f
        p.vy += (dy / d) * f
      }
    }
    // 阻尼回到巡航速度
    const sp = Math.hypot(p.vx, p.vy)
    const cruise = p.life === undefined ? 0.35 : 0.6
    if (sp > cruise) {
      p.vx *= 0.97
      p.vy *= 0.97
    }
    p.x += p.vx
    p.y += p.vy
    if (p.life !== undefined) p.life -= 1
    if (p.x < -10) p.x = w + 10
    else if (p.x > w + 10) p.x = -10
    if (p.y < -10) p.y = h + 10
    else if (p.y > h + 10) p.y = -10
  }
  ps = ps.filter((p) => p.life === undefined || p.life > 0)
}

function draw() {
  if (!ctx) return
  ctx.clearRect(0, 0, w, h)
  const n = ps.length
  ctx.lineWidth = 0.8
  for (let i = 0; i < n; i++) {
    const a = ps[i]
    for (let j = i + 1; j < n; j++) {
      const b = ps[j]
      const dx = a.x - b.x
      if (dx > LINK || dx < -LINK) continue
      const dy = a.y - b.y
      if (dy > LINK || dy < -LINK) continue
      const d = Math.hypot(dx, dy)
      if (d < LINK) {
        ctx.strokeStyle = `rgba(${rgb}, ${((1 - d / LINK) * 0.35).toFixed(3)})`
        ctx.beginPath()
        ctx.moveTo(a.x, a.y)
        ctx.lineTo(b.x, b.y)
        ctx.stroke()
      }
    }
    if (mouse.active) {
      const d = Math.hypot(a.x - mouse.x, a.y - mouse.y)
      if (d < MOUSE) {
        ctx.strokeStyle = `rgba(${rgb}, ${((1 - d / MOUSE) * 0.6).toFixed(3)})`
        ctx.beginPath()
        ctx.moveTo(a.x, a.y)
        ctx.lineTo(mouse.x, mouse.y)
        ctx.stroke()
      }
    }
  }
  for (const p of ps) {
    const near = mouse.active && Math.hypot(p.x - mouse.x, p.y - mouse.y) < MOUSE
    const alpha = p.life !== undefined ? Math.min(1, p.life / 60) : near ? 1 : 0.75
    ctx.fillStyle = `rgba(${rgb}, ${alpha.toFixed(3)})`
    ctx.beginPath()
    ctx.arc(p.x, p.y, near ? p.r + 0.8 : p.r, 0, Math.PI * 2)
    ctx.fill()
  }
  if (mouse.active) {
    const g = ctx.createRadialGradient(mouse.x, mouse.y, 0, mouse.x, mouse.y, MOUSE)
    g.addColorStop(0, `rgba(${rgb}, 0.10)`)
    g.addColorStop(1, `rgba(${rgb}, 0)`)
    ctx.fillStyle = g
    ctx.fillRect(mouse.x - MOUSE, mouse.y - MOUSE, MOUSE * 2, MOUSE * 2)
  }
}

function loop() {
  step()
  draw()
  raf = requestAnimationFrame(loop)
}

function start() {
  cancelAnimationFrame(raf)
  if (!reduced && !document.hidden) raf = requestAnimationFrame(loop)
}

function local(e: PointerEvent) {
  const rect = el.value!.getBoundingClientRect()
  return { x: e.clientX - rect.left, y: e.clientY - rect.top, inside: e.clientX >= rect.left && e.clientX <= rect.right && e.clientY >= rect.top && e.clientY <= rect.bottom }
}
function onMove(e: PointerEvent) {
  const p = local(e)
  mouse.x = p.x
  mouse.y = p.y
  mouse.active = p.inside
}
function onLeave() {
  mouse.active = false
}
function onDown(e: PointerEvent) {
  const p = local(e)
  if (!p.inside || reduced) return
  for (let i = 0; i < 14; i++) ps.push({ ...spawn(p.x, p.y, 2.4), life: rand(80, 140) })
}
const onVisibility = () => start()

onMounted(() => {
  const c = el.value
  if (!c) return
  ctx = c.getContext('2d')
  const v = getComputedStyle(c).getPropertyValue('--erp-color-brand-particle').trim()
  if (v) rgb = v
  reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
  resize()
  resizeObs = new ResizeObserver(resize)
  resizeObs.observe(c)
  window.addEventListener('pointermove', onMove, { passive: true })
  window.addEventListener('pointerdown', onDown, { passive: true })
  document.addEventListener('pointerleave', onLeave)
  document.addEventListener('visibilitychange', onVisibility)
  if (reduced) draw()
  else start()
})

onBeforeUnmount(() => {
  cancelAnimationFrame(raf)
  resizeObs?.disconnect()
  window.removeEventListener('pointermove', onMove)
  window.removeEventListener('pointerdown', onDown)
  document.removeEventListener('pointerleave', onLeave)
  document.removeEventListener('visibilitychange', onVisibility)
})
</script>

<template>
  <canvas ref="el" class="particles" aria-hidden="true" />
</template>

<style scoped>
.particles { position: absolute; inset: 0; width: 100%; height: 100%; display: block; }
</style>
