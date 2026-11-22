-- Additive migration: old installation IDs and credentials remain valid.
create table public.support_devices (
 device_id uuid primary key, token_hash text not null check(token_hash ~ '^[a-f0-9]{64}$'),
 seen_at timestamptz not null default now()
);
alter table public.support_devices enable row level security;
revoke all on public.support_devices from public,anon,authenticated;
grant all on public.support_devices to service_role;
alter table public.support_installations add column device_id uuid references public.support_devices(device_id) on delete set null;
create index support_installations_device on public.support_installations(device_id);
create function public.support_ingest_device(p_app text,p_id uuid,p_hash text,p_action text,p_batch uuid,p_environment jsonb,p_events jsonb,p_device uuid,p_device_hash text)
returns integer language plpgsql security invoker set search_path='' as $$
declare existing text; device_hash text; result integer;
begin
 perform pg_advisory_xact_lock(77124);
 select token_hash into existing from public.support_installations where app=p_app and support_id=p_id;
 if existing is not null and existing<>p_hash then return 403; end if;
 if p_action='upload' and p_device is not null then
  if p_device_hash is null or p_device_hash !~ '^[a-f0-9]{64}$' then return 400; end if;
  select token_hash into device_hash from public.support_devices where device_id=p_device;
  if device_hash is not null and device_hash<>p_device_hash then return 403; end if;
  if device_hash is null and (select count(*) from public.support_devices)>=10000 then return 429; end if;
 end if;
 result:=public.support_ingest(p_app,p_id,p_hash,p_action,p_batch,p_environment,p_events);
 if result=200 and p_action='upload' and p_device is not null then
  insert into public.support_devices(device_id,token_hash) values(p_device,p_device_hash)
    on conflict(device_id) do update set seen_at=now();
  update public.support_installations set device_id=p_device where app=p_app and support_id=p_id;
 end if;
 -- Drop orphaned linking proofs, never detach a retained installation.
 delete from public.support_devices d where not exists(select 1 from public.support_installations i where i.device_id=d.device_id);
 return result;
end $$;
revoke all on function public.support_ingest_device(text,uuid,text,text,uuid,jsonb,jsonb,uuid,text) from public,anon,authenticated;
grant execute on function public.support_ingest_device(text,uuid,text,text,uuid,jsonb,jsonb,uuid,text) to service_role;

create function public.support_report_detail(p_app text,p_id uuid,p_batch uuid)
returns jsonb language sql stable security invoker set search_path='' as $$
 select jsonb_build_object('app',r.app,'support_id',r.support_id,'device_id',i.device_id,
  'batch',r.batch,'received_at',r.received_at,'environment',r.environment,'events',r.events)
 from public.support_reports r join public.support_installations i using(app,support_id)
 where r.app=p_app and r.support_id=p_id and r.batch=p_batch;
$$;
revoke all on function public.support_report_detail(text,uuid,uuid) from public,anon,authenticated;
grant execute on function public.support_report_detail(text,uuid,uuid) to service_role;
