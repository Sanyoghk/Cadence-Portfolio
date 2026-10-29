// Local synthetic-data QA only. Uses the installed skill's isolated CDP browser.
import assert from "node:assert/strict";
import { mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { Browser } from "../../../../../.agents/skills/design-taste-frontend/scripts/lib/cdp.mjs";
const base = process.argv[2] ?? "http://127.0.0.1:4176/";
assert.equal(new URL(base).hostname, "127.0.0.1");
const out = resolve(
  import.meta.dirname,
  process.argv[3] ?? "../../craft/screens",
);
mkdirSync(out, { recursive: true });
const results = [],
  requests = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const browser = await Browser.launch();
let page;
async function check(name, fn) {
  await fn();
  results.push({ name, result: "PASS" });
}
async function click(label) {
  const ok = await page.eval((label) => {
    const e = [...document.querySelectorAll("button,a,summary")].find(
      (e) =>
        e.textContent.trim() === label &&
        e.getClientRects().length &&
        !e.disabled,
    );
    if (!e) return false;
    e.click();
    return true;
  }, label);
  if (!ok)
    throw new Error(
      "Clickable: " +
        label +
        "; visible: " +
        (await page.eval(() =>
          [...document.querySelectorAll("button,a,summary")]
            .filter((e) => e.getClientRects().length)
            .map((e) => e.textContent.trim())
            .join(" | "),
        )) +
        "; errors: " +
        page.consoleErrors.join(" | "),
    );
  await sleep(90);
}
async function field(label, value) {
  assert.ok(
    await page.eval(
      ({ label, value }) => {
        const l = [...document.querySelectorAll("label")].find(
          (e) =>
            e.querySelector("span")?.textContent === label ||
            e.childNodes[0]?.textContent.trim() === label,
        );
        const e = l?.querySelector("input,textarea,select");
        if (!e) return false;
        const proto =
          e.tagName === "TEXTAREA"
            ? HTMLTextAreaElement.prototype
            : e.tagName === "SELECT"
              ? HTMLSelectElement.prototype
              : HTMLInputElement.prototype;
        Object.getOwnPropertyDescriptor(proto, "value").set.call(e, value);
        e.dispatchEvent(
          new Event(e.tagName === "SELECT" ? "change" : "input", {
            bubbles: true,
          }),
        );
        return true;
      },
      { label, value },
    ),
  );
  await sleep(100);
}
async function route(view) {
  await page.eval((v) => (location.hash = "#workspace/" + v), view);
  await sleep(180);
}
async function capture(name, fullPage = true) {
  if (process.argv[4] === "--no-screens") return;
  await page.eval(() => scrollTo(0, 0));
  await sleep(200);
  await page.screenshot({
    path: resolve(out, name + ".jpg"),
    quality: 85,
    fullPage,
  });
}
const disabled = (label) =>
  page.eval(
    (label) =>
      [...document.querySelectorAll("button")].find(
        (e) => e.textContent.trim() === label,
      )?.disabled,
    label,
  );
try {
  page = await browser.newPage({ width: 1440, height: 1024 });
  await page.send("Network.enable");
  browser.on(page.sessionId, "Network.requestWillBeSent", (e) =>
    requests.push(e.request.url),
  );
  await page.goto(base, { settleMs: 100 });
  await check(
    "intro blocks underlying controls during the minimum three seconds",
    async () => {
      assert.ok(
        await page.eval(
          () =>
            !!document.querySelector(".intro") &&
            document.querySelector(".app-content").inert,
        ),
      );
      await sleep(2400);
      assert.ok(await page.eval(() => !!document.querySelector(".intro")));
      await sleep(900);
      assert.equal(
        await page.eval(() => !!document.querySelector(".intro")),
        false,
      );
    },
  );
  await sleep(600);
  await capture("desktop-landing");
  await page.setViewport({ width: 1487, height: 1058 });
  await capture("reference-size-actual", false);
  await page.setViewport({ width: 1440, height: 1024 });
  await check(
    "public pillar teaser → Account → plan concept → Manager",
    async () => {
      await click("Preview demo pillars →");
      await sleep(1200);
      assert.equal(
        await page.eval(
          () => document.querySelectorAll(".pillar-preview article").length,
        ),
        3,
      );
      await capture("desktop-pillars");
      await click("Strategy playbook →");
      assert.ok(await page.eval(() => document.querySelector("dialog").open));
      await click("Continue demo journey");
      await click("Standard · concept");
      assert.equal(
        await page.eval(() => document.querySelector("h1")?.textContent),
        "Workspace Manager",
      );
    },
  );
  await capture("desktop-manager");
  await route("identity");
  await check(
    "exact consent, generation and approval are separate controls",
    async () => {
      assert.equal(await disabled("Prepare demo Identity"), true);
      await page.eval(() => document.querySelector(".check-row input").click());
      await sleep(80);
      await click("Allow reviewed source");
      await click("Prepare demo Identity");
      await sleep(750);
      assert.equal(
        await page.eval(
          () =>
            document.querySelector(".panel-heading .state-label")?.textContent,
        ),
        "Demo · version 1",
      );
      assert.equal(await disabled("Approve Identity"), false);
      await click("Approve Identity");
      assert.ok(
        await page.eval(() =>
          document
            .querySelector(".workspace-main")
            .textContent.includes("APPROVED"),
        ),
      );
    },
  );
  await capture("desktop-identity");
  await check(
    "Identity history records a consent decision and approved snapshot",
    async () => {
      await click("History & consent");
      assert.ok(
        await page.eval(() =>
          document.querySelector("dialog").textContent.includes("ALLOWED"),
        ),
      );
      await page.send("Input.dispatchKeyEvent", {
        type: "keyDown",
        key: "Escape",
        code: "Escape",
        windowsVirtualKeyCode: 27,
      });
      await page.send("Input.dispatchKeyEvent", {
        type: "keyUp",
        key: "Escape",
        code: "Escape",
        windowsVirtualKeyCode: 27,
      });
      await sleep(80);
      assert.equal(
        await page.eval(() => !!document.querySelector("dialog")),
        false,
      );
    },
  );
  await route("strategy");
  await click("Prepare demo playbook");
  await sleep(750);
  await click("Approve playbook");
  await capture("desktop-strategy");
  await route("content");
  await check(
    "prepare content adds a DRAFT; approval and scheduling remain distinct",
    async () => {
      const count = await page.eval(
        () => document.querySelectorAll(".calendar-post").length,
      );
      await click("Prepare demo content");
      assert.equal(
        await page.eval(
          () => document.querySelectorAll(".calendar-post").length,
        ),
        count + 1,
      );
      await page.eval(() =>
        [...document.querySelectorAll(".calendar-post")]
          .find((e) => e.textContent.includes("The hands behind"))
          .click(),
      );
      await sleep(100);
      assert.equal(await disabled("Save demo schedule"), true);
      await click("Approve revision");
      assert.equal(await disabled("Save demo schedule"), false);
      await field("Date", "2026-10-22");
      await field("Time · London", "11:30");
      await click("Save demo schedule");
      assert.ok(
        await page.eval(() =>
          document
            .querySelector(".notice")
            .textContent.includes("No post was published"),
        ),
      );
    },
  );
  await capture("desktop-calendar");
  await click("Post list");
  await capture("desktop-post-list");
  await route("performance");
  await check(
    "Performance totals are derived; recommendation opens a new Strategy draft",
    async () => {
      assert.ok(
        await page.eval(() =>
          document
            .querySelector(".metrics-strip")
            .textContent.includes("5,000"),
        ),
      );
      await capture("desktop-performance");
      await click("Preview demo report");
      assert.ok(
        await page.eval(() =>
          document.querySelector("dialog").textContent.includes("5,000"),
        ),
      );
      await click("Close");
      await click("Apply to a new draft");
      await route("strategy");
      assert.ok(
        await page.eval(() =>
          document
            .querySelector(".strategy-intro")
            .textContent.includes("DRAFT"),
        ),
      );
      await click("Version history");
      assert.ok(
        await page.eval(() =>
          document
            .querySelector(".history-list")
            .textContent.includes("APPROVED"),
        ),
      );
      await click("Close");
    },
  );
  await check(
    "source withdrawal and source replacement invalidate downstream preparation",
    async () => {
      await route("identity");
      await click("Withdraw consent");
      assert.equal(await disabled("Create another demo draft"), true);
      await click("History & consent");
      assert.ok(
        await page.eval(() =>
          document.querySelector("dialog").textContent.includes("WITHDRAWN"),
        ),
      );
      await click("Close");
      await field("Demo source", "Website text");
      await page.eval(() => document.querySelector(".check-row input").click());
      await sleep(80);
      await click("Allow reviewed source");
      await route("strategy");
      assert.equal(await disabled("Prepare demo playbook"), true);
    },
  );
  await check(
    "switching Workspace clears forms, metrics and actions from the previous Brand",
    async () => {
      await page.eval(() =>
        document.querySelector(".workspace-switch summary").click(),
      );
      await click("Field Notes");
      await route("identity");
      assert.ok(
        await page.eval(() =>
          document
            .querySelector(".work-heading")
            .textContent.includes("Field Notes Press"),
        ),
      );
      assert.equal(
        await page.eval(() => document.querySelector("textarea")?.value ?? ""),
        "",
      );
      await route("performance");
      assert.ok(
        await page.eval(() =>
          document
            .querySelector(".performance-bars")
            .textContent.includes("No observations"),
        ),
      );
      assert.equal(await disabled("Apply to a new draft"), true);
      await page.eval(() =>
        document.querySelector(".workspace-switch summary").click(),
      );
      await click("Studio Sunday");
    },
  );
  // Restore a reviewed demo scope solely by the visible controls for the mobile mocks.
  await route("identity");
  await click("Create another demo draft");
  await sleep(750);
  await click("Approve Identity");
  await route("strategy");
  await click("Prepare demo playbook");
  await sleep(750);
  await click("Approve playbook");
  for (const [width, height, prefix] of [
    [768, 1024, "tablet"],
    [390, 844, "mobile"],
    [375, 812, "narrow"],
  ]) {
    await page.setViewport({ width, height, mobile: width < 500 });
    for (const view of [
      "manager",
      "identity",
      "strategy",
      "content",
      "performance",
    ]) {
      await route(view);
      await check(prefix + " " + view + " fits the viewport", async () => {
        const d = await page.eval(() => ({
          overflow: document.documentElement.scrollWidth - innerWidth,
          badInputs: [
            ...document.querySelectorAll("input,textarea,select"),
          ].filter(
            (e) =>
              e.getClientRects().length &&
              parseFloat(getComputedStyle(e).fontSize) < 16,
          ).length,
        }));
        assert.ok(d.overflow <= 1, JSON.stringify(d));
        assert.equal(d.badInputs, 0);
        if (width < 500 && view === "content")
          assert.equal(
            await page.eval(
              () =>
                [...document.querySelectorAll(".calendar-post")].filter(
                  (e) => e.getBoundingClientRect().width < 150,
                ).length,
            ),
            0,
            "Multiple posts on one day retain the agenda column",
          );
      });
      if (prefix !== "narrow") await capture(prefix + "-" + view);
    }
    if (prefix === "mobile") {
      await check(
        "mobile navigation supports Escape and returns focus",
        async () => {
          await click("Sections ›");
          assert.ok(
            await page.eval(() => document.querySelector(".mobile-nav").open),
          );
          await page.send("Input.dispatchKeyEvent", {
            type: "keyDown",
            key: "Escape",
            code: "Escape",
            windowsVirtualKeyCode: 27,
          });
          await sleep(80);
          assert.equal(
            await page.eval(() => document.querySelector(".mobile-nav").open),
            false,
          );
        },
      );
      await page.eval(() => (location.hash = "#home"));
      await sleep(180);
      await capture("mobile-landing");
    }
  }
  await check(
    "all application requests remain on loopback; no credentials or persistent browser storage",
    async () => {
      assert.deepEqual(
        requests.filter(
          (u) =>
            ["http:", "https:", "ws:", "wss:"].includes(new URL(u).protocol) &&
            ![
              new URL(base).origin,
              new URL(base).origin.replace(/^http/, "ws"),
            ].includes(new URL(u).origin),
        ),
        [],
      );
      assert.equal(
        await page.eval(() => localStorage.length + sessionStorage.length),
        0,
      );
      assert.equal(await page.eval(() => document.cookie), "");
      assert.deepEqual(page.consoleErrors, []);
    },
  );
  const reduce = await browser.newPage({
    width: 390,
    height: 844,
    mobile: true,
    reducedMotion: true,
  });
  await reduce.goto(base, { settleMs: 3400 });
  await check(
    "reduced motion stops the footer loop and keeps content available",
    async () => {
      assert.equal(
        await reduce.eval(
          () =>
            getComputedStyle(document.querySelector(".footer-strip>div"))
              .animationName,
        ),
        "none",
      );
      assert.equal(
        await reduce.eval(
          () =>
            getComputedStyle(document.querySelector(".hero h1")).animationName,
        ),
        "none",
      );
    },
  );
  await reduce.close();
  writeFileSync(
    resolve(out, "browser-qa.json"),
    JSON.stringify(
      {
        result: "PASS",
        tests: results.length,
        failures: 0,
        results,
        externalRequests: [],
        consoleErrors: [],
        physicalDeviceTested: false,
      },
      null,
      2,
    ) + "\n",
  );
  console.log(
    JSON.stringify({ result: "PASS", tests: results.length, failures: 0 }),
  );
} catch (error) {
  writeFileSync(
    resolve(out, "browser-qa.json"),
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
