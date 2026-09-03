-- Installed-app inventory published by the child device («Приложения» on the parent side).
--
-- Why: until now the parent could only see apps the child had already USED (usage_apps).
-- Kids360 lets a parent toggle any app on the phone and set a limit before it is ever
-- opened; that needs the launchable-app list. Only launchable apps (MAIN/LAUNCHER) go up,
-- as package + current label + system flag — no icons (binary data against a 500 MB tier,
-- for a cosmetic gain). Refreshed on service start, on PACKAGE_ADDED/REMOVED and at most
-- daily otherwise; the child's «Что видит родитель» screen lists it.
create table if not exists public.child_apps (
  member_id     uuid not null references public.family_members (id) on delete cascade,
  family_id     uuid not null references public.families (id) on delete cascade,
  package_name  text not null check (char_length(package_name) between 1 and 200),
  label         text not null check (char_length(label) between 1 and 120),
  is_system     boolean not null default false,
  updated_at    timestamptz not null default now(),
  primary key (member_id, package_name)
);
create index if not exists child_apps_member_label_idx on public.child_apps (member_id, label);
create index if not exists child_apps_family_idx on public.child_apps (family_id);

alter table public.child_apps enable row level security;
create policy child_apps_select on public.child_apps
  for select using (is_family_member(family_id));
-- Only the child device itself writes its own inventory; parents never write here.
create policy child_apps_own_insert on public.child_apps
  for insert with check (
    exists (select 1 from public.family_members m where m.id = child_apps.member_id and m.user_id = auth.uid() and m.family_id = child_apps.family_id)
  );
create policy child_apps_own_update on public.child_apps
  for update using (
    exists (select 1 from public.family_members m where m.id = child_apps.member_id and m.user_id = auth.uid())
  ) with check (
    exists (select 1 from public.family_members m where m.id = child_apps.member_id and m.user_id = auth.uid() and m.family_id = child_apps.family_id)
  );
create policy child_apps_own_delete on public.child_apps
  for delete using (
    exists (select 1 from public.family_members m where m.id = child_apps.member_id and m.user_id = auth.uid())
  );
