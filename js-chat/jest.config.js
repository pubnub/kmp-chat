module.exports = {
  bail: false,
  reporters: [["jest-silent-reporter", { "useDots": true }], "jest-junit", "summary"],
  testTimeout: 10000
}
