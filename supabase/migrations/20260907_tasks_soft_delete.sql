-- A deleted task keeps its row: «История заданий» has to say who removed it and when, and a
-- real DELETE sends no realtime UPDATE, so the parent's «Задания» badge stayed lit forever.
alter table public.tasks drop constraint if exists tasks_status_check;
alter table public.tasks add constraint tasks_status_check
  check (status = any (array['open'::text, 'done'::text, 'confirmed'::text, 'rejected'::text, 'deleted'::text]));
