import { RevealText } from "./TextReveal.jsx";
import React, { useEffect, useRef, useState } from "react";
import { useContentFlip } from "./motion-hooks.js";
import {
  metricSummary,
  initialBrand,
  currentConsent,
  currentIdentity,
  currentStrategy,
  admit,
  withdrawConsent,
  generateIdentity,
  approveIdentity,
  editIdentity,
  prepareStrategy,
  approveStrategy,
  prepareContent,
  metricsFor,
  approvePost,
  schedulePost,
} from "./demo.js";
import { Button, Field, Pillars, Modal, useDemoAction } from "./ui.jsx";
const text =
  "Sunday Clay makes small-batch ceramics for everyday rituals. Each piece is shaped by hand. Our voice is warm, considered and quietly confident. We value useful objects, honest materials and a slower pace.";
export function WorkspaceManager({
  workspaces,
  setWorkspaces,
  selected,
  choose,
  notify,
}) {
  const [editing, setEditing] = useState(null),
    [name, setName] = useState(""),
    [industry, setIndustry] = useState(""),
    [brandName, setBrandName] = useState("");
  function save(e) {
    e.preventDefault();
    if (!name.trim() || !industry.trim()) return;
    if (!editing && workspaces.length >= 5) {
      notify("This demo supports up to five workspaces.");
      return;
    }
    const id = editing ?? "demo-" + Date.now();
    setWorkspaces((all) =>
      editing
        ? all.map((w) =>
            w.id === editing
              ? { ...w, name: name.trim(), industry: industry.trim() }
              : w,
          )
        : [
            ...all,
            {
              id,
              name: name.trim(),
              industry: industry.trim(),
              brands: [
                {
                  id: "brand-" + Date.now(),
                  ...initialBrand(name.trim()),
                  posts: [],
                },
              ],
            },
          ],
    );
    setEditing(null);
    setName("");
    setIndustry("");
    notify(
      editing
        ? "Demo workspace updated."
        : "Demo workspace created. Select it from the list.",
    );
  }
  return (
    <>
      <div className="work-heading">
        <div>
          <RevealText as="h1" tabIndex="-1">
            Workspace Manager
          </RevealText>
          <RevealText as="p">A space for each part of your work.</RevealText>
        </div>
      </div>
      <div className="workspace-grid">
        <section className="panel">
          <RevealText as="h2">Your workspaces</RevealText>
          <div className="workspace-list">
            {workspaces
              .filter((w) => !w.archived)
              .map((w) => (
                <article key={w.id}>
                  <div>
                    <RevealText as="h3">{w.name}</RevealText>
                    <RevealText as="p">
                      {w.industry} · {w.brands.length} Brands
                      {w.id === selected ? " · Selected" : ""}
                    </RevealText>
                  </div>
                  <div className="inline-actions">
                    <button
                      className="text-button"
                      onClick={() => choose(w.id)}
                    >
                      Select
                    </button>
                    <button
                      className="text-button"
                      onClick={() => {
                        setEditing(w.id);
                        setName(w.name);
                        setIndustry(w.industry);
                      }}
                    >
                      Edit
                    </button>
                    {w.id !== selected && (
                      <button
                        className="text-button"
                        onClick={() => {
                          setWorkspaces((all) =>
                            all.map((x) =>
                              x.id === w.id ? { ...x, archived: true } : x,
                            ),
                          );
                          notify(
                            "Demo workspace archived. Nothing was deleted.",
                          );
                        }}
                      >
                        Archive
                      </button>
                    )}
                  </div>
                </article>
              ))}
          </div>
        </section>
        <section className="panel">
          <RevealText as="h2">
            {editing ? "Edit workspace" : "Create a workspace"}
          </RevealText>
          <form onSubmit={save}>
            <Field
              label="Workspace name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              maxLength={80}
            />
            <Field
              label="Industry"
              value={industry}
              onChange={(e) => setIndustry(e.target.value)}
              required
              maxLength={80}
            />
            <Button>
              {editing ? "Save demo workspace" : "Create demo workspace"}
            </Button>
          </form>
          <RevealText as="p" className="meta">
            This tab only. Refresh resets the synthetic data.
          </RevealText>
        </section>
      </div>
      <section className="panel brand-create">
        <RevealText as="h2">Add a Brand</RevealText>
        <RevealText as="p">
          Workspace and Brand are separate resources. Each Brand keeps its own
          demo decisions.
        </RevealText>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (!brandName.trim()) return;
            setWorkspaces((all) =>
              all.map((w) =>
                w.id !== selected
                  ? w
                  : {
                      ...w,
                      brands: [
                        ...w.brands,
                        {
                          id: "brand-" + Date.now(),
                          ...initialBrand(brandName.trim()),
                          posts: [],
                        },
                      ],
                    },
              ),
            );
            setBrandName("");
            notify("Demo Brand added. Choose it in the Brand selector.");
          }}
        >
          <Field
            label="Brand name"
            value={brandName}
            onChange={(e) => setBrandName(e.target.value)}
            required
            maxLength={80}
          />
          <Button secondary>Add demo Brand</Button>
        </form>
      </section>
    </>
  );
}
export function Identity({ brand, mutate, notify, history, next }) {
  const [checked, setChecked] = useState(false),
    [voice, setVoice] = useState(brand.identity?.voice ?? ""),
    [busy, run] = useDemoAction();
  useEffect(() => setChecked(false), [brand.sourceVersion]);
  useEffect(
    () => setVoice(brand.identity?.voice ?? ""),
    [brand.identity?.version],
  );
  const kind = brand.sourceKind ?? "PDF text";
  const sourceText =
    brand.id === "clay"
      ? text
      : `${brand.name} is a synthetic demo Brand. Its sample voice is thoughtful, useful and direct. Review this illustrative source before allowing demo analysis.`;
  const allowed = currentConsent(brand),
    dirty = !!brand.identity && voice !== brand.identity.voice;
  return (
    <>
      <div className="work-heading">
        <div>
          <RevealText as="h1" tabIndex="-1">
            Brand Identity
          </RevealText>
          <RevealText as="p">
            {brand.name} · Start with your sources. Keep the decisions yours.
          </RevealText>
        </div>
        <button className="text-button" onClick={history}>
          History & consent
        </button>
      </div>
      <div className="workspace-grid">
        <section className="panel">
          <div className="panel-heading">
            <RevealText as="h2">Read your source</RevealText>
            <span className="state-label">
              Demo · version {brand.sourceVersion}
            </span>
          </div>
          <Field label="Demo source">
            <select
              value={kind}
              onChange={(e) => {
                const sourceKind = e.target.value;
                mutate((b) => ({
                  ...b,
                  sourceKind,
                  sourceVersion: b.sourceVersion + 1,
                }));
              }}
            >
              <option>PDF text</option>
              <option>Website text</option>
              <option>Social text</option>
              <option>Image / screenshot</option>
            </select>
          </Field>
          {kind === "Image / screenshot" ? (
            <div className="notice">
              <RevealText as="h3">Requires provider</RevealText>
              <RevealText as="p">
                Visual processing is not connected. A retained image is not a
                generated Identity.
              </RevealText>
            </div>
          ) : (
            <>
              <div className="snapshot">
                <RevealText as="p">{sourceText}</RevealText>
                <span>
                  Exact synthetic snapshot · source version{" "}
                  {brand.sourceVersion}
                </span>
              </div>
              <label className="check-row">
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={(e) => setChecked(e.target.checked)}
                />
                <span>
                  I reviewed this exact demo snapshot and allow its use for demo
                  analysis.
                </span>
              </label>
              <div className="inline-actions">
                <Button
                  secondary
                  disabled={!checked || allowed}
                  onClick={() => {
                    mutate((b) => admit(b, brand.sourceVersion));
                    notify(
                      "Demo consent recorded for this reviewed source version.",
                    );
                  }}
                >
                  {allowed ? "Demo source allowed" : "Allow reviewed source"}
                </Button>
                {allowed && (
                  <button
                    className="text-button"
                    onClick={() => {
                      mutate(withdrawConsent);
                      setChecked(false);
                      notify(
                        "Demo consent withdrawn. Generation and approval are blocked.",
                      );
                    }}
                  >
                    Withdraw consent
                  </button>
                )}
              </div>
              <RevealText as="p" className="meta">
                Registration is not consent. Source changes require a fresh
                review.
              </RevealText>
            </>
          )}
        </section>
        <section className="panel">
          <div className="panel-heading">
            <RevealText as="h2">Your brand voice</RevealText>
            <span className="state-label">
              {brand.identity?.status ?? "No draft"}
            </span>
          </div>
          {!brand.identity ? (
            <>
              <RevealText as="p">
                A considered voice begins with a source you have reviewed.
              </RevealText>
              <div className="voice-sample">
                <RevealText as="h3">Keep a little clarity.</RevealText>
                <RevealText as="p">
                  Palette, type and tone will form your working Identity.
                </RevealText>
              </div>
            </>
          ) : (
            <>
              <div className="palette" aria-label="Synthetic brand palette">
                <span style={{ background: "#1a1814" }} />
                <span style={{ background: "#86623e" }} />
                <span style={{ background: "#d9d3c5" }} />
                <span style={{ background: "#f3f0e8" }} />
              </div>
              <RevealText as="p" className="meta">
                Demo palette · Cormorant Garamond / DM Sans
              </RevealText>
              <Field label="Brand voice">
                <textarea
                  rows="4"
                  value={voice}
                  maxLength={500}
                  onChange={(e) => setVoice(e.target.value)}
                />
              </Field>
              <RevealText as="p" className="meta">
                Version {brand.identity.version} · demo source version{" "}
                {brand.identity.sourceVersion}
              </RevealText>
              <div className="inline-actions">
                <Button
                  secondary
                  disabled={!dirty || !voice.trim()}
                  onClick={() => {
                    mutate((b) => editIdentity(b, voice));
                    notify(
                      "New demo draft saved. Approved history is unchanged.",
                    );
                  }}
                >
                  Save draft
                </Button>
                <Button
                  disabled={
                    dirty || brand.identity.status !== "DRAFT" || !allowed
                  }
                  onClick={() => {
                    mutate(approveIdentity);
                    notify("Demo Identity approved as a separate decision.");
                  }}
                >
                  Approve Identity
                </Button>
              </div>
            </>
          )}
          <div className="panel-actions">
            <Button
              secondary
              disabled={!allowed || kind === "Image / screenshot" || busy}
              onClick={() => {
                const v = brand.sourceVersion;
                run(() => {
                  mutate((b) => {
                    if (b.sourceVersion !== v)
                      throw new Error(
                        "Source changed during demo preparation. Review it again.",
                      );
                    return generateIdentity(b);
                  });
                  notify(
                    "Synthetic Identity draft prepared. No AI was called.",
                  );
                });
              }}
            >
              {busy
                ? "Preparing demo draft…"
                : brand.identity
                  ? "Create another demo draft"
                  : "Prepare demo Identity"}
            </Button>
            {brand.identity?.status === "APPROVED" && (
              <button className="text-button" onClick={next}>
                Open Strategy playbook →
              </button>
            )}
          </div>
          <RevealText as="p" className="meta">
            The real app uses a durable Job and current source checks.
          </RevealText>
        </section>
      </div>
    </>
  );
}
export function Strategy({ brand, mutate, notify, history, next }) {
  const [busy, run] = useDemoAction(),
    [goal, setGoal] = useState(
      brand.strategy?.goal ??
        "Build recognition through useful, honest studio stories.",
    );
  const ready = currentIdentity(brand),
    dirty = brand.strategy && goal !== brand.strategy.goal;
  return (
    <>
      <div className="work-heading">
        <div>
          <RevealText as="h1" tabIndex="-1">
            Strategy playbook
          </RevealText>
          <RevealText as="p">
            {brand.name} · A rhythm to create from.
          </RevealText>
        </div>
        <button className="text-button" onClick={history}>
          Version history
        </button>
      </div>
      <section className="strategy-intro panel">
        <div>
          <RevealText as="h2">
            {brand.strategy
              ? "A considered direction."
              : "Start with Identity."}
          </RevealText>
          <RevealText as="p">
            {brand.strategy
              ? `Demo ${brand.strategy.status} · version ${brand.strategy.version}`
              : "Approve a reviewed Identity before preparing a playbook."}
          </RevealText>
        </div>
        <Button
          disabled={!ready || busy}
          onClick={() => {
            const expected = brand.identity.version;
            run(() => {
              mutate((b) => {
                if (
                  b.identity?.status !== "APPROVED" ||
                  b.identity.version !== expected ||
                  !currentConsent(b)
                )
                  throw new Error("The approved source changed.");
                return prepareStrategy(b, goal);
              });
              notify(
                "Demo Strategy draft prepared from the approved Identity.",
              );
            });
          }}
        >
          {busy ? "Preparing demo playbook…" : "Prepare demo playbook"}
        </Button>
      </section>
      {brand.strategy && (
        <>
          <section className="panel">
            <div className="panel-heading">
              <RevealText as="h2">Content pillars</RevealText>
              <span className="meta">Synthetic mix · 100% total</span>
            </div>
            <Pillars />
          </section>
          <div className="workspace-grid">
            <section className="panel">
              <RevealText as="h2">Direction & audience</RevealText>
              <Field label="Channel goal">
                <textarea
                  rows="3"
                  value={goal}
                  maxLength={500}
                  onChange={(e) => setGoal(e.target.value)}
                />
              </Field>
              <RevealText as="h3">Audience assumption</RevealText>
              <RevealText as="p">
                People who value useful objects and the story behind the making.
                An assumption to test, not measured research.
              </RevealText>
              <RevealText as="h3">Competitive whitespace</RevealText>
              <RevealText as="p">
                Make everyday usefulness feel as considered as the object
                itself. A demo hypothesis, without competitor metrics.
              </RevealText>
            </section>
            <section className="panel">
              <RevealText as="h2">Cadence & tone</RevealText>
              <dl className="facts">
                <div>
                  <dt>Channels</dt>
                  <dd>Instagram · TikTok</dd>
                </div>
                <div>
                  <dt>Rhythm</dt>
                  <dd>4 pieces / week</dd>
                </div>
                <div>
                  <dt>Timezone</dt>
                  <dd>Europe/London</dd>
                </div>
                <div>
                  <dt>Voice</dt>
                  <dd>Warm · considered · direct</dd>
                </div>
              </dl>
              <RevealText as="h3">Four-week outline</RevealText>
              <ol className="outline">
                <RevealText as="li">
                  Introduce the studio and its rituals.
                </RevealText>
                <RevealText as="li">
                  Show the making, from clay to table.
                </RevealText>
                <RevealText as="li">
                  Invite a conversation about everyday use.
                </RevealText>
                <RevealText as="li">
                  Review the response; refine the next draft.
                </RevealText>
              </ol>
            </section>
          </div>
          <div className="approval-bar">
            <RevealText as="p">
              Editing and approval are separate decisions.
            </RevealText>
            <div className="inline-actions">
              <Button
                secondary
                disabled={!dirty || !goal.trim()}
                onClick={() => {
                  mutate((b) => ({
                    ...b,
                    strategy: {
                      ...b.strategy,
                      goal,
                      status: "DRAFT",
                      version: b.strategy.version + 1,
                    },
                  }));
                  notify("New demo Strategy draft saved.");
                }}
              >
                Save Strategy draft
              </Button>
              <Button
                disabled={dirty || brand.strategy.status !== "DRAFT" || !ready}
                onClick={() => {
                  mutate(approveStrategy);
                  notify("Demo Strategy approved.");
                }}
              >
                Approve playbook
              </Button>
              {brand.strategy.status === "APPROVED" && (
                <button className="text-button" onClick={next}>
                  Open Sample content →
                </button>
              )}
            </div>
          </div>
        </>
      )}
    </>
  );
}
export function Content({ brand, mutate, notify, download, history }) {
  const calendarRef = useRef(null);
  const [mode, setMode] = useState("calendar"),
    [selected, setSelected] = useState(brand.posts[0]?.id ?? ""),
    [caption, setCaption] = useState(brand.posts[0]?.caption ?? ""),
    [date, setDate] = useState("2026-10-20"),
    [time, setTime] = useState("11:00"),
    post = brand.posts.find((p) => p.id === selected);
  const dirty = post && caption !== post.caption;
  const captureFlip = useContentFlip(calendarRef, mode);
  function changeMode(next) {
    if (mode === next) return;
    captureFlip();
    setMode(next);
  }
  function select(p) {
    setSelected(p.id);
    setCaption(p.caption);
    setDate(p.date);
    setTime(p.time);
  }
  return (
    <>
      <div className="work-heading">
        <div>
          <RevealText as="h1" tabIndex="-1">
            Sample content
          </RevealText>
          <RevealText as="p">
            {brand.name} · 19–25 October 2026 · Europe/London
          </RevealText>
        </div>
        <div className="inline-actions">
          <button className="text-button" onClick={history}>
            Revision history
          </button>
          <Button secondary onClick={download}>
            Export demo CSV
          </Button>
        </div>
      </div>
      <div className="content-toolbar">
        <div className="segmented" role="group" aria-label="Content view">
          <button
            aria-pressed={mode === "calendar"}
            onClick={() => changeMode("calendar")}
          >
            Calendar
          </button>
          <button
            aria-pressed={mode === "list"}
            onClick={() => changeMode("list")}
          >
            Post list
          </button>
        </div>
        <button
          className="text-button"
          disabled={!currentStrategy(brand) || brand.posts.length >= 12}
          onClick={() => {
            mutate(prepareContent);
            notify(
              "A new synthetic content DRAFT was added. No AI was called.",
            );
          }}
        >
          Prepare demo content
        </button>
      </div>
      {!brand.posts.length ? (
        <section className="panel">
          <RevealText as="h2">A little room to begin.</RevealText>
          <RevealText as="p">
            No demo posts in this Brand. Start with an approved Strategy.
          </RevealText>
        </section>
      ) : (
        <div className="content-layout">
          <section ref={calendarRef} className="panel calendar-panel">
            <RevealText as="h2">
              {mode === "calendar"
                ? "Your week, considered."
                : "Posts to review"}
            </RevealText>
            {mode === "calendar" ? (
              <div className="calendar-grid">
                {["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map(
                  (day, i) => (
                    <div
                      className={"calendar-day " + (i === 0 ? "today" : "")}
                      key={day}
                    >
                      <div className="day-heading">
                        <span>{day}</span>
                        <strong>{19 + i}</strong>
                      </div>
                      {brand.posts
                        .filter((p) => p.date === "2026-10-" + (19 + i))
                        .map((p) => (
                          <button
                            key={p.id}
                            data-flip-id={brand.id + "/" + p.id}
                            className={
                              "calendar-post " +
                              (p.id === selected ? "selected" : "")
                            }
                            onClick={() => select(p)}
                          >
                            <span>
                              {p.time} · {p.platform}
                            </span>
                            <strong>{p.title}</strong>
                            <span>
                              {p.status === "APPROVED"
                                ? "Approved"
                                : "Draft · review needed"}
                            </span>
                          </button>
                        ))}
                    </div>
                  ),
                )}
              </div>
            ) : (
              <div className="post-list">
                {brand.posts.map((p) => (
                  <button
                    key={p.id}
                    data-flip-id={brand.id + "/" + p.id}
                    onClick={() => select(p)}
                  >
                    <strong>{p.title}</strong>
                    <span>
                      {p.platform} · {p.date} · {p.status}
                    </span>
                  </button>
                ))}
              </div>
            )}
            <RevealText as="p" className="meta">
              Scheduling does not publish. Export approved content for manual
              use.
            </RevealText>
          </section>
          <section className="panel editor-panel">
            <div className="panel-heading">
              <RevealText as="h2">Review a post</RevealText>
              <span className="state-label">
                {post?.status ?? "Choose a post"}
              </span>
            </div>
            {post && (
              <>
                <RevealText as="p">{post.title}</RevealText>
                <Field label="Caption">
                  <textarea
                    rows="5"
                    maxLength={2000}
                    value={caption}
                    onChange={(e) => setCaption(e.target.value)}
                  />
                </Field>
                <RevealText as="p" className="meta">
                  Synthetic revision {post.version} · {post.platform}
                </RevealText>
                <div className="inline-actions">
                  <Button
                    secondary
                    disabled={!dirty || !caption.trim()}
                    onClick={() => {
                      mutate((b) => ({
                        ...b,
                        postHistory: [...b.postHistory, { ...post }],
                        posts: b.posts.map((p) =>
                          p.id === post.id
                            ? {
                                ...p,
                                caption,
                                status: "DRAFT",
                                version: p.version + 1,
                              }
                            : p,
                        ),
                      }));
                      notify("New demo revision saved as a draft.");
                    }}
                  >
                    Save caption draft
                  </Button>
                  <Button
                    disabled={dirty || post.status !== "DRAFT"}
                    onClick={() => {
                      mutate((b) => approvePost(b, post.id, caption));
                      notify("Demo revision approved separately.");
                    }}
                  >
                    Approve revision
                  </Button>
                </div>
                <RevealText as="h3">Schedule this revision</RevealText>
                <div className="form-two">
                  <Field
                    label="Date"
                    type="date"
                    value={date}
                    min="2026-10-19"
                    max="2026-10-25"
                    onChange={(e) => setDate(e.target.value)}
                  />
                  <Field
                    label="Time · London"
                    type="time"
                    value={time}
                    onChange={(e) => setTime(e.target.value)}
                  />
                </div>
                <Button
                  secondary
                  disabled={post.status !== "APPROVED" || dirty}
                  onClick={() => {
                    mutate((b) => schedulePost(b, post.id, date, time));
                    notify("Demo schedule updated. No post was published.");
                  }}
                >
                  Save demo schedule
                </Button>
                <RevealText as="p" className="meta">
                  Demo hours 10:00–18:59. Real scheduling uses backend
                  timezone/DST checks.
                </RevealText>
              </>
            )}
          </section>
        </div>
      )}
    </>
  );
}
export function Performance({ brand, mutate, notify, next }) {
  const [platform, setPlatform] = useState("All"),
    [empty, setEmpty] = useState(false),
    [applied, setApplied] = useState(false),
    [report, setReport] = useState(false),
    rows = empty
      ? []
      : metricsFor(brand).filter(
          (r) => platform === "All" || r.platform === platform,
        ),
    summary = metricSummary(rows);
  return (
    <>
      <div className="work-heading">
        <div>
          <RevealText as="h1" tabIndex="-1">
            Performance
          </RevealText>
          <RevealText as="p">
            {brand.name} · 12–18 October 2026 · Europe/London
          </RevealText>
        </div>
        <Button
          secondary
          onClick={() => {
            setEmpty(false);
            notify(
              "Synthetic metric sample loaded. No file or platform was contacted.",
            );
          }}
        >
          Load demo metrics
        </Button>
      </div>
      <div className="content-toolbar">
        <label className="compact-label">
          Channel{" "}
          <select
            value={platform}
            onChange={(e) => setPlatform(e.target.value)}
          >
            <option>All</option>
            <option>Instagram</option>
            <option>TikTok</option>
          </select>
        </label>
        <button className="text-button" onClick={() => setEmpty(!empty)}>
          {empty ? "Show demo observations" : "Preview empty state"}
        </button>
      </div>
      <section
        className="metrics-strip"
        aria-label="Synthetic performance summary"
      >
        {[
          ["Impressions", summary.impressions.toLocaleString("en-GB")],
          ["Engagements", summary.engagements],
          [
            "Engagement rate",
            summary.rate === null ? "—" : summary.rate.toFixed(1) + "%",
          ],
          ["Coverage", rows.length + " posts"],
        ].map(([label, value]) => (
          <div key={label}>
            <span>{label}</span>
            <strong>{value}</strong>
          </div>
        ))}
      </section>
      <div className="workspace-grid performance-grid">
        <section className="panel">
          <RevealText as="h2">Stories that connected.</RevealText>
          <RevealText as="p">
            Impressions per synthetic post. No comparison period is available.
          </RevealText>
          <div className="performance-bars">
            {rows.length ? (
              rows.map((r) => (
                <div className="bar-row" key={r.title}>
                  <span>
                    {r.title}
                    <small>
                      {r.date} · {r.platform}
                    </small>
                  </span>
                  <meter
                    value={r.impressions}
                    min="0"
                    max="2000"
                    aria-label={r.title + ": " + r.impressions + " impressions"}
                  />
                  <strong>{r.impressions.toLocaleString("en-GB")}</strong>
                </div>
              ))
            ) : (
              <RevealText as="p" className="notice">
                No observations in this window. Missing data is not a measured
                zero.
              </RevealText>
            )}
          </div>
          <RevealText as="p" className="meta">
            Source: synthetic manual import · not a live connection.
          </RevealText>
        </section>
        <section className="panel">
          <RevealText as="h2">A next step to consider.</RevealText>
          <RevealText as="p" className="state-label">
            Heuristic · uncalibrated · demo
          </RevealText>
          <RevealText as="h3">Make the making visible.</RevealText>
          <RevealText as="p">
            Try a process-led piece in your next draft, then compare its
            response.
          </RevealText>
          <RevealText as="p" className="meta">
            Demo evidence: “Meet your maker” · 1,600 impressions / 96
            engagements. This sample cannot establish causation.
          </RevealText>
          <Button
            disabled={applied || !rows.length || !currentStrategy(brand)}
            onClick={() => {
              mutate((b) => {
                if (!currentStrategy(b))
                  throw new Error("Approve a current Strategy first.");
                return prepareStrategy(
                  b,
                  "Test a process-led story, then compare its response.",
                );
              });
              setApplied(true);
              notify(
                "Recommendation applied to a new demo Strategy DRAFT. Approved history is unchanged.",
              );
            }}
          >
            {applied ? "New draft created" : "Apply to a new draft"}
          </Button>
          {applied && (
            <button className="text-button block" onClick={next}>
              Review Strategy draft →
            </button>
          )}
          <RevealText as="p" className="meta">
            Apply requires a current approved Strategy. The suggestion creates a
            new draft.
          </RevealText>
          <button className="text-button block" onClick={() => setReport(true)}>
            Preview demo report
          </button>
        </section>
      </div>
      {report && (
        <Modal title="Demo performance report" close={() => setReport(false)}>
          <RevealText as="p">
            Synthetic observations for {brand.name}. This preview is not an
            approved report snapshot.
          </RevealText>
          <dl className="facts">
            <div>
              <dt>Channel</dt>
              <dd>{platform}</dd>
            </div>
            <div>
              <dt>Impressions</dt>
              <dd>{summary.impressions.toLocaleString("en-GB")}</dd>
            </div>
            <div>
              <dt>Engagements</dt>
              <dd>{summary.engagements}</dd>
            </div>
            <div>
              <dt>Coverage</dt>
              <dd>{rows.length} observations</dd>
            </div>
          </dl>
          <RevealText as="p">
            No comparison period is available. Missing data must not be
            interpreted as measured zero.
          </RevealText>
          <RevealText as="p" className="meta">
            Real report creation, approval and rendering remain separate backend
            operations.
          </RevealText>
        </Modal>
      )}
    </>
  );
}
