-- Applied 2026-09-04 via MCP (apply_migration).
-- 1. device_commands.command check lacked allow_removal / locate: the parent's «Разрешить удаление»
--    and the map's «Обновить» inserts were rejected by the constraint. Added those plus `release`
--    (the parent lets the child device go: enforcement off, protection lifted).
-- 2. One pending approval request per (child, type): the child cannot flood the parent.
-- 3. devices: what the child device reports about itself (model, os, missing protection steps).

do $$
declare r record;
begin
  for r in select conname from pg_constraint where conrelid = 'public.device_commands'::regclass and contype = 'c' loop
    execute format('alter table public.device_commands drop constraint %I', r.conname);
  end loop;
end $$;

alter table public.device_commands
  add constraint device_commands_command_check
  check (command = any (array['lock','unlock','ring','stop_ring','grant_time','locate','allow_removal','release']));

update public.approval_requests a
   set status = 'expired', resolved_at = now()
 where a.status = 'pending'
   and exists (
     select 1 from public.approval_requests b
      where b.child_member_id = a.child_member_id and b.type = a.type and b.status = 'pending' and b.created_at > a.created_at
   );

create unique index if not exists approval_requests_one_pending_per_type
  on public.approval_requests (child_member_id, type)
  where status = 'pending';

alter table public.devices add column if not exists protection_missing jsonb not null default '[]'::jsonb;
alter table public.devices add column if not exists app_version_code integer;

drop policy if exists devices_update_self on public.devices;
create policy devices_update_self on public.devices for update to authenticated
  using (exists (select 1 from public.family_members m where m.id = devices.member_id and m.user_id = auth.uid()))
  with check (exists (select 1 from public.family_members m where m.id = devices.member_id and m.user_id = auth.uid()));

drop policy if exists devices_delete_parent on public.devices;
create policy devices_delete_parent on public.devices for delete to authenticated
  using (is_family_parent(family_id));

alter publication supabase_realtime add table public.devices;
