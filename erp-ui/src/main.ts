import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/tokens.css'
import './styles/element.css'
import './styles/global.css'
import App from './App.vue'
import router from './router'
import { vPerm } from './directives/permission'
import { registerComponents } from './components'
import { registerIcons } from './components/icons'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
registerIcons(app)
registerComponents(app)
app.directive('perm', vPerm)
app.mount('#app')
