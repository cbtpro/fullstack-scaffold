<template>
  <a-tabs
    type="editable-card"
    size="small"
    hide-add
    :active-key="currentKey"
    class="tab-bar"
    @tab-click="onTabClick"
    @edit="onEdit"
  >
    <!-- 当前版本 Tabs 不支持 items 属性，用 a-tab-pane 声明标签 -->
    <a-tab-pane v-for="tab in items" :key="tab.key" :tab="tab.label" :closable="tab.closable" />
  </a-tabs>
</template>

<script setup lang="ts">
/**
 * 路由驱动的多标签栏：激活项由 URL 派生，标签历史单独保存。
 * 标签键 = path + 排序后的 query；标题来自路由 meta.title，可按路由参数动态生成。
 */
import { computed, ref, watchEffect } from 'vue';
import { useRoute, useRouter, type RouteParams } from 'vue-router';
import { useI18n } from 'vue-i18n';

interface RouteTab {
  key: string;
  label: string;
  closable: boolean;
}

/** 标签键 = path + 排序后的 query，避免参数顺序不同生成两个标签 */
function getTabKey(path: string, query: Record<string, unknown>) {
  const params = new URLSearchParams();
  for (const [name, value] of Object.entries(query)) {
    if (Array.isArray(value)) value.forEach((item) => params.append(name, String(item)));
    else if (value != null) params.append(name, String(value));
  }
  params.sort();
  const search = params.toString();
  return search ? `${path}?${search}` : path;
}

const HOME_TAB_KEY = '/home';

const route = useRoute();
const router = useRouter();
const { t } = useI18n();

const currentKey = computed(() => getTabKey(route.path, route.query));

const currentLabel = computed(() => {
  const title = route.meta.title;
  if (!title) return '';
  return typeof title === 'function' ? title(t, route.params as RouteParams) : t(title);
});

// 首页为固定首项，closable: false 隐藏关闭按钮
const tabs = ref<RouteTab[]>([{ key: HOME_TAB_KEY, label: '', closable: false }]);

// 激活项由 URL 派生，标签历史在此追加：新地址补录，已有地址仅同步最新标题
watchEffect(() => {
  if (currentKey.value === HOME_TAB_KEY || !currentLabel.value) return;
  const existing = tabs.value.find((tab) => tab.key === currentKey.value);
  if (!existing) {
    tabs.value = [...tabs.value, { key: currentKey.value, label: currentLabel.value, closable: true }];
    return;
  }
  if (existing.label !== currentLabel.value) {
    tabs.value = tabs.value.map((tab) =>
      tab.key === currentKey.value ? { ...tab, label: currentLabel.value } : tab,
    );
  }
});

// 首页标签文案实时取 t，语言切换同步更新
const items = computed(() =>
  tabs.value.map((tab) => (tab.key === HOME_TAB_KEY ? { ...tab, label: t('menu.home') } : tab)),
);

function removeTab(key: string) {
  if (key === HOME_TAB_KEY) return;
  const index = tabs.value.findIndex((tab) => tab.key === key);
  if (index < 0) return;
  const next = tabs.value.filter((tab) => tab.key !== key);
  tabs.value = next;
  // 关闭当前项导航到左侧相邻项；关闭非当前项只修改历史，不改当前 URL
  if (key === currentKey.value) {
    router.replace(next[Math.max(0, index - 1)]?.key ?? HOME_TAB_KEY);
  }
}

function onTabClick(key: string | number) {
  void router.push(String(key));
}

function onEdit(targetKey: unknown, action: 'add' | 'remove') {
  if (action === 'remove' && typeof targetKey === 'string') removeTab(targetKey);
}
</script>

<style scoped>
.tab-bar {
  margin-bottom: 12px;
}
</style>
