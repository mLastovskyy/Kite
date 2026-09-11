-- A parent edits a child's name and avatar from the parent app. Until now the only UPDATE
-- policy was members_update_self, and the app patched by user_id — so «Профиль ребёнка» silently
-- rewrote the parent's own row. The child's row stays a child's row in the same family.
create policy members_update_child_by_parent on public.family_members
  for update using (
    role = 'child' and is_family_parent(family_id)
  ) with check (
    role = 'child' and is_family_parent(family_id)
  );
