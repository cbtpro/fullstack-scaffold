/**
 * IME 组合感知输入框。
 *
 * 解决中文/日文等输入法在组合过程中频繁触发 onChange 导致搜索抖动的问题：
 * - compositionstart → 标记合成中，通知外部取消防抖计时器
 * - compositionend   → 提交最终值，标记合成结束
 * - onChange          → 合成中不 commit（不调 onChange），合成结束后才 commit
 *
 * 内部 draft 状态用于显示输入过程，外部 value 是已提交值。
 * 合成中外部 value 不变，draft 跟随输入过程更新；合成结束后 onChange 提交最终值。
 */
import { useEffect, useRef, useState } from 'react';
import { Input, type InputProps } from 'antd';

export interface ImeInputProps extends Omit<InputProps, 'value' | 'onChange'> {
  /** 已提交值（受控） */
  value?: string;
  /** 值提交回调（非合成态 onChange 或 compositionend 时触发） */
  onChange?: (value: string) => void;
  /** 合成开始回调（用于取消防抖计时器） */
  onCompositionStart?: () => void;
}

export function ImeInput({
  value,
  onChange,
  onCompositionStart,
  onPressEnter,
  ...rest
}: ImeInputProps) {
  const composingRef = useRef(false);
  const [draft, setDraft] = useState(value ?? '');

  // 外部 value 变化（如重置）时同步 draft，合成中不打断
  useEffect(() => {
    if (!composingRef.current) {
      setDraft(value ?? '');
    }
  }, [value]);

  return (
    <Input
      {...rest}
      value={draft}
      onChange={(e) => {
        const v = e.target.value;
        setDraft(v);
        // 合成中或原生 isComposing 时不提交
        if (!composingRef.current && !(e.nativeEvent as InputEvent).isComposing) {
          onChange?.(v);
        }
      }}
      onCompositionStart={() => {
        composingRef.current = true;
        onCompositionStart?.();
      }}
      onCompositionEnd={(e) => {
        composingRef.current = false;
        const finalValue = (e.target as HTMLInputElement).value;
        setDraft(finalValue);
        onChange?.(finalValue);
      }}
      onPressEnter={onPressEnter}
    />
  );
}
