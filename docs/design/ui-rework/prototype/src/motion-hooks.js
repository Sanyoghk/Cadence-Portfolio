import { useEffect, useLayoutEffect, useRef } from "react";

// Motion is optional. A failed chunk must not prevent forms, routes or approvals.
let enginePromise;
export function loadMotion() {
  enginePromise ??= import("./motion-engine.js").catch(() => null);
  return enginePromise;
}

/**
 * @param {import("react").RefObject<HTMLElement | null>} ref
 * @param {"landing" | "header" | "curtain" | "dialog" | "text"} scene
 * @param {string | boolean} key
 */
export function useMotionScene(ref, scene, key = true) {
  useEffect(() => {
    if (scene === "text" && !key) return;
    let live = true;
    let dispose;
    loadMotion()
      .then(async (engine) => {
        if (scene === "landing") await document.fonts?.ready;
        if (live && engine && ref.current) {
          dispose = engine.mountScene(scene, ref.current, key);
        }
      })
      .catch(() => {
        /* Optional enhancement; base content is already visible. */
      });
    return () => {
      live = false;
      dispose?.();
    };
  }, [ref, scene, key]);
}

// Capture before React changes the DOM; Flip reads bounds only for this action.
export function useContentFlip(ref, mode) {
  const engine = useRef(null);
  const controller = useRef(null);
  const pending = useRef(null);
  useEffect(() => {
    let live = true;
    loadMotion().then((loaded) => {
      if (live) engine.current = loaded;
    });
    return () => {
      live = false;
      engine.current = null;
      controller.current?.dispose();
      controller.current = null;
      pending.current = null;
    };
  }, []);
  useLayoutEffect(() => {
    if (pending.current && controller.current) {
      controller.current.play(pending.current);
      pending.current = null;
    }
  }, [mode]);
  return () => {
    if (!ref.current || !engine.current) return;
    try {
      controller.current ??= engine.current.createContentFlip(ref.current);
      pending.current = controller.current.capture();
    } catch {
      pending.current = null; // The user's state change still proceeds immediately.
    }
  };
}
