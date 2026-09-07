-- The child reports whether «Заблокировать сейчас» is currently in force, so the parent's
-- button knows what it is: locking is not a one-shot command the sender has to remember.
alter table public.devices add column if not exists locked boolean not null default false;
