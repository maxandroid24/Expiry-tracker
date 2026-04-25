"use strict";

const express = require("express");
const rateLimit = require("express-rate-limit");
const morgan = require("morgan");
const path = require("path");
const scanLog = require("./scanLog");

const PORT = parseInt(process.env.PORT || "5000", 10);
const HOST = "0.0.0.0";
const OPENAI_API_KEY = process.env.OPENAI_API_KEY || "";
const OPENAI_MODEL = process.env.OPENAI_MODEL || "gpt-4o-mini";
const OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";

const MAX_BASE64_BYTES = 8 * 1024 * 1024;
const MAX_PIXELS_HINT = "1024px on the longest side recommended";

const app = express();

app.set("trust proxy", 1);
app.use(morgan("tiny"));
app.use(express.json({ limit: "12mb" }));
app.use(express.static(path.join(__dirname, "public")));

const ocrLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 20,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: "rate_limited", detail: "Too many OCR requests. Try again in a minute." },
});

app.get("/health", (_req, res) => {
  res.json({
    ok: true,
    openaiConfigured: Boolean(OPENAI_API_KEY),
    model: OPENAI_MODEL,
    uptime: process.uptime(),
  });
});

app.get("/scans", (req, res) => {
  const limit = req.query.limit ? parseInt(req.query.limit, 10) : 50;
  res.json({
    stats: scanLog.stats(),
    entries: scanLog.list({ limit }),
  });
});

app.post("/ocr", ocrLimiter, async (req, res) => {
  if (!OPENAI_API_KEY) {
    return res.status(503).json({
      error: "not_configured",
      detail: "OPENAI_API_KEY is not set on the backend.",
    });
  }

  const { image, hint } = req.body || {};

  if (typeof image !== "string" || image.length === 0) {
    return res.status(400).json({
      error: "invalid_request",
      detail: "Body must include a non-empty `image` field containing base64-encoded JPEG/PNG bytes.",
    });
  }

  if (image.length > MAX_BASE64_BYTES) {
    return res.status(413).json({
      error: "payload_too_large",
      detail: `Base64 image exceeds ${MAX_BASE64_BYTES} bytes. Resize to ${MAX_PIXELS_HINT} before uploading.`,
    });
  }

  const cleaned = image.replace(/^data:image\/[a-zA-Z]+;base64,/, "");
  if (!/^[A-Za-z0-9+/=\s]+$/.test(cleaned)) {
    return res.status(400).json({
      error: "invalid_request",
      detail: "`image` must be valid base64.",
    });
  }

  const dataUrl = `data:image/jpeg;base64,${cleaned}`;

  const systemPrompt =
    "You extract product info from packaging photos. Always respond with strict JSON " +
    'in this exact shape: {"name": string|null, "mfgDate": string|null, "expDate": string|null, ' +
    '"confidence": number}. Dates must be ISO `YYYY-MM-DD` when day is known, or `YYYY-MM` ' +
    "when only month/year is printed. `confidence` is a number from 0 to 1 reflecting how " +
    "sure you are overall. Return null for any field you cannot read. No prose, no code fences.";

  const userText = [
    "Identify the product name, manufacturing date (MFG / PKD / Packed), and expiry date " +
      "(EXP / Best Before / Use By) from this product label image.",
    "Output JSON only.",
    hint ? `Here is OCR text already extracted from the image as a hint:\n${String(hint).slice(0, 2000)}` : null,
  ]
    .filter(Boolean)
    .join("\n\n");

  const payload = {
    model: OPENAI_MODEL,
    temperature: 0,
    response_format: { type: "json_object" },
    messages: [
      { role: "system", content: systemPrompt },
      {
        role: "user",
        content: [
          { type: "text", text: userText },
          { type: "image_url", image_url: { url: dataUrl } },
        ],
      },
    ],
  };

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 45_000);
  const startedAt = Date.now();
  const ip = req.ip;

  try {
    const upstream = await fetch(OPENAI_ENDPOINT, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${OPENAI_API_KEY}`,
      },
      body: JSON.stringify(payload),
      signal: controller.signal,
    });

    if (!upstream.ok) {
      const detail = await safeReadText(upstream);
      console.error("OpenAI error", upstream.status, detail);
      scanLog.record({
        imageBase64: cleaned, ip, model: OPENAI_MODEL,
        durationMs: Date.now() - startedAt, status: `upstream_${upstream.status}`,
      });
      return res.status(502).json({
        error: "upstream_failed",
        detail: `OpenAI returned ${upstream.status}.`,
      });
    }

    const json = await upstream.json();
    const content = json?.choices?.[0]?.message?.content;
    if (!content) {
      scanLog.record({
        imageBase64: cleaned, ip, model: OPENAI_MODEL,
        durationMs: Date.now() - startedAt, status: "upstream_empty",
      });
      return res.status(502).json({
        error: "upstream_empty",
        detail: "OpenAI did not return a message.",
      });
    }

    const extracted = parseExtraction(content);
    if (!extracted) {
      scanLog.record({
        imageBase64: cleaned, ip, model: OPENAI_MODEL,
        durationMs: Date.now() - startedAt, status: "parse_failed",
      });
      return res.status(502).json({
        error: "parse_failed",
        detail: "Could not parse extraction JSON from the model.",
        raw: content,
      });
    }

    scanLog.record({
      imageBase64: cleaned, ip, model: OPENAI_MODEL,
      durationMs: Date.now() - startedAt, status: "ok", fields: extracted,
    });
    return res.json(extracted);
  } catch (err) {
    if (err.name === "AbortError") {
      scanLog.record({
        imageBase64: cleaned, ip, model: OPENAI_MODEL,
        durationMs: Date.now() - startedAt, status: "upstream_timeout",
      });
      return res.status(504).json({ error: "upstream_timeout", detail: "OpenAI request timed out." });
    }
    console.error("OCR call failed", err);
    scanLog.record({
      imageBase64: cleaned, ip, model: OPENAI_MODEL,
      durationMs: Date.now() - startedAt, status: "internal_error",
    });
    return res.status(500).json({ error: "internal_error", detail: "Unexpected server error." });
  } finally {
    clearTimeout(timeout);
  }
});

app.use((err, _req, res, _next) => {
  if (err && err.type === "entity.too.large") {
    return res.status(413).json({ error: "payload_too_large", detail: "Request body too large." });
  }
  console.error("Unhandled error", err);
  return res.status(500).json({ error: "internal_error" });
});

app.listen(PORT, HOST, () => {
  console.log(`Expiry Tracker OCR backend listening on http://${HOST}:${PORT}`);
  console.log(`OpenAI configured: ${Boolean(OPENAI_API_KEY)} · model: ${OPENAI_MODEL}`);
});

function parseExtraction(content) {
  let text = content.trim();
  text = text.replace(/^```(?:json)?\s*/i, "").replace(/```$/i, "").trim();

  let obj;
  try {
    obj = JSON.parse(text);
  } catch (_) {
    return null;
  }

  const name = sanitizeString(obj.name);
  const mfgDate = sanitizeDate(obj.mfgDate ?? obj.manufacturing_date ?? obj.mfg_date);
  const expDate = sanitizeDate(obj.expDate ?? obj.expiry_date ?? obj.exp_date);
  let confidence = Number(obj.confidence);
  if (!Number.isFinite(confidence)) confidence = 0.7;
  confidence = Math.max(0, Math.min(1, confidence));

  return { name, mfgDate, expDate, confidence };
}

function sanitizeString(value) {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  if (!trimmed || trimmed.toLowerCase() === "null") return null;
  return trimmed.slice(0, 200);
}

function sanitizeDate(value) {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  if (!trimmed || trimmed.toLowerCase() === "null") return null;
  if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) return trimmed;
  if (/^\d{4}-\d{2}$/.test(trimmed)) return trimmed;
  return null;
}

async function safeReadText(resp) {
  try {
    return (await resp.text()).slice(0, 500);
  } catch (_) {
    return "";
  }
}
