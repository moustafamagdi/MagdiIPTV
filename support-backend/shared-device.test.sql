begin;
do $test$
declare a uuid:=gen_random_uuid();t uuid:=gen_random_uuid();v uuid:=gen_random_uuid();g uuid:=gen_random_uuid();b uuid:=gen_random_uuid();e jsonb:='{"app_version":"0.0.0","android_api":35}';r integer;j jsonb;
begin
r:=public.support_ingest('tube',t,repeat('a',64),'upload',b,e,'[]');if r<>200 then raise exception 'legacy upload failed %',r;end if;
r:=public.support_ingest_device('auto',a,repeat('b',64),'upload',gen_random_uuid(),e,'[]',g,repeat('c',64));if r<>200 then raise exception 'auto link failed';end if;
r:=public.support_ingest_device('tube',t,repeat('a',64),'upload',b,e,'[]',g,repeat('d',64));if r<>403 then raise exception 'wrong group proof accepted';end if;
r:=public.support_ingest_device('tube',t,repeat('d',64),'upload',b,e,'[]',g,repeat('c',64));if r<>403 then raise exception 'wrong installation proof accepted';end if;
r:=public.support_ingest_device('tube',t,repeat('a',64),'upload',b,e,'[]',g,repeat('c',64));if r<>200 then raise exception 'legacy duplicate link failed';end if;
r:=public.support_ingest_device('iptv',v,repeat('e',64),'upload',gen_random_uuid(),e,'[]',g,repeat('c',64));if r<>200 then raise exception 'iptv link failed';end if;
j:=public.support_dashboard(p_id=>g);if (j->>'total')::int<>3 or (j->>'devices')::int<>1 or (j->>'installations')::int<>3 then raise exception 'group count mismatch';end if;
if (public.support_dashboard(p_id=>t)->>'total')::int<>1 then raise exception 'legacy id search lost';end if;
if public.support_report_detail('tube',t,b)->>'device_id'<>g::text then raise exception 'detail device missing';end if;
r:=public.support_ingest_device('tube',t,repeat('a',64),'delete',null,'{}','[]',null,null);if r<>200 then raise exception 'delete failed';end if;
if (public.support_dashboard(p_id=>g)->>'total')::int<>2 then raise exception 'delete crossed app boundary';end if;
if has_table_privilege('anon','public.support_devices','SELECT') or has_table_privilege('authenticated','public.support_devices','SELECT') then raise exception 'public group access';end if;
if has_function_privilege('anon','public.support_ingest_device(text,uuid,text,text,uuid,jsonb,jsonb,uuid,text)','EXECUTE') then raise exception 'public function access';end if;
end $test$;
rollback;
select 'shared linking / auth / legacy search / isolated deletion / grants passed (rolled back)' as test_result;
