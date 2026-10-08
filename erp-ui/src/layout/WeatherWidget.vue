<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { homeApi, type Weather, type WeatherCity, type WeatherPref } from '@/modules/workbench/api/workbench'

/**
 * 顶部天气（需求 02-01，铃铛旁边）：位置跟个人账号绑定——自动定位（浏览器定位，失败时用手选城市）或手选城市，默认东莞。
 * 点击展开：今天实况（湿度、风速）+ 3 天预报，可切换定位方式、选择 / 搜索城市。每 30 分钟刷新。
 */
const weather = ref<Weather>()
const pref = ref<WeatherPref>({ mode: 'MANUAL', cityName: '东莞', latitude: 23.0207, longitude: 113.7518 })
const locateTip = ref('')
const locating = ref(false)
const saving = ref(false)
const cities = ref<WeatherCity[]>([])
const searching = ref(false)
const DAY_NAMES = ['今天', '明天', '后天']
const LOC_KEY = 'erp.weather.location'

/** 浏览器定位（缓存 30 分钟，避免每次刷新都弹授权）；需要 HTTPS 或 localhost 访问 */
function locate(force = false): Promise<{ lat: number; lon: number } | undefined> {
  if (!force) {
    try {
      const c = JSON.parse(sessionStorage.getItem(LOC_KEY) ?? 'null') as { lat: number; lon: number; at: number } | null
      if (c && Date.now() - c.at < 30 * 60 * 1000) return Promise.resolve({ lat: c.lat, lon: c.lon })
    } catch {
      // 忽略：无法读取会话存储时重新定位
    }
  }
  if (!window.isSecureContext || !navigator.geolocation) {
    locateTip.value = `自动定位需要通过 HTTPS 访问系统，当前使用手选城市「${pref.value.cityName}」`
    return Promise.resolve(undefined)
  }
  locating.value = true
  return new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(
      (p) => {
        locating.value = false
        locateTip.value = ''
        const loc = { lat: Math.round(p.coords.latitude * 10000) / 10000, lon: Math.round(p.coords.longitude * 10000) / 10000 }
        try {
          sessionStorage.setItem(LOC_KEY, JSON.stringify({ ...loc, at: Date.now() }))
        } catch {
          // 忽略
        }
        resolve(loc)
      },
      (e) => {
        locating.value = false
        locateTip.value = `${e.code === e.PERMISSION_DENIED ? '浏览器未允许定位' : '定位失败'}，当前使用手选城市「${pref.value.cityName}」`
        resolve(undefined)
      },
      { timeout: 8000, maximumAge: 30 * 60 * 1000 }
    )
  })
}

async function load(forceLocate = false) {
  const loc = pref.value.mode === 'AUTO' ? await locate(forceLocate) : undefined
  if (pref.value.mode !== 'AUTO') locateTip.value = ''
  weather.value = await homeApi.weather(loc).catch(() => weather.value)
}

async function save(next: WeatherPref) {
  saving.value = true
  try {
    await homeApi.saveWeatherPref(next)
    pref.value = next
    await load(next.mode === 'AUTO')
  } finally {
    saving.value = false
  }
}

function setMode(mode: string | number | boolean | undefined) {
  save({ ...pref.value, mode: mode === 'AUTO' ? 'AUTO' : 'MANUAL' })
}

const cityKey = (c: { name: string; latitude: number; longitude: number }) => `${c.name}|${c.latitude}|${c.longitude}`
const selectedCity = computed(() => cityKey({ name: pref.value.cityName, latitude: pref.value.latitude, longitude: pref.value.longitude }))
function pickCity(key: string) {
  const c = cities.value.find((x) => cityKey(x) === key)
  if (!c) return
  save({ mode: 'MANUAL', cityName: c.name, latitude: Number(c.latitude), longitude: Number(c.longitude) })
  ElMessage.success(`已切换到 ${c.name}`)
}
async function searchCities(keyword: string) {
  searching.value = true
  try {
    cities.value = await homeApi.weatherCities(keyword || undefined)
  } finally {
    searching.value = false
  }
}

const summary = computed(() => {
  const w = weather.value
  if (!w?.available) return ''
  return [`湿度 ${w.humidity ?? '-'}%`, `风速 ${w.windSpeed ?? '-'} km/h`].join('　')
})

let timer = 0
onMounted(async () => {
  const p = await homeApi.weatherPref().catch(() => undefined)
  if (p) pref.value = { ...p, latitude: Number(p.latitude), longitude: Number(p.longitude) }
  await load()
  searchCities('')
  timer = window.setInterval(() => load(), 30 * 60 * 1000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <el-popover v-if="weather?.enabled !== false" trigger="click" placement="bottom-end" :width="320">
    <template #reference>
      <button type="button" class="weather" :title="weather?.available ? `${weather.city} ${weather.text}` : '天气'">
        <template v-if="weather?.available">
          <el-icon class="weather__icon"><component :is="weather.icon" /></el-icon>
          <span class="num">{{ weather.temperature }}°</span>
          <span class="weather__text">{{ weather.text }}</span>
          <span class="weather__city">{{ weather.city }}</span>
        </template>
        <template v-else>
          <el-icon class="weather__icon"><component is="WeatherCloud" /></el-icon>
          <span class="weather__city">{{ weather?.city ?? pref.cityName }}</span>
        </template>
      </button>
    </template>

    <div class="panel">
      <div class="panel__head">
        <span class="panel__city">{{ weather?.city ?? pref.cityName }}</span>
        <ErpBadge v-if="weather?.located" type="success" plain>定位</ErpBadge>
        <ErpBadge v-if="weather?.stale" type="warning" plain>上次数据</ErpBadge>
        <span class="erp-spacer" />
        <span v-if="weather?.updatedAt" class="text-muted">{{ weather.updatedAt.slice(11, 16) }} 更新</span>
      </div>
      <template v-if="weather?.available">
        <div class="panel__now">
          <el-icon class="panel__icon"><component :is="weather.icon" /></el-icon>
          <b class="num">{{ weather.temperature }}°</b>
          <div>
            <div>{{ weather.text }}</div>
            <div class="text-muted">{{ summary }}</div>
          </div>
        </div>
        <div v-for="(d, i) in weather.days" :key="d.date" class="panel__day">
          <span class="panel__dayname">{{ DAY_NAMES[i] ?? d.date.slice(5) }}</span>
          <el-icon><component :is="d.icon" /></el-icon>
          <span class="panel__daytext">{{ d.text }}</span>
          <span v-if="d.rainProbability != null" class="text-muted">降水 {{ d.rainProbability }}%</span>
          <span class="erp-spacer" />
          <span class="num">{{ d.min }}~{{ d.max }}°</span>
        </div>
      </template>
      <div v-else class="text-muted panel__empty">天气暂不可用（服务器需能访问 api.open-meteo.com）</div>

      <el-divider />
      <div class="panel__row">
        <span class="panel__label">位置</span>
        <el-radio-group :model-value="pref.mode" size="small" :disabled="saving" @update:model-value="setMode">
          <el-radio-button value="AUTO">自动定位</el-radio-button>
          <el-radio-button value="MANUAL">手选城市</el-radio-button>
        </el-radio-group>
        <el-button v-if="pref.mode === 'AUTO'" link type="primary" :loading="locating" @click="load(true)">重新定位</el-button>
      </div>
      <div class="panel__row">
        <span class="panel__label">城市</span>
        <el-select :model-value="selectedCity" filterable remote :remote-method="searchCities" :loading="searching" size="small"
                   placeholder="输入城市名搜索" class="panel__select" @update:model-value="pickCity">
          <el-option v-if="!cities.some((c) => cityKey(c) === selectedCity)" :value="selectedCity" :label="pref.cityName" />
          <el-option v-for="c in cities" :key="cityKey(c)" :value="cityKey(c)" :label="c.name">
            <span>{{ c.name }}</span><span class="text-muted panel__prov">{{ c.province }}</span>
          </el-option>
        </el-select>
      </div>
      <div v-if="pref.mode === 'AUTO'" class="text-muted panel__tip">{{ locateTip || '按浏览器定位显示所在城市；定位失败时使用上面选择的城市' }}</div>
    </div>
  </el-popover>
</template>

<style scoped>
.weather { display: inline-flex; align-items: center; gap: var(--erp-space-1); height: 32px; padding: 0 var(--erp-space-2); border: none;
  border-radius: var(--erp-radius-control); background: transparent; color: var(--erp-color-text); font: inherit; cursor: pointer; white-space: nowrap; }
.weather:hover { background: var(--erp-color-bg); }
.weather__icon { font-size: var(--erp-font-size-section-title); color: var(--erp-color-primary); }
.weather__text, .weather__city { color: var(--erp-color-text-secondary); }
.panel__head { display: flex; align-items: center; gap: var(--erp-space-2); }
.panel__city { font-size: var(--erp-font-size-section-title); font-weight: var(--erp-font-weight-semibold); }
.panel__now { display: flex; align-items: center; gap: var(--erp-space-3); margin: var(--erp-space-3) 0; }
.panel__now b { font-size: var(--erp-font-size-metric); font-weight: var(--erp-font-weight-semibold); }
.panel__icon { font-size: var(--erp-font-size-metric); color: var(--erp-color-primary); }
.panel__day { display: flex; align-items: center; gap: var(--erp-space-2); padding: var(--erp-space-1) 0; }
.panel__dayname { width: 36px; color: var(--erp-color-text-secondary); }
.panel__daytext { min-width: 48px; }
.panel__empty { margin: var(--erp-space-3) 0; }
.panel__row { display: flex; align-items: center; gap: var(--erp-space-2); margin-bottom: var(--erp-space-2); }
.panel__label { width: 32px; color: var(--erp-color-text-secondary); }
.panel__select { flex: 1; }
.panel__prov { margin-left: var(--erp-space-2); }
.panel__tip { font-size: var(--erp-font-size-caption); line-height: 1.5; }
</style>
