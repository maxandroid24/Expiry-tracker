"use strict";

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const DATA_DIR = path.join(__dirname, "data");
const LOG_FILE = path.join(DATA_DIR, "scans.jsonl");
const MAX_ENTRIES = 500;

let cache = null;

function ensureLoaded() {
  if (cache !== null) return;
  cache = [];
  try {
    if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
    if (!fs.existsSync(LOG_FILE)) return;
    const text = fs.readFileSync(LOG_FILE, "utf8");
    for (const line of text.split("\n")) {
      const t = line.trim();
      if (!t) continue;
      try {
        cache.push(JSON.parse(t));
      } catch (_) {
        /* skip bad line */
      }
    }
    if (cache.length > MAX_ENTRIES) {
      cache = cache.slice(-MAX_ENTRIES);
      rewriteFile();
    }
  } catch (err) {
    console.error("scanLog: failed to load log file", err);
    cache = [];
  }
}

function rewriteFile() {
  try {
    if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
    const body = cache.map((e) => JSON.stringify(e)).join("\n") + (cache.length ? "\n" : "");
    fs.writeFileSync(LOG_FILE, body);
  } catch (err) {
    console.error("scanLog: failed to rewrite log file", err);
  }
}

function append(entry) {
  ensureLoaded();
  cache.push(entry);
  if (cache.length > MAX_ENTRIES) cache = cache.slice(-MAX_ENTRIES);
  try {
    if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
    fs.appendFileSync(LOG_FILE, JSON.stringify(entry) + "\n");
  } catch (err) {
    console.error("scanLog: failed to append entry", err);
  }
}

/**
 * Record a scan. The raw image is NOT persisted — only a SHA-256 of the
 * base64 bytes, plus the structured fields the model returned.
 */
function record({ imageBase64, ip, model, fields, durationMs, status }) {
  const entry = {
    id: crypto.randomBytes(8).toString("hex"),
    timestamp: new Date().toISOString(),
    ipHash: hashIp(ip),
    imageHash: hashImage(imageBase64),
    imageSizeBytes: imageBase64 ? Buffer.byteLength(imageBase64, "utf8") : 0,
    model: model || null,
    durationMs: typeof durationMs === "number" ? Math.round(durationMs) : null,
    status,
    fields: fields
      ? {
          name: fields.name ?? null,
          mfgDate: fields.mfgDate ?? null,
          expDate: fields.expDate ?? null,
          confidence: typeof fields.confidence === "number" ? fields.confidence : null,
        }
      : null,
  };
  append(entry);
  return entry;
}

function list({ limit = 50 } = {}) {
  ensureLoaded();
  const n = Math.max(1, Math.min(MAX_ENTRIES, parseInt(limit, 10) || 50));
  return cache.slice(-n).reverse();
}

function stats() {
  ensureLoaded();
  const total = cache.length;
  const ok = cache.filter((e) => e.status === "ok").length;
  const failed = total - ok;
  const avgConfidence = (() => {
    const xs = cache
      .map((e) => e.fields?.confidence)
      .filter((x) => typeof x === "number");
    if (!xs.length) return null;
    return Number((xs.reduce((a, b) => a + b, 0) / xs.length).toFixed(3));
  })();
  return { total, ok, failed, avgConfidence };
}

function hashImage(base64) {
  if (!base64) return null;
  return crypto.createHash("sha256").update(base64).digest("hex").slice(0, 16);
}

function hashIp(ip) {
  if (!ip) return null;
  return crypto.createHash("sha256").update(String(ip)).digest("hex").slice(0, 12);
}

module.exports = { record, list, stats };
