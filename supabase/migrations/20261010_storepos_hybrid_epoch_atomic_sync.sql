-- StorePOS hybrid v1.8.1 (additive). Preserve all sales, payments, receipts,
-- inventory movements, product data and independent MotoPOS shops.
-- Run against the EXISTING MotorPOS cloud only after reviewing a recoverable backup.
-- Epoch protects a delayed tablet outbox from re-posting after product deletions/reset.
create table if not exists public.storepos_hybrid_epochs (
  shop_id uuid primary key references public.shops(id) on delete cascade,
  epoch uuid not null default gen_random_uuid(),
  updated_at timestamptz not null default now()
);
insert into public.storepos_hybrid_epochs(shop_id)
select id from public.shops where app_code='storepos'
on conflict(shop_id) do nothing;

alter table public.storepos_hybrid_epochs enable row level security;
revoke all on public.storepos_hybrid_epochs from public,anon,authenticated;
grant select on public.storepos_hybrid_epochs to authenticated;
drop policy if exists storepos_hybrid_epoch_member_read on public.storepos_hybrid_epochs;
create policy storepos_hybrid_epoch_member_read on public.storepos_hybrid_epochs
  for select to authenticated using (
    exists(
      select 1 from public.shop_members m
      join public.shops s on s.id=m.shop_id
      where m.shop_id=storepos_hybrid_epochs.shop_id
        and m.user_id=(select auth.uid()) and m.is_active
        and s.app_code='storepos'
    )
  );

-- Capture ALL affected StorePOS shops after any product DELETE statement.
-- We intentionally do NOT change epochs on sales/stock updates: offline sales
-- must not invalidate one another on the other tablet.
create or replace function public.storepos_hybrid_product_deleted()
returns trigger language plpgsql security definer set search_path=''
as $$
begin
  insert into public.storepos_hybrid_epochs(shop_id)
  select distinct d.shop_id from deleted_products d
  join public.shops s on s.id=d.shop_id and s.app_code='storepos'
  on conflict(shop_id) do update
  set epoch=gen_random_uuid(),updated_at=now();
  return null;
end;
$$;
revoke all on function public.storepos_hybrid_product_deleted() from public,anon,authenticated;
drop trigger if exists storepos_hybrid_products_deleted on public.products;
create trigger storepos_hybrid_products_deleted
after delete on public.products
referencing old table as deleted_products
for each statement execute function public.storepos_hybrid_product_deleted();

-- TRUNCATE is blocked by FK references under ordinary workflows, but if it is
-- used as part of a privileged reset, force a refresh of ALL StorePOS catalogs.
create or replace function public.storepos_hybrid_products_truncated()
returns trigger language plpgsql security definer set search_path=''
as $$
begin
  update public.storepos_hybrid_epochs e
  set epoch=gen_random_uuid(),updated_at=now()
  from public.shops s
  where s.id=e.shop_id and s.app_code='storepos';
  return null;
end;
$$;
revoke all on function public.storepos_hybrid_products_truncated() from public,anon,authenticated;
drop trigger if exists storepos_hybrid_products_truncated on public.products;
create trigger storepos_hybrid_products_truncated after truncate on public.products
for each statement execute function public.storepos_hybrid_products_truncated();

-- One globally unique client key is shared across online StorePOS checkout and
-- subsequent offline retry. The advisory lock matches app_private.retail_checkout.
-- Shop epoch + product row locks serialize catalog changes and stock contention.
create or replace function public.storepos_hybrid_reconcile_sale(
  p_client_key uuid,
  p_shop_id uuid,
  p_catalog_epoch uuid,
  p_cashier_id uuid,
  p_payload jsonb
)
returns setof public.sales
language plpgsql security definer
set search_path='pg_catalog','public'
as $$
declare
  v_user uuid := auth.uid();
  v_existing public.offline_sale_requests%rowtype;
  v_sale public.sales%rowtype;
  v_epoch uuid;
  v_count integer;
  v_line record;
  v_price numeric;
  v_available numeric;
begin
  if v_user is null or v_user is distinct from p_cashier_id then
    raise exception 'HYBRID_CASHIER_MISMATCH: original cashier must sign in to reconcile';
  end if;
  if p_client_key is null or p_shop_id is null or p_catalog_epoch is null then
    raise exception 'HYBRID_CATALOG_REVIEW: missing client key, shop or catalog epoch';
  end if;
  if not exists(select 1 from public.shop_members m join public.shops s on s.id=m.shop_id
                where m.shop_id=p_shop_id and m.user_id=v_user and m.is_active
                and s.app_code='storepos' and m.role in ('owner','admin','manager','cashier')) then
    raise exception 'HYBRID_ACCESS_DENIED: shop cashier authorization required';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(p_client_key::text,0));
  select * into v_existing from public.offline_sale_requests where client_key=p_client_key;
  if v_existing.client_key is not null then
    if v_existing.shop_id is distinct from p_shop_id or
       v_existing.created_by is distinct from p_cashier_id then
      raise exception 'HYBRID_KEY_CONFLICT: client key already belongs to another transaction';
    end if;
    if v_existing.sale_id is not null then
      select * into v_sale from public.sales
      where id=v_existing.sale_id and shop_id=p_shop_id;
      if v_sale.id is not null then
        return next v_sale; return;
      end if;
    end if;
  end if;
  -- The same epoch row is locked by catalog-reset DELETE/TRUNCATE triggers.
  select epoch into v_epoch from public.storepos_hybrid_epochs
    where shop_id=p_shop_id for update;
  if v_epoch is null or v_epoch is distinct from p_catalog_epoch then
    raise exception 'HYBRID_CATALOG_REVIEW: shop catalog was reset; manager reconciliation required';
  end if;
  if coalesce(jsonb_typeof(p_payload),'') <> 'object' then
    raise exception 'HYBRID_INVALID: cash sale payload required';
  end if;
  if p_payload->>'shop_id' is distinct from p_shop_id::text or
     p_payload->>'cashier_id' is distinct from p_cashier_id::text or
     p_payload->>'client_key' is distinct from p_client_key::text then
    raise exception 'HYBRID_INVALID: payload identity mismatch';
  end if;
  if jsonb_typeof(p_payload->'items') is distinct from 'array' or
     jsonb_array_length(p_payload->'items')=0 or
     jsonb_typeof(p_payload->'payments') is distinct from 'array' or
     jsonb_array_length(p_payload->'payments')=0 then
    raise exception 'HYBRID_INVALID: sale items and cash payment required';
  end if;
  if exists(
    select 1 from jsonb_array_elements(p_payload->'payments') pay
    where pay->>'method' is distinct from 'cash'
  ) then
    raise exception 'HYBRID_INVALID: offline reconciliation accepts cash only';
  end if;
  -- Disable complex online-only retail flows. The Android side makes the same
  -- restriction; server-side validation cannot be bypassed by a modified APK.
  select count(*) into v_count from jsonb_array_elements(p_payload->'items');
  if v_count > 300 then raise exception 'HYBRID_INVALID: too many item lines'; end if;
  for v_line in
    select (line->>'product_id')::uuid as product_id,
           (line->>'quantity')::numeric as qty,
           (line->>'unit_price')::numeric as snapshot
    from jsonb_array_elements(p_payload->'items') as line
    order by (line->>'product_id')::uuid
  loop
    if v_line.qty is null or v_line.qty<=0 or v_line.snapshot is null or v_line.snapshot<0 then
      raise exception 'HYBRID_INVALID: invalid quantity or missing unit price';
    end if;
    select p.selling_price,p.stock_quantity into v_price,v_available
    from public.products p
    where p.id=v_line.product_id and p.shop_id=p_shop_id and p.is_active
      and p.retail_parent_id is null and not p.is_weighed
      and not p.batch_tracked and not p.serial_tracked
      and p.wholesale_min=0
    for update;
    if not found then
      raise exception 'HYBRID_CATALOG_REVIEW: product was removed or requires online-only checkout';
    end if;
    if abs(v_price-v_line.snapshot)>0.009 then
      raise exception 'HYBRID_PRICE_REVIEW: product price has changed since offline sale';
    end if;
  end loop;
  -- Distinct locked product rows plus a GROUP BY account for duplicate SKU
  -- lines; otherwise two tablets could both deduct more than available.
  if exists(
    select 1 from (
      select (line->>'product_id')::uuid product_id,
             sum((line->>'quantity')::numeric) sold
      from jsonb_array_elements(p_payload->'items') line
      group by 1
    ) q
    join public.products p on p.id=q.product_id
    where p.shop_id=p_shop_id and p.track_stock and p.stock_quantity<q.sold
  ) then raise exception 'HYBRID_STOCK_REVIEW: insufficient stock; manual reconciliation required';
  end if;
  -- All work including inventory mutations is in this one PostgreSQL txn.
  return query select * from public.complete_sale_transaction_v3(
    p_client_key=>p_client_key,
    p_shop_id=>p_shop_id,
    p_customer_id=>nullif(p_payload->>'customer_id','')::uuid,
    p_motorcycle_id=>nullif(p_payload->>'motorcycle_id','')::uuid,
    p_job_order_id=>nullif(p_payload->>'job_order_id','')::uuid,
    p_discount_amount=>coalesce((p_payload->>'discount_amount')::numeric,0),
    p_tax_amount=>coalesce((p_payload->>'tax_amount')::numeric,0),
    p_manager_pin=>null,
    p_items=>p_payload->'items',
    p_payments=>p_payload->'payments'
  );
end;
$$;
revoke all on function public.storepos_hybrid_reconcile_sale(uuid,uuid,uuid,uuid,jsonb)
  from public,anon,authenticated;
grant execute on function public.storepos_hybrid_reconcile_sale(uuid,uuid,uuid,uuid,jsonb)
  to authenticated;
comment on function public.storepos_hybrid_reconcile_sale(uuid,uuid,uuid,uuid,jsonb) is
  'StorePOS-only idempotent offline CASH replay. Rejects stale post-reset catalogs, price drift and oversold stock without losing tablet receipts.';
