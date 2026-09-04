// send-push: FCM data message to one user's devices or to every parent / child of a family.
// The gateway verifies the caller's JWT; the caller must share a family with every target.
// Body: { target_user_id?, member_id?, family_id?, audience?: "parents" | "children",
//         title?, body?, channel?, collapse?, data? }

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
}

async function rest(path: string): Promise<any[]> {
  const res = await fetch(`${SUPABASE_URL}/rest/v1/${path}`, {
    headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` },
  });
  const data = await res.json();
  return Array.isArray(data) ? data : [];
}

function b64url(input: ArrayBuffer | string): string {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : new Uint8Array(input);
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToDer(pem: string): ArrayBuffer {
  const body = pem.replace(/-----BEGIN PRIVATE KEY-----/, "").replace(/-----END PRIVATE KEY-----/, "").replace(/\s+/g, "");
  const raw = atob(body);
  const buf = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) buf[i] = raw.charCodeAt(i);
  return buf.buffer;
}

let cachedToken: { token: string; exp: number } | null = null;

async function fcmAccessToken(sa: any): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.exp - 60 > now) return cachedToken.token;
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = b64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: sa.token_uri,
    iat: now,
    exp: now + 3600,
  }));
  const signingInput = `${header}.${claim}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(signingInput));
  const assertion = `${signingInput}.${b64url(sig)}`;
  const res = await fetch(sa.token_uri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${assertion}`,
  });
  const data = await res.json();
  if (!data.access_token) throw new Error("fcm_oauth_failed");
  cachedToken = { token: data.access_token, exp: now + 3600 };
  return data.access_token;
}

function callerId(req: Request): string | null {
  const auth = req.headers.get("Authorization")?.replace("Bearer ", "");
  if (!auth) return null;
  try {
    const payload = JSON.parse(atob(auth.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return payload.sub ?? null;
  } catch {
    return null;
  }
}

type Payload = {
  target_user_id?: string;
  member_id?: string;
  family_id?: string;
  audience?: "parents" | "children";
  title?: string;
  body?: string;
  channel?: string;
  collapse?: string;
  data?: Record<string, string>;
};

async function resolveTargets(payload: Payload, caller: string): Promise<string[] | null> {
  const callerFamilies = (await rest(`family_members?user_id=eq.${caller}&select=family_id`)).map((r) => r.family_id);

  if (payload.family_id && payload.audience) {
    if (!callerFamilies.includes(payload.family_id)) return null;
    const roles = payload.audience === "parents" ? "(owner,parent)" : "(child)";
    const rows = await rest(`family_members?family_id=eq.${payload.family_id}&role=in.${roles}&select=user_id`);
    return [...new Set(rows.map((r) => r.user_id as string).filter((id) => id && id !== caller))];
  }

  let target = payload.target_user_id ?? null;
  if (!target && payload.member_id) {
    const rows = await rest(`family_members?id=eq.${payload.member_id}&select=user_id`);
    target = rows[0]?.user_id ?? null;
  }
  if (!target) return [];
  const targetFamilies = (await rest(`family_members?user_id=eq.${target}&select=family_id`)).map((r) => r.family_id);
  if (!callerFamilies.some((f: string) => targetFamilies.includes(f))) return null;
  return [target];
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const caller = callerId(req);
  if (!caller) return json({ error: "unauthorized" }, 401);

  let payload: Payload;
  try {
    payload = await req.json();
  } catch {
    return json({ error: "bad_request" }, 400);
  }

  const targets = await resolveTargets(payload, caller);
  if (targets === null) return json({ error: "forbidden" }, 403);
  if (targets.length === 0) return json({ error: "target_required" }, 400);

  const tokenRows = await rest(`device_push_tokens?user_id=in.(${targets.join(",")})&platform=eq.fcm&select=token`);
  const tokens = tokenRows.map((r) => r.token as string);
  if (tokens.length === 0) return json({ sent: 0, targets: targets.length });

  const sa = JSON.parse((await rest(`app_secrets?key=eq.fcm_service_account&select=value`))[0].value);
  const accessToken = await fcmAccessToken(sa);

  const dataPayload: Record<string, string> = { ...(payload.data ?? {}) };
  if (payload.title) dataPayload.title = payload.title;
  if (payload.body) dataPayload.body = payload.body;
  if (payload.channel) dataPayload.channel = payload.channel;
  if (payload.collapse) dataPayload.collapse = payload.collapse;

  const android: Record<string, unknown> = { priority: "high" };
  if (payload.collapse) android.collapse_key = payload.collapse;

  let sent = 0;
  for (const token of tokens) {
    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
      body: JSON.stringify({ message: { token, data: dataPayload, android } }),
    });
    if (res.ok) sent++;
  }
  return json({ sent, targets: targets.length });
});
