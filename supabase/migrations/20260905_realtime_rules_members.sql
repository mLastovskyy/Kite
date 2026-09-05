-- Applied 2026-09-05 via MCP. Realtime did not publish member_rules, so the child never got
-- an event when the parent saved rules and waited for the hourly refresh instead.
do $$
begin
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'member_rules') then
    execute 'alter publication supabase_realtime add table public.member_rules';
  end if;
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'family_members') then
    execute 'alter publication supabase_realtime add table public.family_members';
  end if;
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'child_apps') then
    execute 'alter publication supabase_realtime add table public.child_apps';
  end if;
end $$;

alter table public.member_rules replica identity full;
alter table public.family_members replica identity full;
