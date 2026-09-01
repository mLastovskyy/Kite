import { SMTPClient } from "https://deno.land/x/denomailer@1.6.0/mod.ts";

// auth-email: Kite auth emails through the user's own Gmail SMTP, bypassing the Supabase
// built-in mailer (rate-limited, link-based). Both flows are 6-digit codes, never links:
//
//   signup_code   {email, password} -> GoTrue admin generate_link(type=signup) mints the OTP
//                 (creates the user unconfirmed, or re-issues for an existing unconfirmed
//                 one), we force the typed password onto the account and email the code.
//                 The app then calls POST /auth/v1/verify {type: signup, email, token}.
//   recovery_code {email}           -> generate_link(type=recovery) mints the OTP, we email
//                 it. The app calls /verify {type: recovery} and then PUT /user {password}.
//
// GoTrue owns the OTP (hashing, expiry = «Email OTP Expiration», verify rate limit); this
// function only generates and delivers it. Gmail creds live in the RLS-locked
// public.app_secrets table, readable only via service role. Deployed with verify_jwt=false:
// callers are not signed in yet by definition.

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

async function adminFetch(method: string, path: string, body: unknown): Promise<Response> {
  return await fetch(`${SUPABASE_URL}/auth/v1/${path}`, {
    method,
    headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

let cachedCreds: { user: string; pass: string } | null = null;
async function gmailCreds(): Promise<{ user: string; pass: string }> {
  if (cachedCreds) return cachedCreds;
  const res = await fetch(`${SUPABASE_URL}/rest/v1/app_secrets?select=key,value`, {
    headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` },
  });
  const rows = (await res.json()) as Array<{ key: string; value: string }>;
  const user = rows.find((r) => r.key === "gmail_user")?.value ?? "";
  const pass = rows.find((r) => r.key === "gmail_pass")?.value ?? "";
  cachedCreds = { user, pass };
  return cachedCreds;
}

async function sendMail(to: string, subject: string, html: string): Promise<void> {
  const { user, pass } = await gmailCreds();
  const client = new SMTPClient({
    connection: { hostname: "smtp.gmail.com", port: 465, tls: true, auth: { username: user, password: pass } },
  });
  try {
    await client.send({ from: `Kite <${user}>`, to, subject, html, content: "text/html" });
  } finally {
    // denomailer's close() is synchronous in 1.6.0; tolerate either shape and never let a
    // close error mask a successful send.
    try { await client.close(); } catch (_) { /* ignore */ }
  }
}

const FONT = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

/** Branded shell: blue header with the kite mark, title, intro, a big 6-digit code, footer. */
function codeMail(title: string, intro: string, code: string, foot: string): string {
  const digits = code.split("").join("&#8201;"); // thin spaces keep the code readable and copyable
  return `<!DOCTYPE html><html lang="ru"><body style="margin:0;padding:0;background:#F2F2F7;"><table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#F2F2F7;"><tr><td align="center" style="padding:32px 16px;"><table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:440px;background:#fff;border-radius:20px;overflow:hidden;font-family:${FONT};"><tr><td align="center" style="background:#007AFF;padding:36px 24px 28px;"><div style="width:72px;height:72px;background:rgba(255,255,255,0.16);border-radius:36px;font-size:34px;line-height:72px;color:#fff;">&#129666;</div><div style="color:#fff;font-size:22px;font-weight:700;padding-top:16px;">Kite</div></td></tr><tr><td style="padding:32px 28px 8px;"><div style="color:#1C1C1E;font-size:22px;font-weight:700;text-align:center;">${title}</div><div style="color:#6E6E73;font-size:15px;line-height:22px;text-align:center;padding:12px 0 4px;">${intro}</div></td></tr><tr><td align="center" style="padding:20px 28px 8px;"><div style="display:inline-block;background:#F2F2F7;border-radius:14px;padding:16px 28px;color:#1C1C1E;font-size:34px;font-weight:700;letter-spacing:6px;font-family:${FONT};">${digits}</div></td></tr><tr><td style="padding:12px 28px 28px;"><div style="color:#8E8E93;font-size:13px;line-height:19px;text-align:center;">Код действует ограниченное время. Никому его не сообщайте.</div></td></tr><tr><td style="background:#FAFAFC;padding:20px 28px;"><div style="color:#8E8E93;font-size:12px;line-height:18px;text-align:center;">${foot}</div></td></tr></table><div style="color:#B0B0B5;font-size:11px;padding-top:16px;font-family:${FONT};">Kite — родительский контроль</div></td></tr></table></body></html>`;
}

type LinkData = { email_otp?: string; id?: string; properties?: { email_otp?: string }; user?: { id?: string } };

/** Pulls the OTP and user id out of generate_link, which GoTrue returns flat (SDKs reshape it). */
function otpOf(data: LinkData): { otp: string | null; userId: string | null } {
  return {
    otp: data.email_otp ?? data.properties?.email_otp ?? null,
    userId: data.id ?? data.user?.id ?? null,
  };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  let payload: { action?: string; email?: string; password?: string };
  try { payload = await req.json(); } catch { return json({ error: "bad_request" }, 400); }
  const email = (payload.email ?? "").trim().toLowerCase();
  if (!email) return json({ error: "email_required" }, 400);

  try {
    if (payload.action === "signup_code") {
      const password = payload.password ?? "";
      if (password.length < 6) return json({ error: "weak_password" }, 400);

      const linkRes = await adminFetch("POST", "admin/generate_link", { type: "signup", email, password });
      if (!linkRes.ok) {
        const text = await linkRes.text();
        console.error("signup_code generate_link", linkRes.status, text);
        // GoTrue answers 422 email_exists only for an already CONFIRMED account; an
        // unconfirmed one just gets a fresh code, which is exactly what the user expects.
        if (text.includes("email_exists") || text.includes("already")) return json({ error: "already_registered" }, 409);
        if (text.includes("weak_password") || text.includes("Password")) return json({ error: "weak_password" }, 400);
        return json({ error: "signup_failed" }, 500);
      }
      const { otp, userId } = otpOf(await linkRes.json());
      if (!otp || !userId) {
        console.error("signup_code: generate_link returned no email_otp/id");
        return json({ error: "signup_failed" }, 500);
      }
      // generate_link keeps the OLD password of a pre-existing unconfirmed account; the user
      // just typed a new one and will sign in with it, so make it the truth.
      const pwRes = await adminFetch("PUT", `admin/users/${userId}`, { password });
      if (!pwRes.ok) {
        console.error("signup_code set password", pwRes.status, await pwRes.text());
        return json({ error: "signup_failed" }, 500);
      }
      try {
        await sendMail(
          email,
          "Код подтверждения Kite",
          codeMail("Подтвердите почту", "Введите этот код в приложении Kite, чтобы завершить регистрацию.", otp, `Письмо для ${email}. Если это были не вы — просто проигнорируйте его.`),
        );
      } catch (e) {
        console.error("signup_code sendMail", String(e));
        return json({ error: "mail_failed" }, 500);
      }
      return json({ ok: true });
    }

    if (payload.action === "recovery_code") {
      const linkRes = await adminFetch("POST", "admin/generate_link", { type: "recovery", email });
      if (!linkRes.ok) {
        // Unknown address: answer ok anyway so the endpoint never confirms who has an account.
        console.error("recovery_code generate_link", linkRes.status);
        return json({ ok: true });
      }
      const { otp } = otpOf(await linkRes.json());
      if (!otp) return json({ ok: true });
      try {
        await sendMail(
          email,
          "Код для сброса пароля Kite",
          codeMail("Сброс пароля", "Введите этот код в приложении Kite и задайте новый пароль.", otp, `Запрос для ${email}. Если это были не вы — проигнорируйте письмо, пароль не изменится.`),
        );
      } catch (e) {
        console.error("recovery_code sendMail", String(e));
        return json({ error: "mail_failed" }, 500);
      }
      return json({ ok: true });
    }

    return json({ error: "unknown_action" }, 400);
  } catch (e) {
    console.error("auth-email", String(e));
    return json({ error: "server_error" }, 500);
  }
});
