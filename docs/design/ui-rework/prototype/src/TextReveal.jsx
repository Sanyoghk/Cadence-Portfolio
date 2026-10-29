import React, { createContext, useContext, useRef } from "react";
import { useMotionScene } from "./motion-hooks.js";

const TextReady = createContext(false);

// Keep readiness in React; content never depends on an animation finishing.
export function MotionRoot({ ready, ...props }) {
  return (
    <TextReady.Provider value={ready}>
      <div {...props} />
    </TextReady.Provider>
  );
}

export function RevealText({
  as: Tag = "p",
  mode = "auto",
  children,
  ...props
}) {
  const ready = useContext(TextReady);
  const ref = useRef(null);
  useMotionScene(ref, "text", ready && mode === "auto");
  return (
    <Tag ref={ref} data-text-reveal="" {...props}>
      <span className="text-reveal-mask">
        <span className="text-reveal-inner">{children}</span>
      </span>
    </Tag>
  );
}
