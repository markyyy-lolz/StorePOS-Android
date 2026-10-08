-- StorePOS v1.7.0: additive, shop-isolated shared print queue.
-- MotoPOS tables, shops, memberships and existing transactions are untouched.
create extension if not exists pgcrypto;

create table if not exists public.storepos_shared_printers (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null unique references public.shops(id) on delete cascade,
  name text not null default 'VOZY G80 Front Counter',
  model text not null default 'VOZY G80',
  transport text not null default 'bluetooth' check (transport in ('bluetooth','usb')),
  paper_width_mm integer not null default 80 check (paper_width_mm in (58,80)),
  host_device_id text,
  host_user_id uuid,
  host_last_seen_at timestamptz,
  enabled boolean not null default true,
  updated_at timestamptz not null default now()
);
create table if not exists public.storepos_print_jobs (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null references public.shops(id) on delete cascade,
  printer_id uuid not null references public.storepos_shared_printers(id) on delete cascade,
  sale_id uuid references public.sales(id) on delete set null,
  request_key text not null,
  source_device_id text not null,
  requested_by uuid not null,
  kind text not null default 'sale' check (kind in ('sale','reprint','test','x_report','z_report','batch_report')),
  receipt_number text,
  payload_base64 text not null check (length(payload_base64) between 4 and 262144),
  paper_width_mm integer not null default 80 check (paper_width_mm in (58,80)),
  status text not null default 'pending' check (status in ('pending','claimed','sent','failed','needs_review','cancelled')),
  attempts integer not null default 0 check (attempts between 0 and 20),
  host_device_id text,
  claimed_at timestamptz,
  lease_until timestamptz,
  sent_at timestamptz,
  last_error text,
  created_at timestamptz not null default now(),
  unique (shop_id, request_key)
);
create index if not exists storepos_print_jobs_fifo on public.storepos_print_jobs
 (printer_id, status, created_at, id);
create index if not exists storepos_print_jobs_shop_recent on public.storepos_print_jobs
 (shop_id, created_at desc);

alter table public.storepos_shared_printers enable row level security;
alter table public.storepos_print_jobs enable row level security;
revoke all on public.storepos_shared_printers,public.storepos_print_jobs from public, anon;
grant select on public.storepos_shared_printers,public.storepos_print_jobs to authenticated;
grant all on public.storepos_shared_printers,public.storepos_print_jobs to service_role;

drop policy if exists storepos_printer_view on public.storepos_shared_printers;
create policy storepos_printer_view on public.storepos_shared_printers
 for select to authenticated using (
   exists (select 1 from public.shop_members m join public.shops s on s.id=m.shop_id
     where m.shop_id=storepos_shared_printers.shop_id and m.user_id=(select auth.uid())
       and m.is_active and s.app_code='storepos' and s.business_type='retail')
 );
drop policy if exists storepos_print_jobs_view on public.storepos_print_jobs;
create policy storepos_print_jobs_view on public.storepos_print_jobs
 for select to authenticated using (
   exists (select 1 from public.shop_members m join public.shops s on s.id=m.shop_id
     where m.shop_id=storepos_print_jobs.shop_id and m.user_id=(select auth.uid())
       and m.is_active and s.app_code='storepos' and s.business_type='retail')
 );

-- All writes go through a single authenticated, validated RPC. Locks serialize
-- claim/host changes. An expired in-flight job becomes NEEDS_REVIEW, never auto
-- reprinted, because ESC/POS does not acknowledge physical paper completion.
create or replace function public.storepos_shared_print_action(
  p_shop_id uuid, p_action text, p_data jsonb default '{}'::jsonb
) returns jsonb
language plpgsql security definer set search_path = public, pg_temp
as $$
declare
  v_uid uuid := auth.uid();
  v_role text;
  v_printer public.storepos_shared_printers%rowtype;
  v_job public.storepos_print_jobs%rowtype;
  v_device text := trim(coalesce(p_data->>'device_id',''));
  v_kind text;
  v_sale uuid;
  v_key text;
  v_payload text;
  v_reason text;
  v_count bigint;
begin
  if v_uid is null then raise exception 'Sign in required' using errcode='42501'; end if;
  select m.role into v_role
  from public.shop_members m join public.shops s on s.id=m.shop_id
  where m.shop_id=p_shop_id and m.user_id=v_uid and m.is_active
    and s.app_code='storepos' and s.business_type='retail'
  limit 1;
  if v_role is null then
    raise exception 'Active StorePOS membership required' using errcode='42501';
  end if;
  if p_action = 'list' then
    return jsonb_build_object('printer',(select to_jsonb(p) from public.storepos_shared_printers p where p.shop_id=p_shop_id),
      'jobs',coalesce((select jsonb_agg(to_jsonb(j) - 'payload_base64' order by j.created_at desc)
       from (select * from public.storepos_print_jobs where shop_id=p_shop_id
         order by created_at desc limit 40) j),'[]'::jsonb));
  end if;
  if p_action = 'configure_host' then
    if v_role not in ('owner','admin') then raise exception 'Owner or admin only' using errcode='42501'; end if;
    if length(v_device) < 5 or length(v_device)>128 then raise exception 'Invalid device ID'; end if;
    if not exists(select 1 from public.shop_members where shop_id=p_shop_id and user_id=(p_data->>'host_user_id')::uuid and is_active) then
      raise exception 'Host must be an active shop member' using errcode='42501';
    end if;
    insert into public.storepos_shared_printers(shop_id,host_device_id,host_user_id,transport)
    values(p_shop_id,v_device,(p_data->>'host_user_id')::uuid,
      case when p_data->>'transport'='usb' then 'usb' else 'bluetooth' end)
    on conflict(shop_id) do update
      set host_device_id=excluded.host_device_id,host_user_id=excluded.host_user_id,
        transport=excluded.transport,host_last_seen_at=null,updated_at=now()
    returning * into v_printer;
    return jsonb_build_object('printer',to_jsonb(v_printer));
  end if;
  select * into v_printer from public.storepos_shared_printers
   where shop_id=p_shop_id for update;
  if not found then raise exception 'Configure a shared printer first'; end if;
  if p_action = 'heartbeat' or p_action='claim' or p_action='ack' or p_action='fail' then
    if v_printer.host_user_id is distinct from v_uid or
       v_printer.host_device_id is distinct from v_device or not v_printer.enabled then
      raise exception 'This device is not the assigned print host' using errcode='42501';
    end if;
  end if;
  if p_action='heartbeat' then
    update public.storepos_shared_printers set host_last_seen_at=now(),updated_at=now()
      where id=v_printer.id;
    return jsonb_build_object('ok',true);
  end if;
  if p_action='enqueue' then
    v_kind:=coalesce(p_data->>'kind','sale');
    if v_kind not in ('sale','reprint','test','x_report','z_report','batch_report') then
      raise exception 'Unknown print type';
    end if;
    if v_kind in ('z_report','batch_report') and v_role not in ('owner','admin','manager') then
      raise exception 'Manager approval required' using errcode='42501';
    end if;
    v_key:=trim(coalesce(p_data->>'request_key',''));
    v_payload:=coalesce(p_data->>'payload_base64','');
    if length(v_key) not between 8 and 120 or length(v_payload) not between 4 and 262144 or
       v_payload !~ '^[A-Za-z0-9+/=]+$' then raise exception 'Invalid print job payload'; end if;
    if v_kind in ('sale','reprint') then
      v_sale:=nullif(p_data->>'sale_id','')::uuid;
      if v_sale is null or not exists(select 1 from public.sales
        where id=v_sale and shop_id=p_shop_id and status not in ('void','voided','cancelled')) then
        raise exception 'Sale not found in current StorePOS shop' using errcode='42501';
      end if;
    end if;
    insert into public.storepos_print_jobs(shop_id,printer_id,sale_id,request_key,
      source_device_id,requested_by,kind,receipt_number,payload_base64,paper_width_mm)
    values (p_shop_id,v_printer.id,v_sale,v_key,
      left(v_device,128),v_uid,v_kind,left(coalesce(p_data->>'receipt_number',''),100),
      v_payload,v_printer.paper_width_mm)
    on conflict(shop_id,request_key) do nothing returning * into v_job;
    if not found then
      select * into v_job from public.storepos_print_jobs
        where shop_id=p_shop_id and request_key=v_key;
    end if;
    return jsonb_build_object('job_id',v_job.id,'status',v_job.status);
  end if;
  if p_action='claim' then
    update public.storepos_print_jobs set status='needs_review',
      last_error='Host disconnected while receipt status was uncertain',
      lease_until=null where printer_id=v_printer.id
        and status='claimed' and lease_until<now();
    if exists(select 1 from public.storepos_print_jobs
      where printer_id=v_printer.id and status='claimed') then
      return jsonb_build_object('job',null);
    end if;
    if v_printer.host_last_seen_at is null or v_printer.host_last_seen_at < now()-interval '45 seconds' then
      return jsonb_build_object('job',null);
    end if;
    select * into v_job from public.storepos_print_jobs
      where printer_id=v_printer.id and status='pending'
      order by created_at,id limit 1 for update skip locked;
    if not found then return jsonb_build_object('job',null); end if;
    update public.storepos_print_jobs set status='claimed', attempts=attempts+1,
      host_device_id=v_device,claimed_at=now(),
      lease_until=now()+interval '90 seconds',last_error=null
      where id=v_job.id returning * into v_job;
    return jsonb_build_object('job',to_jsonb(v_job));
  end if;
  if p_action='ack' or p_action='fail' then
    select * into v_job from public.storepos_print_jobs
      where id=(p_data->>'job_id')::uuid and shop_id=p_shop_id
        and printer_id=v_printer.id and status='claimed'
        and host_device_id=v_device for update;
    if not found then raise exception 'Print job is not claimed by this host'; end if;
    if p_action='ack' then
      update public.storepos_print_jobs set status='sent',sent_at=now(),lease_until=null
        where id=v_job.id;
      return jsonb_build_object('status','sent');
    end if;
    v_reason:=left(coalesce(p_data->>'error','Print failure'),300);
    if coalesce((p_data->>'safe_to_retry')::boolean,false) and v_job.attempts<3 then
      update public.storepos_print_jobs set status='pending',last_error=v_reason,
        lease_until=null where id=v_job.id;
      return jsonb_build_object('status','pending');
    end if;
    update public.storepos_print_jobs set
      status=case when coalesce((p_data->>'safe_to_retry')::boolean,false)
        then 'failed' else 'needs_review' end,
      last_error=v_reason,lease_until=null where id=v_job.id;
    return jsonb_build_object('status',case when coalesce((p_data->>'safe_to_retry')::boolean,false)
      then 'failed' else 'needs_review' end);
  end if;
  if p_action='retry' or p_action='cancel' then
    if v_role not in ('owner','admin','manager') then
      raise exception 'Manager approval required' using errcode='42501'; end if;
    select * into v_job from public.storepos_print_jobs
      where id=(p_data->>'job_id')::uuid and shop_id=p_shop_id
        and printer_id=v_printer.id and status in ('failed','needs_review','pending') for update;
    if not found then raise exception 'Job is not eligible for recovery'; end if;
    v_reason:=left(trim(coalesce(p_data->>'reason','')),300);
    if length(v_reason)<4 then raise exception 'Recovery reason required'; end if;
    update public.storepos_print_jobs set
      status=case when p_action='retry' then 'pending' else 'cancelled' end,
      last_error=v_reason,lease_until=null where id=v_job.id;
    insert into public.audit_logs(shop_id,actor_id,action,entity_type,entity_id,new_data)
      values(p_shop_id,v_uid,'storepos.print.'||p_action,'storepos_print_jobs',v_job.id,
        jsonb_build_object('reason',v_reason,'prior_status',v_job.status));
    return jsonb_build_object('status',case when p_action='retry' then 'pending' else 'cancelled' end);
  end if;
  raise exception 'Unknown shared printer action';
end $$;
revoke all on function public.storepos_shared_print_action(uuid,text,jsonb) from public,anon;
grant execute on function public.storepos_shared_print_action(uuid,text,jsonb) to authenticated;
