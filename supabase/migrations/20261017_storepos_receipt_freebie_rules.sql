-- Standalone StorePOS-only promotional receipt rules.
-- The table is initially EMPTY: NO promotion is enabled until a shop owner configures it.
-- NO changes to MotoPOS rows, sales, historical receipts or inventory.
create table if not exists public.storepos_receipt_freebie_rules (
    shop_id uuid primary key references public.shops(id) on delete cascade,
    enabled boolean not null default false,
    minimum_total numeric(12,2) not null default 1500 check (minimum_total > 0),
    gift_name text not null default '' check (length(gift_name) <= 80),
    quantity integer not null default 1 check (quantity between 1 and 20),
    updated_at timestamptz not null default now()
);
alter table public.storepos_receipt_freebie_rules enable row level security;
revoke all on public.storepos_receipt_freebie_rules from anon;
grant select, insert, update on public.storepos_receipt_freebie_rules to authenticated;
drop policy if exists storepos_receipt_freebie_read on public.storepos_receipt_freebie_rules;
create policy storepos_receipt_freebie_read on public.storepos_receipt_freebie_rules
    for select to authenticated
    using (
        app_private.has_shop_role(shop_id, null::text[]) and
        exists (select 1 from public.shops s where s.id = shop_id and s.app_code = 'storepos')
    );
drop policy if exists storepos_receipt_freebie_insert on public.storepos_receipt_freebie_rules;
create policy storepos_receipt_freebie_insert on public.storepos_receipt_freebie_rules
    for insert to authenticated
    with check (
        app_private.has_shop_role(shop_id, array['owner','admin']::text[]) and
        exists (select 1 from public.shops s where s.id = shop_id and s.app_code = 'storepos')
    );
drop policy if exists storepos_receipt_freebie_update on public.storepos_receipt_freebie_rules;
create policy storepos_receipt_freebie_update on public.storepos_receipt_freebie_rules
    for update to authenticated
    using (
        app_private.has_shop_role(shop_id, array['owner','admin']::text[]) and
        exists (select 1 from public.shops s where s.id = shop_id and s.app_code = 'storepos')
    )
    with check (
        app_private.has_shop_role(shop_id, array['owner','admin']::text[]) and
        exists (select 1 from public.shops s where s.id = shop_id and s.app_code = 'storepos')
    );
