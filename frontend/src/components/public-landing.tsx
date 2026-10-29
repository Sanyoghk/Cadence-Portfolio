'use client';

import { OrbitStar } from './ui';

/** Public information, never an anonymous source collector or a demo operation. */
export function PublicLanding() {
  return (
    <main id="main-content" className="public-main" tabIndex={-1}>
      <section id="home" className="hero page-grid">
        <h1>CADENCE</h1>
        <div className="hero-bottom">
          <div className="hero-copy">
            <p className="hero-tagline">Create YOUR brand.</p>
            <p className="hero-description">
              A thoughtful workspace for your brand, content and next chapter.
            </p>
            <p className="meta">Your sources. Your review. Your creative decisions.</p>
          </div>
          <img
            className="hero-orbit"
            src="/assets/orbit.png"
            alt=""
            width="1696"
            height="960"
            fetchPriority="high"
          />
        </div>
      </section>
      <section id="brand-identity" className="identity-curtain page-grid">
        <div className="public-section-heading">
          <h2>Let’s read your brand.</h2>
          <p>Start with the things that make it yours.</p>
        </div>
        <div className="public-source-grid">
          <div className="public-upload">
            <OrbitStar />
            <h3>Bring your source material.</h3>
            <p>A brand book, your website, a few notes. Give your voice a place to begin.</p>
            <p className="meta">
              PDF, PNG or JPEG up to 10 MiB in your signed-in Workspace. Visual analysis requires a
              qualified provider.
            </p>
            <a className="button" href="/workspaces/">
              Open your Workspace <span aria-hidden="true">→</span>
            </a>
          </div>
          <div className="public-process">
            <h3>Keep the decisions yours.</h3>
            <ol>
              <li>
                <strong>Add sources.</strong>
                <p>Register material privately within your Workspace and Brand.</p>
              </li>
              <li>
                <strong>Review the exact snapshot.</strong>
                <p>Choose what may be used. Consent can be withdrawn.</p>
              </li>
              <li>
                <strong>Shape your Identity.</strong>
                <p>Edit a draft, check its citations and approve it separately.</p>
              </li>
            </ol>
            <a href="/sign-up/">Create an account</a>
          </div>
        </div>
        <p className="meta">
          Sign in before sharing material. This public page does not upload, analyse or save
          sources. Live AI adapters are being qualified.
        </p>
      </section>
      <section id="plans" className="plans-section page-grid">
        <div className="public-section-heading">
          <h2>Find your rhythm.</h2>
          <p>Two proposed ways to grow. Pricing and plan details are still being shaped.</p>
        </div>
        <div className="plan-columns">
          <img
            className="plan-line"
            src="/assets/scroll-line.png"
            alt=""
            width="800"
            height="2000"
            loading="lazy"
          />
          {[
            {
              name: 'Standard',
              copy: 'Room to find your voice and build a considered content routine.',
              features: [
                'Your Brand Identity, reviewed by you',
                'A Strategy playbook to work from',
                'A content calendar and CSV export',
              ],
            },
            {
              name: 'Plus',
              copy: 'For a deeper rhythm, with more room to explore your next chapter.',
              features: [
                'The same human-led creative workflow',
                'Expanded capacity is being defined',
                'Performance and recommendations',
              ],
            },
          ].map(({ name, copy, features }) => (
            <article className="plan" key={name}>
              <OrbitStar />
              <h3>{name}</h3>
              <p>{copy}</p>
              <ul>
                {features.map((f) => (
                  <li key={f}>{f}</li>
                ))}
              </ul>
              <p className="plan-price">Price to be confirmed</p>
              <a className="button primary" href="/sign-up/">
                Create an account
              </a>
            </article>
          ))}
        </div>
        <p className="plan-disclaimer">
          Concept plans. No checkout, payment or subscription is offered here. Current account
          access does not depend on a plan.
        </p>
      </section>
      <section id="about" className="about-section page-grid">
        <h2>About Us</h2>
        <div>
          <p className="about-statement">
            A little clarity.
            <br />A steady creative rhythm.
          </p>
          <p>
            Cadence is a home for independent creators and entrepreneurs to bring their Brand, ideas
            and next chapter together.
          </p>
          <p>
            From source material to reviewed decisions, you remain the author of what comes next.
          </p>
          <a className="button" href="/workspaces/">
            Start with your Brand <span aria-hidden="true">→</span>
          </a>
        </div>
      </section>
    </main>
  );
}
