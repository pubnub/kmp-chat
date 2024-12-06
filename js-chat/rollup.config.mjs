import pkg from "./package.json" assert { type: "json" }
import terser from "@rollup/plugin-terser"
import commonjs from "@rollup/plugin-commonjs"

export default [
  {
    input: "../build/dist/js/productionLibrary/pubnub-chat.js",
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
      terser(),
      commonjs()
    ],
  },
]
