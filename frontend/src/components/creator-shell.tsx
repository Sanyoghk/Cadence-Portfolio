'use client';

import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Wordmark } from './ui';

const themes = ['cream', 'dark', 'sage'] as const;
type Theme = (typeof themes)[number];

/** Appearance and navigation only. This shell never owns tokens or private data. */
export function CreatorShell({
  children,
  accountControl,
  navigation,
  workspaceControl,
  footer = 'Cadence · Keep the decisions yours.',
  publicPage = false,
}: {
  children: ReactNode;
  accountControl?: ReactNode;
  navigation?: ReactNode;
  workspaceControl?: ReactNode;
  footer?: string;
  publicPage?: boolean;
}) {
  const [theme, setTheme] = useState<Theme>('cream');
  const root = useRef<HTMLDivElement>(null);
  const header = useRef<HTMLElement>(null);
  useEffect(() => {
    if (!root.current || !header.current) return;
    const surface = root.current;
    const bar = header.current;
    const measure = () =>
      surface.style.setProperty(
        '--header-height',
        `${Math.ceil(bar.getBoundingClientRect().height)}px`,
      );
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(bar);
    return () => observer.disconnect();
  }, []);
  useEffect(() => {
    const element = root.current;
    if (!element) return;
    function closeMenus(target?: EventTarget | null, restore = false) {
      element?.querySelectorAll<HTMLDetailsElement>('.nav-disclosure[open]').forEach((menu) => {
        if (restore || !(target instanceof Node) || !menu.contains(target)) {
          menu.open = false;
          if (restore) menu.querySelector('summary')?.focus();
        }
      });
    }
    const key = (e: KeyboardEvent) => {
      element.dataset.keyboard = 'true';
      if (e.key === 'Escape') closeMenus(null, true);
    };
    const pointer = (e: PointerEvent) => {
      element.dataset.keyboard = 'false';
      closeMenus(e.target);
    };
    const clicked = (e: MouseEvent) => {
      if (
        e.target instanceof Element &&
        e.target.closest('.nav-disclosure a, .nav-disclosure button')
      )
        closeMenus();
    };
    document.addEventListener('keydown', key);
    document.addEventListener('pointerdown', pointer);
    element.addEventListener('click', clicked);
    return () => {
      document.removeEventListener('keydown', key);
      document.removeEventListener('pointerdown', pointer);
      element.removeEventListener('click', clicked);
    };
  }, []);
  useEffect(() => {
    const meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
    const original = meta?.content;
    if (meta) meta.content = { cream: '#f3f0e8', dark: '#191612', sage: '#f2eee4' }[theme];
    return () => {
      if (meta && original !== undefined) meta.content = original;
    };
  }, [theme]);
  const links = publicPage ? (
    <>
      <a href="/#home">Home</a>
      <a href="/#brand-identity">Brand Identity</a>
      <a href="/#about">About Us</a>
      <a href="/workspaces/">Workspace</a>
      <a href="/sign-in/">Account</a>
    </>
  ) : (
    navigation
  );
  return (
    <div
      ref={root}
      className={`theme-surface ${publicPage ? 'public-site' : 'workspace-site'}`}
      data-theme={theme}
    >
      <div className="fixed-frame" aria-hidden="true" />
      <div className="app-shell">
        <header ref={header} className="topbar">
          <Wordmark />
          {workspaceControl}
          {links && (
            <>
              <nav
                className="desktop-nav"
                aria-label={publicPage ? 'Public navigation' : 'Workspace sections'}
              >
                {links}
              </nav>
              <details className="mobile-nav nav-disclosure">
                <summary>
                  {publicPage ? 'Menu' : 'Sections'} <span aria-hidden="true">›</span>
                </summary>
                <nav
                  aria-label={publicPage ? 'Mobile public navigation' : 'Mobile workspace sections'}
                >
                  {links}
                </nav>
              </details>
            </>
          )}
          <div className="account">
            {accountControl ?? (!publicPage && <a href="/sign-in/">Account</a>)}
          </div>
        </header>
        {children}
        <footer className="panel-foot">
          {publicPage ? (
            <div className="public-contact">
              <span className="footer-brand">Cadence</span>
              <address>
                hello@cadence.example
                <br />
                24 Orbit Lane, Example City
              </address>
              <span>Demo contact details — not monitored.</span>
            </div>
          ) : (
            <span>{footer}</span>
          )}
          <label className="theme-picker">
            <span>Appearance</span>
            <select
              value={theme}
              onChange={(e) => {
                if (themes.includes(e.target.value as Theme)) setTheme(e.target.value as Theme);
              }}
            >
              {themes.map((t) => (
                <option key={t} value={t}>
                  {t[0].toUpperCase() + t.slice(1)}
                </option>
              ))}
            </select>
          </label>
        </footer>
      </div>
    </div>
  );
}
