-- The battery only ever arrived attached to a location fix, so with geolocation switched off on
-- the child's phone the parent saw a charge from hours ago — or none (owner, 09.09.2026). The
-- device report goes up hourly regardless of location, so it carries the charge now.
alter table public.devices add column if not exists battery_pct smallint;

comment on column public.devices.battery_pct is 'Charge at the last device report; independent of whether location is on.';
