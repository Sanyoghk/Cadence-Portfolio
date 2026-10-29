import { RevealText } from "./TextReveal.jsx";
import React, { useEffect, useRef, useState } from "react";
import { Star, Button, Field, Pillars, useDemoAction } from "./ui.jsx";
import { useMotionScene } from "./motion-hooks.js";
export function Landing({ account, navigate, paused, setPaused, ready }) {
  const [preview, setPreview] = useState(false),
    [leaving, setLeaving] = useState(false),
    [file, setFile] = useState(false),
    [busy, run] = useDemoAction();
  const root = useRef(null),
    curtain = useRef(null);
  useMotionScene(root, "landing", ready);
  useMotionScene(
    curtain,
    "curtain",
    leaving ? "leaving" : preview ? "preview" : "form",
  );
  useEffect(() => {
    if (!leaving) return;
    const timer = setTimeout(() => {
      setPreview(true);
      setLeaving(false);
    }, 420);
    return () => clearTimeout(timer);
  }, [leaving]);
  return (
    <main ref={root} id="public-main">
      <section id="home" className="hero page-grid">
        <h1>CADENCE</h1>
        <div className="hero-bottom">
          <div className="hero-copy">
            <RevealText as="p" className="hero-tagline">
              Create YOUR brand.
            </RevealText>
            <RevealText as="p" className="hero-description">
              A thoughtful workspace for your brand, content and next chapter.
            </RevealText>
            <a className="button" href="#brand-identity">
              Explore your brand <span aria-hidden="true">→</span>
            </a>
            <RevealText as="p" className="demo-note">
              A design preview. Yours to explore with demo data.
            </RevealText>
          </div>
          <img
            className="hero-orbit"
            src="/assets/orbit.png"
            alt=""
            width="1696"
            height="960"
          />
        </div>
      </section>
      <section
        ref={curtain}
        id="brand-identity"
        className={
          "identity-curtain page-grid reveal " + (preview ? "is-open" : "")
        }
      >
        <div className="section-heading">
          <RevealText as="h2">Let’s read your brand.</RevealText>
          <RevealText as="p">
            Start with the things that make it yours.
          </RevealText>
        </div>
        <RevealText as="p" className="demo-inline">
          Demo preview · no files or links are sent
        </RevealText>
        {!preview ? (
          <form
            className={"source-form" + (leaving ? " is-leaving" : "")}
            data-ui
            onSubmit={(e) => {
              e.preventDefault();
              run(() =>
                matchMedia("(prefers-reduced-motion: reduce)").matches ||
                document.documentElement.dataset.keyboard === "true"
                  ? setPreview(true)
                  : setLeaving(true),
              );
            }}
          >
            <div className="upload-area">
              <Star />
              <RevealText as="h3">Bring your source material.</RevealText>
              <RevealText as="p">
                A brand book, your website, a few notes. See how the pieces
                could come together.
              </RevealText>
              <div className="demo-file">
                <strong>PDF</strong>
                <RevealText as="span">Sunday Clay · demo brand book</RevealText>
              </div>
              <Button secondary type="button" onClick={() => setFile(true)}>
                {file ? "Demo PDF selected" : "Browse demo files"}
              </Button>
              <RevealText as="p" className="meta">
                Demo source only. Real file uploads belong in your signed-in
                Workspace.
              </RevealText>
            </div>
            <div className="input-stack">
              <Field
                label="Website"
                type="url"
                placeholder="https://yourbrand.example"
                autoCapitalize="none"
                autoCorrect="off"
              />
              <div className="form-two">
                <Field
                  label="Instagram"
                  placeholder="@yourbrand"
                  autoCapitalize="none"
                />
                <Field
                  label="TikTok"
                  placeholder="@yourbrand"
                  autoCapitalize="none"
                />
              </div>
              <Field
                label="Competitors (optional)"
                placeholder="A brand you look up to"
                maxLength={120}
              />
              <Field label="Brand voice notes (optional)">
                <textarea
                  rows="3"
                  maxLength={500}
                  placeholder="How should your brand sound?"
                />
              </Field>
            </div>
            <div className="form-bottom">
              <RevealText as="p">
                Use Sunday Clay sample data. Your inputs are not analysed or
                saved.
              </RevealText>
              <Button disabled={busy || leaving}>
                {busy ? "Preparing demo preview…" : "Preview demo pillars"}{" "}
                <span aria-hidden="true">→</span>
              </Button>
            </div>
          </form>
        ) : (
          <div className="pillar-preview" data-ui>
            <RevealText as="p">
              Content pillar sample for <strong>Sunday Clay</strong>. Synthetic
              examples, not AI output.
            </RevealText>
            <Pillars />
            <div className="form-bottom">
              <button className="text-button" onClick={() => setPreview(false)}>
                Back to Brand Identity
              </button>
              <Button onClick={account}>
                Strategy playbook <span aria-hidden="true">→</span>
              </Button>
            </div>
          </div>
        )}
        <Star className="curtain-star-one" />
        <Star className="curtain-star-two" />
      </section>
      <section id="plans" className="plans-section page-grid">
        <div className="section-heading reveal">
          <RevealText as="h2">Find your rhythm.</RevealText>
          <RevealText as="p">
            Two proposed ways to grow. Pricing and plan details are still being
            shaped.
          </RevealText>
        </div>
        <div className="plan-columns">
          <img
            className="plan-line"
            src="/assets/scroll-line.png"
            alt=""
            width="800"
            height="2000"
          />
          {[
            [
              "Standard",
              "Room to find your voice and build a considered content routine.",
              [
                "Your Brand Identity, reviewed by you",
                "A Strategy playbook to work from",
                "A content calendar and CSV export",
              ],
            ],
            [
              "Plus",
              "For a deeper rhythm, with more room to explore your next chapter.",
              [
                "The same human-led creative workflow",
                "Expanded capacity is being defined",
                "Performance and recommendations",
              ],
            ],
          ].map(([name, copy, features]) => (
            <article className="plan reveal" key={name}>
              <Star />
              <RevealText as="h3" mode="group">
                {name}
              </RevealText>
              <RevealText as="p" mode="group">
                {copy}
              </RevealText>
              <ul>
                {features.map((f) => (
                  <RevealText as="li" mode="group" key={f}>
                    {f}
                  </RevealText>
                ))}
              </ul>
              <RevealText as="p" mode="group" className="plan-price">
                Price to be confirmed
              </RevealText>
              <Button secondary onClick={account}>
                Explore {name}
              </Button>
            </article>
          ))}
        </div>
        <RevealText as="p" className="plan-disclaimer">
          Concept plans · no checkout, payment or active subscription
        </RevealText>
      </section>
      <section id="about" className="about-section page-grid reveal">
        <RevealText as="h2">About us</RevealText>
        <div>
          <RevealText as="p" className="about-statement">
            More time for the work you love.
          </RevealText>
          <RevealText as="p">
            We’re building Cadence to help small and medium businesses find
            their brand’s voice, plan thoughtful content and understand what
            connects.
          </RevealText>
          <RevealText as="p">
            As developers, we want to make the routine lighter, so you can spend
            more time on your business and the creative decisions that stay
            yours.
          </RevealText>
          <button className="text-button" onClick={() => navigate("identity")}>
            Explore the demo workspace →
          </button>
        </div>
      </section>
      <footer className="public-footer page-grid">
        <div className="footer-top">
          <RevealText as="span">
            Created with intention. Reviewed by you.
          </RevealText>
          <a href="#home">Back to top</a>
          <button className="text-button" onClick={() => setPaused(!paused)}>
            {paused ? "Resume motion" : "Pause motion"}
          </button>
        </div>
        <RevealText as="p" className="footer-name">
          CADENCE
        </RevealText>
        <div className="footer-strip" aria-hidden="true">
          <div>
            {Array.from({ length: 18 }, (_, i) => (
              <Star key={i} />
            ))}
          </div>
        </div>
        <div className="footer-legal">
          <RevealText as="span">© 2026 Cadence · Design preview</RevealText>
          <RevealText as="span">
            Demo data only · No account required
          </RevealText>
        </div>
      </footer>
    </main>
  );
}
