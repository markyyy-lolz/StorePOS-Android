-- StorePOS v1.6.0 supplier payables
create table if not exists public.supplier_payables (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete cascade,
    supplier_id uuid not null references public.suppliers(id) on delete restrict,
    purchase_order_id uuid not null references public.purchase_orders(id) on delete restrict,
    original_amount numeric(14,2) not null check (original_amount >= 0),
    balance numeric(14,2) not null check (balance >= 0),
    due_date date,
    status text not null default 'open' check (status in ('open','partial','paid','cancelled')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (purchase_order_id)
);

create table if not exists public.supplier_payments (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete cascade,
    payable_id uuid not null references public.supplier_payables(id) on delete restrict,
    supplier_id uuid not null references public.suppliers(id) on delete restrict,
    amount numeric(14,2) not null check (amount > 0),
    method text not null,
    reference_number text,
    notes text,
    paid_by uuid not null,
    paid_at timestamptz not null default now()
);

create index if not exists supplier_payables_shop_status_idx
    on public.supplier_payables(shop_id, status, due_date);
create index if not exists supplier_payments_shop_paid_idx
    on public.supplier_payments(shop_id, paid_at desc);

alter table public.supplier_payables enable row level security;
alter table public.supplier_payments enable row level security;

drop policy if exists supplier_payables_select on public.supplier_payables;
create policy supplier_payables_select on public.supplier_payables
for select to authenticated
using (app_private.has_shop_role(shop_id, array['owner','admin','manager','inventory']::text[]));

drop policy if exists supplier_payables_write on public.supplier_payables;
create policy supplier_payables_write on public.supplier_payables
for all to authenticated
using (app_private.has_shop_role(shop_id, array['owner','admin','manager']::text[]))
with check (app_private.has_shop_role(shop_id, array['owner','admin','manager']::text[]));

drop policy if exists supplier_payments_select on public.supplier_payments;
create policy supplier_payments_select on public.supplier_payments
for select to authenticated
using (app_private.has_shop_role(shop_id, array['owner','admin','manager','inventory']::text[]));

drop policy if exists supplier_payments_insert on public.supplier_payments;
create policy supplier_payments_insert on public.supplier_payments
for insert to authenticated
with check (
    paid_by = auth.uid()
    and app_private.has_shop_role(shop_id, array['owner','admin','manager']::text[])
);

create or replace function public.storepos_supplier_payable_from_po()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
    if new.status = 'received'
       and (old.status is distinct from 'received')
       and new.total_amount > 0 then
        insert into public.supplier_payables(
            shop_id, supplier_id, purchase_order_id,
            original_amount, balance, due_date, status
        )
        values(
            new.shop_id, new.supplier_id, new.id,
            new.total_amount, new.total_amount,
            coalesce(new.received_at::date + 30, current_date + 30),
            'open'
        )
        on conflict (purchase_order_id) do nothing;
    end if;
    return new;
end;
$$;

drop trigger if exists trg_storepos_supplier_payable_from_po on public.purchase_orders;
create trigger trg_storepos_supplier_payable_from_po
after update of status on public.purchase_orders
for each row
execute function public.storepos_supplier_payable_from_po();

create or replace function public.storepos_pay_supplier(
    p_payable_id uuid,
    p_amount numeric,
    p_method text,
    p_reference_number text default null,
    p_notes text default null
)
returns setof public.supplier_payables
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    p public.supplier_payables%rowtype;
    next_balance numeric;
begin
    select * into p
    from public.supplier_payables
    where id = p_payable_id
    for update;

    if p.id is null then raise exception 'Supplier payable not found.'; end if;
    if not app_private.has_shop_role(p.shop_id, array['owner','admin','manager']::text[]) then
        raise exception 'Manager access required.';
    end if;
    if p.status in ('paid','cancelled') then raise exception 'This supplier payable is already closed.'; end if;
    if p_amount is null or p_amount <= 0 or p_amount > p.balance then
        raise exception 'Invalid supplier payment amount.';
    end if;

    insert into public.supplier_payments(
        shop_id, payable_id, supplier_id, amount, method,
        reference_number, notes, paid_by
    )
    values(
        p.shop_id, p.id, p.supplier_id, p_amount,
        lower(trim(coalesce(p_method,'cash'))),
        nullif(trim(coalesce(p_reference_number,'')),''),
        nullif(trim(coalesce(p_notes,'')),''),
        auth.uid()
    );

    next_balance := greatest(0, p.balance - p_amount);

    update public.supplier_payables
    set balance = next_balance,
        status = case when next_balance <= 0.009 then 'paid' else 'partial' end,
        updated_at = now()
    where id = p.id
    returning * into p;

    insert into public.audit_logs(
        shop_id, actor_id, action, entity_type, entity_id, new_data
    )
    values(
        p.shop_id, auth.uid(), 'supplier_payment',
        'supplier_payable', p.id,
        jsonb_build_object('amount', p_amount, 'method', p_method, 'balance', p.balance)
    );

    return next p;
end;
$$;

grant execute on function public.storepos_pay_supplier(uuid,numeric,text,text,text) to authenticated;

insert into public.supplier_payables(
    shop_id, supplier_id, purchase_order_id, original_amount, balance, due_date, status
)
select
    po.shop_id, po.supplier_id, po.id, po.total_amount, po.total_amount,
    coalesce(po.received_at::date + 30, current_date + 30), 'open'
from public.purchase_orders po
where po.status='received'
  and po.total_amount > 0
  and not exists (
      select 1 from public.supplier_payables sp where sp.purchase_order_id=po.id
  );
