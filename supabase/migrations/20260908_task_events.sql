-- «История заданий» stops being a list of tasks and becomes a list of events: creating a task,
-- editing it and deleting it each get their own line, next to the ones the task already had
-- (owner, 08.09.2026). A trigger writes them, not the client — the child's «Выполнил» and a
-- second parent acting from another phone have to land in the same log, and a client forgets.
alter table public.tasks
  add column if not exists from_repeat boolean not null default false;

comment on column public.tasks.from_repeat is
  'True when the parent app recreated a recurring task after confirming it: the log then says «повторилось», not «создано».';

create table if not exists public.task_events (
  id               uuid primary key default uuid_generate_v7(),
  family_id        uuid not null references public.families (id) on delete cascade,
  task_id          uuid not null references public.tasks (id) on delete cascade,
  child_member_id  uuid not null references public.family_members (id) on delete cascade,
  -- Null when the row was backfilled from a task that never recorded who acted.
  actor            uuid references auth.users (id) on delete set null,
  kind             text not null check (kind in ('created', 'repeated', 'updated', 'done', 'confirmed', 'rejected', 'deleted')),
  -- The title and the reward AS THEY WERE: editing a task must not rewrite its own past.
  title            text not null,
  reward_minutes   integer not null,
  created_at       timestamptz not null default now()
);

create index if not exists task_events_child_idx on public.task_events (child_member_id, created_at desc);
create index if not exists task_events_family_idx on public.task_events (family_id, created_at desc);

alter table public.task_events enable row level security;

-- Read-only for the family; only the trigger writes, and it runs as the owner.
create policy task_events_select on public.task_events
  for select using (is_family_member(family_id));

create or replace function public.log_task_event() returns trigger
  language plpgsql security definer set search_path = public as $$
declare
  event_kind text;
begin
  if tg_op = 'INSERT' then
    event_kind := case when new.from_repeat then 'repeated' else 'created' end;
  elsif new.status is distinct from old.status then
    -- 'open' is a rejected task being picked up again; the rejection is already in the log.
    if new.status = 'open' then
      return new;
    end if;
    event_kind := new.status;
  elsif new.title is distinct from old.title
     or new.reward_minutes is distinct from old.reward_minutes
     or new.repeat_days is distinct from old.repeat_days then
    event_kind := 'updated';
  else
    return new;
  end if;

  insert into public.task_events (family_id, task_id, child_member_id, actor, kind, title, reward_minutes)
  values (new.family_id, new.id, new.child_member_id, auth.uid(), event_kind, new.title, new.reward_minutes);
  return new;
end;
$$;

drop trigger if exists tasks_log_event on public.tasks;
create trigger tasks_log_event after insert or update on public.tasks
  for each row execute function public.log_task_event();

-- Backfill, so the history the parent already had does not go blank: every task gets its
-- creation, and whatever end state it reached. What a task went through in between was never
-- recorded and cannot be invented.
insert into public.task_events (family_id, task_id, child_member_id, actor, kind, title, reward_minutes, created_at)
select t.family_id, t.id, t.child_member_id, t.created_by, 'created', t.title, t.reward_minutes, t.created_at
from public.tasks t
where not exists (select 1 from public.task_events e where e.task_id = t.id);

insert into public.task_events (family_id, task_id, child_member_id, actor, kind, title, reward_minutes, created_at)
select t.family_id, t.id, t.child_member_id, t.resolved_by, t.status, t.title, t.reward_minutes, t.resolved_at
from public.tasks t
where t.status in ('confirmed', 'rejected', 'deleted')
  and t.resolved_at is not null
  and not exists (select 1 from public.task_events e where e.task_id = t.id and e.kind = t.status);

comment on table public.task_events is 'One line per thing that happened to a task; «История заданий» reads this, not the tasks themselves.';

-- A trigger function has no business in the REST surface: revoke it from the API roles.
revoke execute on function public.log_task_event() from anon, authenticated, public;
