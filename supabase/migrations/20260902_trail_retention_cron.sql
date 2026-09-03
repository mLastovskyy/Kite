-- Server-side retention for the coarse trail (7 days) and place events (30 days).
create extension if not exists pg_cron with schema pg_catalog;
grant usage on schema cron to postgres;

select cron.schedule(
  'kite_trail_retention',
  '17 3 * * *',
  $$delete from public.location_trail where recorded_at < now() - interval '7 days'$$
);
select cron.schedule(
  'kite_place_events_retention',
  '23 3 * * *',
  $$delete from public.place_events where at < now() - interval '30 days'$$
);
