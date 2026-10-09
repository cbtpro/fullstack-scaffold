/**
 * 吸底操作栏（hidden / sticky / inline 三态）。
 *
 * - hidden：首次进入页面尚未滚动时隐藏，但保留占位高度，避免栏体
 *   显隐引起文档流高度变化，导致锚点可见性来回翻转
 * - sticky：滚动后锚点离开视口，栏吸附在滚动容器底部（CSS sticky）
 * - inline：锚点回到视口，栏恢复文档流
 *
 * 锚点即栏自身的文档流位置：栏持续挂载（hidden 用 visibility 隐藏），
 * 按钮不随模式切换重挂，避免焦点丢失。判定走 resolveStickyState 纯函数。
 */
import { useEffect, useRef, useState, type ReactNode } from 'react';

export type StickyState = 'hidden' | 'sticky' | 'inline';

/**
 * 状态判定规则：锚点可见优先恢复文档流；未发生过滚动保持隐藏；
 * 滚动后吸附。修改规则时统一改这里，保持状态机单一来源。
 */
export function resolveStickyState(anchorVisible: boolean, hasScrolled: boolean): StickyState {
  if (anchorVisible) return 'inline';
  return hasScrolled ? 'sticky' : 'hidden';
}

export function StickyActions({ children }: { children: ReactNode }) {
  const anchorRef = useRef<HTMLDivElement | null>(null);
  const [state, setState] = useState<StickyState>('hidden');

  useEffect(() => {
    const anchor = anchorRef.current;
    if (!anchor) return;

    // 环境不支持 IntersectionObserver 时退化为普通底部按钮，不再切换状态
    if (typeof IntersectionObserver === 'undefined') {
      setState('inline');
      return;
    }

    // hasScrolled 指本次挂载后发生过滚动，而非当前 scrollTop 是否大于零；
    // 恢复到非零滚动位置时同样按已滚动初始化
    let hasScrolled = window.scrollY > 0;
    let anchorVisible = false;

    const sync = () => setState(resolveStickyState(anchorVisible, hasScrolled));

    const observer = new IntersectionObserver(
      (entries) => {
        anchorVisible = entries[0]?.isIntersecting ?? false;
        sync();
      },
      { root: null },
    );
    observer.observe(anchor);

    // 用户第一次滚动不必然触发 IntersectionObserver 回调，hasScrolled 依赖 scroll 事件维护
    const onScroll = () => {
      hasScrolled = true;
      sync();
    };
    const onResize = () => sync();
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onResize);
    sync();

    return () => {
      observer.disconnect();
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', onResize);
    };
  }, []);

  return (
    <>
      {/* 独立锚点：栏吸附后文档流位置不变，避免「吸附进入视口 → 判定 inline → 回落」来回翻转 */}
      <div ref={anchorRef} className="sticky-actions-anchor" />
      <div className={`sticky-actions sticky-actions--${state}`}>{children}</div>
    </>
  );
}
