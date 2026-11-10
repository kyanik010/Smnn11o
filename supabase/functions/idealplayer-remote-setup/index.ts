// Connected IdealPlayer Supabase Edge Function
//
// This function implements the Android/web contract documented in web/remote-setup/README.md:
//   POST /api/pairings
//   GET  /api/pairings/{pairingCode}
//   POST /api/pairings/{pairingCode}/playlist
//
// Deploy with --no-verify-jwt. The function uses SUPABASE_SERVICE_ROLE_KEY internally so the
// companion web app never receives database keys and cannot list pairing rows.

const FUNCTION_NAME = "idealplayer-remote-setup";
const TABLE_NAME = "tv_pairings";

const PAIRING_CODE_LENGTH = 8;
const PAIRING_CODE_PATTERN = /^[A-HJ-NP-Z2-9]{8}$/;
const SUPPORTED_SOURCES = new Set(["connected-idealplayer"]);
const SUPPORTED_TYPES = new Set(["xtream", "m3u", "file"]);
const PAIRING_TTL_MS = 10 * 60 * 1000;
const MAX_PLAYLIST_BYTES = 5 * 1024 * 1024;
const MAX_REQUEST_BYTES = MAX_PLAYLIST_BYTES + 64 * 1024;
const MAX_NAME_LENGTH = 128;
const MAX_URL_LENGTH = 4096;

Deno.serve(async (request) => {
  const cors = resolveCors(request);
  if (request.method === "OPTIONS") {
    return new Response(null, {
      status: cors.allowed ? 204 : 403,
      headers: cors.headers,
    });
  }
  if (!cors.allowed) {
    return jsonResponse({ error: "origin_not_allowed" }, 403, cors.headers);
  }

  const route = routeFromRequest(request);

  try {
    await cleanupExpiredPairings();

    if (request.method === "POST" && route === "/api/pairings") {
      return openPairing(request, cors.headers);
    }

    const pairingRoute = route.match(/^\/api\/pairings\/([A-HJ-NP-Z2-9]{8})$/);
    if (request.method === "GET" && pairingRoute) {
      return getPairing(pairingRoute[1], cors.headers);
    }

    const playlistRoute = route.match(
      /^\/api\/pairings\/([A-HJ-NP-Z2-9]{8})\/playlist$/,
    );
    if (request.method === "POST" && playlistRoute) {
      return submitPlaylist(request, playlistRoute[1], cors.headers);
    }

    return jsonResponse({ error: "not_found" }, 404, cors.headers);
  } catch (error) {
    if (error instanceof HttpBodyError) {
      const status = error.message === "request_too_large" ? 413 : 400;
      return jsonResponse({ error: error.message }, status, cors.headers);
    }
    console.error("Connected IdealPlayer request failed", {
      route,
      method: request.method,
      name: error instanceof Error ? error.name : "Error",
    });
    return jsonResponse({ error: "internal_error" }, 500, cors.headers);
  }
});

async function openPairing(
  request: Request,
  headers: HeadersInit,
): Promise<Response> {
  const body = await readJsonBody(request);
  const pairingCode = normalizePairingCode(body?.pairingCode);
  if (!isValidPairingCode(pairingCode)) {
    return jsonResponse({ error: "invalid_pairing_code" }, 400, headers);
  }

  await deletePairing(pairingCode);
  const response = await postgrest(TABLE_NAME, {
    method: "POST",
    headers: { Prefer: "return=minimal" },
    body: JSON.stringify({
      pairing_code: pairingCode,
      status: "pending",
      payload: {},
    }),
  });

  if (!response.ok) {
    return jsonResponse({ error: "pairing_open_failed" }, 502, headers);
  }

  return jsonResponse({ status: "pending" }, 201, headers);
}

async function getPairing(
  pairingCode: string,
  headers: HeadersInit,
): Promise<Response> {
  if (!isValidPairingCode(pairingCode)) {
    return jsonResponse({ error: "invalid_pairing_code" }, 400, headers);
  }

  const row = await findPairing(pairingCode);
  if (!row) {
    return jsonResponse({ error: "not_found" }, 404, headers);
  }

  if (isExpired(row.created_at)) {
    await deletePairing(pairingCode);
    return jsonResponse({ status: "expired" }, 200, headers);
  }

  if (row.status === "completed") {
    await deletePairing(pairingCode);
    return jsonResponse(
      { status: "completed", payload: row.payload ?? {} },
      200,
      headers,
    );
  }

  if (row.status === "error" || row.status === "expired") {
    await deletePairing(pairingCode);
    return jsonResponse({ status: row.status }, 200, headers);
  }

  return jsonResponse({ status: "pending" }, 200, headers);
}

async function submitPlaylist(
  request: Request,
  pairingCode: string,
  headers: HeadersInit,
): Promise<Response> {
  if (!isValidPairingCode(pairingCode)) {
    return jsonResponse({ error: "invalid_pairing_code" }, 400, headers);
  }

  const row = await findPairing(pairingCode);
  if (!row) {
    return jsonResponse({ error: "not_found" }, 404, headers);
  }
  if (isExpired(row.created_at)) {
    await deletePairing(pairingCode);
    return jsonResponse({ status: "expired" }, 410, headers);
  }

  const payload = await readJsonBody(request);
  const validationError = validateConnectedSetupPayload(payload, pairingCode);
  if (validationError) {
    return jsonResponse({ error: validationError }, 400, headers);
  }

  const response = await postgrest(
    `${TABLE_NAME}?pairing_code=eq.${encodeURIComponent(pairingCode)}`,
    {
      method: "PATCH",
      headers: { Prefer: "return=minimal" },
      body: JSON.stringify({
        status: "completed",
        payload,
        completed_at: new Date().toISOString(),
      }),
    },
  );

  if (!response.ok) {
    return jsonResponse({ error: "playlist_submit_failed" }, 502, headers);
  }

  return jsonResponse({ status: "completed" }, 200, headers);
}

async function findPairing(pairingCode: string): Promise<PairingRow | null> {
  const query = [
    `pairing_code=eq.${encodeURIComponent(pairingCode)}`,
    "select=pairing_code,status,payload,created_at,completed_at",
    "limit=1",
  ].join("&");
  const response = await postgrest(`${TABLE_NAME}?${query}`, { method: "GET" });
  if (!response.ok) return null;

  const rows = (await response.json()) as PairingRow[];
  return rows[0] ?? null;
}

async function deletePairing(pairingCode: string): Promise<void> {
  await postgrest(
    `${TABLE_NAME}?pairing_code=eq.${encodeURIComponent(pairingCode)}`,
    {
      method: "DELETE",
      headers: { Prefer: "return=minimal" },
    },
  );
}

async function cleanupExpiredPairings(): Promise<void> {
  const expiredBefore = new Date(Date.now() - PAIRING_TTL_MS).toISOString();
  await postgrest(
    `${TABLE_NAME}?created_at=lt.${encodeURIComponent(expiredBefore)}`,
    {
      method: "DELETE",
      headers: { Prefer: "return=minimal" },
    },
  );
}

async function postgrest(path: string, init: RequestInit): Promise<Response> {
  const supabaseUrl = requiredEnv("SUPABASE_URL").replace(/\/$/, "");
  const serviceRoleKey = requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
  const requestHeaders = new Headers(init.headers);
  requestHeaders.set("apikey", serviceRoleKey);
  requestHeaders.set("Authorization", `Bearer ${serviceRoleKey}`);
  requestHeaders.set("Content-Type", "application/json");

  return fetch(`${supabaseUrl}/rest/v1/${path}`, {
    ...init,
    headers: requestHeaders,
  });
}

async function readJsonBody(
  request: Request,
): Promise<Record<string, unknown>> {
  const contentLength = Number(request.headers.get("content-length") ?? "0");
  if (contentLength > MAX_REQUEST_BYTES) {
    throw new HttpBodyError("request_too_large");
  }

  const text = await request.text();
  if (new TextEncoder().encode(text).length > MAX_REQUEST_BYTES) {
    throw new HttpBodyError("request_too_large");
  }

  try {
    const value = JSON.parse(text);
    if (!value || typeof value !== "object" || Array.isArray(value)) {
      throw new Error("invalid_json");
    }
    return value as Record<string, unknown>;
  } catch (_error) {
    throw new HttpBodyError("invalid_json");
  }
}

function validateConnectedSetupPayload(
  payload: Record<string, unknown>,
  expectedCode: string,
): string | null {
  if (payload.version !== 1) return "unsupported_version";
  if (
    typeof payload.source !== "string" || !SUPPORTED_SOURCES.has(payload.source)
  ) {
    return "unsupported_source";
  }

  const payloadCode = normalizePairingCode(payload.pairingCode);
  if (
    !isValidPairingCode(payloadCode) ||
    !constantTimeEquals(payloadCode, expectedCode)
  ) {
    return "pairing_code_mismatch";
  }

  const playlist = payload.playlist;
  if (!playlist || typeof playlist !== "object" || Array.isArray(playlist)) {
    return "missing_playlist";
  }
  const info = playlist as Record<string, unknown>;
  const type = typeof info.type === "string" ? info.type : "";
  if (!SUPPORTED_TYPES.has(type)) return "unsupported_playlist_type";

  const name = optionalString(info.name).trim();
  if (!name || name.length > MAX_NAME_LENGTH) return "invalid_playlist_name";

  const epgUrl = optionalString(info.epgUrl).trim();
  if (epgUrl && !isHttpUrl(epgUrl)) return "invalid_epg_url";

  if (type === "xtream") {
    if (!isHttpUrl(optionalString(info.serverUrl).trim())) {
      return "invalid_server_url";
    }
    if (!optionalString(info.username).trim()) return "missing_username";
    if (!optionalString(info.password)) return "missing_password";
  }

  if (type === "m3u" && !isHttpUrl(optionalString(info.url).trim())) {
    return "invalid_m3u_url";
  }

  if (type === "file") {
    const fileContent = optionalString(info.fileContent);
    const fileSize = typeof info.fileSize === "number" ? info.fileSize : 0;
    if (!fileContent) return "missing_file_content";
    if (fileSize > MAX_PLAYLIST_BYTES) return "file_too_large";
    if (new TextEncoder().encode(fileContent).length > MAX_PLAYLIST_BYTES) {
      return "file_too_large";
    }
  }

  return null;
}

function routeFromRequest(request: Request): string {
  const pathname = new URL(request.url).pathname.replace(/\/+$/, "") || "/";
  const marker = `/${FUNCTION_NAME}`;
  const markerIndex = pathname.indexOf(marker);
  if (markerIndex >= 0) {
    return pathname.slice(markerIndex + marker.length) || "/";
  }
  return pathname;
}

function resolveCors(
  request: Request,
): { allowed: boolean; headers: HeadersInit } {
  const origin = request.headers.get("Origin") ?? "";
  const allowedOrigins = (Deno.env.get("CONNECTED_IDEALPLAYER_ALLOWED_ORIGINS") ?? "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);
  const allowAnyOrigin = allowedOrigins.length === 0;
  const allowed = allowAnyOrigin || origin === "" ||
    allowedOrigins.includes(origin);
  const allowOrigin = allowAnyOrigin ? "*" : origin;

  return {
    allowed,
    headers: {
      "Access-Control-Allow-Origin": allowed ? allowOrigin : "null",
      "Access-Control-Allow-Headers": "authorization, apikey, content-type",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Max-Age": "600",
      "Cache-Control": "no-store",
      "Content-Type": "application/json; charset=utf-8",
      Vary: "Origin",
    },
  };
}

function jsonResponse(
  body: unknown,
  status: number,
  headers: HeadersInit,
): Response {
  return new Response(JSON.stringify(body), { status, headers });
}

function normalizePairingCode(value: unknown): string {
  if (typeof value !== "string") return "";
  return value.toUpperCase().slice(0, PAIRING_CODE_LENGTH);
}

function isValidPairingCode(value: string): boolean {
  return PAIRING_CODE_PATTERN.test(value);
}

function isExpired(createdAt: string): boolean {
  const createdTime = new Date(createdAt).getTime();
  return !Number.isFinite(createdTime) ||
    Date.now() - createdTime > PAIRING_TTL_MS;
}

function optionalString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function isHttpUrl(value: string): boolean {
  if (!value || value.length > MAX_URL_LENGTH) return false;
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch (_error) {
    return false;
  }
}

function constantTimeEquals(left: string, right: string): boolean {
  if (left.length !== right.length) return false;
  let mismatch = 0;
  for (let index = 0; index < left.length; index += 1) {
    mismatch |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return mismatch === 0;
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`Missing required env: ${name}`);
  return value;
}

class HttpBodyError extends Error {}

interface PairingRow {
  pairing_code: string;
  status: "pending" | "completed" | "expired" | "error";
  payload: Record<string, unknown> | null;
  created_at: string;
  completed_at: string | null;
}
