import { defineConfig, globalIgnores } from 'eslint/config';
import js from '@eslint/js';
import ts from 'typescript-eslint';
import hooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
export default defineConfig([
  js.configs.recommended,
  ...ts.configs.recommended,
  hooks.configs.flat.recommended,
  { languageOptions: { globals: { ...globals.browser, ...globals.node } } },
  globalIgnores(['.next/**', 'out/**', 'playwright-report/**', 'test-results/**', 'next-env.d.ts']),
]);
