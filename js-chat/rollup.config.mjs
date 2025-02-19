import pkg from "./package.json" assert { type: "json" }
import terser from "@rollup/plugin-terser"

export default [
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
]
