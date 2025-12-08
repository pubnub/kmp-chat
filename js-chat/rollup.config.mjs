import pkg from "./package.json" assert { type: "json" }
import terser from "@rollup/plugin-terser"
import resolve from "@rollup/plugin-node-resolve"
import commonjs from "@rollup/plugin-commonjs"
import gzip from "rollup-plugin-gzip"

// Get PubNub version from dependencies
const pubnubVersion = pkg.dependencies?.pubnub || "unknown"

// Common banner for CDN builds
const banner = `/**
 * PubNub Chat SDK v${pkg.version}
 * Includes PubNub JS SDK v${pubnubVersion}
 * https://www.pubnub.com/
 */`

const minBanner = `/*! PubNub Chat SDK v${pkg.version} | Includes PubNub JS SDK v${pubnubVersion} | https://www.pubnub.com/ */`

// Common plugins for CDN builds
const cdnPlugins = [
  resolve({
    browser: true,
    preferBuiltins: false
  }),
  commonjs()
]

export default [
  // ===========================================
  // NPM builds (CommonJS + ESM) - PubNub as external dependency
  // ===========================================
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
  // CDN builds (gzipped) - PubNub bundled inside
  // ===========================================

  // Unminified + gzip
  {
    input: "./main.mjs",
    external: [],
    output: {
      file: `upload/pubnub-chat.${pkg.version}.js`,
      format: "umd",
      name: "PubNubChat",
      banner,
    },
    plugins: [
      ...cdnPlugins,
      gzip({ fileName: '' })
    ],
  },
  // Minified + gzip
  {
    input: "./main.mjs",
    external: [],
    output: {
      file: `upload/pubnub-chat.${pkg.version}.min.js`,
      format: "umd",
      name: "PubNubChat",
      banner: minBanner,
    },
    plugins: [
      ...cdnPlugins,
      terser({
        format: {
          comments: /^!/
        }
      }),
      gzip({ fileName: '' })
    ],
  },
]