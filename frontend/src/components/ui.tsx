import type { ButtonHTMLAttributes, ReactNode } from 'react';

export function Icon({ name }: { name: 'spark' | 'arrow' | 'workspace' }) {
  const paths = {
    spark:
      'M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8zM19 16l.6 1.6L21 18l-1.4.4L19 20l-.6-1.6L17 18l1.4-.4z',
    arrow: 'M5 12h14M13 5l7 7-7 7',
    workspace: 'M4 21V5l8-3 8 3v16M2 21h20M8 8h1m6 0h1M8 12h1m6 0h1M10 21v-5h4v5',
  };
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      viewBox="0 0 24 24"
      width="18"
      height="18"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d={paths[name]} />
    </svg>
  );
}

export function Wordmark() {
  return (
    <a className="wordmark" href="/" aria-label="Cadence home">
      <OrbitStar />
      <span>Cadence</span>
    </a>
  );
}

export function Chip({ children }: { children: ReactNode }) {
  return <span className="chip">{children}</span>;
}

export function OrbitStar() {
  return (
    <span className="star-crop" aria-hidden="true">
      <img src="/assets/orbit.png" alt="" width="1696" height="960" />
    </span>
  );
}

export function Button({
  children,
  className = '',
  type = 'button',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button type={type} className={`button ${className}`} {...props}>
      {children}
    </button>
  );
}

export function WorkflowSteps() {
  const steps = [
    ['Brand Identity', 'Sources · review · approval'],
    ['Strategy playbook', 'Not connected yet'],
    ['Content calendar', 'Not connected yet'],
    ['Performance', 'Not connected yet'],
  ];
  return (
    <section aria-label="Your creative workflow">
      <ol className="workflow-steps">
        {steps.map(([title, detail], i) => (
          <li key={title} aria-current={i === 0 ? 'step' : undefined}>
            <span className="step-num" aria-hidden="true">
              {String(i + 1).padStart(2, '0')}
            </span>
            <span className="step-body">
              <span className="step-meta">{i === 0 ? 'Getting started' : `Step ${i + 1}`}</span>
              <span className="step-title">{title}</span>
              <span className="step-detail">{detail}</span>
            </span>
          </li>
        ))}
      </ol>
    </section>
  );
}
