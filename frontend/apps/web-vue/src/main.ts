import { createApp } from 'vue';
import { createPinia } from 'pinia';
import Antd from 'ant-design-vue';
import 'ant-design-vue/dist/reset.css';
import { createVueI18n } from '@fs/locales';
import App from './App.vue';
import router from './router';
import './index.css';

const app = createApp(App);
app.use(createPinia());
const i18n = await createVueI18n();
app.use(i18n);
app.use(Antd);
app.use(router);
app.mount('#app');
