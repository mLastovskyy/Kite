-- Places («Места», Kids360 «Уведомления по местам») and the coarse location trail («Маршруты»).
--
-- places: a named circle the parent saves for one child; the CHILD device evaluates
-- enter/exit locally on every fix (works offline, on every flavor) and writes place_events,
-- then pushes the parents. Raw points stay on the device (CLAUDE.md); location_trail carries
-- only a thinned trail (≥ 5 min and ≥ 50 m apart) kept 7 days — the day's movement as an
-- aggregate, not telemetry.
create table if not exists public.places (
  id               uuid primary key default uuid_generate_v7(),
  family_id        uuid not null references public.families (id) on delete cascade,
  child_member_id  uuid not null references public.family_members (id) on delete cascade,
  name             text not null check (char_length(name) between 1 and 40),
  latitude         double precision not null,
  longitude        double precision not null,
  radius_m         integer not null default 150 check (radius_m between 50 and 2000),
  notify_enter     boolean not null default true,
  notify_exit      boolean not null default true,
  created_by       uuid not null references auth.users (id) on delete cascade,
  created_at       timestamptz not null default now()
);
create index if not exists places_child_idx on public.places (child_member_id);
create index if not exists places_family_idx on public.places (family_id);

alter table public.places enable row level security;
create policy places_select on public.places
  for select using (is_family_member(family_id));
create policy places_parent_insert on public.places
  for insert with check (
    is_family_parent(family_id)
    and created_by = auth.uid()
    and exists (select 1 from public.family_members m where m.id = places.child_member_id and m.family_id = places.family_id and m.role = 'child')
  );
create policy places_parent_update on public.places
  for update using (is_family_parent(family_id)) with check (is_family_parent(family_id));
create policy places_parent_delete on public.places
  for delete using (is_family_parent(family_id));

create table if not exists public.place_events (
  id               uuid primary key default uuid_generate_v7(),
  family_id        uuid not null references public.families (id) on delete cascade,
  child_member_id  uuid not null references public.family_members (id) on delete cascade,
  place_id         uuid not null references public.places (id) on delete cascade,
  kind             text not null check (kind in ('enter', 'exit')),
  at               timestamptz not null default now()
);
create index if not exists place_events_child_at_idx on public.place_events (child_member_id, at desc);

alter table public.place_events enable row level security;
create policy place_events_select on public.place_events
  for select using (is_family_member(family_id));
-- Only the child device itself reports its own events.
create policy place_events_child_insert on public.place_events
  for insert with check (
    exists (select 1 from public.family_members m where m.id = place_events.child_member_id and m.user_id = auth.uid() and m.family_id = place_events.family_id)
  );

create table if not exists public.location_trail (
  id           uuid primary key default uuid_generate_v7(),
  family_id    uuid not null references public.families (id) on delete cascade,
  member_id    uuid not null references public.family_members (id) on delete cascade,
  latitude     double precision not null,
  longitude    double precision not null,
  accuracy_m   real,
  recorded_at  timestamptz not null,
  unique (member_id, recorded_at)
);
create index if not exists location_trail_member_at_idx on public.location_trail (member_id, recorded_at desc);
create index if not exists location_trail_family_idx on public.location_trail (family_id);

alter table public.location_trail enable row level security;
create policy location_trail_select on public.location_trail
  for select using (is_family_member(family_id));
create policy location_trail_own_insert on public.location_trail
  for insert with check (
    exists (select 1 from public.family_members m where m.id = location_trail.member_id and m.user_id = auth.uid() and m.family_id = location_trail.family_id)
  );
-- The device may prune its own trail (retention also runs server-side, see the cron migration).
create policy location_trail_own_delete on public.location_trail
  for delete using (
    exists (select 1 from public.family_members m where m.id = location_trail.member_id and m.user_id = auth.uid())
  );

comment on table public.places is 'Parent-saved places per child; enter/exit evaluated on the child device.';
comment on table public.place_events is 'Enter/exit events reported by the child device.';
comment on table public.location_trail is 'Thinned 7-day location trail per child (>= 5 min / 50 m apart); raw fixes never leave the device.';
