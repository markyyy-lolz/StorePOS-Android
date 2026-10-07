-- StorePOS v1.6.0 schema health diagnostic
create or replace function public.storepos_schema_health()
returns jsonb
language sql
security definer
set search_path = pg_catalog, public
as $$
with required_tables(name) as (
    values
      ('shops'), ('shop_members'), ('shop_settings'), ('products'),
      ('product_categories'), ('customers'), ('suppliers'), ('purchase_orders'),
      ('purchase_order_items'), ('sales'), ('sale_items'), ('inventory_movements'),
      ('cashier_shifts'), ('cash_movements'), ('z_reports'), ('sale_returns'),
      ('stock_transfers'), ('inventory_counts'), ('product_batches'), ('product_serials'),
      ('customer_receivables'), ('receivable_payments'), ('audit_logs'),
      ('device_sessions'), ('app_versions'), ('supplier_payables'),
      ('supplier_payments'), ('shop_member_permissions')
),
required_functions(name) as (
    values
      ('validate_device_access'),
      ('get_shop_entitlements'),
      ('get_shop_alerts'),
      ('adjust_inventory_stock'),
      ('receive_purchase_order'),
      ('receive_customer_payment'),
      ('storepos_pay_supplier')
),
missing_tables as (
    select rt.name
    from required_tables rt
    where not exists (
        select 1 from information_schema.tables t
        where t.table_schema='public' and t.table_name=rt.name
    )
),
missing_functions as (
    select rf.name
    from required_functions rf
    where not exists (
        select 1
        from pg_proc p
        join pg_namespace n on n.oid=p.pronamespace
        where n.nspname='public' and p.proname=rf.name
    )
)
select jsonb_build_object(
    'ok',
    not exists(select 1 from missing_tables)
    and not exists(select 1 from missing_functions),
    'missing_tables',
    coalesce((select jsonb_agg(name order by name) from missing_tables), '[]'::jsonb),
    'missing_functions',
    coalesce((select jsonb_agg(name order by name) from missing_functions), '[]'::jsonb),
    'checked_at',
    now()
);
$$;

grant execute on function public.storepos_schema_health() to authenticated;
