import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { SplitText } from "gsap/SplitText";
import { Flip } from "gsap/Flip";
import { CustomEase } from "gsap/CustomEase";

gsap.registerPlugin(ScrollTrigger, SplitText, Flip, CustomEase);

const isKeyboard = () => document.documentElement.dataset.keyboard === "true";
const reduceMotion = () =>
  matchMedia("(prefers-reduced-motion: reduce)").matches;
let easeOut;
function out() {
  // Extend the existing CSS token; no parallel easing system.
  easeOut ??= CustomEase.create(
    "cadence-out",
    getComputedStyle(document.documentElement)
      .getPropertyValue("--ease-out")
      .trim()
      .replace(/^cubic-bezier\((.*)\)$/, "$1"),
  );
  return easeOut;
}
function seconds(token, fallback) {
  const value = getComputedStyle(document.documentElement)
    .getPropertyValue(token)
    .trim();
  const duration = Number.parseFloat(value) / (value.endsWith("ms") ? 1000 : 1);
  return Number.isFinite(duration)
    ? Math.min(0.3, Math.max(0, duration))
    : fallback;
}

function restoreMotionStyle(node) {
  node.style.removeProperty("opacity");
  node.style.removeProperty("transform");
}

function enter(
  nodes,
  context,
  own,
  { duration = 0.5, stagger = 0, y = 12, yPercent = 0, fade = true } = {},
) {
  const targets = gsap.utils.toArray(nodes);
  if (!targets.length || isKeyboard()) return;
  let tween;
  let timer;
  let settled = false;
  const settle = () => {
    if (settled) return;
    settled = true;
    clearTimeout(timer);
    document.removeEventListener("keydown", settle);
    try {
      tween?.progress(1);
      tween?.kill();
    } catch {
      // A motion failure must never leave readable content behind its mask.
    } finally {
      targets.forEach(restoreMotionStyle);
    }
  };
  // Install recovery before GSAP can touch a node. Also finish on keyboard input.
  own(settle);
  document.addEventListener("keydown", settle, { once: true });
  timer = setTimeout(
    settle,
    (duration + stagger * targets.length) * 1000 + 200,
  );
  try {
    context.add(() => {
      tween = gsap.fromTo(
        targets,
        { opacity: fade ? 0 : 1, y, yPercent },
        {
          opacity: 1,
          y: 0,
          yPercent: 0,
          duration,
          stagger,
          ease: out(),
          clearProps: "opacity,transform",
          overwrite: "auto",
          onComplete: settle,
        },
      );
    });
  } catch {
    settle();
  }
}

function text(root, context, own, reduced) {
  if (
    reduced ||
    isKeyboard() ||
    root.closest('[role="status"], [role="alert"], [aria-live]') ||
    root.querySelector("input, textarea, select, button, a, [contenteditable]")
  )
    return;
  const inner = root.querySelector(".text-reveal-inner");
  if (!inner) return;
  const working = !!root.closest("#workspace-main, dialog");
  const reveal = () =>
    enter(inner, context, own, {
      duration: working ? 0.24 : 0.7,
      y: 0,
      yPercent: 110,
      fade: false,
    });
  if (working) reveal();
  else
    ScrollTrigger.create({
      trigger: root,
      start: "top 94%",
      once: true,
      onEnter: reveal,
    });
}

function landing(root, context, own, reduced) {
  if (reduced) return;
  const title = root.querySelector(".hero h1");
  let split;
  const splitTimer = setTimeout(() => split?.revert(), 1400);
  own(() => {
    clearTimeout(splitTimer);
    split?.revert();
  });
  if (title && !isKeyboard()) {
    split = SplitText.create(title, {
      type: "chars",
      mask: "chars",
      tag: "span",
      aria: "auto",
    });
    enter(split.chars, context, own, {
      duration: 0.7,
      stagger: 0.04,
      y: 0,
      yPercent: 110,
      fade: false,
    });
    enter(root.querySelectorAll(".hero-orbit"), context, own, {
      duration: 0.7,
      stagger: 0.04,
      y: 12,
    });
  }
  const plans = root.querySelector(".plan-columns");
  if (plans) {
    ScrollTrigger.create({
      trigger: plans,
      start: "top 92%",
      once: true,
      // Shared timing preserves symmetry between Standard and Plus.
      onEnter: () =>
        enter(plans.querySelectorAll(".text-reveal-inner"), context, own, {
          duration: 0.7,
          y: 0,
          yPercent: 110,
          fade: false,
        }),
    });
  }
  const line = root.querySelector(".plan-line");
  if (line) {
    // Reveal only the decorative artwork; no layout changes or scroll pinning.
    gsap.fromTo(
      line,
      { clipPath: "inset(0 0 100% 0)" },
      {
        clipPath: "inset(0 0 0% 0)",
        ease: "none",
        scrollTrigger: {
          trigger: root.querySelector(".plan-columns"),
          start: "top 85%",
          end: "bottom 65%",
          scrub: 0.3,
          invalidateOnRefresh: true,
        },
      },
    );
    own(() => line.style.removeProperty("clip-path"));
  }
}

function header(root, context, own, reduced) {
  root.querySelectorAll("details").forEach((details) => {
    const panel = details.querySelector("nav, .switch-panel");
    const summary = details.querySelector("summary");
    if (!panel || !summary) return;
    let watchdog;
    const timeline = gsap.timeline({ paused: true }).fromTo(
      panel,
      { opacity: 0, y: -4, scale: 0.98 },
      {
        opacity: 1,
        y: 0,
        scale: 1,
        duration: seconds("--ui", 0.18),
        ease: out(),
        immediateRender: false,
        clearProps: "opacity,transform",
      },
    );
    const toggle = () => {
      clearTimeout(watchdog);
      if (!details.open) {
        timeline.pause();
        restoreMotionStyle(panel);
        return;
      }
      if (reduced || isKeyboard()) {
        timeline.pause();
        restoreMotionStyle(panel);
        return;
      }
      const s = summary.getBoundingClientRect(),
        p = panel.getBoundingClientRect();
      panel.style.transformOrigin = `${Math.max(0, Math.min(p.width, s.left + s.width / 2 - p.left))}px top`;
      timeline.restart();
      watchdog = setTimeout(
        () => {
          timeline.pause();
          restoreMotionStyle(panel);
        },
        seconds("--ui", 0.18) * 1000 + 200,
      );
    };
    details.addEventListener("toggle", toggle);
    own(() => {
      details.removeEventListener("toggle", toggle);
      clearTimeout(watchdog);
      timeline.kill();
      restoreMotionStyle(panel);
      panel.style.removeProperty("transform-origin");
    });
  });
}

function curtain(root, context, own, reduced, stage) {
  if (isKeyboard() || reduced) return;
  if (stage === "leaving") {
    const form = root.querySelector(".source-form");
    if (!form) return;
    gsap.to(form, { opacity: 0, scale: 1.06, duration: 0.42, ease: out() });
    own(() => restoreMotionStyle(form));
  }
}

// Every scene gets its own matchMedia context. No global killAll/killTweensOf.
/**
 * @param {"landing" | "header" | "curtain" | "dialog" | "text"} scene
 * @param {HTMLElement} root
 * @param {string | boolean} key
 */
export function mountScene(scene, root, key) {
  const media = gsap.matchMedia();
  try {
    media.add(
      {
        reduced: "(prefers-reduced-motion: reduce)",
        normal: "(prefers-reduced-motion: no-preference)",
      },
      (context) => {
        const cleanups = [];
        const own = (fn) => cleanups.push(fn);
        const finish = () => {
          cleanups
            .splice(0)
            .reverse()
            .forEach((fn) => fn());
        };
        try {
          const reduced = !!context.conditions.reduced;
          if (scene === "text" && key) text(root, context, own, reduced);
          if (scene === "landing" && key) landing(root, context, own, reduced);
          if (scene === "header") header(root, context, own, reduced);
          if (scene === "curtain") curtain(root, context, own, reduced, key);
          if (scene === "dialog" && !isKeyboard())
            enter(root, context, own, {
              duration: reduced ? 0.12 : seconds("--motion-dialog", 0.2),
              y: reduced ? 0 : 8,
            });
          root.dataset.motionScene = scene;
        } catch {
          // Progressive enhancement: restore base DOM and keep all controls live.
          context.revert();
          finish();
        }
        return () => {
          finish();
          delete root.dataset.motionScene;
        };
      },
      root,
    );
  } catch {
    media.revert();
  }
  return () => media.revert();
}

/** @param {HTMLElement} root */
export function createContentFlip(root) {
  const context = gsap.context(() => {}, root);
  let animation;
  const allowed = () =>
    !reduceMotion() &&
    !isKeyboard() &&
    matchMedia("(hover: hover) and (pointer: fine)").matches;
  const settle = () => {
    animation?.progress(1);
    animation?.kill();
    animation = null;
  };
  const reduced = matchMedia("(prefers-reduced-motion: reduce)");
  reduced.addEventListener("change", settle);
  return {
    capture() {
      if (!allowed()) {
        settle();
        return null;
      }
      try {
        const targets = root.querySelectorAll("[data-flip-id]");
        // Public API: stop without jumping to the old destination, then measure.
        Flip.killFlipsOf(targets, false);
        const state = Flip.getState(targets);
        return state;
      } catch {
        settle();
        return null;
      }
    },
    play(state) {
      if (!state || !allowed()) {
        settle();
        return;
      }
      try {
        context.add(() => {
          animation = Flip.from(state, {
            targets: root.querySelectorAll("[data-flip-id]"),
            duration: seconds("--motion-layout", 0.22),
            ease: out(),
            scale: true,
            absolute: false,
            prune: true,
            clearProps: "transform,opacity",
            overwrite: true,
          });
        });
      } catch {
        settle();
        context.revert();
      }
    },
    dispose() {
      reduced.removeEventListener("change", settle);
      animation?.kill();
      context.revert();
    },
  };
}
