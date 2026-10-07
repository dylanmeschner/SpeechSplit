// ============================================================================
// SPEECH SPLIT · WEB VERSION
// The same app as on Android and Windows, for iPad, Mac and any other browser.
// Everything is stored in this browser (localStorage). Nothing is sent anywhere.
// The timer works exactly like the app's TimerEngine.kt: every clock comes from one
// running total of milliseconds, so the segment clocks always add up to the total.
// ============================================================================
(function () {
  "use strict";
  var APP_VERSION = "2.2";
  var REPO = "dylanmeschner/SpeechSplit";
  var SITE = "https://dylanmeschner.github.io/SpeechSplit/";
  var RELEASES = "https://github.com/" + REPO + "/releases";
  var FEEDBACK_EMAIL = "dylan123@live.no";
  var STORE_KEY = "speechsplit.data.v1", RUN_KEY = "speechsplit.run.v1";
  var MAX_RUNS = 50, RECENT = 5, MIN_RUNS_FOR_SUGGESTION = 3;
  var WARNING_OPTIONS = [0, 5, 10, 15, 20], CELEBRATE_OPTIONS = [-1, 0, 2, 5], QUICK_OPTIONS = [5, 10, 15, 20, 30, 45], PACE_OPTIONS = [110, 130, 150];
  var IM = window.SpeechImport;

  // --- Icons (Material Symbols paths) ------------------------------------------
  var P = {
    add: "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z",
    timer: "M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61l1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42C16.07 4.74 14.12 4 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z",
    settings: "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z",
    more: "M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z",
    sort: "M3 18h6v-2H3v2zM3 6v2h18V6H3zm0 7h12v-2H3v2z",
    download: "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z",
    edit: "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z",
    share: "M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92-1.31-2.92-2.92-2.92z",
    archive: "M20.54 5.23l-1.39-1.68C18.88 3.21 18.47 3 18 3H6c-.47 0-.88.21-1.16.55L3.46 5.23C3.17 5.57 3 6.02 3 6.5V19c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6.5c0-.48-.17-.93-.46-1.27zM12 17.5L6.5 12H10v-2h4v2h3.5L12 17.5zM5.12 5l.81-1h12l.94 1H5.12z",
    unarchive: "M20.55 5.22l-1.39-1.68C18.88 3.21 18.47 3 18 3H6c-.47 0-.88.21-1.15.55L3.46 5.22C3.17 5.57 3 6.01 3 6.5V19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6.5c0-.49-.17-.93-.45-1.28zM12 9.5l5.5 5.5H14v2h-4v-2H6.5L12 9.5zM5.12 5l.82-1h12l.93 1H5.12z",
    del: "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z",
    back: "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z",
    close: "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z",
    play: "M8 5v14l11-7z",
    pause: "M6 19h4V5H6v14zm8-14v14h4V5h-4z",
    next: "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z",
    flag: "M14.4 6L14 4H5v17h2v-7h5.6l.4 2h7V6z",
    undo: "M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z",
    check: "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z",
    circle: "M12 2C6.47 2 2 6.47 2 12s4.47 10 10 10 10-4.47 10-10S17.53 2 12 2zm0 18c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8z",
    full: "M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z",
    fullExit: "M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z",
    doc: "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z",
    paste: "M19 2h-4.18C14.4.84 13.3 0 12 0c-1.3 0-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm7 18H5V4h2v3h10V4h2v16z",
    notes: "M23 12l-2.44-2.78.34-3.68-3.61-.82-1.89-3.18L12 3 8.6 1.54 6.71 4.72l-3.61.81.34 3.68L1 12l2.44 2.78-.34 3.69 3.61.82 1.89 3.18L12 21l3.4 1.46 1.89-3.18 3.61-.82-.34-3.68L23 12zm-10 5h-2v-2h2v2zm0-4h-2V7h2v6z",
    feedback: "M20 2H4c-1.1 0-1.99.9-1.99 2L2 22l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-7 12h-2v-2h2v2zm0-4h-2V6h2v4z",
    web: "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z",
    code: "M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z",
    shield: "M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z",
    bug: "M20 8h-2.81c-.45-.78-1.07-1.45-1.82-1.96L17 4.41 15.59 3l-2.17 2.17C12.96 5.06 12.49 5 12 5c-.49 0-.96.06-1.41.17L8.41 3 7 4.41l1.62 1.63C7.88 6.55 7.26 7.22 6.81 8H4v2h2.09c-.05.33-.09.66-.09 1v1H4v2h2v1c0 .34.04.67.09 1H4v2h2.81c1.04 1.79 2.97 3 5.19 3s4.15-1.21 5.19-3H20v-2h-2.09c.05-.33.09-.66.09-1v-1h2v-2h-2v-1c0-.34-.04-.67-.09-1H20V8zm-6 8h-4v-2h4v2zm0-4h-4v-2h4v2z",
    bulb: "M9 21c0 .55.45 1 1 1h4c.55 0 1-.45 1-1v-1H9v1zm3-19C8.14 2 5 5.14 5 9c0 2.38 1.19 4.47 3 5.74V17c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-2.26c1.81-1.27 3-3.36 3-5.74 0-3.86-3.14-7-7-7z",
    chat: "M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z",
    chev: "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z",
    ext: "M19 19H5V5h7V3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z",
    up: "M7.41 15.41L12 10.83l4.59 4.58L18 14l-6-6-6 6z",
    down: "M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z",
    info: "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z",
    sparkle: "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z",
    clock: "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z",
    install: "M18 1.01L8 1c-1.1 0-2 .9-2 2v3h2V5h10v14H8v-1H6v3c0 1.1.9 2 2 2h10c1.1 0 2-.8 2-2V3c0-1.1-.9-1.99-2-1.99zM10 15h2V8H5v2h3.59L3 15.59 4.41 17 10 11.41z",
    inventory: "M20 2H4c-1 0-2 .9-2 2v3.01c0 .72.43 1.34 1 1.69V20c0 1.1 1.1 2 2 2h14c.9 0 2-.9 2-2V8.7c.57-.35 1-.97 1-1.69V4c0-1.1-1-2-2-2zm-5 12H9v-2h6v2zm5-7H4V4l16-.02V7z",
  };
  function icon(name) { return '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="' + P[name] + '"/></svg>'; }

  // --- Helpers -----------------------------------------------------------------
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  function newId() { return (crypto.randomUUID && crypto.randomUUID()) || "id-" + Date.now().toString(36) + Math.random().toString(36).slice(2); }
  function pad2(n) { return String(n).padStart(2, "0"); }
  function fmt(sec) { var a = Math.abs(sec); return (sec < 0 ? "-" : "") + pad2(Math.floor(a / 60)) + ":" + pad2(a % 60); }
  function fmtDiff(d) { return d > 0 ? "+" + fmt(d) : fmt(d); }
  /** "4.30", "4,30" and "4:30" all mean 4 min 30 s, like in the app. */
  function parseTime(input) {
    var parts = String(input).trim().replace(/,/g, ".").replace(/:/g, ".").split(".");
    var m = parseInt(parts[0], 10) || 0;
    var s = parts[1] ? parseInt((parts[1].slice(0, 2) + "00").slice(0, 2), 10) || 0 : 0;
    return Math.max(0, m * 60 + s);
  }
  function timeForInput(sec) { var m = Math.floor(sec / 60), s = sec % 60; return s === 0 ? String(m) : m + "." + pad2(s); }
  function roundTo5(s) { return Math.floor((s + 2) / 5) * 5; }
  function status(remaining, target, warnPct) {
    if (remaining < 0) return "over";
    if (warnPct > 0 && target > 0 && remaining <= target * warnPct / 100) return "warn";
    return "ok";
  }
  function totalTarget(plan) { return plan.segments.reduce(function (a, s) { return a + s.targetSeconds; }, 0); }
  function sum(list) { return list.reduce(function (a, b) { return a + b; }, 0); }

  // --- Storage -----------------------------------------------------------------
  var DEFAULT_SETTINGS = {
    language: "SYSTEM", themeMode: "SYSTEM", keepScreenOn: true, clockMode: "REMAINING", warningPercent: 10,
    showAdjusted: true, flashAlerts: true, vibrateAlerts: false, celebrateTolerance: 0, lecternMode: false,
    speechSort: "LAST_USED", wordsPerMinute: 130, installHintHidden: false,
  };
  var data = { plans: [], runs: [], settings: Object.assign({}, DEFAULT_SETTINGS) };
  function load() {
    try {
      var raw = localStorage.getItem(STORE_KEY);
      if (raw) {
        var d = JSON.parse(raw);
        data.plans = Array.isArray(d.plans) ? d.plans.filter(function (p) { return p && Array.isArray(p.segments); }) : [];
        data.runs = Array.isArray(d.runs) ? d.runs : [];
        data.settings = Object.assign({}, DEFAULT_SETTINGS, d.settings || {});
      }
    } catch (e) { /* first start, or storage blocked */ }
    if (WARNING_OPTIONS.indexOf(data.settings.warningPercent) < 0) data.settings.warningPercent = 10;
    if (CELEBRATE_OPTIONS.indexOf(data.settings.celebrateTolerance) < 0) data.settings.celebrateTolerance = 0;
    data.runs.sort(function (a, b) { return b.finishedAt - a.finishedAt; });
  }
  var saveFailed = false;
  function save() {
    try { localStorage.setItem(STORE_KEY, JSON.stringify(data)); saveFailed = false; }
    catch (e) { if (!saveFailed) { saveFailed = true; toast("Storage is full or blocked: changes can't be saved."); } }
  }
  // Ask the browser not to clear our storage when space runs low (Safari clears unused sites otherwise)
  if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(function () {});

  // --- Language ----------------------------------------------------------------
  var lang = "en";
  function resolveLang() {
    var l = data.settings.language;
    if (l === "ENGLISH") return "en";
    if (l === "GERMAN") return "de";
    if (l === "NORWEGIAN") return "no";
    var sys = (navigator.language || "en").slice(0, 2).toLowerCase();
    return sys === "de" ? "de" : (sys === "nb" || sys === "nn" || sys === "no") ? "no" : "en";
  }
  function t(key) { var v = window.STR[lang][key]; return v == null ? (window.STR.en[key] || key) : v; }
  // Texts with numbers in them (functions in the app's Strings.kt)
  var F = {
    en: {
      summary: function (n, tt) { return n + " segment" + (n === 1 ? "" : "s") + " · " + tt + " total"; },
      deleteTitle: function (x) { return "Delete “" + x + "”?"; }, segmentLabel: function (i) { return "Segment " + i; },
      totalPlan: function (x) { return "TOTAL · PLAN " + x; }, nowOf: function (i, n) { return "NOW · " + i + " OF " + n; },
      overPlan: function (x) { return x + " over plan"; }, underPlan: function (x) { return x + " under plan"; },
      percent: function (x) { return x + "%"; }, within: function (x) { return "±" + x + " s"; },
      statAverage: function (x) { return "Average (last " + x + ")"; },
      suggestOver: function (a, s) { return "You average " + a + " here. Plan " + s + ", or trim this part."; },
      suggestUnder: function (a, s) { return "You average " + a + " here. Plan " + s + ", or add material."; },
      reportPlan: function (x) { return "plan " + x; }, quickName: function (m) { return "Quick timer · " + m + " min"; },
      version: function (v) { return "Version " + v; }, archiveCount: function (n) { return "Archive (" + n + ")"; },
      pace: function (w) { return (w === 110 ? "Calm" : w === 150 ? "Brisk" : "Normal") + " " + w; },
      part: function (i) { return "Part " + i; }, words: function (n) { return n + " words"; },
    },
    de: {
      summary: function (n, tt) { return n + " " + (n === 1 ? "Abschnitt" : "Abschnitte") + " · " + tt + " gesamt"; },
      deleteTitle: function (x) { return "„" + x + "“ löschen?"; }, segmentLabel: function (i) { return "Abschnitt " + i; },
      totalPlan: function (x) { return "GESAMT · PLAN " + x; }, nowOf: function (i, n) { return "JETZT · " + i + " VON " + n; },
      overPlan: function (x) { return x + " über Plan"; }, underPlan: function (x) { return x + " unter Plan"; },
      percent: function (x) { return x + " %"; }, within: function (x) { return "±" + x + " s"; },
      statAverage: function (x) { return "Schnitt (letzte " + x + ")"; },
      suggestOver: function (a, s) { return "Hier liegst du im Schnitt bei " + a + ". Plane " + s + " ein oder kürze diesen Teil."; },
      suggestUnder: function (a, s) { return "Hier liegst du im Schnitt bei " + a + ". Plane " + s + " ein oder ergänze Inhalt."; },
      reportPlan: function (x) { return "Plan " + x; }, quickName: function (m) { return "Schnell-Timer · " + m + " Min."; },
      version: function (v) { return "Version " + v; }, archiveCount: function (n) { return "Archiv (" + n + ")"; },
      pace: function (w) { return (w === 110 ? "Ruhig" : w === 150 ? "Zügig" : "Normal") + " " + w; },
      part: function (i) { return "Teil " + i; }, words: function (n) { return n + " Wörter"; },
    },
    no: {
      summary: function (n, tt) { return n + " " + (n === 1 ? "del" : "deler") + " · " + tt + " totalt"; },
      deleteTitle: function (x) { return "Slette «" + x + "»?"; }, segmentLabel: function (i) { return "Del " + i; },
      totalPlan: function (x) { return "TOTALT · PLAN " + x; }, nowOf: function (i, n) { return "NÅ · " + i + " AV " + n; },
      overPlan: function (x) { return x + " over planen"; }, underPlan: function (x) { return x + " under planen"; },
      percent: function (x) { return x + " %"; }, within: function (x) { return "±" + x + " s"; },
      statAverage: function (x) { return "Snitt (siste " + x + ")"; },
      suggestOver: function (a, s) { return "Her ligger du i snitt på " + a + ". Sett av " + s + ", eller kort ned denne delen."; },
      suggestUnder: function (a, s) { return "Her ligger du i snitt på " + a + ". Sett av " + s + ", eller legg til mer stoff."; },
      reportPlan: function (x) { return "plan " + x; }, quickName: function (m) { return "Hurtigtimer · " + m + " min"; },
      version: function (v) { return "Versjon " + v; }, archiveCount: function (n) { return "Arkiv (" + n + ")"; },
      pace: function (w) { return (w === 110 ? "Rolig" : w === 150 ? "Raskt" : "Normalt") + " " + w; },
      part: function (i) { return "Del " + i; }, words: function (n) { return n + " ord"; },
    },
  };
  function f(name) { return F[lang][name]; }
  function summaryOf(plan) { return f("summary")(plan.segments.length, fmt(totalTarget(plan))); }
  function fmtDate(ms) {
    try { return new Date(ms).toLocaleString(lang === "no" ? "nb-NO" : lang, { dateStyle: "medium", timeStyle: "short" }); }
    catch (e) { return new Date(ms).toLocaleString(); }
  }

  // --- Theme -------------------------------------------------------------------
  var darkQuery = matchMedia("(prefers-color-scheme: dark)");
  function applyTheme() {
    var m = data.settings.themeMode;
    var dark = m === "DARK" || (m === "SYSTEM" && darkQuery.matches);
    document.documentElement.dataset.theme = dark ? "dark" : "light";
  }
  darkQuery.addEventListener && darkQuery.addEventListener("change", applyTheme);

  // --- App state ---------------------------------------------------------------
  var ui = {
    screen: "library",       // library | archive | settings | edit | ready | timer | history
    plan: null,              // the active speech (a copy while editing)
    editOriginal: null, editReturn: "library", quick: false,
    importDraft: null, busy: false, expandedRun: null, celebrating: false,
  };
  function planById(id) { return data.plans.filter(function (p) { return p.id === id; })[0]; }
  function recent(p) { return Math.max(p.lastUsedAt || 0, p.createdAt || 0); }
  function sorted(list) {
    var s = data.settings.speechSort;
    return list.slice().sort(function (a, b) {
      if (s === "NAME") return a.title.trim().localeCompare(b.title.trim(), undefined, { sensitivity: "base" });
      if (s === "CREATED") return (b.createdAt || 0) - (a.createdAt || 0);
      return recent(b) - recent(a);
    });
  }
  function visiblePlans() { return sorted(data.plans.filter(function (p) { return !p.archived; })); }
  function archivedPlans() { return sorted(data.plans.filter(function (p) { return p.archived; })); }
  function runsFor(id) { return data.runs.filter(function (r) { return r.planId === id; }); }
  function replacePlan(id, fn) {
    data.plans = data.plans.map(function (p) { return p.id === id ? fn(Object.assign({}, p)) : p; });
    save();
    if (ui.plan && ui.plan.id === id && ui.screen !== "edit") ui.plan = planById(id);
  }

  // --- Timer engine (port of TimerEngine.kt) -----------------------------------
  var E = { count: 0, index: 0, running: false, rec: [], start: 0, now: 0, finAt: null };
  function clockMs() { return Date.now(); } // wall clock: keeps counting while an iPad sleeps
  function live() { return E.running ? Math.max(0, E.now - E.start) : 0; }
  function isActive() { return E.count > 0; }
  function isFinished() { return E.count > 0 && E.index >= E.count; }
  function msThrough(i) {
    if (i < 0) return 0;
    var s = 0;
    for (var j = 0; j <= Math.min(i, E.rec.length - 1); j++) s += E.rec[j];
    if (i >= E.index) s += live();
    return s;
  }
  function elapsedOf(i) { return Math.floor(msThrough(i) / 1000) - Math.floor(msThrough(i - 1) / 1000); }
  function totalElapsed() { return Math.floor((sum(E.rec) + live()) / 1000); }
  function allElapsed() { var out = []; for (var i = 0; i < E.count; i++) out.push(elapsedOf(i)); return out; }
  function tickNow() { E.now = clockMs(); }
  function eStart(n) { E.running = false; E.count = n; E.rec = new Array(n).fill(0); E.index = 0; E.finAt = null; eResume(); }
  function eResume() { if (E.running || isFinished() || !isActive()) return; E.start = clockMs(); E.now = E.start; E.running = true; }
  function ePause() { if (!E.running) return; commit(); E.running = false; }
  function commit() { var tt = clockMs(); addTo(E.index, tt - E.start); E.start = tt; E.now = tt; }
  function addTo(i, ms) { if (i >= 0 && i < E.rec.length) E.rec[i] += ms; }
  function eNext() {
    if (!isActive() || isFinished()) return false;
    var was = E.running;
    if (was) commit();
    E.index++;
    if (E.index >= E.count) { E.finAt = was ? clockMs() : null; E.running = false; return true; }
    return false;
  }
  function ePrevious() {
    if (E.index === 0) return;
    if (isFinished()) {
      E.index--;
      if (E.finAt != null) { addTo(E.index, clockMs() - E.finAt); eResume(); }
      E.finAt = null;
      return;
    }
    if (E.running) commit();
    var moved = E.rec[E.index];
    E.rec[E.index] = 0; E.rec[E.index - 1] += moved;
    E.index--;
  }
  function eStop() { E.running = false; E.count = 0; E.index = 0; E.rec = []; E.finAt = null; }

  // A running speech survives a reload (Safari sometimes reloads tabs in the background)
  var recordedRunId = null;
  function persistRun() {
    try {
      if (!isActive() || !ui.plan) { localStorage.removeItem(RUN_KEY); return; }
      localStorage.setItem(RUN_KEY, JSON.stringify({ E: E, plan: ui.plan, quick: ui.quick, recordedRunId: recordedRunId }));
    } catch (e) {}
  }
  function restoreRun() {
    try {
      var r = JSON.parse(localStorage.getItem(RUN_KEY) || "null");
      if (!r || !r.plan || !r.E || !r.E.count) return false;
      if (!r.quick && !planById(r.plan.id)) return false;
      Object.assign(E, r.E);
      ui.plan = r.quick ? r.plan : planById(r.plan.id);
      ui.quick = !!r.quick; recordedRunId = r.recordedRunId || null;
      if (ui.plan.segments.length !== E.count) { eStop(); return false; }
      ui.screen = "timer";
      tickNow();
      return true;
    } catch (e) { return false; }
  }

  // --- Practice runs and stats (port of History.kt) ----------------------------
  function snapshot() {
    var plan = ui.plan;
    if (!plan || !isActive()) return null;
    var secs = allElapsed();
    return { id: newId(), planId: plan.id, planTitle: plan.title, finishedAt: Date.now(),
      segments: plan.segments.map(function (s, i) { return { segmentId: s.id, title: s.title, target: s.targetSeconds, actual: secs[i] || 0 }; }) };
  }
  function runTotal(r, k) { return sum(r.segments.map(function (s) { return s[k]; })); }
  function runDiff(r) { return runTotal(r, "actual") - runTotal(r, "target"); }
  function recordRun() {
    if (ui.quick || !isFinished() || recordedRunId) return;
    var run = snapshot();
    if (!run) return;
    recordedRunId = run.id;
    var forPlan = [run].concat(runsFor(run.planId)).slice(0, MAX_RUNS);
    data.runs = forPlan.concat(data.runs.filter(function (r) { return r.planId !== run.planId; }))
      .sort(function (a, b) { return b.finishedAt - a.finishedAt; });
    save();
  }
  function unrecordRun() {
    if (!recordedRunId) return;
    var id = recordedRunId; recordedRunId = null;
    data.runs = data.runs.filter(function (r) { return r.id !== id; }); save();
  }
  function findSeg(run, seg) {
    return run.segments.filter(function (s) { return s.segmentId === seg.id; })[0] ||
      run.segments.filter(function (s) { return s.title && s.title.toLowerCase() === seg.title.toLowerCase(); })[0];
  }
  function planStats(plan, runs, tol) {
    var rec = runs.slice(0, RECENT);
    var segs = plan.segments.map(function (seg) {
      var diffs = rec.map(function (r) { var s = findSeg(r, seg); return s ? s.actual - s.target : null; }).filter(function (d) { return d != null; });
      var avg = diffs.length ? Math.round(sum(diffs) / diffs.length) : 0;
      var threshold = Math.max(10, Math.floor(seg.targetSeconds / 10));
      var sug = diffs.length >= MIN_RUNS_FOR_SUGGESTION && Math.abs(avg) >= threshold ? Math.max(5, roundTo5(seg.targetSeconds + avg)) : null;
      return { seg: seg, runs: diffs.length, avg: avg, suggested: sug !== seg.targetSeconds ? sug : null };
    });
    var best = runs.slice().sort(function (a, b) { return Math.abs(runDiff(a)) - Math.abs(runDiff(b)); })[0] || null;
    return {
      runCount: runs.length,
      onTarget: runs.filter(function (r) { return Math.abs(runDiff(r)) <= Math.max(0, tol); }).length,
      avgDiff: rec.length ? Math.round(sum(rec.map(runDiff)) / rec.length) : 0,
      best: best, segments: segs,
    };
  }
  function report(run) {
    var lines = ["Speech Split · " + run.planTitle, fmtDate(run.finishedAt),
      t("reportTotal") + " " + fmt(runTotal(run, "actual")) + " (" + f("reportPlan")(fmt(runTotal(run, "target"))) + ")  " + fmtDiff(runDiff(run)), ""];
    run.segments.forEach(function (s, i) {
      lines.push((i + 1) + ". " + s.title + "  " + fmt(s.actual) + " (" + f("reportPlan")(fmt(s.target)) + ")  " + fmtDiff(s.actual - s.target));
    });
    return lines.join("\n");
  }

  // --- Toast, dialogs, menus ---------------------------------------------------
  var toastTimer = null;
  function toast(text) {
    var old = document.querySelector(".toast"); if (old) old.remove();
    var el = document.createElement("div"); el.className = "toast"; el.setAttribute("role", "status"); el.textContent = text;
    document.body.appendChild(el);
    clearTimeout(toastTimer); toastTimer = setTimeout(function () { el.remove(); }, 3200);
  }
  var dialogEl = null, dialogClose = null;
  /** opts: { title, body (html), actions: [{label, cls, onClick}], onClose, wide } */
  function dialog(opts) {
    closeDialog();
    var scrim = document.createElement("div"); scrim.className = "scrim";
    var d = document.createElement("div"); d.className = "dialog"; d.setAttribute("role", "dialog"); d.setAttribute("aria-modal", "true");
    d.innerHTML = (opts.icon ? '<div style="text-align:center;color:var(--sub)">' + icon(opts.icon).replace("<svg", '<svg style="width:28px;height:28px"') + "</div>" : "") +
      (opts.title ? "<h3" + (opts.icon ? ' style="text-align:center"' : "") + ">" + esc(opts.title) + "</h3>" : "") +
      '<div class="body">' + (opts.body || "") + '</div><div class="actions"></div>';
    var actions = d.querySelector(".actions");
    (opts.actions || []).forEach(function (a) {
      var b = document.createElement("button");
      b.className = a.cls || "btn"; b.innerHTML = a.html || esc(a.label);
      if (a.disabled) b.disabled = true;
      if (a.id) b.id = a.id;
      b.addEventListener("click", function () { if (a.onClick) a.onClick(); else closeDialog(); });
      actions.appendChild(b);
    });
    scrim.appendChild(d);
    scrim.addEventListener("mousedown", function (e) { if (e.target === scrim) closeDialog(); });
    document.body.appendChild(scrim);
    dialogEl = scrim; dialogClose = opts.onClose || null;
    var first = d.querySelector("input, textarea");
    if (first && !opts.noFocus) setTimeout(function () { first.focus(); }, 30);
    return d;
  }
  function closeDialog() {
    if (!dialogEl) return;
    dialogEl.remove(); dialogEl = null;
    var cb = dialogClose; dialogClose = null; if (cb) cb();
  }
  function confirmDialog(title, text, okLabel, onOk, danger, extra) {
    dialog({ title: title, body: '<p class="sub" style="margin:0">' + esc(text) + "</p>",
      actions: (extra || []).concat([
        { label: t("cancel"), cls: "tbtn" },
        { label: okLabel, cls: danger ? "btn danger" : "btn", onClick: function () { closeDialog(); onOk(); } },
      ]) });
  }
  var menuEl = null;
  function menu(anchor, items) {
    closeMenu();
    var m = document.createElement("div"); m.className = "menu"; m.setAttribute("role", "menu");
    items.forEach(function (it) {
      if (it.label && !it.onClick) { var l = document.createElement("div"); l.className = "label mlabel"; l.textContent = it.label; m.appendChild(l); return; }
      var b = document.createElement("button"); b.setAttribute("role", "menuitem");
      b.innerHTML = (it.radio != null ? '<span class="radio' + (it.radio ? " on" : "") + '"></span>' : icon(it.icon)) + "<span>" + esc(it.text) + "</span>";
      b.addEventListener("click", function () { closeMenu(); it.onClick(); });
      m.appendChild(b);
    });
    document.body.appendChild(m);
    var r = anchor.getBoundingClientRect();
    var left = Math.min(r.right - m.offsetWidth, innerWidth - m.offsetWidth - 8);
    var top = r.bottom + 4;
    if (top + m.offsetHeight > innerHeight - 8) top = Math.max(8, r.top - m.offsetHeight - 4);
    m.style.left = Math.max(8, left) + "px"; m.style.top = top + "px";
    menuEl = m;
    setTimeout(function () { document.addEventListener("mousedown", outsideMenu, true); document.addEventListener("touchstart", outsideMenu, true); }, 0);
  }
  function outsideMenu(e) { if (menuEl && !menuEl.contains(e.target)) closeMenu(); }
  function closeMenu() {
    if (menuEl) { menuEl.remove(); menuEl = null; }
    document.removeEventListener("mousedown", outsideMenu, true); document.removeEventListener("touchstart", outsideMenu, true);
  }

  // --- Navigation ----------------------------------------------------------------
  var root = document.getElementById("root");
  function go(screen) {
    ui.screen = screen; closeMenu();
    if (screen !== "timer") { var c = document.querySelector("canvas.confetti"); if (c) c.remove(); }
    render(); scrollTo(0, 0);
  }
  function backToLibrary() { ui.plan = null; ui.quick = false; go("library"); }
  function openPlan(p) { ui.quick = false; ui.plan = p; go("ready"); }
  function startEditing(plan, returnTo) {
    ui.quick = false;
    ui.plan = JSON.parse(JSON.stringify(plan));
    ui.editOriginal = JSON.stringify(plan); ui.editReturn = returnTo;
    go("edit");
  }
  function hasUnsavedEdits() { return ui.screen === "edit" && JSON.stringify(ui.plan) !== ui.editOriginal; }
  function cancelEditing() {
    var orig = JSON.parse(ui.editOriginal || "null");
    if (ui.editReturn === "ready" && orig) { ui.plan = planById(orig.id) || orig; go("ready"); } else backToLibrary();
  }
  function createNew() {
    startEditing({ id: newId(), title: t("defaultSpeechTitle"), segments: [{ id: newId(), title: t("defaultFirstSegment"), targetSeconds: 120 }] }, "library");
  }
  function saveEdit() {
    var p = ui.plan;
    p.title = p.title.trim() || t("untitledSpeech");
    if (!p.createdAt) p.createdAt = Date.now();
    var i = data.plans.findIndex(function (x) { return x.id === p.id; });
    var copy = JSON.parse(JSON.stringify(p));
    if (i >= 0) data.plans[i] = copy; else data.plans.push(copy);
    save();
    ui.plan = copy; ui.editOriginal = null;
    go("ready");
  }
  function deletePlan(p) {
    data.plans = data.plans.filter(function (x) { return x.id !== p.id; });
    data.runs = data.runs.filter(function (r) { return r.planId !== p.id; });
    save();
  }
  function exportText(p) {
    return JSON.stringify({ title: p.title, segments: p.segments.map(function (s) { return { title: s.title, target: s.targetSeconds }; }) });
  }
  function share(text, title) {
    if (navigator.share) { navigator.share({ title: title, text: text }).catch(function () {}); return; }
    copy(text, t("copiedToClipboard"));
  }
  function copy(text, msg) {
    (navigator.clipboard ? navigator.clipboard.writeText(text) : Promise.reject()).then(function () { toast(msg); }, function () {
      var ta = document.createElement("textarea"); ta.value = text; document.body.appendChild(ta); ta.select();
      try { document.execCommand("copy"); toast(msg); } catch (e) {} ta.remove();
    });
  }
  /** An exported speech (JSON from any version of Speech Split). */
  function importPlanText(text) {
    try {
      var o = JSON.parse(String(text).trim());
      if (!o || !Array.isArray(o.segments) || !o.segments.length) return false;
      var plan = { id: newId(), title: String(o.title || "").trim() || t("importedSpeech"), createdAt: Date.now(),
        segments: o.segments.map(function (s) { return { id: newId(), title: String(s.title || ""), targetSeconds: Math.max(0, parseInt(s.target, 10) || 0) }; }) };
      data.plans.push(plan); save();
      return true;
    } catch (e) { return false; }
  }
  function importText(text) {
    if (importPlanText(text)) { toast(t("imported")); render(); return true; }
    var blocks = IM.blocksFromPlainText(text);
    if (!blocks.some(function (b) { return !b.heading && IM.countWords(b.text) > 0; })) return false;
    showSuggestion({ title: IM.guessTitle(blocks, t("importedSpeech")), blocks: blocks });
    return true;
  }
  function importFile(file) {
    if (ui.busy || !file) return;
    if (file.size > 30 * 1024 * 1024) { toast(t("importUnsupported")); return; }
    ui.busy = true; render();
    IM.readFile(file, function () { var b = document.querySelector(".busy span"); if (b) b.textContent = t("pdfLoading"); })
      .then(function (res) {
        ui.busy = false; render();
        if (!res) { toast(t("importUnsupported")); return; }
        if (res.kind === "plan") { if (importPlanText(res.text)) { toast(t("imported")); render(); } else toast(t("importFailed")); return; }
        var blocks = res.blocks;
        if (!blocks.some(function (b) { return !b.heading && IM.countWords(b.text) > 0; })) { toast(t("importUnreadable")); return; }
        var fromName = (file.name || "").replace(/\.[^.]+$/, "").replace(/_/g, " ").trim();
        showSuggestion({ title: IM.guessTitle(blocks, fromName || t("importedSpeech")), blocks: blocks });
      })
      .catch(function () { ui.busy = false; render(); toast(t("importUnreadable")); });
  }
  function pickFile() {
    var input = document.createElement("input");
    input.type = "file";
    input.accept = ".pdf,.docx,.odt,.rtf,.txt,.md,.json,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain";
    input.addEventListener("change", function () { if (input.files && input.files[0]) importFile(input.files[0]); });
    input.click();
  }

  // --- Timer actions -------------------------------------------------------------
  var alerted = {}, ticker = null;
  function startTimer() {
    var plan = ui.plan;
    if (!plan || !plan.segments.length) return;
    alerted = {}; ui.celebrating = false; recordedRunId = null;
    if (!ui.quick) replacePlan(plan.id, function (p) { p.lastUsedAt = Date.now(); return p; });
    eStart(plan.segments.length);
    go("timer");
    afterChange();
  }
  function startQuick(minutes) {
    ui.plan = { id: "quick", title: f("quickName")(minutes), segments: [{ id: "quick-1", title: t("quickTimer"), targetSeconds: minutes * 60 }] };
    ui.quick = true;
    startTimer();
  }
  function isPerfect() {
    var tol = data.settings.celebrateTolerance;
    return isFinished() && tol >= 0 && Math.abs(totalElapsed() - totalTarget(ui.plan)) <= tol;
  }
  function next() {
    if (eNext()) { ui.celebrating = isPerfect(); recordRun(); }
    afterChange(true);
  }
  function previous() {
    if (!isActive()) return;
    ui.celebrating = false;
    if (isFinished()) unrecordRun();
    ePrevious();
    afterChange(true);
  }
  function togglePause() { if (E.running) ePause(); else eResume(); afterChange(true); }
  function stopTimer() {
    recordRun(); recordedRunId = null;
    eStop(); ui.celebrating = false;
    afterChange();
    if (ui.quick) backToLibrary(); else go("ready");
  }
  function requestStop() {
    if (isFinished()) { stopTimer(); return; }
    confirmDialog(t("stopDialogTitle"), t("stopDialogText"), t("stop"), stopTimer, true);
  }
  function afterChange(rerender) {
    tickNow();
    if (E.running) { if (!ticker) ticker = setInterval(tick, 100); }
    else { clearInterval(ticker); ticker = null; }
    persistRun();
    syncWakeLock();
    if (rerender && ui.screen === "timer") render();
  }
  function tick() {
    tickNow();
    checkAlerts();
    if (ui.screen === "timer") updateTimer();
  }
  function checkAlerts() {
    if (!E.running) return;
    var seg = ui.plan.segments[E.index]; if (!seg) return;
    var remaining = seg.targetSeconds - elapsedOf(E.index);
    var st = status(remaining, seg.targetSeconds, data.settings.warningPercent);
    if (st === "ok") return;
    var key = E.index + ":" + st;
    if (alerted[key]) return;
    alerted[key] = true;
    if (data.settings.vibrateAlerts && navigator.vibrate) navigator.vibrate(st === "over" ? [350, 150, 350] : 180);
    if (data.settings.flashAlerts) flash(st);
  }
  function flash(st) {
    var el = document.querySelector(".flash");
    if (!el) return;
    var lect = data.settings.lecternMode;
    el.style.borderColor = st === "over" ? (lect ? "#FF6E60" : "var(--red)") : (lect ? "#FFD54F" : "var(--amber)");
    el.style.setProperty("--n", st === "over" ? 3 : 2);
    el.classList.remove("go"); void el.offsetWidth; el.classList.add("go");
  }

  // Keep the screen on while timing (Screen Wake Lock API; Safari 16.4+, Chrome, Edge)
  var wakeLock = null;
  function syncWakeLock() {
    var want = ui.screen === "timer" && data.settings.keepScreenOn && isActive() && document.visibilityState === "visible";
    if (want && !wakeLock) {
      if (!("wakeLock" in navigator)) return;
      navigator.wakeLock.request("screen").then(function (l) {
        wakeLock = l; l.addEventListener("release", function () { wakeLock = null; });
      }).catch(function () {});
    } else if (!want && wakeLock) { wakeLock.release().catch(function () {}); wakeLock = null; }
  }
  document.addEventListener("visibilitychange", function () { tickNow(); syncWakeLock(); if (ui.screen === "timer") updateTimer(); });

  // --- Rendering -----------------------------------------------------------------
  function render() {
    lang = resolveLang();
    document.documentElement.lang = lang === "no" ? "nb" : lang;
    applyTheme();
    var s = ui.screen;
    if (s === "library") renderLibrary();
    else if (s === "archive") renderArchive();
    else if (s === "settings") renderSettings();
    else if (s === "edit") renderEdit();
    else if (s === "ready") renderReady();
    else if (s === "timer") renderTimer();
    else if (s === "history") renderHistory();
    if (ui.celebrating && s === "timer") confetti();
  }
  function tabs() {
    var home = ui.screen !== "settings";
    return '<nav class="tabs"><button data-a="tab-library" class="' + (home ? "on" : "") + '"><i>' + icon("timer") + "</i>" + esc(t("tabSpeeches")) +
      '</button><button data-a="tab-settings" class="' + (!home ? "on" : "") + '"><i>' + icon("settings") + "</i>" + esc(t("tabSettings")) + "</button></nav>";
  }
  function isIos() { return /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1); }
  function isStandalone() { return matchMedia("(display-mode: standalone)").matches || navigator.standalone === true; }

  function renderLibrary() {
    var plans = visiblePlans(), arch = archivedPlans().length;
    var html = '<div class="page"><div class="head"><h1>' + esc(t("libraryTitle")) + "</h1>" +
      '<button class="ibtn" data-a="sort" title="' + esc(t("sortTitle")) + '" aria-label="' + esc(t("sortTitle")) + '">' + icon("sort") + "</button>" +
      '<button class="ibtn" data-a="import" title="' + esc(t("importLabel")) + '" aria-label="' + esc(t("importLabel")) + '">' + icon("download") + "</button></div>";
    if (!isStandalone() && !data.settings.installHintHidden) {
      html += '<div class="banner">' + icon("install") + "<div><b>" + esc(t("installTitle")) + "</b><br>" + esc(isIos() ? t("installIos") : t("installOther")) +
        '</div><button class="ibtn x" data-a="hide-install" aria-label="' + esc(t("close")) + '">' + icon("close") + "</button></div>";
    }
    if (ui.busy) html += '<div class="busy"><div class="spin"></div><span>' + esc(t("importReading")) + "</span></div>";
    html += '<div class="list">';
    if (!plans.length) {
      html += '<p class="empty">' + esc(t("noSpeeches")) + '</p><p class="empty small">' + esc(t("dropHint")) + "</p>";
    } else {
      plans.forEach(function (p) {
        html += '<div class="card plan" data-a="open" data-id="' + esc(p.id) + '" tabindex="0"><div class="txt"><b>' + esc(p.title) + '</b><span class="sub small">' + esc(summaryOf(p)) +
          '</span></div><button class="ibtn subtle" data-a="plan-menu" data-id="' + esc(p.id) + '" aria-label="' + esc(t("moreOptions")) + '">' + icon("more") + "</button></div>";
      });
    }
    if (arch) html += '<button class="tbtn sub" data-a="archive">' + icon("inventory") + " " + esc(f("archiveCount")(arch)) + "</button>";
    html += '</div><div class="stack"><button class="btn big block" data-a="new">' + icon("add") + esc(t("createNewSpeech")) + "</button>" +
      '<button class="btn outline block" data-a="quick">' + icon("timer") + esc(t("quickTimer")) + "</button></div></div>" + tabs();
    root.innerHTML = html;
  }

  function renderArchive() {
    var plans = archivedPlans();
    var html = '<div class="page"><div class="head"><button class="ibtn" data-a="back-library" aria-label="' + esc(t("back")) + '">' + icon("back") + "</button><h2>" + esc(t("archiveTitle")) + "</h2></div>";
    html += '<p class="sub small" style="margin:0 0 12px">' + esc(t("archiveEmpty").replace(/^[^.]*\.\s*/, "")) + "</p>";
    if (!plans.length) html += '<p class="empty">' + esc(t("archiveEmpty")) + "</p>";
    plans.forEach(function (p) {
      html += '<div class="card" style="padding:12px 8px 4px 18px;margin-bottom:10px"><b style="font-size:18px">' + esc(p.title) + '</b><div class="sub small">' + esc(summaryOf(p)) +
        '</div><div style="display:flex;justify-content:flex-end"><button class="tbtn sub" data-a="delete" data-id="' + esc(p.id) + '">' + esc(t("delete")) +
        '</button><button class="tbtn" data-a="restore" data-id="' + esc(p.id) + '">' + icon("unarchive") + " " + esc(t("restore")) + "</button></div></div>";
    });
    root.innerHTML = html + "</div>" + tabs();
  }

  function chips(name, options, selected, label) {
    return '<div class="chips">' + options.map(function (o) {
      return '<button class="chip' + (o === selected ? " on" : "") + '" data-a="set" data-k="' + name + '" data-v="' + esc(JSON.stringify(o)) + '">' + esc(label(o)) + "</button>";
    }).join("") + "</div>";
  }
  function switchRow(key, title, desc) {
    var on = !!data.settings[key];
    return '<div class="setting switchrow" data-a="toggle" data-k="' + key + '" role="switch" aria-checked="' + on + '" tabindex="0"><div><h4>' + esc(title) + "</h4><p>" + esc(desc) +
      '</p></div><span class="switch' + (on ? " on" : "") + '"></span></div>';
  }
  function linkRow(a, ic, text, external, href) {
    var tail = icon(external ? "ext" : "chev").replace("<svg", '<svg style="width:18px;height:18px"');
    if (href) return '<a class="linkrow" href="' + esc(href) + '" target="_blank" rel="noopener">' + icon(ic) + "<span>" + esc(text) + "</span>" + tail + "</a>";
    return '<button class="linkrow" data-a="' + a + '">' + icon(ic) + "<span>" + esc(text) + "</span>" + tail + "</button>";
  }
  function renderSettings() {
    var s = data.settings;
    var langName = function (l) { return { SYSTEM: t("languageSystem"), ENGLISH: "English", GERMAN: "Deutsch", NORWEGIAN: "Norsk" }[l]; };
    var html = '<div class="page"><div class="head"><h1>' + esc(t("settingsTitle")) + "</h1></div>";
    html += '<div class="section"><div class="label">' + esc(t("sectionGeneral")) + '</div><div class="card">' +
      '<div class="setting"><h4>' + esc(t("language")) + "</h4><p></p>" + chips("language", ["SYSTEM", "ENGLISH", "GERMAN", "NORWEGIAN"], s.language, langName) + "</div>" +
      '<div class="setting"><h4>' + esc(t("appearanceTitle")) + "</h4><p></p>" + chips("themeMode", ["SYSTEM", "LIGHT", "DARK"], s.themeMode, function (m) { return t({ SYSTEM: "themeSystem", LIGHT: "themeLight", DARK: "themeDark" }[m]); }) + "</div>" +
      switchRow("keepScreenOn", t("keepScreenOnTitle"), t("keepScreenOnDesc") + ("wakeLock" in navigator ? "" : " " + t("wakeLockMissing"))) + "</div></div>";
    html += '<div class="section"><div class="label">' + esc(t("sectionTimer")) + '</div><div class="card">' +
      '<div class="setting"><h4>' + esc(t("clockModeTitle")) + "</h4><p></p>" + chips("clockMode", ["REMAINING", "ELAPSED"], s.clockMode, function (m) { return m === "REMAINING" ? t("clockRemaining") : t("clockElapsed"); }) + "</div>" +
      '<div class="setting"><h4>' + esc(t("warningTitle")) + "</h4><p>" + esc(t("warningDesc")) + "</p>" + chips("warningPercent", WARNING_OPTIONS, s.warningPercent, function (p) { return p === 0 ? t("warningOff") : f("percent")(p); }) + "</div>" +
      switchRow("showAdjusted", t("showAdjustedTitle"), t("showAdjustedDesc")) +
      '<div class="setting"><h4>' + esc(t("celebrateTitle")) + "</h4><p>" + esc(t("celebrateDesc")) + "</p>" + chips("celebrateTolerance", CELEBRATE_OPTIONS, s.celebrateTolerance, function (v) { return v === -1 ? t("celebrateOff") : v === 0 ? t("celebrateExact") : f("within")(v); }) + "</div></div></div>";
    html += '<div class="section"><div class="label">' + esc(t("sectionAlerts")) + '</div><div class="card">' + switchRow("flashAlerts", t("flashTitle"), t("flashDesc")) +
      (navigator.vibrate && !isIos() ? switchRow("vibrateAlerts", t("vibrateTitle"), t("vibrateDesc")) : "") + "</div></div>";
    html += '<div class="section"><div class="label">' + esc(t("keyboardTitle")) + '</div><div class="card"><p class="sub small" style="margin:0;white-space:pre-line">' + esc(t("keyboardDescWeb")) + "</p></div></div>";
    html += '<div class="section"><div class="label">' + esc(t("aboutTitle")) + '</div><div class="card"><b>Speech Split</b><div class="sub small">' + esc(f("version")(APP_VERSION) + " · " + t("webVersion")) + "</div>" +
      '<p class="sub small">' + esc(t("storageNote")) + "</p>" +
      linkRow("notes", "notes", t("patchNotes")) + linkRow("feedback", "feedback", t("feedback")) +
      linkRow("", "web", t("website"), true, SITE) + linkRow("", "code", t("viewOnGitHub"), true, RELEASES) + linkRow("", "shield", t("privacy"), true, SITE + "privacy.html") +
      '<p class="sub small" style="margin:10px 0 0">' + esc(t("madeBy")) + "</p></div></div>";
    root.innerHTML = html + '<div style="height:24px"></div></div>' + tabs();
  }

  function renderEdit() {
    var p = ui.plan;
    var html = '<div class="page"><div class="head"><button class="ibtn" data-a="edit-back" aria-label="' + esc(t("back")) + '">' + icon("back") + "</button><h2>" + esc(t("editTitle")) + "</h2></div>" +
      '<label class="field"><span>' + esc(t("speechTitleLabel")) + '</span><input data-in="title" value="' + esc(p.title) + '" maxlength="120"></label>' +
      '<p class="sub small" id="edit-summary">' + esc(summaryOf(p)) + "</p><div>";
    p.segments.forEach(function (s, i) {
      html += '<div class="card seg"><div class="r"><input data-in="seg-title" data-i="' + i + '" value="' + esc(s.title) + '" aria-label="' + esc(f("segmentLabel")(i + 1)) + '" placeholder="' + esc(f("segmentLabel")(i + 1)) + '">' +
        '<button class="ibtn subtle" data-a="seg-del" data-i="' + i + '" aria-label="' + esc(t("deleteSegment")) + '">' + icon("del") + "</button></div>" +
        '<div class="r"><input class="time tnum" inputmode="decimal" data-in="seg-time" data-i="' + i + '" value="' + esc(timeForInput(s.targetSeconds)) + '" aria-label="' + esc(t("timeLabel")) + '" placeholder="' + esc(t("timeLabel")) + '">' +
        '<span class="eq tnum" id="eq-' + i + '">= ' + fmt(s.targetSeconds) + '</span><span class="grow"></span>' +
        '<button class="ibtn" data-a="seg-up" data-i="' + i + '" ' + (i === 0 ? "disabled" : "") + ' aria-label="' + esc(t("moveUp")) + '">' + icon("up") + "</button>" +
        '<button class="ibtn" data-a="seg-down" data-i="' + i + '" ' + (i === p.segments.length - 1 ? "disabled" : "") + ' aria-label="' + esc(t("moveDown")) + '">' + icon("down") + "</button></div></div>";
    });
    html += '</div><button class="tbtn" data-a="seg-add" style="align-self:flex-start;margin:6px 0 16px">' + icon("add") + " " + esc(t("addSegment")) + "</button>" +
      '<span class="grow" style="flex:1"></span><button class="btn big block" data-a="save" ' + (p.segments.length ? "" : "disabled") + ">" + esc(t("saveAndReady")) + "</button></div>";
    root.innerHTML = html;
  }

  function renderReady() {
    var p = ui.plan; if (!p) return backToLibrary();
    var runs = runsFor(p.id).length;
    root.innerHTML = '<div class="ready"><div class="label" style="letter-spacing:.15em">' + esc(t("readyToSpeak")) + "</div><h1>" + esc(p.title) + '</h1><div class="sub">' + esc(summaryOf(p)) + "</div>" +
      '<button class="btn start" data-a="start" ' + (p.segments.length ? "" : "disabled") + ">" + icon("play") + esc(t("startTimer")) + "</button>" +
      '<div class="links"><button class="tbtn sub" data-a="edit-active">' + esc(t("edit")) + '</button><button class="tbtn sub" data-a="history">' + esc(runs ? t("history") + " (" + runs + ")" : t("history")) +
      '</button><button class="tbtn sub" data-a="back-library">' + esc(t("library")) + "</button></div></div>";
  }

  // Timer: built once per change, then only the numbers are updated every tick
  function renderTimer() {
    var p = ui.plan;
    if (!p) return backToLibrary();
    var fin = isFinished(), segs = p.segments, idx = E.index;
    var lect = data.settings.lecternMode && !fin;
    var last = idx === segs.length - 1;
    var controls = '<div class="controls"><button class="btn undo" data-a="undo" ' + (idx > 0 ? "" : "disabled") + ' aria-label="' + esc(t("undoNext")) + '" title="' + esc(t("undoNext")) + '">' + icon("undo") + "</button>" +
      (fin ? '<button class="btn" data-a="stop-now">' + esc(t("done")) + "</button>"
        : '<button class="btn pause" data-a="pause" id="t-pause">' + icon(E.running ? "pause" : "play") + "<span>" + esc(E.running ? t("pause") : t("resume")) + "</span></button>" +
          '<button class="btn next" data-a="next">' + icon(last ? "flag" : "next") + "<span>" + esc(last ? t("finish") : t("next")) + "</span></button>") + "</div>";
    var html;
    if (lect) {
      html = '<div class="lectern"><div class="top"><span>' + (idx + 1) + "/" + segs.length + " · " + esc(segs[idx].title) + '</span><button class="ibtn" data-a="lectern" aria-label="' + esc(t("lecternOff")) + '">' + icon("fullExit") +
        '</button><button class="ibtn" data-a="stop" aria-label="' + esc(t("stopTimer")) + '">' + icon("close") + '</button></div><div class="face"><div class="tnum" id="l-clock"></div></div>' +
        '<div class="bottom tnum"><span id="l-total"></span><span id="l-diff"></span></div>' + controls + '<div class="flash"></div></div>';
    } else {
      html = '<div class="timer"><div class="t-top"><span class="title">' + esc(p.title) + "</span>" +
        (fin ? "" : '<button class="ibtn subtle" data-a="lectern" aria-label="' + esc(t("lecternOn")) + '" title="' + esc(t("lecternOn")) + '">' + icon("full") + "</button>") +
        '<button class="ibtn subtle" data-a="stop" aria-label="' + esc(t("stopTimer")) + '">' + icon("close") + "</button></div>" +
        '<div class="t-total"><div><div class="label" style="font-size:10px">' + esc(f("totalPlan")(fmt(totalTarget(p)))) + '</div><div class="big tnum" id="t-total"></div></div>' +
        '<div style="text-align:right;padding-bottom:6px"><div class="label" style="font-size:10px;margin-bottom:4px">' + esc(t("aheadBehind")) + '</div><span class="pill tnum" id="t-diff"></span></div></div>';
      if (!fin) {
        html += '<div class="t-seg"><div class="row"><span class="label" style="font-size:10px">' + esc(f("nowOf")(idx + 1, segs.length)) + '</span><span class="label" style="font-size:10px">' + esc(t("target") + "  " + fmt(segs[idx].targetSeconds)) + "</span></div>" +
          "<h3>" + esc(segs[idx].title) + '</h3><div class="label" style="font-size:10px" id="t-clocklabel"></div><div class="clock tnum" id="t-clock"></div>' +
          (idx > 0 && data.settings.showAdjusted ? '<div class="adj" id="t-adj"><div style="flex:1"><div class="l" id="t-adjlabel"></div><div class="v tnum" id="t-adjv"></div></div><div style="text-align:right"><div class="label" style="font-size:10px;margin-bottom:4px">' + esc(t("banked")) + '</div><span class="pill tnum" id="t-banked"></span></div></div>' : "") + "</div>";
      } else {
        var diff = totalElapsed() - totalTarget(p), perfect = isPerfect();
        html += '<div class="finished"><div class="ic">' + (perfect ? "🏆" : "🏁") + "</div><h3>" + esc(perfect ? t("perfectTiming") : t("finished")) + '</h3><div class="sub">' +
          esc(diff > 0 ? f("overPlan")(fmt(diff)) : diff < 0 ? f("underPlan")(fmt(-diff)) : t("onTime")) + '</div><button class="tbtn" data-a="share-report" style="margin-top:8px">' + icon("share") + " " + esc(t("shareReport")) + "</button></div>";
      }
      html += '<div class="t-list full">';
      segs.forEach(function (s, i) {
        var cls = i === idx ? "t-li cur" : "t-li";
        html += '<div class="' + cls + '" id="li-' + i + '">' + icon(i < idx ? "check" : i === idx ? "play" : "circle") + '<span class="n">' + esc(s.title) + '</span><span class="pill tnum' + (i <= idx && !fin || i < idx ? "" : " hidden") + '" id="lp-' + i + '"></span><span class="tnum" style="font-weight:700;font-size:14px">' + fmt(s.targetSeconds) + "</span></div>";
      });
      html += "</div>";
      var nx = segs[idx + 1];
      html += '<div class="upnext t-li" style="display:none"><span class="label" style="font-size:10px">' + esc(t("upNext")) + '</span><span class="n">' + (nx ? esc(nx.title) : "") + "</span>" + (nx ? '<span class="tnum">' + fmt(nx.targetSeconds) + "</span>" : "") + "</div>";
      html += controls + '<div class="flash"></div></div>';
    }
    root.innerHTML = html;
    updateTimer();
    var cur = document.getElementById("li-" + idx);
    if (cur && cur.scrollIntoView) cur.scrollIntoView({ block: "nearest" });
  }
  function setPill(el, d, warn) { if (!el) return; el.textContent = fmtDiff(d); el.className = "pill tnum" + (d > 0 ? " over" : warn ? " warn" : ""); }
  function updateTimer() {
    var p = ui.plan; if (!p) return;
    var segs = p.segments, idx = E.index, fin = isFinished(), st = data.settings;
    var el = allElapsed(), tot = totalElapsed();
    var pastBanked = 0;
    for (var i = 0; i < Math.min(idx, segs.length); i++) pastBanked += el[i] - segs[i].targetSeconds;
    var cur = segs[idx];
    var segDiff = cur ? el[idx] - cur.targetSeconds : 0;
    var schedule = fin ? tot - totalTarget(p) : pastBanked + Math.max(0, segDiff);
    var $ = function (id) { return document.getElementById(id); };
    if (st.lecternMode && !fin && $("l-clock")) {
      var rem = cur.targetSeconds - el[idx], s1 = status(rem, cur.targetSeconds, st.warningPercent);
      $("l-clock").textContent = st.clockMode === "REMAINING" ? (rem < 0 ? fmtDiff(-rem) : fmt(rem)) : fmt(el[idx]);
      $("l-clock").className = "tnum" + (s1 === "ok" ? "" : " " + s1);
      $("l-total").textContent = t("reportTotal") + " " + fmt(tot);
      $("l-diff").textContent = fmtDiff(schedule);
      $("l-diff").style.color = schedule > 0 ? "#FF6E60" : "#fff";
      return;
    }
    if (!$("t-total")) return;
    $("t-total").textContent = fmt(tot);
    setPill($("t-diff"), schedule);
    if (cur && !fin) {
      var remaining = cur.targetSeconds - el[idx], s2 = status(remaining, cur.targetSeconds, st.warningPercent), over = s2 === "over";
      if (st.clockMode === "REMAINING") {
        $("t-clocklabel").textContent = (over ? t("segmentOvertime") : t("segmentRemaining")) + " · " + t("elapsedShort") + " " + fmt(el[idx]);
        $("t-clock").textContent = over ? fmtDiff(-remaining) : fmt(remaining);
      } else {
        $("t-clocklabel").textContent = t("segmentElapsed") + " · " + (over ? t("overShort") + " " + fmtDiff(-remaining) : t("remainingShort") + " " + fmt(remaining));
        $("t-clock").textContent = fmt(el[idx]);
      }
      $("t-clock").className = "clock tnum" + (s2 === "ok" ? "" : " " + s2);
      if ($("t-adj")) {
        var adj = remaining - pastBanked, s3 = status(adj, cur.targetSeconds, st.warningPercent);
        var color = s3 === "over" ? "var(--red)" : s3 === "warn" ? "var(--amber)" : "var(--blue)";
        $("t-adj").style.borderColor = "color-mix(in srgb, " + color + " 70%, transparent)";
        $("t-adj").style.background = "color-mix(in srgb, " + color + " 12%, transparent)";
        $("t-adjlabel").textContent = s3 === "over" ? t("adjustedOver") : t("adjustedIncBanked");
        $("t-adjlabel").style.color = color; $("t-adjv").style.color = color;
        $("t-adjv").textContent = adj < 0 ? fmtDiff(-adj) : fmt(adj);
        setPill($("t-banked"), pastBanked);
      }
    }
    segs.forEach(function (s, i) {
      var pill = $("lp-" + i); if (!pill) return;
      if (i < idx || (fin && i < segs.length)) setPill(pill, el[i] - s.targetSeconds);
      else if (i === idx) setPill(pill, segDiff, status(-segDiff, s.targetSeconds, st.warningPercent) === "warn");
    });
    var pb = $("t-pause");
    if (pb && pb.dataset.running !== String(E.running)) {
      pb.dataset.running = String(E.running);
      pb.innerHTML = icon(E.running ? "pause" : "play") + "<span>" + esc(E.running ? t("pause") : t("resume")) + "</span>";
      pb.style.background = E.running ? "" : "var(--ink)"; pb.style.color = E.running ? "" : "var(--on-ink)";
    }
  }

  function renderHistory() {
    var p = ui.plan; if (!p) return backToLibrary();
    var runs = runsFor(p.id), st = planStats(p, runs, data.settings.celebrateTolerance);
    var html = '<div class="page"><div class="head"><button class="ibtn" data-a="close-history" aria-label="' + esc(t("back")) + '">' + icon("back") + '</button><div style="flex:1;min-width:0"><h2>' + esc(t("historyTitle")) + '</h2><div class="sub small">' + esc(p.title) + "</div></div></div>";
    if (!runs.length) { root.innerHTML = html + '<p class="empty">' + esc(t("noHistory")) + "</p></div>"; return; }
    var pill = function (d) { return '<span class="pill tnum' + (d > 0 ? " over" : "") + '">' + fmtDiff(d) + "</span>"; };
    html += '<div class="stats"><div class="card stat"><div class="label">' + esc(t("statRuns")) + '</div><div class="v">' + st.runCount + '</div></div><div class="card stat"><div class="label">' + esc(t("statOnTarget")) + '</div><div class="v">' + st.onTarget + "</div></div>" +
      '<div class="card stat"><div class="label">' + esc(f("statAverage")(Math.min(RECENT, runs.length))) + '</div><div style="margin-top:8px">' + pill(st.avgDiff) + "</div></div>" +
      '<div class="card stat"><div class="label">' + esc(t("statBest")) + '</div><div style="margin-top:8px">' + (st.best ? pill(runDiff(st.best)) : "") + "</div></div></div>";
    // Trend: last runs as bars around the plan line (up = over, down = under)
    var last = runs.slice(0, 12).reverse(), maxAbs = Math.max(10, Math.max.apply(null, last.map(function (r) { return Math.abs(runDiff(r)); })));
    html += '<div class="section"><div class="label">' + esc(t("trendTitle")) + '</div><div class="card bars">' + last.map(function (r) {
      var d = runDiff(r), h = Math.max(3, Math.abs(d) / maxAbs * 45);
      return '<i title="' + fmtDiff(d) + '" style="height:' + h + "%;background:" + (d > 0 ? "var(--red)" : "var(--green)") + ";align-self:" + (d > 0 ? "flex-start;margin-top:" + (50 - h) + "%" : "flex-end;margin-bottom:" + (50 - h) + "%") + '"></i>';
    }).join("") + "</div></div>";
    if (st.segments.some(function (x) { return x.suggested != null; })) {
      html += '<div class="section"><div class="label">' + esc(t("suggestionsTitle")) + '</div><div class="card" style="padding:14px">' + st.segments.filter(function (x) { return x.suggested != null; }).map(function (x) {
        return "<p style='margin:0 0 10px'><b>" + esc(x.seg.title) + "</b><br><span class='sub small'>" + esc((x.avg > 0 ? f("suggestOver") : f("suggestUnder"))(fmt(x.seg.targetSeconds + x.avg), fmt(x.suggested))) + "</span></p>";
      }).join("") + '<p class="sub small" style="margin:0">' + esc(t("suggestionsNote")) + "</p></div></div>";
    }
    html += '<div class="section"><div class="label">' + esc(t("segmentAveragesTitle")) + '</div><div class="card">' + st.segments.map(function (x) {
      return '<div class="runrow" style="cursor:default"><span class="d">' + esc(x.seg.title) + '</span><span class="sub small tnum">' + fmt(x.seg.targetSeconds) + "</span>" + (x.runs ? pill(x.avg) : "") + "</div>";
    }).join("") + "</div></div>";
    html += '<div class="section"><div class="label">' + esc(t("runsTitle")) + '</div><div class="card">' + runs.map(function (r) {
      var open = ui.expandedRun === r.id;
      return '<div class="runrow" data-a="toggle-run" data-id="' + esc(r.id) + '"><span class="d">' + esc(fmtDate(r.finishedAt)) + '<br><span class="sub small tnum">' + fmt(runTotal(r, "actual")) + "</span></span>" + pill(runDiff(r)) + "</div>" +
        (open ? '<div class="rundetail">' + r.segments.map(function (s) { return "<div><span>" + esc(s.title) + '</span><span class="tnum">' + fmt(s.actual) + '</span><span class="tnum sub">' + fmtDiff(s.actual - s.target) + "</span></div>"; }).join("") +
          '<div style="justify-content:flex-end"><button class="tbtn sub" data-a="delete-run" data-id="' + esc(r.id) + '">' + esc(t("deleteRun")) + '</button><button class="tbtn" data-a="share-run" data-id="' + esc(r.id) + '">' + esc(t("shareReport")) + "</button></div></div>" : "");
    }).join("") + '</div><button class="tbtn sub" data-a="clear-history" style="margin-top:10px">' + esc(t("clearHistory")) + "</button></div>";
    root.innerHTML = html + "</div>";
  }

  // --- Dialogs ---------------------------------------------------------------------
  function importChoice() {
    var d = dialog({ title: t("importTitle"), noFocus: true,
      body: '<button class="choice" id="c-file">' + icon("doc") + "<span><b>" + esc(t("importFromFile")) + '</b><br><span class="sub small">' + esc(t("importFromFileDesc")) + "</span></span></button>" +
        '<button class="choice" id="c-paste">' + icon("paste") + "<span><b>" + esc(t("importPaste")) + '</b><br><span class="sub small">' + esc(t("importPasteDesc")) + "</span></span></button>" +
        '<p class="sub small" style="margin:12px 0 0">' + esc(t("dropHint")) + "</p>",
      actions: [{ label: t("cancel"), cls: "tbtn" }] });
    d.querySelector("#c-file").onclick = function () { closeDialog(); pickFile(); };
    d.querySelector("#c-paste").onclick = function () { closeDialog(); pasteDialog(); };
  }
  function pasteDialog() {
    var d = dialog({ title: t("importTitle"), body: '<label class="field"><span>' + esc(t("importHint")) + '</span><textarea id="paste"></textarea></label>',
      actions: [{ label: t("cancel"), cls: "tbtn" }, { label: t("importLabel"), id: "paste-ok", onClick: function () {
        var text = document.getElementById("paste").value;
        if (!text.trim()) return;
        closeDialog();
        if (!importText(text)) toast(t("importNothing"));
      } }] });
    return d;
  }
  function showSuggestion(draft) {
    var state = { title: draft.title, pace: data.settings.wordsPerMinute, useTotal: false, parts: 0, totalText: "" };
    var words = IM.totalWords(draft.blocks);
    var paceSec = Math.max(60, roundTo5(Math.round(words * 60 / state.pace)));
    state.totalText = timeForInput(paceSec - paceSec % 60);
    var d = dialog({ icon: "sparkle", title: t("suggestTitle"), body: '<div id="sg"></div>', noFocus: true,
      actions: [{ label: t("cancel"), cls: "tbtn" }, { label: t("continueLabel"), id: "sg-ok", onClick: accept }] });
    var result;
    function compute() {
      var total = state.useTotal ? (parseTime(state.totalText) || null) : null;
      result = IM.suggestSegments(draft.blocks, state.pace, total, state.parts || null, t("defaultFirstSegment"), f("part"));
    }
    function draw() {
      compute();
      var paces = PACE_OPTIONS.concat([state.pace]).filter(function (v, i, a) { return a.indexOf(v) === i; }).sort(function (a, b) { return a - b; });
      var html = '<label class="field" style="margin-top:0"><span>' + esc(t("speechTitleLabel")) + '</span><input id="sg-title" value="' + esc(state.title) + '"></label>' +
        '<p class="sub small">' + esc((result.fromHeadings ? t("suggestFromHeadings") : t("suggestEvenly")) + " " + t("suggestEditNote")) + "</p>" +
        '<div class="label" style="margin:14px 0 6px">' + esc(t("timesFrom")) + '</div><div class="chips"><button class="chip' + (!state.useTotal ? " on" : "") + '" data-sg="mode" data-v="0">' + esc(t("timesFromPace")) +
        '</button><button class="chip' + (state.useTotal ? " on" : "") + '" data-sg="mode" data-v="1">' + esc(t("timesFromTotal")) + "</button></div>";
      if (state.useTotal) {
        html += '<label class="field"><span>' + esc(t("timeLabel")) + '</span><input id="sg-total" inputmode="decimal" value="' + esc(state.totalText) + '"></label><p class="sub small" id="sg-eq">= ' + fmt(parseTime(state.totalText)) + "</p>";
      } else {
        html += '<div class="chips" style="margin-top:8px">' + paces.map(function (v) { return '<button class="chip' + (v === state.pace ? " on" : "") + '" data-sg="pace" data-v="' + v + '">' + esc(f("pace")(v)) + "</button>"; }).join("") +
          '</div><p class="sub small" style="margin:6px 0 0">' + esc(t("paceNote")) + "</p>";
      }
      if (!result.fromHeadings) {
        html += '<div class="label" style="margin:14px 0 6px">' + esc(t("partsTitle")) + '</div><div class="chips">' + [0, 2, 3, 4, 5, 6, 8].map(function (v) {
          return '<button class="chip' + (v === state.parts ? " on" : "") + '" data-sg="parts" data-v="' + v + '">' + esc(v === 0 ? t("partsAuto") : String(v)) + "</button>"; }).join("") + "</div>";
      }
      html += '<div id="sg-list" style="margin-top:14px;border-top:1px solid var(--line)"></div>';
      d.querySelector("#sg").innerHTML = html;
      drawList();
      var ti = d.querySelector("#sg-title"); ti.oninput = function () { state.title = ti.value; };
      var to = d.querySelector("#sg-total");
      if (to) to.oninput = function () { state.totalText = to.value; d.querySelector("#sg-eq").textContent = "= " + fmt(parseTime(to.value)); compute(); drawList(); };
    }
    function drawList() {
      d.querySelector("#sg-list").innerHTML = result.segments.map(function (s, i) {
        return '<div class="sugg"><span class="n">' + (i + 1) + '.</span><span class="t"><b>' + esc(s.title) + '</b><span class="sub small">' + esc(f("words")(s.words)) + '</span></span><b class="tnum">' + fmt(s.seconds) + "</b></div>";
      }).join("") + '<div class="sugg" style="border:0"><b style="flex:1">' + esc(t("reportTotal")) + '</b><span class="sub small">' + esc(f("words")(sum(result.segments.map(function (s) { return s.words; })))) + '</span><b class="tnum">' + fmt(result.total) + "</b></div>";
      d.querySelector("#sg-ok") && (document.getElementById("sg-ok").disabled = !result.segments.length);
    }
    d.querySelector("#sg").addEventListener("click", function (e) {
      var b = e.target.closest("[data-sg]"); if (!b) return;
      var v = parseInt(b.dataset.v, 10);
      if (b.dataset.sg === "mode") state.useTotal = v === 1;
      else if (b.dataset.sg === "pace") state.pace = v;
      else state.parts = v;
      draw();
    });
    function accept() {
      if (!result.segments.length) return;
      closeDialog();
      if (state.pace !== data.settings.wordsPerMinute) { data.settings.wordsPerMinute = state.pace; save(); }
      startEditing({ id: newId(), title: state.title.trim() || t("importedSpeech"),
        segments: result.segments.map(function (s) { return { id: newId(), title: s.title, targetSeconds: s.seconds }; }) }, "library");
    }
    draw();
  }
  function quickDialog() {
    var minutes = 20;
    var d = dialog({ title: t("quickTimer"), body: '<div class="chips">' + QUICK_OPTIONS.map(function (m) { return '<button class="chip" data-m="' + m + '">' + m + " min</button>"; }).join("") +
      '</div><label class="field"><span>' + esc(t("quickMinutesLabel")) + '</span><input id="qm" inputmode="numeric" value="20" maxlength="3"></label>',
      actions: [{ label: t("cancel"), cls: "tbtn" }, { label: t("start"), id: "q-ok", onClick: function () { if (minutes) { closeDialog(); startQuick(minutes); } } }] });
    var input = d.querySelector("#qm");
    function sync() {
      var v = parseInt(input.value, 10); minutes = v >= 1 && v <= 600 ? v : 0;
      d.querySelectorAll("[data-m]").forEach(function (c) { c.classList.toggle("on", parseInt(c.dataset.m, 10) === minutes); });
      document.getElementById("q-ok").disabled = !minutes;
    }
    input.oninput = function () { input.value = input.value.replace(/\D/g, "").slice(0, 3); sync(); };
    d.querySelectorAll("[data-m]").forEach(function (c) { c.onclick = function () { input.value = c.dataset.m; sync(); }; });
    sync();
  }
  function notesDialog() {
    var d = dialog({ icon: "notes", title: t("patchNotes"), body: '<div class="notes"><div class="busy"><div class="spin"></div><span>' + esc(t("notesLoading")) + "</span></div></div>",
      actions: [{ label: t("viewOnGitHub"), cls: "tbtn", onClick: function () { open(RELEASES, "_blank", "noopener"); } }, { label: t("close"), cls: "btn" }] });
    fetch("https://api.github.com/repos/" + REPO + "/releases?per_page=15", { headers: { Accept: "application/vnd.github+json" } })
      .then(function (r) { if (!r.ok) throw new Error(); return r.json(); })
      .then(function (list) {
        var box = d.querySelector(".notes"); if (!box) return;
        box.innerHTML = list.filter(function (r) { return !r.draft && !r.prerelease; }).map(function (r) {
          var v = String(r.tag_name || "").replace(/^v/i, "");
          return '<div class="rel"><h4>' + esc(f("version")(v)) + (v === APP_VERSION ? '<span class="tag">' + esc(t("yourVersion")) + "</span>" : "") + '</h4><div class="sub small">' + esc((r.published_at || "").slice(0, 10)) +
            "</div><p>" + esc(plainNotes(r.body || "") || t("noNotes")) + "</p></div>";
        }).join("") || esc(t("noNotes"));
      })
      .catch(function () { var box = d.querySelector(".notes"); if (box) box.textContent = t("notesFailed"); });
  }
  function plainNotes(md) {
    return md.split("\n").map(function (l) { return l.trim().replace(/^#{1,6}\s*/, "").replace(/^[*-]\s+/, "• ").replace(/\*\*|__/g, ""); }).join("\n").replace(/\n{3,}/g, "\n\n").trim();
  }
  function feedbackDialog() {
    var d = dialog({ icon: "feedback", title: t("feedback"), noFocus: true,
      body: '<p class="sub small" style="margin-top:0">' + esc(t("feedbackText")) + "</p>" + [["bug", "feedbackBug"], ["bulb", "feedbackIdea"], ["chat", "feedbackOther"]].map(function (k) {
        return '<button class="choice" data-fb="' + k[1] + '">' + icon(k[0]) + "<b>" + esc(t(k[1])) + "</b></button>"; }).join(""),
      actions: [{ label: t("cancel"), cls: "tbtn" }] });
    d.querySelectorAll("[data-fb]").forEach(function (b) {
      b.onclick = function () {
        closeDialog();
        var subject = "Speech Split: " + t(b.dataset.fb);
        var body = t("feedbackDescribe") + "\n\n\n\n---\nSpeech Split " + APP_VERSION + " (web) · " + navigator.userAgent;
        location.href = "mailto:" + FEEDBACK_EMAIL + "?subject=" + encodeURIComponent(subject) + "&body=" + encodeURIComponent(body);
      };
    });
  }
  function deleteDialog(p) {
    confirmDialog(f("deleteTitle")(p.title), p.archived ? t("cannotBeUndone") : t("deleteForeverText"), t("delete"), function () { deletePlan(p); render(); }, true,
      p.archived ? [] : [{ label: t("archiveAction"), cls: "tbtn", onClick: function () { closeDialog(); replacePlan(p.id, function (x) { x.archived = true; return x; }); toast(t("archived")); render(); } }]);
  }

  // --- Events ------------------------------------------------------------------------
  var actions = {
    "tab-library": backToLibrary,
    "tab-settings": function () { ui.plan = null; go("settings"); },
    "back-library": backToLibrary,
    "archive": function () { go("archive"); },
    "new": createNew,
    "quick": quickDialog,
    "import": importChoice,
    "hide-install": function () { data.settings.installHintHidden = true; save(); render(); },
    "open": function (el) { var p = planById(el.dataset.id); if (p) openPlan(p); },
    "plan-menu": function (el) {
      var p = planById(el.dataset.id); if (!p) return;
      menu(el, [
        { icon: "edit", text: t("edit"), onClick: function () { startEditing(p, "library"); } },
        { icon: "share", text: t("export"), onClick: function () { copy(exportText(p), t("exportCopiedWeb")); } },
        { icon: "archive", text: t("archiveAction"), onClick: function () { replacePlan(p.id, function (x) { x.archived = true; return x; }); toast(t("archived")); render(); } },
        { icon: "del", text: t("delete"), onClick: function () { deleteDialog(p); } },
      ]);
    },
    "sort": function (el) {
      var cur = data.settings.speechSort;
      menu(el, [{ label: t("sortTitle") }].concat([["LAST_USED", "sortLastUsed"], ["CREATED", "sortCreated"], ["NAME", "sortName"]].map(function (o) {
        return { radio: cur === o[0], text: t(o[1]), onClick: function () { data.settings.speechSort = o[0]; save(); render(); } };
      })));
    },
    "restore": function (el) { replacePlan(el.dataset.id, function (x) { x.archived = false; return x; }); toast(t("restored")); render(); },
    "delete": function (el) { var p = planById(el.dataset.id); if (p) deleteDialog(p); },
    "set": function (el) { data.settings[el.dataset.k] = JSON.parse(el.dataset.v); save(); render(); },
    "toggle": function (el) { var k = el.dataset.k; data.settings[k] = !data.settings[k]; save(); render(); if (k === "vibrateAlerts" && data.settings[k] && navigator.vibrate) navigator.vibrate(180); },
    "notes": notesDialog,
    "feedback": feedbackDialog,
    "edit-back": function () { if (hasUnsavedEdits()) confirmDialog(t("discardTitle"), t("discardText"), t("discard"), cancelEditing, true); else cancelEditing(); },
    "seg-del": function (el) { ui.plan.segments.splice(+el.dataset.i, 1); render(); },
    "seg-up": function (el) { moveSeg(+el.dataset.i, -1); },
    "seg-down": function (el) { moveSeg(+el.dataset.i, 1); },
    "seg-add": function () { ui.plan.segments.push({ id: newId(), title: t("defaultNewSegment"), targetSeconds: 120 }); render(); var ins = root.querySelectorAll('[data-in="seg-title"]'); if (ins.length) ins[ins.length - 1].focus(); },
    "save": saveEdit,
    "start": startTimer,
    "edit-active": function () { startEditing(ui.plan, "ready"); },
    "history": function () { ui.expandedRun = null; go("history"); },
    "close-history": function () { go(ui.plan ? "ready" : "library"); },
    "toggle-run": function (el) { ui.expandedRun = ui.expandedRun === el.dataset.id ? null : el.dataset.id; render(); },
    "delete-run": function (el) { data.runs = data.runs.filter(function (r) { return r.id !== el.dataset.id; }); save(); render(); },
    "share-run": function (el) { var r = data.runs.filter(function (x) { return x.id === el.dataset.id; })[0]; if (r) share(report(r), t("shareReport")); },
    "clear-history": function () { confirmDialog(t("clearHistoryTitle"), t("cannotBeUndone"), t("delete"), function () { var id = ui.plan.id; data.runs = data.runs.filter(function (r) { return r.planId !== id; }); save(); render(); }, true); },
    "pause": togglePause,
    "next": function () { if (navigator.vibrate && !isIos()) navigator.vibrate(15); next(); },
    "undo": previous,
    "stop": requestStop,
    "stop-now": stopTimer,
    "lectern": function () { data.settings.lecternMode = !data.settings.lecternMode; save(); render(); },
    "share-report": function () { var r = snapshot(); if (r) share(report(r), t("shareReport")); },
  };
  function moveSeg(i, dir) {
    var segs = ui.plan.segments, j = i + dir;
    if (j < 0 || j >= segs.length) return;
    var x = segs.splice(i, 1)[0]; segs.splice(j, 0, x); render();
  }
  root.addEventListener("click", function (e) {
    var el = e.target.closest("[data-a]");
    if (!el || !root.contains(el)) return;
    if (el.dataset.a === "open" && e.target.closest('[data-a="plan-menu"]')) return;
    var fn = actions[el.dataset.a];
    if (fn) { e.preventDefault(); fn(el); }
  });
  root.addEventListener("keydown", function (e) {
    if ((e.key === "Enter" || e.key === " ") && e.target.matches('.plan, .switchrow')) { e.preventDefault(); e.target.click(); }
  });
  root.addEventListener("input", function (e) {
    var el = e.target, k = el.dataset && el.dataset.in;
    if (!k || ui.screen !== "edit") return;
    var p = ui.plan;
    if (k === "title") p.title = el.value;
    else if (k === "seg-title") p.segments[+el.dataset.i].title = el.value;
    else if (k === "seg-time") {
      var secs = parseTime(el.value);
      p.segments[+el.dataset.i].targetSeconds = secs;
      var eq = document.getElementById("eq-" + el.dataset.i); if (eq) eq.textContent = "= " + fmt(secs);
    }
    var sm = document.getElementById("edit-summary"); if (sm) sm.textContent = summaryOf(p);
  });

  // Keyboard and presentation clicker on the timer (like the Windows app)
  var lastNext = 0;
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape") { if (menuEl) { closeMenu(); return; } if (dialogEl) { closeDialog(); return; } }
    if (ui.screen !== "timer" || dialogEl || e.metaKey || e.ctrlKey || e.altKey) return;
    var k = e.key;
    if (k === " ") { e.preventDefault(); if (!isFinished()) togglePause(); }
    else if (k === "ArrowRight" || k === "PageDown" || k === "Enter") {
      e.preventDefault();
      var now = Date.now(); if (now - lastNext > 400) next(); lastNext = now;
    }
    else if (k === "ArrowLeft" || k === "PageUp" || k === "Backspace") { e.preventDefault(); previous(); }
    else if (k === "f" || k === "F") { if (!isFinished()) actions.lectern(); }
  });

  // Drag and drop a document anywhere (not while timing)
  var dragDepth = 0, dropEl = null;
  function hasFiles(e) { return e.dataTransfer && Array.prototype.indexOf.call(e.dataTransfer.types || [], "Files") >= 0; }
  document.addEventListener("dragenter", function (e) {
    if (!hasFiles(e) || ui.screen === "timer") return;
    e.preventDefault(); dragDepth++;
    if (!dropEl) { dropEl = document.createElement("div"); dropEl.className = "drop"; dropEl.textContent = t("dropHere"); document.body.appendChild(dropEl); }
  });
  document.addEventListener("dragover", function (e) { if (hasFiles(e) && ui.screen !== "timer") e.preventDefault(); });
  document.addEventListener("dragleave", function () { if (--dragDepth <= 0 && dropEl) { dropEl.remove(); dropEl = null; dragDepth = 0; } });
  document.addEventListener("drop", function (e) {
    if (dropEl) { dropEl.remove(); dropEl = null; } dragDepth = 0;
    if (!hasFiles(e) || ui.screen === "timer") return;
    e.preventDefault();
    if (ui.screen === "edit" && hasUnsavedEdits()) return;
    var file = e.dataTransfer.files[0];
    if (file) importFile(file);
  });

  // Confetti for a perfect finish (silent, like the app)
  function confetti() {
    if (matchMedia("(prefers-reduced-motion: reduce)").matches || document.querySelector("canvas.confetti")) return;
    var c = document.createElement("canvas"); c.className = "confetti"; document.body.appendChild(c);
    var ctx = c.getContext("2d"), dpr = devicePixelRatio || 1, W = innerWidth, H = innerHeight;
    c.width = W * dpr; c.height = H * dpr; ctx.scale(dpr, dpr);
    var cs = getComputedStyle(document.documentElement);
    var cols = ["--green", "--blue", "--amber", "--red"].map(function (v) { return cs.getPropertyValue(v).trim(); }).concat(["#7A5BC7", "#1E9AA0"]);
    var parts = [];
    for (var i = 0; i < 160; i++) parts.push({ x: Math.random() * W, y: -Math.random() * H * 0.6 - 10, vx: (Math.random() - 0.5) * 60, vy: H * (0.28 + Math.random() * 0.3),
      r: Math.random() * 6.28, vr: (Math.random() - 0.5) * 12, w: 6 + Math.random() * 6, h: 10 + Math.random() * 8, c: cols[i % cols.length] });
    var t0 = performance.now(), dur = 4.5;
    (function frame(now) {
      var tt = (now - t0) / 1000;
      ctx.clearRect(0, 0, W, H);
      ctx.globalAlpha = Math.max(0, Math.min(1, dur - tt));
      parts.forEach(function (p) {
        var x = p.x + p.vx * tt + Math.sin(p.r + tt * 4) * 12, y = p.y + p.vy * tt, k = 0.35 + 0.65 * Math.abs(Math.cos(p.r + tt * 5));
        ctx.save(); ctx.translate(x, y); ctx.rotate(p.r + p.vr * tt); ctx.fillStyle = p.c; ctx.fillRect(-p.w / 2, -p.h * k / 2, p.w, p.h * k); ctx.restore();
      });
      if (!c.isConnected) { ui.celebrating = false; return; }
      if (tt < dur) requestAnimationFrame(frame); else { c.remove(); ui.celebrating = false; }
    })(t0);
  }

  // Warn before closing the tab in the middle of editing
  addEventListener("beforeunload", function (e) { if (hasUnsavedEdits()) { e.preventDefault(); e.returnValue = ""; } });

  // --- Start ------------------------------------------------------------------------
  load();
  lang = resolveLang();
  if (restoreRun()) { afterChange(); setTimeout(function () { toast(t("restoredRun")); }, 400); }
  render();
  if ("serviceWorker" in navigator && location.protocol === "https:") {
    navigator.serviceWorker.register("sw.js").catch(function () {});
  }
  window.SpeechSplitWeb = { data: data, E: E, ui: ui }; // for debugging in the console
})();
