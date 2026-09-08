-- Crash reports stop being a file on one phone. A sideloaded build has no Play Console and no
-- Crashlytics (that one needs GMS — exactly the phone we cannot debug), so the report was only
-- readable on the device it happened on. Now every phone in the family uploads its last crash
-- and every parent sees the lot, each line saying who and what it came from (owner, 08.09.2026).
create table if not exists public.crash_reports (
  id             uuid primary key default uuid_generate_v7(),
  family_id      uuid not null references public.families (id) on delete cascade,
  -- Null only if a device somehow reports before it has a member row.
  member_id      uuid references public.family_members (id) on delete set null,
  actor          uuid references auth.users (id) on delete set null,
  app            text not null check (app in ('parent', 'child')),
  -- Denormalised on purpose: the report has to name its source even when the member is gone.
  author_name    text,
  device_model   text,
  os_version     text,
  version_name   text,
  happened_at    timestamptz not null,
  report         text not null check (char_length(report) <= 20000),
  created_at     timestamptz not null default now()
);

-- One row per crash per device: the same stored report must not pile up on every app start.
create unique index if not exists crash_reports_unique_idx
  on public.crash_reports (family_id, member_id, happened_at);
create index if not exists crash_reports_family_idx on public.crash_reports (family_id, happened_at desc);

alter table public.crash_reports enable row level security;

-- The whole family reads them; a device may only file its own.
create policy crash_reports_select on public.crash_reports
  for select using (is_family_member(family_id));
create policy crash_reports_insert on public.crash_reports
  for insert with check (is_family_member(family_id) and actor = auth.uid());

-- A stack trace is worth keeping for as long as it is worth reading.
select cron.schedule(
  'kite_crash_reports_retention',
  '31 3 * * *',
  $$delete from public.crash_reports where created_at < now() - interval '30 days'$$
);

comment on table public.crash_reports is 'Last crash per device, shared with the family; see core/diagnostics/CrashLog.kt.';
