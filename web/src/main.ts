import { createApp } from 'vue'
import ArcoVue, { Card, Drawer, Modal } from '@arco-design/web-vue'
import formCreate from '@form-create/element-ui'
import ElementPlus, { ElDatePicker } from 'element-plus'
import 'element-plus/dist/index.css'
import '@/styles/arco-ui/index.less'
// import '@arco-themes/vue-gi-demo/index.less'
// import '@arco-design/web-vue/dist/arco.css'

// 额外引入 Arco Design Icon图标库
import ArcoVueIcon from '@arco-design/web-vue/es/icon'
import App from './App.vue'
import router from './router'

// 使用动画库
import 'animate.css/animate.min.css'

// 自定义过渡动画
import '@/styles/css/transition.css'

// 导入全局scss主文件
import '@/styles/index.scss'

// 支持SVG
import 'virtual:svg-icons-register'

// 自定义指令
import directives from './directives'
import TableForm from '@/components/ApprovalForm/TableForm.vue'

// 状态管理
import pinia from '@/stores'

// 对特定组件进行默认配置
Card.props.bordered = false

const app = createApp(App)
Modal._context = app._context
Drawer._context = app._context

app.use(router)
app.use(pinia)
app.use(ArcoVue)
app.use(formCreate)
app.use(ElementPlus)
// 现有表单的 date 规则按原名查找组件，补上 Element Plus 的日期组件别名。
if (!app.component('date')) {
  app.component('date', ElDatePicker)
}
// Warm-Flow 设计器的「表格表单」在规则里以 type=tableForm 保存，宿主补上配套渲染组件。
// 设计器只写 props.columns，行数据与增删行由该组件按 form-create 子表单契约处理。
app.component('tableForm', TableForm)
app.use(ArcoVueIcon)
app.use(directives)

app.mount('#app')
