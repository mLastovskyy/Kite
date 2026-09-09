-- Фото к заданию (owner, 09.09.2026): the child may attach a photo when marking a task done —
-- the tidied room, the finished homework — and the parent decides with it in front of them.
-- Optional: a task without a photo works exactly as before.
alter table public.tasks
  add column if not exists photo_url text;

comment on column public.tasks.photo_url is
  'Public URL of the photo the child attached to the current attempt; null when none was attached.';

-- The history keeps the photo of every attempt, not just the last one: «историю присылания и
-- фотки отказа родителя или ребёнка тоже сохраняем в общую историю тасок» (owner, 09.09.2026).
alter table public.task_events
  add column if not exists photo_url text;

-- The child picks a rejected task up again, so the update policy has to let it: with
-- `status = 'open'` alone the PATCH matched no row, silently did nothing, and «Сделать снова»
-- left the task waiting for a confirmation the parent never got asked for.
drop policy if exists tasks_child_mark_done on public.tasks;
create policy tasks_child_mark_done on public.tasks
  for update using (
    status in ('open', 'rejected')
    and exists (select 1 from public.family_members m where m.id = tasks.child_member_id and m.user_id = auth.uid())
  ) with check (
    status = 'done'
    and exists (select 1 from public.family_members m where m.id = tasks.child_member_id and m.user_id = auth.uid())
  );

-- Same trigger, one more column: 'done' carries the photo the child sent, and 'confirmed' /
-- 'rejected' carry the photo the parent was looking at when they decided.
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

  insert into public.task_events (family_id, task_id, child_member_id, actor, kind, title, reward_minutes, photo_url)
  values (new.family_id, new.id, new.child_member_id, auth.uid(), event_kind, new.title, new.reward_minutes, new.photo_url);
  return new;
end;
$$;

revoke execute on function public.log_task_event() from anon, authenticated, public;

-- Photos live in Storage, never in a column: the free tier is 500 MB of Postgres and a photo
-- would eat it in a week. Path: task-photos/<child_member_id>/<task_id>_<epoch_ms>.jpg, so a
-- second attempt cannot overwrite the picture the history already points at.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('task-photos', 'task-photos', true, 3145728, array['image/jpeg'])
on conflict (id) do nothing;

drop policy if exists task_photos_child_write on storage.objects;
create policy task_photos_child_write on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'task-photos'
    and exists (
      select 1 from public.family_members m
      where m.id::text = (storage.foldername(name))[1] and m.user_id = auth.uid()
    )
  );

drop policy if exists task_photos_child_update on storage.objects;
create policy task_photos_child_update on storage.objects
  for update to authenticated
  using (
    bucket_id = 'task-photos'
    and exists (
      select 1 from public.family_members m
      where m.id::text = (storage.foldername(name))[1] and m.user_id = auth.uid()
    )
  );

-- Public read, like `avatars` and `app-icons`: the parent's phone renders the URL with no
-- extra round trip, and the path holds two random uuids, so it cannot be guessed.
drop policy if exists task_photos_public_read on storage.objects;
create policy task_photos_public_read on storage.objects
  for select using (bucket_id = 'task-photos');
