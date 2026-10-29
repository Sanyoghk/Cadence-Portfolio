// Reuse repository tooling; the working frontend is not changed or linted here.
import js from "../../../../frontend/node_modules/@eslint/js/src/index.js";
import globals from "../../../../frontend/node_modules/globals/index.js";
export default [
  {
    ...js.configs.recommended,
    files: ["src/**/*.{js,jsx}"],
    languageOptions: {
      globals: globals.browser,
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    rules: {
      ...js.configs.recommended.rules,
      "no-unused-vars": [
        "error",
        { varsIgnorePattern: "^[A-Z]", argsIgnorePattern: "^_" },
      ],
    },
  },
];
