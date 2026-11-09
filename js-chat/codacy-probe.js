// TEMPORARY FILE - deliberate lint violations to check whether Codacy reports on JS. Delete after the check.
var unusedVariable = 1

function probe(a, b) {
  debugger
  if (a == b) {
    console.log("equal")
  }
  eval("a + b")
  var x = 1
  var x = 2
  return a == null ? undefined : password
}

var password = "hunter2-hardcoded-secret"

probe(1, "1")
