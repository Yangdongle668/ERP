import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { vPerm } from './directives/permission'
import { registerComponents } from './components'
import './styles/global.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
for (const [name, component] of Object.entries(ElementPlusIcons)) {
  app.component(name, component)
}
registerComponents(app)
app.directive('perm', vPerm)
app.mount('#app')
