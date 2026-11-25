import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { fmtMoney } from '../lib/format';
import type { ConfirmOptions } from './ctx';
import { useApp } from './ctx';

export function Modal({ title, onClose, children, footer, wide }: { title: string; onClose: () => void; children: ReactNode; footer?: ReactNode; wide?: boolean }) {
  useEffect(() => {
    const h = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [onClose]);
  return (
    <div className="modal-back" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={'modal' + (wide ? ' wide' : '')} role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head">
          <h2>{title}</h2>
          <button className="icon-btn" onClick={onClose} aria-label="close">
            ✕
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

export function ConfirmDialog({ opts, onDone }: { opts: ConfirmOptions; onDone: (ok: boolean) => void }) {
  const { t } = useApp();
  const [text, setText] = useState('');
  const okDisabled = !!opts.requireText && text.trim() !== opts.requireText;
  return (
    <Modal
      title={opts.title}
      onClose={() => onDone(false)}
      footer={
        <>
          <button className="btn" onClick={() => onDone(false)}>
            {t('c.cancel')}
          </button>
          <button className={'btn ' + (opts.danger ? 'danger' : 'primary')} disabled={okDisabled} onClick={() => onDone(true)} data-testid="confirm-ok">
            {opts.ok ?? t('c.confirm')}
          </button>
        </>
      }
    >
      <div className="confirm-msg">{opts.message}</div>
      {opts.requireText && <input className="input" autoFocus value={text} onChange={(e) => setText(e.target.value)} placeholder={opts.requireText} data-testid="confirm-text" />}
    </Modal>
  );
}

export function Seg<V extends string>({ value, options, onChange, name }: { value: V; options: { value: V; label: string }[]; onChange: (v: V) => void; name: string }) {
  return (
    <div className="seg" role="radiogroup" aria-label={name}>
      {options.map((o) => (
        <button type="button" key={o.value} role="radio" aria-checked={value === o.value} className={value === o.value ? 'on' : ''} onClick={() => onChange(o.value)} data-testid={`${name}-${o.value || 'all'}`}>
          {o.label}
        </button>
      ))}
    </div>
  );
}

export function Field({ label, error, hint, children }: { label: string; error?: string; hint?: string; children: ReactNode }) {
  return (
    <label className={'field' + (error ? ' has-err' : '')}>
      <span className="field-label">{label}</span>
      {children}
      {error ? <span className="field-err">{error}</span> : hint ? <span className="field-hint">{hint}</span> : null}
    </label>
  );
}

export function StatCard({ label, value, sub, accent }: { label: string; value: string; sub?: string; accent?: string }) {
  return (
    <div className="stat" style={accent ? ({ '--accent': `var(${accent})` } as React.CSSProperties) : undefined}>
      <div className="stat-label">{label}</div>
      <div className="stat-value">{value}</div>
      {sub && <div className="stat-sub">{sub}</div>}
    </div>
  );
}

/** Stacked bar chart (2D / 3D per day) drawn with plain SVG. */
export function BarChart({ data }: { data: { label: string; a: number; b: number }[] }) {
  const { t } = useApp();
  const [hover, setHover] = useState<number | null>(null);
  const max = Math.max(1, ...data.map((d) => d.a + d.b));
  const H = 160;
  const bw = 100 / data.length;
  return (
    <div className="chart">
      <div className="chart-legend">
        <span><i className="sw sw-a" />2D</span>
        <span><i className="sw sw-b" />3D</span>
        {hover !== null && (
          <span className="chart-tip">
            {data[hover].label}: 2D {fmtMoney(data[hover].a)} · 3D {fmtMoney(data[hover].b)} · {t('c.total')} {fmtMoney(data[hover].a + data[hover].b)}
          </span>
        )}
      </div>
      <svg viewBox={`0 0 100 ${H}`} preserveAspectRatio="none" className="chart-svg" onMouseLeave={() => setHover(null)}>
        {[0.25, 0.5, 0.75].map((g) => (
          <line key={g} x1="0" x2="100" y1={H * g} y2={H * g} className="chart-grid" vectorEffect="non-scaling-stroke" />
        ))}
        {data.map((d, i) => {
          const ha = (d.a / max) * (H - 4);
          const hb = (d.b / max) * (H - 4);
          const x = i * bw + bw * 0.18;
          const w = bw * 0.64;
          return (
            <g key={d.label} onMouseEnter={() => setHover(i)} onClick={() => setHover(i)}>
              <rect x={i * bw} y={0} width={bw} height={H} fill="transparent" />
              <rect x={x} y={H - ha} width={w} height={ha} className="bar-a" rx="0.6" />
              <rect x={x} y={H - ha - hb - (hb ? 0.8 : 0)} width={w} height={hb} className="bar-b" rx="0.6" />
            </g>
          );
        })}
      </svg>
      <div className="chart-x">
        {data.map((d, i) => (
          <span key={d.label} className={hover === i ? 'on' : ''}>
            {i % 2 === (data.length - 1) % 2 ? d.label.slice(8) : ''}
          </span>
        ))}
      </div>
    </div>
  );
}

export function Toasts({ items }: { items: { id: number; msg: string; kind: 'ok' | 'err' }[] }) {
  return (
    <div className="toasts" aria-live="polite">
      {items.map((x) => (
        <div key={x.id} className={'toast ' + x.kind} data-testid="toast">
          {x.msg}
        </div>
      ))}
    </div>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty-box">{children}</div>;
}

export function useAutoFocus<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  useEffect(() => ref.current?.focus(), []);
  return ref;
}
