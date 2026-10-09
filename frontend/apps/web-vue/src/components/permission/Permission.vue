<script setup lang="ts">
/**
 * 权限包装组件：规则通过则渲染默认插槽，否则按 fallback 隐藏（不渲染）
 * 或灰置（Tooltip 提示 + inert 阻止焦点与交互）。
 * 适合包裹 Popconfirm 这类触发器与确认层必须同生共死的场景。
 * 判定走与 usePermission 相同的 checkPermission 纯函数，口径一致。
 */
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { checkPermission } from '@fs/auth';
import { useAuthStore } from '@/store/auth';

const props = withDefaults(
  defineProps<{
    /** 需要的权限码，任一满足即通过；字符串或数组，空/缺省不加限制 */
    codes?: string | string[];
    /** 需要的角色编码，仅当未指定非空 codes 时检查 */
    roles?: string | string[];
    /** 业务条件（如记录状态），通过权限/角色检查后执行 */
    customFn?: (userRoles: string[], extra?: unknown) => boolean;
    /** 传给 customFn 的业务数据 */
    extra?: unknown;
    /** 无权限时的表现：hide 不渲染，disable 灰置并解释原因 */
    fallback?: 'hide' | 'disable';
    /** disable 模式的提示文案，缺省为「暂无操作权限」 */
    tooltip?: string;
  }>(),
  { fallback: 'hide' },
);

const { t } = useI18n();
const auth = useAuthStore();

const allowed = computed(() =>
  checkPermission({
    permissions: auth.user?.permissions,
    userRoles: auth.user?.roles,
    codes: toList(props.codes),
    roles: toList(props.roles),
    customFn: props.customFn,
    extra: props.extra,
  }),
);

function toList(value?: string | string[]) {
  if (!value) return undefined;
  return Array.isArray(value) ? value : [value];
}

/** 外层拦截点击冒泡；后代焦点与交互由 inert 负责 */
function onBlocked(event: Event) {
  event.preventDefault();
  event.stopPropagation();
}
</script>

<template>
  <template v-if="allowed">
    <slot />
  </template>
  <a-tooltip
    v-else-if="fallback === 'disable'"
    :title="tooltip ?? t('common.noPermission')"
    :trigger="['hover', 'focus']"
  >
    <span aria-disabled="true" tabindex="0" class="perm-denied" @click.capture="onBlocked">
      <span :inert="true" class="perm-denied-inner">
        <slot />
      </span>
    </span>
  </a-tooltip>
</template>

<style scoped>
.perm-denied {
  display: inline-block;
  opacity: 0.5;
  cursor: not-allowed;
}
.perm-denied-inner {
  display: inline-block;
  pointer-events: none;
}
</style>
