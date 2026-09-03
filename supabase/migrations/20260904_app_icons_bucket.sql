-- Public bucket for the child's app icons: app-icons/<member_id>/<package>.png (64 px PNG,
-- a few KB each). Only the child device writes its own folder; everyone can read (public URL),
-- like the avatars bucket. Why icons at all: the parent's phone does not have the child's
-- apps, and a list of grey letters does not tell a parent what «B» is.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('app-icons', 'app-icons', true, 262144, array['image/png'])
on conflict (id) do nothing;

drop policy if exists app_icons_child_write on storage.objects;
create policy app_icons_child_write on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'app-icons'
    and exists (
      select 1 from public.family_members m
      where m.id::text = (storage.foldername(name))[1] and m.user_id = auth.uid()
    )
  );
drop policy if exists app_icons_child_update on storage.objects;
create policy app_icons_child_update on storage.objects
  for update to authenticated
  using (
    bucket_id = 'app-icons'
    and exists (
      select 1 from public.family_members m
      where m.id::text = (storage.foldername(name))[1] and m.user_id = auth.uid()
    )
  );
drop policy if exists app_icons_public_read on storage.objects;
create policy app_icons_public_read on storage.objects
  for select using (bucket_id = 'app-icons');
