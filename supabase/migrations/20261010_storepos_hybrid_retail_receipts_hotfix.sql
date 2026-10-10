-- Safe, in-place StorePOS-only hybrid RPC correction: retail checkout creates receipt details.
-- The original migration ran already; no existing sales/payments/inventory are modified.
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
  v_quote jsonb;
  v_result jsonb;
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
  -- Use StorePOS's normal retail engine, NOT the bare generic sale RPC.
  -- This also produces retail_sale_details/receipt_token and respects existing
  -- cash-shift, retail pricing and register accounting invariants.
  v_quote:=app_private.retail_quote(p_shop_id,jsonb_build_object(
    'items',p_payload->'items',
    'discount',coalesce((p_payload->>'discount_amount')::numeric,0),
    'charges','[]'::jsonb
  ));
  if abs((v_quote->>'tax')::numeric -
         coalesce((p_payload->>'tax_amount')::numeric,0))>0.009 then
    raise exception 'HYBRID_TAX_REVIEW: shop tax configuration changed since offline checkout';
  end if;
  if abs((v_quote->>'total')::numeric -
    (select coalesce(sum((pay->>'amount')::numeric),0)
     from jsonb_array_elements(p_payload->'payments') pay))>0.009 then
    raise exception 'HYBRID_TOTAL_REVIEW: offline cash total differs from cloud quote';
  end if;
  v_result:=app_private.retail_checkout(p_shop_id,
    jsonb_build_object(
      'client_key',p_client_key,
      'items',p_payload->'items',
      'discount',coalesce((p_payload->>'discount_amount')::numeric,0),
      'charges','[]'::jsonb,
      'payments',p_payload->'payments',
      'customer_id',nullif(p_payload->>'customer_id','')
    )
  );
  select * into v_sale from public.sales
    where id=(v_result->'sale'->>'id')::uuid and shop_id=p_shop_id;
  if v_sale.id is null then raise exception 'HYBRID_REVIEW: retail checkout did not return a sale'; end if;
  return next v_sale;
  return;
end;
$$;
