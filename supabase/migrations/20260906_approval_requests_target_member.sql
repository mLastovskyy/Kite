alter table public.approval_requests
  add column if not exists target_member_id uuid references public.family_members(id) on delete set null;

create index if not exists approval_requests_target_idx
  on public.approval_requests (target_member_id)
  where status = 'pending';
