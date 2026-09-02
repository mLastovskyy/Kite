-- One-time codes for linking an email + password to an ANONYMOUS parent session.
-- Written and read only by the auth-email Edge Function (service role); no client access.
-- Rationale: a parent starts Kite without an account and adds an email later, solely to
-- sign in on another phone. GoTrue cannot mint an OTP for a user that has no email yet, so
-- the function keeps its own hashed code here and, on success, sets email + password on the
-- same auth user (keeping the family membership).
create table if not exists public.email_link_codes (
  user_id     uuid primary key references auth.users (id) on delete cascade,
  email       text not null,
  code_hash   text not null,             -- sha256(user_id || ':' || code), hex
  attempts    integer not null default 0,
  expires_at  timestamptz not null,
  created_at  timestamptz not null default now()
);

alter table public.email_link_codes enable row level security;
revoke all on table public.email_link_codes from anon, authenticated;

comment on table public.email_link_codes is
  'Service-role only. Pending 6-digit codes for attaching an email to an anonymous auth user (auth-email Edge Function).';
