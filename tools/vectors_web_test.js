#!/usr/bin/env node
// Runs the shared test cases (app/src/test/resources/vectors.json) against the web version,
// so the web version and the apps keep giving the same answers.
//
//   npm install --no-save jsdom     (once)
//   node tools/vectors_web_test.js
const fs = require("fs"), path = require("path");
const { JSDOM } = require("jsdom");
const root = path.resolve(__dirname, "..");
const read = f => fs.readFileSync(path.join(root, f), "utf8");
const vectors = JSON.parse(read("app/src/test/resources/vectors.json"));

const dom = new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>', { runScripts: "outside-only", url: "https://example.org/app/", pretendToBeVisual: true });
const w = dom.window;
w.matchMedia = () => ({ matches: false, addEventListener() {} });
w.scrollTo = () => {};
w.localStorage.setItem("speechsplit.data.v1", JSON.stringify({ plans: [], runs: [], folders: [], settings: { tutorialSeen: true, lastSeenVersion: "test", language: "ENGLISH" } }));
for (const f of ["docs/app/strings.js", "docs/app/strings3.js", "docs/app/app.js"]) w.eval(read(f));
const fn = w.SpeechSplitWeb.fn;

let failed = 0, count = 0;
function check(name, input, got, want) {
  count++;
  if (got !== want) { failed++; console.error(`FAIL ${name}(${JSON.stringify(input)}): got ${JSON.stringify(got)}, want ${JSON.stringify(want)}`); }
}
const VERDICT = { ok: "ON_TIME", bitOver: "BIT_OVER", over: "OVER", bitUnder: "BIT_UNDER", under: "UNDER" };
vectors.parseTime.forEach(([i, o]) => check("parseTime", i, fn.parseTime(i), o));
vectors.formatTime.forEach(([i, o]) => check("formatTime", i, fn.fmt(i), o));
vectors.formatDiff.forEach(([i, o]) => check("formatDiff", i, fn.fmtDiff(i), o));
vectors.timeForInput.forEach(([i, o]) => check("timeForInput", i, fn.timeForInput(i), o));
vectors.verdict.forEach(([d, t, o]) => check("verdict", [d, t], VERDICT[fn.verdict(d, t)], o));
vectors.runDiff.forEach(v => {
  const run = { segments: [{ target: v.target, actual: v.actual }], buffer: v.buffer };
  if (v.meeting) run.meeting = "midweek";
  check("runDiff", v, fn.runDiff(run), v.diff);
  check("bufferUsed", v, fn.bufferUsed(run), v.used);
});
vectors.weekLabel.forEach(([d, l, o]) => check("weekLabel", [d, l], fn.weekLabel(d, l), o));
console.log(failed ? `${failed} of ${count} failed` : `All ${count} shared test cases pass in the web version`);
process.exit(failed ? 1 : 0);
