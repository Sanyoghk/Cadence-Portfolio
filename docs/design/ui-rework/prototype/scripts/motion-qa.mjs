import assert from "node:assert/strict";
import { mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { Browser } from "../../../../../.agents/skills/design-taste-frontend/scripts/lib/cdp.mjs";
const base = process.argv[2] ?? "http://127.0.0.1:4176/";
assert.equal(new URL(base).hostname, "127.0.0.1");
const output = resolve(
  import.meta.dirname,
  process.argv[3] ?? "../../craft/motion",
);
mkdirSync(output, { recursive: true });
const results = [],
  requests = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const browser = await Browser.launch();
let page, gsapURL;
async function test(name, fn) {
  await fn();
  results.push({ name, result: "PASS" });
}
async function pointer(selector) {
  const r = await page.eval((selector) => {
    const e = document.querySelector(selector);
    e?.scrollIntoView({ block: "center" });
    if (!e) return null;
    const r = e.getBoundingClientRect();
    return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
  }, selector);
  assert.ok(r, selector);
  await page.send("Input.dispatchMouseEvent", { type: "mouseMoved", ...r });
  await page.send("Input.dispatchMouseEvent", {
    type: "mousePressed",
    button: "left",
    clickCount: 1,
    ...r,
  });
  await page.send("Input.dispatchMouseEvent", {
    type: "mouseReleased",
    button: "left",
    clickCount: 1,
    ...r,
  });
  await sleep(25);
}
async function route(view) {
  await page.eval((v) => {
    location.hash = v === "home" ? "#home" : "#workspace/" + v;
  }, view);
  await sleep(160);
}
async function inspect() {
  return page.eval(async (url) => {
    const { gsap } = await import(url);
    const globals = gsap.core.globals();
    return {
      version: gsap.version,
      triggers: globals.ScrollTrigger.getAll().length,
      animations: gsap.globalTimeline.getChildren(true, true, true).length,
      active: gsap.globalTimeline
        .getChildren(true, true, true)
        .filter((t) => t.isActive()).length,
      split: document.querySelectorAll(".hero h1 [aria-hidden]").length,
    };
  }, gsapURL);
}
async function screenshot(name) {
  await page.screenshot({ path: resolve(output, name + ".jpg"), quality: 88 });
}
try {
  page = await browser.newPage({ width: 1440, height: 1024 });
  await page.send("Network.enable");
  browser.on(page.sessionId, "Network.requestWillBeSent", (e) =>
    requests.push(e.request.url),
  );
  await page.goto(base, { settleMs: 100 });
  await sleep(3100);
  gsapURL = requests.find((u) => /\/deps\/gsap\.js\?/.test(u));
  assert.ok(gsapURL, "Observed GSAP core module URL");
  await test("SplitText animates the real CADENCE heading without changing its accessible label", async () => {
    const d = await page.eval(() => ({
      label: document.querySelector(".hero h1").getAttribute("aria-label"),
      text: document.querySelector(".hero h1").textContent,
      chars: document.querySelector(".hero h1").children.length,
    }));
    assert.equal(d.label, "CADENCE");
    assert.equal(d.text, "CADENCE");
    assert.equal(d.chars, 7);
    assert.equal(
      await page.eval(
        () => document.querySelector(".hero-copy .button").style.transform,
      ),
      "",
      "CTA press feedback must not compete with a GSAP transform",
    );
    await screenshot("desktop-hero-mid-motion");
  });
  await sleep(1500);
  await test("SplitText reverts its markup after entrance; base heading remains visible", async () => {
    const d = await page.eval(() => ({
      children: document.querySelector(".hero h1").children.length,
      opacity: getComputedStyle(document.querySelector(".hero h1")).opacity,
    }));
    assert.equal(d.children, 0);
    assert.equal(d.opacity, "1");
  });
  await screenshot("desktop-hero-settled");
  await test("ScrollTrigger owns the public reveals and pricing line", async () => {
    assert.ok((await inspect()).triggers >= 8);
    await page.wheel(1600);
    await sleep(800);
    assert.equal(
      await page.eval(() => document.documentElement.scrollWidth > innerWidth),
      false,
    );
    await screenshot("desktop-pricing-scroll");
  });
  await test("text rises inside a stationary mask; a stalled tween cannot leave it clipped", async () => {
    const sample = await page.eval(async (url) => {
      const { gsap } = await import(url);
      const heading = document.querySelector(".about-section h2");
      heading.scrollIntoView({ block: "center" });
      await new Promise((resolve) =>
        requestAnimationFrame(() => requestAnimationFrame(resolve)),
      );
      const inner = heading.querySelector(".text-reveal-inner");
      const tween = gsap.getTweensOf(inner)[0];
      if (!tween) return null;
      const top = heading.getBoundingClientRect().top;
      tween.pause().progress(0.25);
      return {
        y: new DOMMatrix(getComputedStyle(inner).transform).m42,
        layoutShift: heading.getBoundingClientRect().top - top,
        overflow: getComputedStyle(inner.parentElement).overflow,
        text: heading.textContent,
      };
    }, gsapURL);
    assert.ok(sample);
    assert.ok(sample.y > 0, "text starts below its final baseline");
    assert.equal(sample.layoutShift, 0);
    assert.equal(sample.overflow, "clip");
    assert.equal(sample.text, "About us");
    await screenshot("text-mask-mid-motion");
    await sleep(1000);
    assert.equal(
      await page.eval(
        () =>
          document.querySelector(".about-section h2 .text-reveal-inner").style
            .transform,
      ),
      "",
      "independent watchdog restores text even when GSAP is paused",
    );
  });
  await test("keyboard input finishes text immediately and a following pointer cannot rewind it", async () => {
    await page.eval(() =>
      document
        .querySelector(".footer-name")
        .scrollIntoView({ block: "center" }),
    );
    await sleep(40);
    await page.send("Input.dispatchKeyEvent", {
      type: "keyDown",
      key: "Tab",
      code: "Tab",
      windowsVirtualKeyCode: 9,
    });
    await page.send("Input.dispatchKeyEvent", {
      type: "keyUp",
      key: "Tab",
      code: "Tab",
      windowsVirtualKeyCode: 9,
    });
    await page.eval(() =>
      document.body.dispatchEvent(
        new PointerEvent("pointerdown", { bubbles: true }),
      ),
    );
    assert.equal(
      await page.eval(
        () =>
          document.querySelector(".footer-name .text-reveal-inner").style
            .transform,
      ),
      "",
    );
    assert.equal(
      await page.eval(async (url) => {
        const { gsap } = await import(url);
        return gsap.getTweensOf(
          document.querySelector(".footer-name .text-reveal-inner"),
        ).length;
      }, gsapURL),
      0,
    );
  });
  await test("leaving public page removes its ScrollTriggers; repeated mounts do not accumulate timelines", async () => {
    await route("content");
    await sleep(400);
    const first = await inspect();
    assert.equal(first.triggers, 0);
    for (let n = 0; n < 6; n++) {
      await route("home");
      await route("content");
    }
    await sleep(1600);
    const after = await inspect();
    assert.equal(after.triggers, 0);
    assert.equal(after.animations, first.animations);
  });
  await test("desktop Calendar ↔ list uses Flip transforms and settles to natural layout", async () => {
    await pointer(".segmented button:nth-child(2)");
    const flipping = await page.eval(async (url) => {
      const { gsap } = await import(url);
      return [...document.querySelectorAll("[data-flip-id]")].some((e) =>
        gsap.core.globals().Flip.isFlipping(e),
      );
    }, gsapURL);
    assert.equal(flipping, true);
    await sleep(300);
    assert.equal(
      await page.eval(
        () => document.querySelectorAll(".post-list button").length,
      ),
      4,
    );
    assert.equal(
      await page.eval(() =>
        [...document.querySelectorAll("[data-flip-id]")].some(
          (e) => e.style.transform && e.style.transform !== "none",
        ),
      ),
      false,
    );
    await screenshot("desktop-post-list");
  });
  await test("rapid alternating pointer actions keep the last content mode and no stale transform", async () => {
    for (let n = 0; n < 9; n++)
      await pointer(
        n % 2 === 0
          ? ".segmented button:first-child"
          : ".segmented button:nth-child(2)",
      );
    await sleep(400);
    assert.equal(
      await page.eval(() =>
        document
          .querySelector(".segmented [aria-pressed=true]")
          .textContent.trim(),
      ),
      "Calendar",
    );
    assert.equal(
      await page.eval(() => document.querySelectorAll(".calendar-post").length),
      4,
    );
    assert.equal(
      await page.eval(() =>
        [...document.querySelectorAll("[data-flip-id]")].some(
          (e) => e.style.transform && e.style.transform !== "none",
        ),
      ),
      false,
    );
  });
  await test("keyboard content toggle is immediate and creates no Flip animation", async () => {
    await page.eval(() =>
      document.querySelector(".segmented button:nth-child(2)").focus(),
    );
    await page.send("Input.dispatchKeyEvent", {
      type: "keyDown",
      text: "\r",
      key: "Enter",
      code: "Enter",
      windowsVirtualKeyCode: 13,
    });
    await page.send("Input.dispatchKeyEvent", {
      type: "keyUp",
      key: "Enter",
      code: "Enter",
      windowsVirtualKeyCode: 13,
    });
    await sleep(30);
    assert.equal(
      await page.eval(() => !!document.querySelector(".post-list")),
      true,
    );
    assert.equal(
      await page.eval(async (url) => {
        const { gsap } = await import(url);
        return [...document.querySelectorAll("[data-flip-id]")].some((e) =>
          gsap.core.globals().Flip.isFlipping(e),
        );
      }, gsapURL),
      false,
    );
  });
  await test("native menu can be rapidly opened, closed and dismissed without orphaned styles", async () => {
    for (let n = 0; n < 5; n++) await pointer(".workspace-switch summary");
    await sleep(240);
    assert.equal(
      await page.eval(() => document.querySelector(".workspace-switch").open),
      true,
    );
    await page.send("Input.dispatchKeyEvent", {
      type: "keyDown",
      key: "Escape",
      code: "Escape",
      windowsVirtualKeyCode: 27,
    });
    await sleep(80);
    assert.equal(
      await page.eval(() => document.querySelector(".workspace-switch").open),
      false,
    );
    assert.equal(
      await page.eval(
        () =>
          document.activeElement ===
          document.querySelector(".workspace-switch summary"),
      ),
      true,
    );
  });
  await test("modal preserves native focus boundary and Escape, including fast close during entry", async () => {
    await pointer(".desktop-account");
    assert.ok(await page.eval(() => document.querySelector("dialog").open));
    assert.ok(
      await page.eval(() =>
        document.querySelector("dialog").contains(document.activeElement),
      ),
    );
    await page.send("Input.dispatchKeyEvent", {
      type: "keyDown",
      key: "Escape",
      code: "Escape",
      windowsVirtualKeyCode: 27,
    });
    await sleep(60);
    assert.equal(
      await page.eval(() => !!document.querySelector("dialog")),
      false,
    );
  });
  await test("paused GSAP cannot leave menu or dialog content invisible", async () => {
    await page.eval(async (url) => {
      const { gsap } = await import(url);
      gsap.globalTimeline.pause();
    }, gsapURL);
    try {
      await pointer(".workspace-switch summary");
      await sleep(450);
      assert.equal(
        await page.eval(
          () =>
            getComputedStyle(document.querySelector(".switch-panel")).opacity,
        ),
        "1",
      );
      await pointer(".workspace-switch summary");
      await pointer(".desktop-account");
      await sleep(450);
      assert.equal(
        await page.eval(
          () => getComputedStyle(document.querySelector("dialog")).opacity,
        ),
        "1",
      );
      await page.send("Input.dispatchKeyEvent", {
        type: "keyDown",
        key: "Escape",
        code: "Escape",
        windowsVirtualKeyCode: 27,
      });
      await sleep(60);
    } finally {
      await page.eval(async (url) => {
        const { gsap } = await import(url);
        gsap.globalTimeline.resume();
      }, gsapURL);
    }
  });
  await route("home");
  await sleep(1600);
  await test("changing prefers-reduced-motion at runtime removes triggers, text splitting and motion styles", async () => {
    await page.send("Emulation.setEmulatedMedia", {
      features: [{ name: "prefers-reduced-motion", value: "reduce" }],
    });
    await sleep(100);
    assert.equal((await inspect()).triggers, 0);
    assert.equal(
      await page.eval(() =>
        [...document.querySelectorAll(".text-reveal-inner")].every(
          (node) => getComputedStyle(node).transform === "none",
        ),
      ),
      true,
    );
    assert.equal(
      await page.eval(() => document.querySelector(".hero h1").children.length),
      0,
    );
    assert.equal(
      await page.eval(
        () => getComputedStyle(document.querySelector(".plan-line")).clipPath,
      ),
      "none",
    );
  });
  await page.send("Emulation.setEmulatedMedia", {
    features: [{ name: "prefers-reduced-motion", value: "no-preference" }],
  });
  await sleep(1600);
  await pointer(".nav-mark");
  await page.send("Emulation.setCPUThrottlingRate", { rate: 4 });
  const performanceSample = await page.eval(async (url) => {
    const { gsap } = await import(url);
    const frames = [],
      longTasks = [];
    const start = performance.now();
    let previous = start;
    const observer = new PerformanceObserver((list) =>
      longTasks.push(
        ...list
          .getEntries()
          .map((e) => ({ duration: e.duration, start: e.startTime })),
      ),
    );
    observer.observe({ type: "longtask", buffered: false });
    let animatedSamples = 0;
    const timer = setInterval(() => {
      scrollBy(0, 85);
      if (
        gsap.globalTimeline
          .getChildren(true, true, true)
          .some((t) => t.isActive())
      )
        animatedSamples++;
    }, 80);
    await new Promise((resolve) => {
      function frame(now) {
        frames.push(now - previous);
        previous = now;
        if (now - start < 2000) requestAnimationFrame(frame);
        else resolve();
      }
      requestAnimationFrame(frame);
    });
    clearInterval(timer);
    observer.disconnect();
    frames.sort((a, b) => a - b);
    return {
      cpuThrottle: 4,
      animatedSamples,
      frames: frames.length,
      p95FrameMs: frames[Math.floor(frames.length * 0.95)],
      maxFrameMs: frames.at(-1),
      longTasks: longTasks.filter((e) => e.start >= start),
    };
  }, gsapURL);
  await page.send("Emulation.setCPUThrottlingRate", { rate: 1 });
  assert.ok(performanceSample.frames > 20);
  assert.ok(
    performanceSample.animatedSamples > 0,
    "Profile must include active GSAP motion",
  );
  writeFileSync(
    resolve(output, "performance-sample.json"),
    JSON.stringify(performanceSample, null, 2) + "\n",
  );
  await test("touch layout uses an immediate agenda switch and accessible native mobile menu", async () => {
    await page.setViewport({ width: 390, height: 844, mobile: true });
    await route("content");
    await pointer(".segmented button:nth-child(2)");
    await sleep(80);
    assert.equal(
      await page.eval(() =>
        [...document.querySelectorAll("[data-flip-id]")].some(
          (e) => e.style.transform && e.style.transform !== "none",
        ),
      ),
      false,
    );
    await pointer(".mobile-nav summary");
    await sleep(230);
    assert.equal(
      await page.eval(() => document.querySelector(".mobile-nav").open),
      true,
    );
    await screenshot("mobile-menu");
    await pointer(".mobile-nav summary");
    await pointer(".segmented button:first-child");
    await sleep(120);
    await screenshot("mobile-agenda");
    assert.equal(
      await page.eval(() => document.documentElement.scrollWidth > innerWidth),
      false,
    );
  });
  await test("unmounting while Flip is running cancels the old component animation", async () => {
    await page.setViewport({ width: 1440, height: 1024 });
    await pointer(".segmented button:nth-child(2)");
    await route("identity");
    await sleep(350);
    assert.equal((await inspect()).triggers, 0);
    assert.equal(
      await page.eval(() => !!document.querySelector(".post-list")),
      false,
    );
  });
  await test("an entrance tween exception restores the original heading and keeps the page usable", async () => {
    const result = await page.eval(async (url) => {
      const { gsap } = await import(url),
        original = gsap.fromTo;
      gsap.fromTo = () => {
        throw new Error("Injected QA entrance failure");
      };
      try {
        location.hash = "#home";
        await new Promise((resolve) => setTimeout(resolve, 200));
        const h = document.querySelector(".hero h1");
        return {
          text: h.textContent,
          children: h.children.length,
          opacity: getComputedStyle(h).opacity,
          form: !!document.querySelector(".source-form"),
        };
      } finally {
        gsap.fromTo = original;
      }
    }, gsapURL);
    assert.deepEqual(result, {
      text: "CADENCE",
      children: 0,
      opacity: "1",
      form: true,
    });
  });
  await route("content");
  await test("a Flip exception cannot prevent the requested content view or leave hidden cards", async () => {
    const result = await page.eval(async (url) => {
      const { gsap } = await import(url),
        Flip = gsap.core.globals().Flip,
        original = Flip.from;
      Flip.from = () => {
        throw new Error("Injected QA Flip failure");
      };
      try {
        document.querySelector(".segmented button:nth-child(2)").click();
        await new Promise((resolve) => setTimeout(resolve, 80));
        return {
          count: document.querySelectorAll(".post-list button").length,
          hidden: [...document.querySelectorAll("[data-flip-id]")].some(
            (e) => getComputedStyle(e).opacity === "0",
          ),
        };
      } finally {
        Flip.from = original;
      }
    }, gsapURL);
    assert.deepEqual(result, { count: 4, hidden: false });
  });
  const fallback = await browser.newPage({
    width: 390,
    height: 844,
    mobile: true,
  });
  await fallback.send("Network.enable");
  await fallback.send("Network.setBlockedURLs", { urls: ["*motion-engine*"] });
  await fallback.goto(base, { settleMs: 3400 });
  await test("blocked motion module leaves visible content, usable forms and native modal", async () => {
    assert.equal(
      await fallback.eval(() =>
        [...document.querySelectorAll(".text-reveal-inner")].every(
          (node) => getComputedStyle(node).transform === "none",
        ),
      ),
      true,
    );
    assert.equal(
      await fallback.eval(
        () => getComputedStyle(document.querySelector(".hero h1")).opacity,
      ),
      "1",
    );
    await fallback.eval(() =>
      document.querySelector(".source-form").requestSubmit(),
    );
    await sleep(1250);
    assert.equal(
      await fallback.eval(
        () => document.querySelectorAll(".pillar-preview article").length,
      ),
      3,
    );
    await fallback.eval(() =>
      document.querySelector(".pillar-preview .button").click(),
    );
    await sleep(50);
    assert.equal(
      await fallback.eval(() => document.querySelector("dialog").open),
      true,
    );
    assert.equal(
      await fallback.eval(
        () => getComputedStyle(document.querySelector(".plan-line")).clipPath,
      ),
      "none",
    );
    await fallback.screenshot({
      path: resolve(output, "mobile-without-motion.jpg"),
      quality: 85,
    });
  });
  await fallback.close();
  assert.deepEqual(page.consoleErrors, []);
  writeFileSync(
    resolve(output, "qa.json"),
    JSON.stringify(
      {
        result: "PASS",
        tests: results.length,
        results,
        consoleErrors: page.consoleErrors,
        physicalDeviceTested: false,
      },
      null,
      2,
    ) + "\n",
  );
  console.log(
    JSON.stringify({
      result: "PASS",
      tests: results.length,
      performanceSample,
    }),
  );
} catch (error) {
  writeFileSync(
    resolve(output, "qa.json"),
    JSON.stringify(
      { result: "FAIL", completed: results, error: error.stack },
      null,
      2,
    ),
  );
  throw error;
} finally {
  await browser.close();
}
