<!--
  IME 组合感知输入框。

  解决中文/日文等输入法在组合过程中频繁触发 onChange 导致搜索抖动的问题：
  - compositionstart → 标记合成中，通知外部取消防抖计时器
  - compositionend   → 提交最终值，标记合成结束
  - input/onInput    → 合成中不 commit（不触发 update:value），合成结束后才 commit

  内部 draft 状态用于显示输入过程，外部 value 是已提交值。
  合成中外部 value 不变，draft 跟随输入过程更新；合成结束后 update:value 提交最终值。
-->
<template>
  <a-input
    v-bind="$attrs"
    :value="draft"
    @input="onInput"
    @compositionstart="onCompositionStart"
    @compositionend="onCompositionEnd"
  />
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';

interface Props {
  value?: string;
}

interface Emits {
  (e: 'update:value', value: string): void;
  (e: 'compositionstart'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

const composing = ref(false);
const draft = ref(props.value ?? '');

// 外部 value 变化（如重置）时同步 draft，合成中不打断
watch(
  () => props.value,
  (val) => {
    if (!composing.value) {
      draft.value = val ?? '';
    }
  },
);

function onInput(e: Event) {
  const v = (e.target as HTMLInputElement).value;
  draft.value = v;
  const nativeEvent = e as InputEvent;
  // 合成中或原生 isComposing 时不提交
  if (!composing.value && !nativeEvent.isComposing) {
    emit('update:value', v);
  }
}

function onCompositionStart() {
  composing.value = true;
  emit('compositionstart');
}

function onCompositionEnd(e: CompositionEvent) {
  composing.value = false;
  const finalValue = (e.target as HTMLInputElement).value;
  draft.value = finalValue;
  emit('update:value', finalValue);
}
</script>
