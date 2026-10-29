import { RevealText, MotionRoot } from "./TextReveal.jsx";
import React, { useEffect, useRef, useState } from "react";
import { useMotionScene } from "./motion-hooks.js";
import {
  createDemoWorkspaces,
  selectBrand,
  currentConsent,
  csvFor,
} from "./demo.js";
import { Star, Button, Modal } from "./ui.jsx";
import { Landing } from "./Landing.jsx";
import {
  WorkspaceManager,
  Identity,
  Strategy,
  Content,
  Performance,
} from "./Workspace.jsx";
const views = {
  manager: "Workspace Manager",
  identity: "Brand Identity",
  strategy: "Strategy playbook",
  content: "Sample content",
  performance: "Performance",
};
const readRoute = () =>
  location.hash.startsWith("#workspace/") &&
  Object.hasOwn(views, location.hash.split("/")[1])
    ? location.hash.split("/")[1]
    : "home";
export function App() {
  const headerRef = useRef(null);
  const [view, setView] = useState(readRoute),
    [intro, setIntro] = useState(true),
    [workspaces, setWorkspaces] = useState(createDemoWorkspaces),
    [workspaceId, setWorkspaceId] = useState("studio"),
    [brandId, setBrandId] = useState("clay"),
    [modal, setModal] = useState(null),
    [notice, setNotice] = useState(""),
    [paused, setPaused] = useState(false),
    [theme, setTheme] = useState("cream");
  const workspace = workspaces.find((w) => w.id === workspaceId && !w.archived),
    brand = selectBrand(workspaces, workspaceId, brandId);
  useEffect(() => {
    const t = setTimeout(() => setIntro(false), 3000);
    const f = () => {
      setView(readRoute());
      setNotice("");
      document
        .querySelectorAll("details[open]")
        .forEach((e) => e.removeAttribute("open"));
    };
    addEventListener("hashchange", f);
    return () => {
      clearTimeout(t);
      removeEventListener("hashchange", f);
    };
  }, []);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.querySelector('meta[name="theme-color"]').content = {
      cream: "#f3f0e8",
      dark: "#191612",
      sage: "#f2eee4",
    }[theme];
  }, [theme]);
  useEffect(() => {
    if (intro) return;
    requestAnimationFrame(() => {
      if (view !== "home") {
        scrollTo(0, 0);
        document.querySelector("#workspace-main h1")?.focus();
      } else document.getElementById(location.hash.slice(1))?.scrollIntoView();
    });
  }, [view, intro]);
  useMotionScene(headerRef, "header", view);
  function navigate(next) {
    location.hash = next === "home" ? "home" : "workspace/" + next;
    setView(next);
    setNotice("");
  }
  function choose(id) {
    const w = workspaces.find((w) => w.id === id && !w.archived);
    if (!w) return;
    setWorkspaceId(id);
    setBrandId(w.brands[0]?.id ?? "");
    setNotice("");
    document
      .querySelectorAll("details[open]")
      .forEach((e) => e.removeAttribute("open"));
  }
  function mutate(fn) {
    setWorkspaces((all) =>
      all.map((w) =>
        w.id !== workspaceId
          ? w
          : {
              ...w,
              brands: w.brands.map((b) => {
                if (b.id !== brandId) return b;
                try {
                  return fn(b);
                } catch (e) {
                  setTimeout(() => setNotice(e.message), 0);
                  return b;
                }
              }),
            },
      ),
    );
  }
  function download() {
    const url = URL.createObjectURL(
        new Blob([csvFor(brand)], { type: "text/csv;charset=utf-8" }),
      ),
      a = document.createElement("a");
    a.href = url;
    a.download = "cadence-demo-approved-content.csv";
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    setNotice(
      "Demo CSV downloaded. Only approved synthetic revisions are included.",
    );
  }
  const account = () => setModal("Account preview"),
    props = { brand, mutate, notify: setNotice };
  useEffect(() => {
    document.documentElement.style.overflow = intro ? "hidden" : "";
    return () => {
      document.documentElement.style.overflow = "";
    };
  }, [intro]);
  useEffect(() => {
    const key = (e) => {
      document.documentElement.dataset.keyboard = "true";
      if (e.key === "Escape")
        document.querySelectorAll("details[open]").forEach((d) => {
          d.removeAttribute("open");
          d.querySelector("summary")?.focus();
        });
    };
    const pointer = (e) => {
      document.documentElement.dataset.keyboard = "false";
      document.querySelectorAll("details[open]").forEach((d) => {
        if (!d.contains(e.target)) d.removeAttribute("open");
      });
    };
    const clicked = (e) => {
      if (e.target.closest("details a,details button"))
        document
          .querySelectorAll("details[open]")
          .forEach((d) => d.removeAttribute("open"));
    };
    addEventListener("keydown", key);
    addEventListener("pointerdown", pointer);
    addEventListener("click", clicked);
    return () => {
      removeEventListener("keydown", key);
      removeEventListener("pointerdown", pointer);
      removeEventListener("click", clicked);
    };
  }, []);
  return (
    <MotionRoot
      ready={!intro}
      className={view === "home" ? "site" : "site workspace-site"}
      data-paused={paused}
      data-ready={!intro}
    >
      <a
        className="skip"
        href={view === "home" ? "#brand-identity" : "#workspace-main"}
      >
        Skip to content
      </a>
      <div className="fixed-frame" aria-hidden="true" />
      <div className="app-content" inert={intro ? true : undefined}>
        <header ref={headerRef} className="topbar">
          <a className="nav-mark" href="#home" aria-label="Cadence home">
            <Star />
            <span>Cadence</span>
          </a>
          {view === "home" ? (
            <>
              <nav className="desktop-nav" aria-label="Public navigation">
                <a href="#home">Home</a>
                <a href="#brand-identity">Brand Identity</a>
                <a href="#about">About Us</a>
                <button
                  className="text-button"
                  onClick={() => navigate("identity")}
                >
                  Demo workspace
                </button>
                <button className="text-button" onClick={account}>
                  Account
                </button>
              </nav>
              <details className="mobile-nav">
                <summary>
                  Menu <span aria-hidden="true">›</span>
                </summary>
                <nav aria-label="Mobile public navigation">
                  {[
                    ["home", "Home"],
                    ["brand-identity", "Brand Identity"],
                    ["plans", "Plans"],
                    ["about", "About Us"],
                  ].map(([id, label]) => (
                    <a key={id} href={"#" + id}>
                      {label}
                    </a>
                  ))}
                  <button onClick={() => navigate("identity")}>
                    Demo workspace
                  </button>
                  <button onClick={account}>Account</button>
                </nav>
              </details>
            </>
          ) : (
            <>
              <details className="workspace-switch">
                <summary>
                  {workspace?.name ?? "Choose workspace"}{" "}
                  <span aria-hidden="true">›</span>
                </summary>
                <div className="switch-panel">
                  {workspaces
                    .filter((w) => !w.archived)
                    .map((w) => (
                      <button
                        key={w.id}
                        onClick={() => choose(w.id)}
                        aria-current={w.id === workspaceId ? "true" : undefined}
                      >
                        {w.name}
                      </button>
                    ))}
                  <button onClick={() => navigate("manager")}>
                    Create workspace
                  </button>
                  <button onClick={() => navigate("manager")}>
                    Manage workspaces
                  </button>
                </div>
              </details>
              <nav
                className="desktop-nav work-nav"
                aria-label="Workspace sections"
              >
                {Object.entries(views)
                  .filter(([key]) => key !== "manager")
                  .map(([key, label]) => (
                    <a
                      key={key}
                      href={"#workspace/" + key}
                      aria-current={view === key ? "page" : undefined}
                    >
                      {label}
                    </a>
                  ))}
              </nav>
              <details className="mobile-nav">
                <summary>
                  Sections <span aria-hidden="true">›</span>
                </summary>
                <nav aria-label="Mobile workspace sections">
                  {Object.entries(views).map(([key, label]) => (
                    <a key={key} href={"#workspace/" + key}>
                      {label}
                    </a>
                  ))}
                  <button onClick={account}>Account</button>
                </nav>
              </details>
              <button className="text-button desktop-account" onClick={account}>
                Account
              </button>
            </>
          )}
        </header>
        {view === "home" ? (
          <Landing
            ready={!intro}
            account={account}
            navigate={navigate}
            paused={paused}
            setPaused={setPaused}
          />
        ) : (
          <main
            id="workspace-main"
            className="workspace-main"
            key={workspaceId + "/" + brandId}
          >
            <div className="scope-row">
              <span>Demo workspace · synthetic data · this tab only</span>
              <label className="compact-label">
                Brand{" "}
                <select
                  value={brandId}
                  onChange={(e) => {
                    setBrandId(e.target.value);
                    setNotice("");
                  }}
                >
                  {workspace?.brands.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.name}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            {notice && (
              <p className="notice" role="status">
                {notice}
              </p>
            )}
            {view === "manager" ? (
              <WorkspaceManager
                workspaces={workspaces}
                setWorkspaces={setWorkspaces}
                selected={workspaceId}
                choose={choose}
                notify={setNotice}
              />
            ) : !brand ? (
              <>
                <RevealText as="h1" tabIndex="-1">
                  Choose a Brand
                </RevealText>
                <Button onClick={() => navigate("manager")}>
                  Open Workspace Manager
                </Button>
              </>
            ) : (
              <>
                {view === "identity" && (
                  <Identity
                    {...props}
                    history={() => setModal("Identity history")}
                    next={() => navigate("strategy")}
                  />
                )}
                {view === "strategy" && (
                  <Strategy
                    {...props}
                    history={() => setModal("Strategy history")}
                    next={() => navigate("content")}
                  />
                )}
                {view === "content" && (
                  <Content
                    {...props}
                    download={download}
                    history={() => setModal("Content history")}
                  />
                )}
                {view === "performance" && (
                  <Performance {...props} next={() => navigate("strategy")} />
                )}
              </>
            )}
            <footer className="workspace-footer">
              <RevealText as="span">
                Cadence · Keep the decisions yours.
              </RevealText>
              <label>
                Appearance{" "}
                <select
                  value={theme}
                  onChange={(e) => setTheme(e.target.value)}
                >
                  <option value="cream">Cream</option>
                  <option value="dark">Dark</option>
                  <option value="sage">Sage</option>
                </select>
              </label>
              <a href="#home">Back to home</a>
            </footer>
          </main>
        )}
      </div>
      {intro && (
        <div className="intro" role="status">
          <Star />
          <p>Cadence</p>
          <span>Setting the rhythm</span>
          <div className="intro-track">
            <div />
          </div>
          <small>Design preview · demo data</small>
        </div>
      )}
      {modal && (
        <Modal title={modal} close={() => setModal(null)}>
          {modal === "Account preview" ? (
            <>
              <RevealText as="p">
                In Cadence, account access is handled by Clerk. This preview
                does not collect credentials or create accounts.
              </RevealText>
              <div className="dialog-options">
                <Button onClick={() => setModal("Choose a plan")}>
                  Continue demo journey
                </Button>
                <Button
                  secondary
                  onClick={() => {
                    setModal(null);
                    navigate("identity");
                  }}
                >
                  Explore demo workspace
                </Button>
              </div>
            </>
          ) : modal === "Choose a plan" ? (
            <>
              <RevealText as="p">
                Proposed plans. Pricing and billing are not available. Nothing
                is charged.
              </RevealText>
              <div className="plan-choice">
                {["Standard", "Plus"].map((p) => (
                  <Button
                    secondary
                    key={p}
                    onClick={() => {
                      setModal(null);
                      navigate("manager");
                    }}
                  >
                    {p} · concept
                  </Button>
                ))}
              </div>
            </>
          ) : (
            <History modal={modal} brand={brand} />
          )}
        </Modal>
      )}
    </MotionRoot>
  );
}
function History({ modal, brand }) {
  const items =
    (modal === "Identity history"
      ? brand?.identityHistory
      : modal === "Strategy history"
        ? brand?.strategyHistory
        : brand?.postHistory) ?? [];
  return (
    <>
      <RevealText as="p">
        Synthetic snapshots stay separate from new drafts. These are not server
        receipts.
      </RevealText>
      {items.length ? (
        <ol className="history-list">
          {items.map((r, i) => (
            <li key={i}>
              <strong>
                {r.title ?? brand.name} · version {r.version}
              </strong>
              <RevealText as="p">
                {r.status} · {r.voice ?? r.caption ?? "Strategy snapshot"}
              </RevealText>
            </li>
          ))}
        </ol>
      ) : (
        <RevealText as="p" className="notice">
          No approved snapshots in this demo scope yet.
        </RevealText>
      )}
      {modal === "Identity history" && (
        <>
          <RevealText as="p">
            Demo source version {brand.sourceVersion}:{" "}
            {brand.withdrawn
              ? "consent withdrawn"
              : currentConsent(brand)
                ? "consent allowed"
                : "consent not allowed"}
            .
          </RevealText>
          <RevealText as="h3">Consent decisions</RevealText>
          {brand.consentHistory.length ? (
            <ol>
              {brand.consentHistory.map((event, i) => (
                <li key={i}>
                  Source version {event.version} · {event.action} · demo
                  decision {i + 1}
                </li>
              ))}
            </ol>
          ) : (
            <RevealText as="p">No demo consent decisions yet.</RevealText>
          )}
        </>
      )}
    </>
  );
}
