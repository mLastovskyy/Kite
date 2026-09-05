create table if not exists public.time_grants (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  child_member_id uuid not null references public.family_members(id) on delete cascade,
  granted_by uuid references public.family_members(id) on delete set null,
  minutes int not null check (minutes between 1 and 720),
  package_name text,
  source text not null default 'request' check (source in ('request', 'task', 'manual', 'offline_code')),
  created_at timestamptz not null default now()
);

create index if not exists time_grants_child_idx on public.time_grants (child_member_id, created_at desc);

alter table public.time_grants enable row level security;

drop policy if exists time_grants_select on public.time_grants;
create policy time_grants_select on public.time_grants
  for select using (is_family_member(family_id));

drop policy if exists time_grants_parent_insert on public.time_grants;
create policy time_grants_parent_insert on public.time_grants
  for insert with check (is_family_parent(family_id));

alter publication supabase_realtime add table public.time_grants;
