'use client';

/** Keep keyboard navigation independent from the application's hash route. */
export function SkipLink() {
  return (
    <a
      className="skip-link"
      href="#main-content"
      onClick={(event) => {
        const main = document.getElementById('main-content');
        if (!main) return;
        event.preventDefault();
        main.tabIndex = -1;
        main.focus({ preventScroll: true });
        main.scrollIntoView({ block: 'start', behavior: 'instant' });
      }}
    >
      Skip to content
    </a>
  );
}
