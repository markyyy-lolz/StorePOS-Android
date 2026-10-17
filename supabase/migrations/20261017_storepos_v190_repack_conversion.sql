-- StorePOS v1.9: atomic, idempotent repacking of two REAL tracked SKUs.
-- Additive only. Never reset/alter existing sale, offline queue or other POS app records.
-- DO NOT DEPLOY before controlled security, integration and 2-tablet tests.

create table if not exists app_private.storepos_repack_operations (
    id uuid primary key,
    shop_id uuid not null references public.shops(id),
    source_product_id uuid not null references public.products(id),
    target_product_id uuid not null references public.products(id),
    source_quantity numeric(16,3) not null check(source_quantity > 0),
    target_quantity numeric(16,3) not null check(target_quantity > 0),
    source_balance_after numeric(16,3) not null,
    target_balance_after numeric(16,3) not null,
    performed_by uuid not null,
    created_at timestamptz not null default now(),
    constraint repack_distinct_skus check (source_product_id <> target_product_id)
);
create index if not exists storepos_repack_shop_created
    on app_private.storepos_repack_operations(shop_id,created_at desc);
alter table app_private.storepos_repack_operations enable row level security;
revoke all on app_private.storepos_repack_operations from anon, public;
grant usage on schema app_private to authenticated;
grant select, insert on app_private.storepos_repack_operations to authenticated;

drop policy if exists storepos_repack_read on app_private.storepos_repack_operations;
create policy storepos_repack_read on app_private.storepos_repack_operations
  for select to authenticated
  using (
    app_private.has_shop_role(shop_id,array['owner','admin','manager']::text[])
    and exists(select 1 from public.shops s where s.id=shop_id and s.app_code='storepos')
  );
drop policy if exists storepos_repack_insert on app_private.storepos_repack_operations;
create policy storepos_repack_insert on app_private.storepos_repack_operations
  for insert to authenticated
  with check (
    performed_by=(select auth.uid())
    and app_private.has_shop_role(shop_id,array['owner','admin','manager']::text[])
    and exists(select 1 from public.shops s where s.id=shop_id and s.app_code='storepos')
  );

create or replace function public.storepos_repack_inventory(
    p_shop_id uuid,
    p_source_product_id uuid,
    p_target_product_id uuid,
    p_source_quantity numeric,
    p_target_quantity numeric,
    p_request_id uuid
) returns jsonb
language plpgsql
security invoker
set search_path = pg_catalog, public, app_private
as $fn$
declare
    v_actor uuid := auth.uid();
    v_source public.products%rowtype;
    v_target public.products%rowtype;
    v_existing app_private.storepos_repack_operations%rowtype;
    v_id uuid;
    v_source_after numeric;
    v_target_after numeric;
begin
    if v_actor is null
       or not app_private.has_shop_role(p_shop_id,array['owner','admin','manager']::text[])
       or not exists(select 1 from public.shops where id=p_shop_id and app_code='storepos')
    then
        raise exception 'StorePOS owner, admin or manager authorization required'
            using errcode='42501';
    end if;
    if p_source_product_id is null or p_target_product_id is null
       or p_source_product_id = p_target_product_id or p_request_id is null
       or p_source_quantity is null or p_target_quantity is null
       or p_source_quantity <= 0 or p_target_quantity <= 0
       or p_source_quantity > 100000 or p_target_quantity > 100000
       or p_source_quantity <> round(p_source_quantity,3)
       or p_target_quantity <> round(p_target_quantity,3)
    then
        raise exception 'Invalid repacking request';
    end if;

    select * into v_existing
    from app_private.storepos_repack_operations
    where id=p_request_id;
    if found then
        if v_existing.shop_id <> p_shop_id
           or v_existing.source_product_id <> p_source_product_id
           or v_existing.target_product_id <> p_target_product_id
           or v_existing.source_quantity <> p_source_quantity
           or v_existing.target_quantity <> p_target_quantity
        then raise exception 'Repacking idempotency key belongs to another request'; end if;
        return jsonb_build_object('id',v_existing.id,'status','already_completed',
               'source_balance',v_existing.source_balance_after,
               'target_balance',v_existing.target_balance_after);
    end if;

    -- Deterministic row lock ordering protects simultaneous POS stock changes.
    for v_id in
        select id from public.products
        where shop_id=p_shop_id and id in (p_source_product_id,p_target_product_id)
        order by id for update
    loop
        null;
    end loop;

    -- Re-check after locks: a simultaneous retry can have committed meanwhile.
    select * into v_existing from app_private.storepos_repack_operations where id=p_request_id;
    if found then
        if v_existing.shop_id <> p_shop_id
           or v_existing.source_product_id <> p_source_product_id
           or v_existing.target_product_id <> p_target_product_id
           or v_existing.source_quantity <> p_source_quantity
           or v_existing.target_quantity <> p_target_quantity
        then raise exception 'Repacking idempotency key belongs to another request'; end if;
        return jsonb_build_object('id',v_existing.id,'status','already_completed',
              'source_balance',v_existing.source_balance_after,
              'target_balance',v_existing.target_balance_after);
    end if;

    select * into v_source from public.products
    where shop_id=p_shop_id and id=p_source_product_id;
    if not found then raise exception 'Source SKU not found in StorePOS shop'; end if;
    select * into v_target from public.products
    where shop_id=p_shop_id and id=p_target_product_id;
    if not found then raise exception 'Target SKU not found in StorePOS shop'; end if;

    -- Shared-stock virtual packs, batches and serialized goods need specialized
    -- accounting. Refuse rather than double-count.
    if not v_source.is_active or not v_target.is_active
       or not v_source.track_stock or not v_target.track_stock
       or v_source.batch_tracked or v_target.batch_tracked
       or v_source.serial_tracked or v_target.serial_tracked
       or v_source.retail_parent_id is not null or v_target.retail_parent_id is not null
    then raise exception 'Repacking requires two active, independently stocked, non-serialized SKUs'; end if;
    if v_source.stock_quantity < p_source_quantity then
        raise exception 'Not enough source stock for repack';
    end if;

    v_source_after := v_source.stock_quantity - p_source_quantity;
    v_target_after := v_target.stock_quantity + p_target_quantity;

    update public.products set stock_quantity=v_source_after
      where shop_id=p_shop_id and id=p_source_product_id;
    if not found then raise exception 'Source stock update rejected'; end if;
    update public.products set stock_quantity=v_target_after
      where shop_id=p_shop_id and id=p_target_product_id;
    if not found then raise exception 'Target stock update rejected'; end if;

    insert into app_private.storepos_repack_operations(
        id,shop_id,source_product_id,target_product_id,source_quantity,target_quantity,
        source_balance_after,target_balance_after,performed_by
    ) values (
        p_request_id,p_shop_id,p_source_product_id,p_target_product_id,
        p_source_quantity,p_target_quantity,v_source_after,v_target_after,v_actor
    );

    insert into public.inventory_movements(
        shop_id,product_id,movement_type,quantity_delta,balance_after,
        reference_id,reference_type,notes,created_by
    ) values
        (p_shop_id,p_source_product_id,'adjustment',-p_source_quantity,v_source_after,
         p_request_id,'storepos_repack','Source converted to '||v_target.name,v_actor),
        (p_shop_id,p_target_product_id,'adjustment',p_target_quantity,v_target_after,
         p_request_id,'storepos_repack','Output converted from '||v_source.name,v_actor);

    insert into public.audit_logs(
        shop_id,actor_id,action,entity_type,entity_id,old_data,new_data
    ) values (
        p_shop_id,v_actor,'storepos_repack','repack_operation',p_request_id,
        jsonb_build_object('source_stock',v_source.stock_quantity,'target_stock',v_target.stock_quantity),
        jsonb_build_object('source_id',p_source_product_id,'target_id',p_target_product_id,
           'source_quantity',p_source_quantity,'target_quantity',p_target_quantity,
           'source_stock',v_source_after,'target_stock',v_target_after)
    );

    return jsonb_build_object('id',p_request_id,'status','completed',
        'source_balance',v_source_after,'target_balance',v_target_after);
end;
$fn$;

revoke all on function public.storepos_repack_inventory(uuid,uuid,uuid,numeric,numeric,uuid) from public, anon;
grant execute on function public.storepos_repack_inventory(uuid,uuid,uuid,numeric,numeric,uuid) to authenticated;
