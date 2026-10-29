import { RevealText } from "./TextReveal.jsx";
import React, { useEffect, useRef, useState } from "react";
import { PILLARS } from "./demo.js";
import { useMotionScene } from "./motion-hooks.js";
export function Star({ className = "" }) {
  return (
    <span aria-hidden="true" className={"star-crop " + className}>
      <img src="/assets/orbit.png" width="1696" height="960" alt="" />
    </span>
  );
}
export function Button({ secondary = false, children, ...props }) {
  return (
    <button className={secondary ? "button secondary" : "button"} {...props}>
      {children}
    </button>
  );
}
export function Field({ label, children, ...props }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children || <input {...props} />}
    </label>
  );
}
export function Pillars() {
  return (
    <div className="pillars">
      {PILLARS.map((p) => (
        <article key={p.name}>
          <div className="pillar-title">
            <RevealText as="h3">{p.name}</RevealText>
            <span>{p.weight}%</span>
          </div>
          <RevealText as="p">{p.description}</RevealText>
          <RevealText as="p" className="meta">
            {p.formats}
          </RevealText>
          <meter
            value={p.weight}
            max="100"
            aria-label={p.name + " " + p.weight + "%"}
          />
        </article>
      ))}
    </div>
  );
}
export function useDemoAction() {
  const [busy, setBusy] = useState(false),
    timer = useRef();
  useEffect(() => () => clearTimeout(timer.current), []);
  return [
    busy,
    (fn) => {
      if (busy) return;
      setBusy(true);
      timer.current = setTimeout(() => {
        fn();
        setBusy(false);
      }, 650);
    },
  ];
}
export function Modal({ title, close, children }) {
  const ref = useRef();
  useEffect(() => {
    const prior = document.activeElement,
      d = ref.current;
    d.showModal();
    return () => {
      d.close();
      prior?.focus?.();
    };
  }, []);
  useMotionScene(ref, "dialog", title);
  return (
    <dialog
      ref={ref}
      className="modal"
      aria-labelledby="dialog-title"
      onCancel={(e) => {
        e.preventDefault();
        close();
      }}
    >
      <div className="dialog-heading">
        <RevealText as="h2" id="dialog-title">
          {title}
        </RevealText>
        <button className="text-button" onClick={close}>
          Close
        </button>
      </div>
      {children}
    </dialog>
  );
}
