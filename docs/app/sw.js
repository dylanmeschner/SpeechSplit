// Speech Split web version: works offline once it has been opened.
// Raise VERSION whenever a file below changes, so browsers pick up the new version.
var VERSION = "speechsplit-web-2.2";
var CORE = ["./", "index.html", "app.js", "import.js", "strings.js", "manifest.webmanifest", "icon-192.png", "icon-512.png", "apple-touch-icon.png"];
// The PDF reader is big, so it is only stored after the first PDF import
var LAZY = ["vendor/pdf.min.js", "vendor/pdf.worker.min.js"];

self.addEventListener("install", function (e) {
  e.waitUntil(caches.open(VERSION).then(function (c) { return c.addAll(CORE); }).then(function () { return self.skipWaiting(); }));
});

self.addEventListener("activate", function (e) {
  e.waitUntil(caches.keys().then(function (keys) {
    return Promise.all(keys.filter(function (k) { return k !== VERSION; }).map(function (k) { return caches.delete(k); }));
  }).then(function () { return self.clients.claim(); }));
});

// Network first for the app itself (so updates arrive at once), cache when offline.
// Cache first for the PDF reader, which never changes.
self.addEventListener("fetch", function (e) {
  var url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.origin !== location.origin) return;
  var lazy = LAZY.some(function (p) { return url.pathname.endsWith(p); });
  if (lazy) {
    e.respondWith(caches.match(e.request).then(function (hit) {
      return hit || fetch(e.request).then(function (res) {
        if (res.ok) { var copy = res.clone(); caches.open(VERSION).then(function (c) { c.put(e.request, copy); }); }
        return res;
      });
    }));
    return;
  }
  e.respondWith(fetch(e.request).then(function (res) {
    if (res.ok) { var copy = res.clone(); caches.open(VERSION).then(function (c) { c.put(e.request, copy); }); }
    return res;
  }).catch(function () {
    return caches.match(e.request, { ignoreSearch: true }).then(function (hit) { return hit || caches.match("index.html"); });
  }));
});
