<script setup lang="ts">
/**
 * 吸底操作栏（hidden / sticky / inline 三态）。
 *
 * - hidden：首次进入页面尚未滚动时隐藏，但保留占位高度，避免栏体
 *   显隐引起文档流高度变化，导致锚点可见性来回翻转
 * - sticky：滚动后锚点离开视口，栏吸附在滚动容器底部（CSS sticky）
 * - inline：锚点回到视口，栏恢复文档流
 *
 * 锚点即栏自身的文档流位置：栏持续挂载（hidden 用 visibility 隐藏），
 * 按钮不随模式切换重挂。判定规则与 React 端 StickyActions 保持一致。
 */
import { onBeforeUnmount, onMounted, ref } from 'vue';

type StickyState = 'hidden' | 'sticky' | 'inline';

/** 状态判定规则：锚点可见优先恢复文档流；未发生过滚动保持隐藏；滚动后吸附。 */
function resolveStickyState(anchorVisible: boolean, hasScrolled: boolean): StickyState {
  if (anchorVisible) return 'inline';
  return hasScrolled ? 'sticky' : 'hidden';
}

const anchorEl = ref<HTMLElement | null>(null);
const state = ref<StickyState>('hidden');

let observer: IntersectionObserver | null = null;
let hasScrolled = false;
let anchorVisible = false;

function sync() {
  state.value = resolveStickyState(anchorVisible, hasScrolled);
}

// 用户第一次滚动不必然触发 IntersectionObserver 回调，hasScrolled 依赖 scroll 事件维护
function onScroll() {
  hasScrolled = true;
  sync();
}

function onResize() {
  sync();
}

onMounted(() => {
  const anchor = anchorEl.value;
  if (!anchor) return;

  // 环境不支持 IntersectionObserver 时退化为普通底部按钮，不再切换状态
  if (typeof IntersectionObserver === 'undefined') {
    state.value = 'inline';
    return;
  }

  // hasScrolled 指本次挂载后发生过滚动；恢复到非零滚动位置时同样按已滚动初始化
  hasScrolled = window.scrollY > 0;
  anchorVisible = false;

  observer = new IntersectionObserver(
    (entries) => {
      anchorVisible = entries[0]?.isIntersecting ?? false;
      sync();
    },
    { root: null },
  );
  observer.observe(anchor);

  window.addEventListener('scroll', onScroll, { passive: true });
  window.addEventListener('resize', onResize);
  sync();
});

onBeforeUnmount(() => {
  observer?.disconnect();
  observer = null;
  window.removeEventListener('scroll', onScroll);
  window.removeEventListener('resize', onResize);
});
</script>

<template>
  <!-- 独立锚点：栏吸附后文档流位置不变，避免「吸附进入视口 → 判定 inline → 回落」来回翻转 -->
  <div ref="anchorEl" class="sticky-actions-anchor" />
  <div class="sticky-actions" :class="`sticky-actions--${state}`">
    <slot />
  </div>
</template>

<style scoped>
/* 锚点零高度，仅用于观察栏体正常文档流位置 */
.sticky-actions-anchor {
  height: 0;
}

/* 三态共用同一占位高度，visibility 切换避免文档流抖动；安全区交给 CSS env 值 */
.sticky-actions {
  position: sticky;
  bottom: 0;
  z-index: 10;
  margin-top: 16px;
  padding-top: 12px;
  padding-bottom: calc(12px + env(safe-area-inset-bottom, 0px));
  border-top: 1px solid #f0f0f0;
  background: #fff;
}

.sticky-actions--hidden {
  visibility: hidden;
}

.sticky-actions--inline {
  position: static;
}
</style>
