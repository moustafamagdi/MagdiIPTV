alter table public.support_installations drop constraint support_installations_app_check;
alter table public.support_installations add constraint support_installations_app_check check(app in ('iptv','tube','auto'));
