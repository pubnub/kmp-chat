import pkg from "./package.json" assert { type: "json" }
import terser from "@rollup/plugin-terser"
import resolve from "@rollup/plugin-node-resolve"
import commonjs from "@rollup/plugin-commonjs"

// Get PubNub version from dependencies
const pubnubVersion = pkg.dependencies?.pubnub || "unknown"

export default [
  // NPM builds (CommonJS + ESM) - PubNub as external dependency
  {
    input: "./main.mjs",
    external: ["pubnub", "format-util"],
    output: [
      {
        file: pkg.main,
        format: "cjs",
      },
      {
        file: pkg.module,
        format: "esm",
      },
    ],
    plugins: [
      terser()
    ],
  },

  // ===========================================
  // CDN builds (UMD) - PubNub bundled inside
  // ===========================================

  // CDN build (UMD, unminified) - for debugging
  {
    input: "./main.mjs",
    external: [],  // Bundle everything
    output: {
      file: `dist/pubnub-chat.${pkg.version}.js`,
      format: "umd",
      name: "PubNubChat",
      banner: `/**
 * PubNub Chat SDK v${pkg.version}
 * Includes PubNub JS SDK v${pubnubVersion}
 * https://www.pubnub.com/
 */`,
    },
    plugins: [
      resolve({
        browser: true,
        preferBuiltins: false
      }),
      commonjs()
    ],
  },
  // CDN build (UMD, minified) - for production
  {
    input: "./main.mjs",
    external: [],  // Bundle everything
    output: {
      file: `dist/pubnub-chat.${pkg.version}.min.js`,
      format: "umd",
      name: "PubNubChat",
      banner: `/*! PubNub Chat SDK v${pkg.version} | Includes PubNub JS SDK v${pubnubVersion} | https://www.pubnub.com/ */`,
    },
    plugins: [
      resolve({
        browser: true,
        preferBuiltins: false
      }),
      commonjs(),
      terser({
        format: {
          comments: /^!/
        }
      })
    ],
  },
]