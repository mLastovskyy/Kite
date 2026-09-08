-- The parent's «Обновить» waits five seconds and then stops waiting; whatever the child sends
-- after that has to land on the screen by itself (owner, 08.09.2026). Screen time is the one
-- table the parent watches that was not published.
alter publication supabase_realtime add table public.usage_days;
