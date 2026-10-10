-- StorePOS v1.8.2: additive and idempotent offline inventory reconciliation.
-- IMPORTANT: DO NOT apply to MotoPOS or BrewPOS DB. Zero historical rows changed.
create table if not exists public.storepos_offline_inventory_ops (
  operation_id uuid primary key,
  shop_id uuid not null references public.shops(id),
  actor_id uuid not null references auth.users(id),
  catalog_epoch uuid not null,
  operation_kind text not null check (operation_kind in ('create','edit','adjust','count')),
  payload jsonb not null,
  created_at timestamptz not null default now()
);
create index if not exists storepos_offline_inventory_ops_shop_idx
  on public.storepos_offline_inventory_ops (shop_id,created_at);
alter table public.storepos_offline_inventory_ops enable row level security;
revoke all on public.storepos_offline_inventory_ops from public,anon,authenticated;
-- The RPC (SECURITY DEFINER) is the only permitted interface.

create or replace function public.storepos_reconcile_inventory(
  p_operation_id uuid, p_shop_id uuid, p_actor_id uuid,
  p_epoch uuid, p_kind text, p_data jsonb
)
returns jsonb
language plpgsql security definer
set search_path='pg_catalog','public'
as $$
declare
  v_existing public.storepos_offline_inventory_ops%rowtype;
  v_epoch uuid;
  v_role text;
  v_product public.products%rowtype;
  v_previous jsonb;
  v_next jsonb;
  v_product_id uuid;
  v_delta numeric;
  v_expected numeric;
  v_counted numeric;
  v_reason text;
  v_notes text;
begin
  if auth.uid() is null or auth.uid() is distinct from p_actor_id then
    raise exception 'INVENTORY_AUTH: original inventory operator must sign in';
  end if;
  if p_operation_id is null or p_shop_id is null or p_epoch is null or
     p_kind not in ('create','edit','adjust','count') or jsonb_typeof(p_data)<>'object' then
    raise exception 'INVENTORY_INVALID: missing operation fields';
  end if;
  select m.role into v_role from public.shop_members m
  join public.shops s on s.id=m.shop_id and s.app_code='storepos'
  where m.shop_id=p_shop_id and m.user_id=auth.uid() and m.is_active;
  if v_role not in ('owner','admin','manager','inventory') or v_role is null then
    raise exception 'INVENTORY_AUTH: unauthorized StorePOS inventory operator';
  end if;
  if p_kind in ('edit','create','count') and v_role='inventory' then
    -- Only owners/managers can alter pricing or approve a physical stocktake.
    if p_kind<>'count' then
      raise exception 'INVENTORY_AUTH: manager approval needed for product details';
    end if;
  end if;
  if p_kind='count' and v_role not in ('owner','admin','manager') then
    raise exception 'INVENTORY_AUTH: manager must authorize stocktake';
  end if;
  -- A retry after a lost response returns the same result without replaying a delta.
  perform pg_advisory_xact_lock(hashtextextended(p_operation_id::text,0));
  select * into v_existing from public.storepos_offline_inventory_ops
  where operation_id=p_operation_id;
  if found then
    if v_existing.shop_id is distinct from p_shop_id or
       v_existing.actor_id is distinct from p_actor_id or
       v_existing.catalog_epoch is distinct from p_epoch or
       v_existing.operation_kind is distinct from p_kind or
       v_existing.payload is distinct from p_data then
      raise exception 'INVENTORY_CONFLICT: operation ID was reused with different content';
    end if;
    return jsonb_build_object('synced',true,'duplicate',true,'id',p_operation_id);
  end if;
  -- Serialization with the existing StorePOS sales reconciliation epoch lock.
  select epoch into v_epoch from public.storepos_hybrid_epochs
  where shop_id=p_shop_id for update;
  if v_epoch is null or v_epoch is distinct from p_epoch then
    raise exception 'INVENTORY_REVIEW: catalog was reset; do not overwrite new products';
  end if;

  v_product_id:=nullif(p_data->>'product_id','')::uuid;
  if v_product_id is null then raise exception 'INVENTORY_INVALID: missing product ID'; end if;
  v_previous:=p_data->'before';
  v_next:=p_data->'after';
  v_reason:=coalesce(nullif(p_data->>'reason',''),'adjustment');
  v_notes:=left(coalesce(p_data->>'notes',''),500);

  if p_kind='create' then
    if jsonb_typeof(v_next)<>'object' or v_next->>'id' is distinct from v_product_id::text or
       v_next->>'shop_id' is distinct from p_shop_id::text then
      raise exception 'INVENTORY_INVALID: mismatched new product';
    end if;
    if length(btrim(coalesce(v_next->>'sku',''))) not between 1 and 90 or
       length(btrim(coalesce(v_next->>'name',''))) not between 1 and 200 then
      raise exception 'INVENTORY_INVALID: product name and SKU required';
    end if;
    if coalesce((v_next->>'selling_price')::numeric, -1)<0 or
       coalesce((v_next->>'cost_price')::numeric, -1)<0 or
       coalesce((v_next->>'reorder_level')::numeric, -1)<0 or
       coalesce((v_next->>'stock_quantity')::numeric,-1)<0 then
      raise exception 'INVENTORY_INVALID: negative price or starting stock';
    end if;
    if (v_next->>'stock_quantity')::numeric>100000000 then
      raise exception 'INVENTORY_INVALID: too many opening units';
    end if;
    if exists (select 1 from public.products where shop_id=p_shop_id and
      (id=v_product_id or lower(sku)=lower(btrim(v_next->>'sku')))) then
      raise exception 'INVENTORY_REVIEW: SKU/ID already exists from another device';
    end if;
    insert into public.products(
      id,shop_id,category_id,sku,barcode,name,brand,part_number,item_type,
      cost_price,selling_price,stock_quantity,reorder_level,track_stock,unit,is_active
    ) values (
      v_product_id,p_shop_id,nullif(v_next->>'category_id','')::uuid,
      btrim(v_next->>'sku'),nullif(v_next->>'barcode',''),btrim(v_next->>'name'),
      nullif(v_next->>'brand',''),nullif(v_next->>'part_number',''),
      coalesce(nullif(v_next->>'item_type',''),'product'),
      (v_next->>'cost_price')::numeric,(v_next->>'selling_price')::numeric,
      0,(v_next->>'reorder_level')::numeric,
      coalesce((v_next->>'track_stock')::boolean,true),
      coalesce(nullif(v_next->>'unit',''),'pc'),
      coalesce((v_next->>'is_active')::boolean,true)
    );
    v_delta:=(v_next->>'stock_quantity')::numeric;
    if v_delta>0 then
      perform public.adjust_inventory_stock(v_product_id,v_delta,'opening',
        'Offline product opening stock • '||p_operation_id::text);
    end if;

  else
    select * into v_product from public.products
      where id=v_product_id and shop_id=p_shop_id and is_active
      for update;
    if not found then
      raise exception 'INVENTORY_REVIEW: product no longer exists or is inactive';
    end if;
    if v_product.retail_parent_id is not null or v_product.batch_tracked or
       v_product.serial_tracked or v_product.is_weighed then
      raise exception 'INVENTORY_REVIEW: complex tracked inventory requires online processing';
    end if;

    if p_kind='edit' then
      if jsonb_typeof(v_previous)<>'object' or jsonb_typeof(v_next)<>'object' then
        raise exception 'INVENTORY_INVALID: product edit has no before/after snapshots';
      end if;
      -- Field-by-field CAS. Stock sales do NOT invalidate a product detail edit,
      -- but a conflicting edit from Tablet B cannot overwrite Tablet A.
      if v_previous->>'name' is distinct from v_product.name or
         v_previous->>'category_id' is distinct from v_product.category_id::text or
         v_previous->>'brand' is distinct from v_product.brand or
         v_previous->>'barcode' is distinct from v_product.barcode or
         v_previous->>'part_number' is distinct from v_product.part_number or
         (v_previous->>'cost_price')::numeric is distinct from v_product.cost_price or
         (v_previous->>'selling_price')::numeric is distinct from v_product.selling_price or
         (v_previous->>'reorder_level')::numeric is distinct from v_product.reorder_level or
         v_previous->>'unit' is distinct from v_product.unit then
        raise exception 'INVENTORY_REVIEW: another tablet edited this product; review before merging';
      end if;
      if length(btrim(coalesce(v_next->>'name',''))) not between 1 and 200 or
         coalesce((v_next->>'cost_price')::numeric,-1)<0 or
         coalesce((v_next->>'selling_price')::numeric,-1)<0 or
         coalesce((v_next->>'reorder_level')::numeric,-1)<0 then
        raise exception 'INVENTORY_INVALID: invalid product details';
      end if;
      update public.products set
        name=btrim(v_next->>'name'),
        category_id=nullif(v_next->>'category_id','')::uuid,
        brand=nullif(v_next->>'brand',''),
        barcode=nullif(v_next->>'barcode',''),
        part_number=nullif(v_next->>'part_number',''),
        cost_price=(v_next->>'cost_price')::numeric,
        selling_price=(v_next->>'selling_price')::numeric,
        reorder_level=(v_next->>'reorder_level')::numeric,
        unit=coalesce(nullif(v_next->>'unit',''),'pc'),
        updated_at=now()
      where id=v_product_id and shop_id=p_shop_id;
      -- Never silently delete/archive an item that may have queued sales.
      if (v_next->>'is_active')::boolean is distinct from v_product.is_active then
        raise exception 'INVENTORY_REVIEW: archive/reactivation must be done while online';
      end if;

    elsif p_kind='adjust' then
      v_delta:=(p_data->>'delta')::numeric;
      if v_delta is null or v_delta=0 or abs(v_delta)>100000000 or
         v_reason not in ('adjustment','damage','theft','return','opening') then
        raise exception 'INVENTORY_INVALID: invalid stock adjustment';
      end if;
      -- Delta-based movement is additive across both tablets and online sales.
      perform public.adjust_inventory_stock(v_product_id,v_delta,v_reason,
        left('Offline '||v_notes||' • '||p_operation_id::text,500));

    elsif p_kind='count' then
      v_expected:=(p_data->>'expected_stock')::numeric;
      v_counted:=(p_data->>'counted_stock')::numeric;
      if v_expected is null or v_counted is null or v_counted<0 or
         v_counted>100000000 then
        raise exception 'INVENTORY_INVALID: stocktake counts missing or invalid';
      end if;
      if v_product.stock_quantity is distinct from v_expected then
        raise exception 'INVENTORY_REVIEW: quantity changed due to sales or other tablet; physical recount required';
      end if;
      v_delta:=v_counted-v_expected;
      if v_delta<>0 then
        perform public.adjust_inventory_stock(v_product_id,v_delta,'adjustment',
          left('Offline physical count • '||p_operation_id::text,500));
      end if;
    end if;
  end if;
  insert into public.storepos_offline_inventory_ops
    (operation_id,shop_id,actor_id,catalog_epoch,operation_kind,payload)
  values(p_operation_id,p_shop_id,p_actor_id,p_epoch,p_kind,p_data);
  return jsonb_build_object('synced',true,'duplicate',false,'id',p_operation_id);
end;
$$;
revoke all on function public.storepos_reconcile_inventory(uuid,uuid,uuid,uuid,text,jsonb)
  from public,anon,authenticated;
grant execute on function public.storepos_reconcile_inventory(uuid,uuid,uuid,uuid,text,jsonb)
  to authenticated;
