-- «Данные на 14:03» on the parent card read the moment the row was INSERTED and then never
-- moved: an upsert updates total_ms but nothing touches updated_at, so a day synced at 00:17
-- still claimed 00:17 at nine in the evening (owner, 09.09.2026).
create or replace function public.touch_updated_at() returns trigger
  language plpgsql security definer set search_path = public as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

revoke execute on function public.touch_updated_at() from anon, authenticated, public;

drop trigger if exists usage_days_touch on public.usage_days;
create trigger usage_days_touch before insert or update on public.usage_days
  for each row execute function public.touch_updated_at();
