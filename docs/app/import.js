// ============================================================================
// SPEECH SPLIT (web) · IMPORTING A SPEECH FROM A DOCUMENT
// The same rules as the app (core/SpeechImport.kt and jvm/DocumentReader.kt):
// a document becomes blocks (paragraphs and headings), and segments are suggested
// from the headings, or as parts of about equal length. Everything runs in the browser;
// the document is never uploaded anywhere.
// ============================================================================
(function () {
  "use strict";

  function countWords(text) {
    return text.split(/\s+/).filter(function (w) { return /[\p{L}\p{N}]/u.test(w); }).length;
  }

  var SENTENCE_END = /[.!?…;,]$/;
  var MD_HEADING = /^#{1,6}\s+/;

  function looksLikeHeading(line) {
    var t = line.trim();
    if (!t) return false;
    if (MD_HEADING.test(t)) return true;
    var words = countWords(t);
    if (words === 0 || words > 10 || t.length > 80) return false;
    if (SENTENCE_END.test(t)) return false;
    if (!/\p{L}/u.test(t)) return false;
    return true;
  }

  function isUpper(ch) { return ch !== ch.toLowerCase() && ch === ch.toUpperCase(); }
  function isLetter(ch) { return /\p{L}/u.test(ch); }

  function cleanHeading(text) {
    return text.trim().replace(MD_HEADING, "").replace(/^(\d{1,2}|[IVXivx]{1,5})[.)]\s+/, "").trim()
      .replace(/:+$/, "").trim().slice(0, 60);
  }

  /** Plain text → blocks. Paragraphs are separated by blank lines; a short first line can be a heading. */
  function blocksFromPlainText(text) {
    var normalized = text.replace(/\r\n/g, "\n").replace(/\r/g, "\n").replace(/\f/g, "\n");
    var paragraphs = normalized.split(/\n\s*\n/);
    var blocks = [];
    paragraphs.forEach(function (raw) {
      var lines = raw.split("\n").map(function (l) { return l.trim(); }).filter(Boolean);
      if (!lines.length) return;
      var rest = lines;
      var first = rest[0];
      var startsLikeHeading = isUpper(first[0]) || /\d/.test(first[0]) || first[0] === "#";
      var nextStartsSentence = rest.length === 1 || !isLetter(rest[1][0]) || isUpper(rest[1][0]);
      if (startsLikeHeading && nextStartsSentence && looksLikeHeading(first) &&
          (rest.length === 1 || countWords(rest.slice(1).join(" ")) >= 8)) {
        blocks.push({ text: cleanHeading(first), heading: true });
        rest = rest.slice(1);
      }
      if (!rest.length) return;
      var joined = "";
      rest.forEach(function (line, i) {
        if (i > 0) {
          if (joined.endsWith("-") && line[0] && line[0] === line[0].toLowerCase() && isLetter(line[0])) joined = joined.slice(0, -1);
          else joined += " ";
        }
        joined += line;
      });
      blocks.push({ text: joined, heading: false });
    });
    return blocks;
  }

  function firstWords(text, max) {
    max = max || 5;
    var words = text.trim().split(/\s+/).filter(Boolean);
    var head = words.slice(0, max).join(" ").replace(/[,.;:!?–-]+$/, "");
    return words.length > max ? head + "…" : head;
  }

  function roundTo5(s) { return Math.floor((s + 2) / 5) * 5; }

  function section(title, texts) {
    return { title: title, texts: texts || [],
      words: function () { return this.texts.reduce(function (a, t) { return a + countWords(t); }, 0); },
      text: function () { return this.texts.join(" "); } };
  }

  function splitEvenly(texts, wpm, totalSeconds, parts) {
    var pieces = texts.filter(function (t) { return countWords(t) > 0; });
    if (!pieces.length) return [];
    var totalWords = pieces.reduce(function (a, t) { return a + countWords(t); }, 0);
    var minutes = totalSeconds != null ? totalSeconds / 60 : totalWords / Math.max(1, wpm);
    var wanted = Math.max(1, parts || Math.min(8, Math.max(2, Math.round(minutes / 3))));
    if (pieces.length < wanted) {
      pieces = [].concat.apply([], pieces.map(function (p) { return p.split(/(?<=[.!?…])\s+/).filter(function (x) { return x.trim(); }); }));
    }
    var n = Math.min(wanted, pieces.length);
    var result = [], cur = section(null), done = 0;
    pieces.forEach(function (piece, i) {
      cur.texts.push(piece);
      done += countWords(piece);
      var partsLeft = n - result.length - 1, piecesLeft = pieces.length - i - 1;
      var share = totalWords * (result.length + 1) / n;
      if (partsLeft > 0 && (done >= share || piecesLeft === partsLeft)) { result.push(cur); cur = section(null); }
    });
    if (cur.texts.length) result.push(cur);
    return result;
  }

  function withTimes(parts, wpm, totalSeconds) {
    if (!parts.length) return [];
    var totalWords = Math.max(1, parts.reduce(function (a, p) { return a + p.words; }, 0));
    var rounded = parts.map(function (p) {
      var raw = totalSeconds != null ? totalSeconds * p.words / totalWords : p.words * 60 / Math.max(1, wpm);
      return Math.max(5, roundTo5(Math.round(raw)));
    });
    if (totalSeconds != null && totalSeconds > 0) {
      var diff = totalSeconds - rounded.reduce(function (a, b) { return a + b; }, 0);
      var order = rounded.map(function (_, i) { return i; }).sort(function (a, b) { return rounded[b] - rounded[a]; });
      var k = 0;
      while (diff !== 0 && k < 10000) {
        var i = order[k % order.length];
        var step = diff >= 5 ? 5 : diff <= -5 ? -5 : diff;
        if (rounded[i] + step >= 1) { rounded[i] += step; diff -= step; }
        k++;
      }
    }
    return parts.map(function (p, i) { return { title: p.title, words: p.words, seconds: rounded[i] }; });
  }

  /** Same as suggestSegments() in the app. */
  function suggestSegments(blocks, wpm, totalSeconds, parts, introTitle, partTitle) {
    var sections = [], cur = section(null);
    blocks.forEach(function (b) {
      if (b.heading) {
        if (cur.title != null || cur.texts.length) sections.push(cur);
        cur = section(b.text);
      } else cur.texts.push(b.text);
    });
    if (cur.title != null || cur.texts.length) sections.push(cur);
    var withText = sections.filter(function (s) { return s.words() > 0; });
    var headed = withText.filter(function (s) { return s.title != null; });
    var chosen, fromHeadings;
    if (headed.length >= 2) {
      fromHeadings = true;
      chosen = withText.filter(function (s) { return s.title != null || s.words() >= 15; })
        .map(function (s) { return s.title == null ? section(introTitle, s.texts) : s; });
      if (!chosen.length) chosen = withText;
    } else {
      fromHeadings = false;
      chosen = splitEvenly([].concat.apply([], withText.map(function (s) { return s.texts; })), wpm, totalSeconds, parts);
    }
    var named = chosen.map(function (s, i) {
      var title = (s.title && s.title.trim()) || firstWords(s.text()) || partTitle(i + 1);
      return { title: title, words: s.words() };
    });
    var segs = withTimes(named, wpm, totalSeconds);
    return { segments: segs, fromHeadings: fromHeadings, total: segs.reduce(function (a, s) { return a + s.seconds; }, 0) };
  }

  function totalWords(blocks) {
    return blocks.filter(function (b) { return !b.heading; }).reduce(function (a, b) { return a + countWords(b.text); }, 0);
  }

  function guessTitle(blocks, fallback) {
    return blocks[0] && blocks[0].heading && blocks[1] && blocks[1].heading ? blocks[0].text : fallback;
  }

  // --- Reading files ------------------------------------------------------------
  function decodeText(bytes) {
    var text;
    try { text = new TextDecoder("utf-8", { fatal: true }).decode(bytes); }
    catch (e) { text = new TextDecoder("windows-1252").decode(bytes); }
    return text.replace(/^﻿/, "");
  }

  /** Minimal zip reader (Word and OpenDocument files are zips). Uses the browser's own decompression. */
  async function zipEntry(bytes, name) {
    var dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    var eocd = -1;
    for (var i = bytes.length - 22; i >= Math.max(0, bytes.length - 65557); i--) {
      if (dv.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
    }
    if (eocd < 0) return null;
    var count = dv.getUint16(eocd + 10, true), off = dv.getUint32(eocd + 16, true);
    var dec = new TextDecoder();
    for (var n = 0; n < count; n++) {
      if (dv.getUint32(off, true) !== 0x02014b50) return null;
      var method = dv.getUint16(off + 10, true), csize = dv.getUint32(off + 20, true);
      var nlen = dv.getUint16(off + 28, true), xlen = dv.getUint16(off + 30, true), clen = dv.getUint16(off + 32, true);
      var local = dv.getUint32(off + 42, true);
      var fname = dec.decode(bytes.subarray(off + 46, off + 46 + nlen));
      if (fname === name) {
        var lnlen = dv.getUint16(local + 26, true), lxlen = dv.getUint16(local + 28, true);
        var data = bytes.subarray(local + 30 + lnlen + lxlen, local + 30 + lnlen + lxlen + csize);
        if (method === 0) return data;
        if (method !== 8 || typeof DecompressionStream === "undefined") throw new Error("unsupported");
        var stream = new Blob([data]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
        return new Uint8Array(await new Response(stream).arrayBuffer());
      }
      off += 46 + nlen + xlen + clen;
    }
    return null;
  }

  function isHeadingStyle(style) {
    style = (style || "").toLowerCase();
    return style.indexOf("heading") === 0 || style.indexOf("overskrift") === 0 || style.indexOf("berschrift") >= 0 ||
      style === "title" || style === "tittel" || style === "titel" || style.indexOf("subtitle") === 0;
  }

  function fixLongHeadings(blocks) {
    return blocks.map(function (b) { return b.heading && b.text.length > 120 ? { text: b.text, heading: false } : b; });
  }

  async function readDocx(bytes) {
    var xml = await zipEntry(bytes, "word/document.xml");
    if (!xml) return null;
    var doc = new DOMParser().parseFromString(new TextDecoder().decode(xml), "application/xml");
    var blocks = [];
    Array.prototype.forEach.call(doc.getElementsByTagName("w:p"), function (p) {
      var text = "";
      var walker = doc.createTreeWalker(p, NodeFilter.SHOW_ELEMENT);
      var node;
      while ((node = walker.nextNode())) {
        if (node.nodeName === "w:t") text += node.textContent;
        else if (node.nodeName === "w:tab" || node.nodeName === "w:br") text += " ";
      }
      text = text.replace(/\s+/g, " ").trim();
      if (!text) return;
      var styleEl = p.getElementsByTagName("w:pStyle")[0];
      var style = styleEl ? styleEl.getAttribute("w:val") : "";
      var heading = p.getElementsByTagName("w:outlineLvl").length > 0 || isHeadingStyle(style);
      blocks.push(heading ? { text: cleanHeading(text), heading: true } : { text: text, heading: false });
    });
    return fixLongHeadings(blocks);
  }

  async function readOdt(bytes) {
    var xml = await zipEntry(bytes, "content.xml");
    if (!xml) return null;
    var doc = new DOMParser().parseFromString(new TextDecoder().decode(xml), "application/xml");
    var blocks = [];
    var walker = doc.createTreeWalker(doc, NodeFilter.SHOW_ELEMENT);
    var node;
    while ((node = walker.nextNode())) {
      if (node.nodeName !== "text:h" && node.nodeName !== "text:p") continue;
      var text = node.textContent.replace(/\s+/g, " ").trim();
      if (!text) continue;
      blocks.push(node.nodeName === "text:h" ? { text: cleanHeading(text), heading: true } : { text: text, heading: false });
    }
    return fixLongHeadings(blocks);
  }

  function rtfToText(rtf) {
    if (rtf.trimStart().indexOf("{\\rtf") !== 0) return rtf;
    var out = "", i = 0, depth = 0, skip = -1;
    var SKIP = ["fonttbl", "colortbl", "stylesheet", "info", "pict", "header", "footer"];
    while (i < rtf.length) {
      var c = rtf[i];
      if (c === "{") { depth++; i++; }
      else if (c === "}") { if (depth === skip) skip = -1; depth--; i++; }
      else if (c === "\\") {
        var m = /^\\([a-z]+)(-?\d+)? ?|^\\'([0-9a-fA-F]{2})|^\\(.)/.exec(rtf.slice(i, i + 40));
        if (!m) { i++; continue; }
        if (skip < 0) {
          if (m[1] && SKIP.indexOf(m[1]) >= 0) skip = depth;
          else if (m[1] === "par") out += "\n\n";
          else if (m[1] === "line") out += "\n";
          else if (m[1] === "tab") out += " ";
          else if (m[3]) out += String.fromCharCode(parseInt(m[3], 16));
          else if (m[4] && "\\{}".indexOf(m[4]) >= 0) out += m[4];
        }
        i += m[0].length;
      } else { if (skip < 0 && c !== "\r" && c !== "\n") out += c; i++; }
    }
    return out;
  }

  var pdfjsPromise = null;
  function loadPdfJs() {
    if (!pdfjsPromise) {
      pdfjsPromise = new Promise(function (resolve, reject) {
        var s = document.createElement("script");
        s.src = "vendor/pdf.min.js";
        s.onload = function () {
          window.pdfjsLib.GlobalWorkerOptions.workerSrc = "vendor/pdf.worker.min.js";
          resolve(window.pdfjsLib);
        };
        s.onerror = function () { pdfjsPromise = null; reject(new Error("pdf.js")); };
        document.head.appendChild(s);
      });
    }
    return pdfjsPromise;
  }

  /** Text of a PDF, with a blank line where the gap between lines is bigger than normal (a new paragraph). */
  async function pdfText(bytes) {
    var pdfjs = await loadPdfJs();
    var pdf = await pdfjs.getDocument({ data: bytes }).promise;
    var out = [];
    for (var p = 1; p <= pdf.numPages; p++) {
      var page = await pdf.getPage(p);
      var content = await page.getTextContent();
      var lines = [], line = null;
      content.items.forEach(function (it) {
        if (typeof it.str !== "string") return;
        var y = it.transform[5], h = Math.abs(it.transform[3]) || it.height || 10;
        if (!line || Math.abs(line.y - y) > h * 0.5) { line = { y: y, h: h, text: "" }; lines.push(line); }
        line.text += it.str;
        if (it.hasEOL) line = null;
      });
      lines = lines.filter(function (l) { return l.text.trim(); });
      var gaps = [];
      for (var i = 1; i < lines.length; i++) gaps.push(Math.abs(lines[i - 1].y - lines[i].y));
      var sorted = gaps.slice().sort(function (a, b) { return a - b; });
      var normal = sorted.length ? sorted[Math.floor(sorted.length / 2)] : 0;
      lines.forEach(function (l, i) {
        if (i > 0 && normal > 0 && gaps[i - 1] > normal * 1.45) out.push("");
        out.push(l.text.trim());
      });
      out.push("");
    }
    return out.join("\n");
  }

  /**
   * Reads a dropped/picked file. Returns { kind: "plan", text } for an exported speech,
   * { kind: "blocks", blocks } for a document, or null if the type isn't supported.
   */
  async function readFile(file, onPdf) {
    var bytes = new Uint8Array(await file.arrayBuffer());
    var name = (file.name || "").toLowerCase();
    var ext = name.indexOf(".") >= 0 ? name.split(".").pop() : "";
    var isPdf = bytes[0] === 0x25 && bytes[1] === 0x50 && bytes[2] === 0x44;
    var isZip = bytes[0] === 0x50 && bytes[1] === 0x4b;
    if (isPdf || ext === "pdf") { if (onPdf) onPdf(); return { kind: "blocks", blocks: blocksFromPlainText(await pdfText(bytes)) }; }
    if (ext === "docx" || ext === "docm" || (isZip && ext !== "odt")) {
      var d = await readDocx(bytes);
      if (d) return { kind: "blocks", blocks: d };
      if (!isZip) return null;
    }
    if (ext === "odt" || isZip) { var o = await readOdt(bytes); return o ? { kind: "blocks", blocks: o } : null; }
    var looksText = ["txt", "text", "md", "markdown", "rtf", "json"].indexOf(ext) >= 0 ||
      bytes.subarray(0, 4096).every(function (b) { return b >= 9 && !(b > 13 && b < 32); });
    if (!looksText) return null;
    var text = decodeText(bytes);
    if (text.trim()[0] === "{") return { kind: "plan", text: text };
    if (ext === "rtf" || text.trimStart().indexOf("{\\rtf") === 0) text = rtfToText(text);
    return { kind: "blocks", blocks: blocksFromPlainText(text) };
  }

  window.SpeechImport = {
    countWords: countWords, blocksFromPlainText: blocksFromPlainText, suggestSegments: suggestSegments,
    totalWords: totalWords, guessTitle: guessTitle, readFile: readFile, roundTo5: roundTo5,
  };
})();
