-- Tasks for screen time («Задания», Kids360 «Ваши задания»): the parent creates a task with a reward in minutes, the
-- child marks it done, the parent confirms and the minutes are granted as today's bonus
-- (delivered to the device as a grant_time device_command — the existing extra-time path).
-- Tasks are the way out of an exhausted limit: the child's block screen lists them.
create table if not exists public.tasks (
  id               uuid primary key default uuid_generate_v7(),
  family_id        uuid not null references public.families (id) on delete cascade,
  child_member_id  uuid not null references public.family_members (id) on delete cascade,
  created_by       uuid not null references auth.users (id) on delete cascade,
  title            text not null check (char_length(title) between 1 and 80),
  reward_minutes   integer not null check (reward_minutes between 5 and 240),
  -- open → done (child) → confirmed | rejected (parent). Rejected goes back to open.
  status           text not null default 'open' check (status in ('open', 'done', 'confirmed', 'rejected')),
  -- ISO weekdays 1 (Mon) … 7 (Sun) on which the task recurs; empty = one-time.
  repeat_days      smallint[] not null default '{}',
  created_at       timestamptz not null default now(),
  done_at          timestamptz,
  resolved_at      timestamptz,
  resolved_by      uuid references auth.users (id) on delete set null
);

create index if not exists tasks_child_status_idx on public.tasks (child_member_id, status, created_at desc);
create index if not exists tasks_family_idx on public.tasks (family_id);

alter table public.tasks enable row level security;

-- Everyone in the family reads (the child app shows its own tasks; parents see all).
create policy tasks_select on public.tasks
  for select using (is_family_member(family_id));

-- Parents create, edit, confirm/reject and delete.
create policy tasks_parent_insert on public.tasks
  for insert with check (
    is_family_parent(family_id)
    and created_by = auth.uid()
    and exists (select 1 from public.family_members m where m.id = tasks.child_member_id and m.family_id = tasks.family_id and m.role = 'child')
  );
create policy tasks_parent_update on public.tasks
  for update using (is_family_parent(family_id)) with check (is_family_parent(family_id));
create policy tasks_parent_delete on public.tasks
  for delete using (is_family_parent(family_id));

-- The child may only move its own OPEN task to 'done' (USING restricts the old row, WITH
-- CHECK the new one), so it cannot confirm itself or reopen a resolved task.
create policy tasks_child_mark_done on public.tasks
  for update using (
    status = 'open'
    and exists (select 1 from public.family_members m where m.id = tasks.child_member_id and m.user_id = auth.uid())
  ) with check (
    status = 'done'
    and exists (select 1 from public.family_members m where m.id = tasks.child_member_id and m.user_id = auth.uid())
  );

-- Instant delivery to both sides (parent sees «выполнено», child sees new tasks).
alter publication supabase_realtime add table public.tasks;

comment on table public.tasks is 'Parent-assigned tasks rewarded with screen-time minutes; see docs/KIDS360_PARITY.md.';

-- The child may also ask the parent to give it a task (Kids360 «Попросить задание»).
alter table public.approval_requests drop constraint if exists approval_requests_type_check;
alter table public.approval_requests
  add constraint approval_requests_type_check
  check (type in ('uninstall', 'extra_time', 'unlock', 'task_request'));
