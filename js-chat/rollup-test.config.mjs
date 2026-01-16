import pkg from "./package.json" with { type: "json" }

export default [
  {
    input: "./main.mjs",
    external: ["pubnub", "format-util"],
    output: [
      {
        file:  "dist-test/index.js",
        format: "cjs",
      },
      {
        file: "dist-test/index.es.js",
        format: "esm",
      },
    ],
    plugins: [
    ],
  },
]
