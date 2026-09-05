alter table public.device_commands drop constraint if exists device_commands_command_check;

alter table public.device_commands
  add constraint device_commands_command_check
  check (command = any (array['lock', 'unlock', 'ring', 'stop_ring', 'grant_time', 'locate', 'allow_removal', 'release', 'refresh']));
