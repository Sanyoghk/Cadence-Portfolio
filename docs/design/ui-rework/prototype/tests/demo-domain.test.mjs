import test from "node:test";
import assert from "node:assert/strict";
import {
  PILLARS,
  METRICS,
  metricSummary,
  initialBrand,
  createDemoWorkspaces,
  selectBrand,
  admit,
  withdrawConsent,
  generateIdentity,
  approveIdentity,
  editIdentity,
  currentConsent,
  prepareStrategy,
  approveStrategy,
  prepareContent,
  currentIdentity,
  metricsFor,
  approvePost,
  csvFor,
  schedulePost,
} from "../src/demo.js";
test("pillar weights and metrics derive from the displayed rows", () => {
  assert.equal(
    PILLARS.reduce((s, p) => s + p.weight, 0),
    100,
  );
  assert.deepEqual(metricSummary(METRICS), {
    impressions: 5000,
    engagements: 347,
    rate: 6.94,
  });
  assert.equal(metricSummary([]).rate, null);
});
test("a Brand from another workspace is not returned by a scoped lookup", () => {
  const ws = createDemoWorkspaces();
  assert.equal(selectBrand(ws, "studio", "notes"), null);
  assert.equal(selectBrand(ws, "field", "clay"), null);
  assert.equal(selectBrand(ws, "missing", "clay"), null);
  assert.equal(selectBrand(ws, "studio", "clay").name, "Sunday Clay");
});
test("generation and approval require current exact consent", () => {
  const b = initialBrand();
  assert.throws(() => generateIdentity(b));
  assert.throws(() => admit(b, 0));
  const draft = generateIdentity(admit(b, 1));
  assert.equal(draft.identity.status, "DRAFT");
  assert.throws(() => approveIdentity({ ...draft, sourceVersion: 2 }));
  assert.throws(() => generateIdentity({ ...draft, withdrawn: true }));
  assert.equal(currentConsent({ ...draft, sourceVersion: 2 }), false);
});
test("editing preserves approved history and creates a distinct draft", () => {
  const approved = approveIdentity(generateIdentity(admit(initialBrand(), 1)));
  const edited = editIdentity(approved, "A fresh voice");
  assert.equal(edited.identity.status, "DRAFT");
  assert.equal(edited.identityHistory[0].status, "APPROVED");
  assert.notEqual(edited.identity.voice, edited.identityHistory[0].voice);
  assert.equal(approved.identity.status, "APPROVED");
});
test("CSV includes only approved revisions and neutralizes spreadsheet formula prefixes", () => {
  const b = initialBrand();
  b.posts[0].caption = '=HYPERLINK("demo")';
  const csv = csvFor(b);
  assert.ok(csv.includes("'=HYPERLINK"));
  assert.ok(!csv.includes("The hands behind"));
  assert.equal(csv.split("\r\n").length, 3);
});
test("draft scheduling and out-of-range/ambiguous demo times are rejected", () => {
  const b = initialBrand();
  assert.throws(() => schedulePost(b, "hands", "2026-10-21", "12:00"));
  assert.throws(() => schedulePost(b, "cup", "2026-10-25", "01:30"));
  assert.throws(() => schedulePost(b, "cup", "2026-10-30", "12:00"));
  assert.equal(
    schedulePost(b, "cup", "2026-10-20", "11:30").posts[0].time,
    "11:30",
  );
});
test("a newly allowed source cannot reuse an older approved Identity", () => {
  const b = approveIdentity(generateIdentity(admit(initialBrand(), 1)));
  const changed = admit({ ...b, sourceVersion: 2 }, 2);
  assert.equal(currentIdentity(changed), false);
  assert.throws(() => prepareStrategy(changed, "A goal"));
});
test("consent withdrawal preserves the ordered decisions and blocks downstream preparation", () => {
  const b = withdrawConsent(admit(initialBrand(), 1));
  assert.deepEqual(
    b.consentHistory.map((e) => e.action),
    ["ALLOWED", "WITHDRAWN"],
  );
  assert.throws(() => generateIdentity(b));
  assert.throws(() => prepareStrategy(b, "A goal"));
  assert.equal(admit(b, 1).consentHistory.length, 3);
});
test("content requires a current separately approved Strategy and is bounded", () => {
  let b = approveIdentity(generateIdentity(admit(initialBrand(), 1)));
  assert.throws(() => prepareContent(b));
  b = prepareStrategy(b, "A considered goal");
  assert.throws(() => prepareContent(b));
  b = approveStrategy(b);
  const next = prepareContent(b);
  assert.equal(next.posts.length, 5);
  assert.equal(next.posts.at(-1).status, "DRAFT");
  assert.equal(next.strategyHistory[0].status, "APPROVED");
  assert.throws(() =>
    prepareContent({ ...b, posts: Array(12).fill(b.posts[0]) }),
  );
  assert.throws(() =>
    approveStrategy({
      ...b,
      strategy: { ...b.strategy, status: "DRAFT", sourceVersion: 99 },
    }),
  );
});
test("metric fixtures never spill into a different Brand", () => {
  const ws = createDemoWorkspaces();
  assert.equal(metricsFor(ws[0].brands[0]).length, 4);
  assert.equal(metricsFor(ws[1].brands[0]).length, 0);
  assert.equal(metricsFor({ id: "unrecognised" }).length, 0);
});
test("an approved post cannot be approved again; original fixture stays immutable", () => {
  const b = initialBrand();
  assert.throws(() => approvePost(b, "cup", "A caption"));
  const next = approvePost(b, "hands", "A reviewed caption");
  assert.equal(next.posts[1].status, "APPROVED");
  assert.equal(b.posts[1].status, "DRAFT");
});
