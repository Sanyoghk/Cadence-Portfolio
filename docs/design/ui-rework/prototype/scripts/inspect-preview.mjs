// A real three-second intro is a user requirement. Capture the settled UI after it;
// do not change or bypass the application, hide findings, or patch the skill files.
import { Page } from "../../../../../.agents/skills/design-taste-frontend/scripts/lib/cdp.mjs";
const original = Page.prototype.goto;
Page.prototype.goto = function (url, options = {}) {
  return original.call(this, url, { ...options, settleMs: 3600 });
};
await import("../../../../../.agents/skills/design-taste-frontend/scripts/inspect.mjs");
