-- Service-only ingestion; no client can read, write or enumerate reports through REST.
create table public.support_installations (
 app text not null check(app in ('iptv','tube')), support_id uuid not null, token_hash text not null,
 created_at timestamptz not null default now(), seen_at timestamptz not null default now(), primary key(app,support_id)
);
create table public.support_reports (
 app text not null, support_id uuid not null, batch uuid not null, received_at timestamptz not null default now(),
 environment jsonb not null, events jsonb not null, primary key(app,support_id,batch),
 foreign key(app,support_id) references public.support_installations on delete cascade,
 check(pg_column_size(events)<32768), check(jsonb_array_length(events)<=128)
);
create index support_reports_received on public.support_reports(received_at);
create table public.support_budget(day date primary key, reports integer not null default 0, registrations integer not null default 0);
alter table public.support_installations enable row level security;
alter table public.support_reports enable row level security;
alter table public.support_budget enable row level security;
revoke all on public.support_installations,public.support_reports,public.support_budget from public,anon,authenticated;
grant all on public.support_installations,public.support_reports,public.support_budget to service_role;
-- Authenticated by the Edge Function's server-only credential. This function is NOT security definer.
create function public.support_ingest(p_app text,p_id uuid,p_hash text,p_action text,p_batch uuid,p_environment jsonb,p_events jsonb) returns integer
language plpgsql security invoker set search_path = '' as $$
declare existing text; registered integer; sent integer;
begin
 perform pg_advisory_xact_lock(77124);
 select token_hash into existing from public.support_installations where app=p_app and support_id=p_id;
 if existing is not null and existing<>p_hash then return 403; end if;
 if p_action='delete' then
   delete from public.support_installations where app=p_app and support_id=p_id;
   return 200;
 end if;
 if p_action<>'upload' then return 400; end if;
 if exists(select 1 from public.support_reports where app=p_app and support_id=p_id and batch=p_batch) then return 200; end if;
 insert into public.support_budget(day) values(current_date) on conflict do nothing;
 select reports,registrations into sent,registered from public.support_budget where day=current_date;
 if sent>=2000 or (existing is null and registered>=100) then return 429; end if;
 if (select count(*) from public.support_reports where app=p_app and support_id=p_id and received_at>now()-interval '1 day')>=96 then return 429; end if;
 if existing is null then
   if (select count(*) from public.support_installations)>=10000 then return 429; end if;
   insert into public.support_installations(app,support_id,token_hash) values(p_app,p_id,p_hash);
   update public.support_budget set registrations=registrations+1 where day=current_date;
 end if;
 update public.support_installations set seen_at=now() where app=p_app and support_id=p_id;
 insert into public.support_reports(app,support_id,batch,environment,events) values(p_app,p_id,p_batch,p_environment,p_events);
 update public.support_budget set reports=reports+1 where day=current_date;
 delete from public.support_reports where received_at<now()-interval '30 days';
 delete from public.support_reports where (app,support_id,batch) in (select app,support_id,batch from public.support_reports order by received_at desc offset 5000);
 return 200;
end $$;
revoke all on function public.support_ingest(text,uuid,text,text,uuid,jsonb,jsonb) from public,anon,authenticated;
grant execute on function public.support_ingest(text,uuid,text,text,uuid,jsonb,jsonb) to service_role;
create extension if not exists pg_cron;
select cron.schedule('mm-support-retention','17 * * * *', $job$
 delete from public.support_reports where received_at<now()-interval '30 days';
 delete from public.support_installations where seen_at<now()-interval '30 days';
 delete from public.support_budget where day<current_date-31;
$job$);
