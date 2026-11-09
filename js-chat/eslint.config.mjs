import js from "@eslint/js";
import globals from "globals";
import tseslint from "typescript-eslint";

export default tseslint.config(
  {
    // Generated output and local untracked scratch (CI only sees tracked files).
    ignores: ["dist/**", "dist-test/**", "upload/**", "node_modules/**", "react-repro/**", "timing-test.js"],
  },
  {
    files: ["**/*.{js,mjs,cjs}"],
    extends: [js.configs.recommended],
    languageOptions: { globals: globals.node },
  },
  {
    // No "type" in package.json, so .js is CommonJS.
    files: ["jest.config.js", "babel.config.js", "**/*.cjs"],
    languageOptions: { sourceType: "commonjs", globals: globals.node },
  },
  {
    files: ["**/*.ts"],
    extends: [tseslint.configs.recommended],
    languageOptions: { globals: { ...globals.node, ...globals.jest } },
  },
  {
    files: ["tests/**/*.ts"],
    rules: {
      "@typescript-eslint/no-explicit-any": "off",
    },
  },
);
