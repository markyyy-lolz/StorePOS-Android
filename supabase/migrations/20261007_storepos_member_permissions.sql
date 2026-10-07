-- StorePOS v1.6.0 granular staff permission overrides
create table if not exists public.shop_member_permissions (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete cascade,
    member_id uuid not null references public.shop_members(id) on delete cascade,
    permission_key text not null,
    allowed boolean not null default true,
    updated_at timestamptz not null default now(),
    unique(member_id, permission_key)
);

create index if not exists shop_member_permissions_shop_idx
    on public.shop_member_permissions(shop_id, member_id);

alter table public.shop_member_permissions enable row level security;

drop policy if exists member_permissions_read on public.shop_member_permissions;
create policy member_permissions_read on public.shop_member_permissions
for select to authenticated
using (
    exists (
        select 1 from public.shop_members sm
        where sm.id = member_id
          and sm.user_id = auth.uid()
          and sm.is_active
    )
    or app_private.has_shop_role(shop_id, array['owner','admin']::text[])
);

drop policy if exists member_permissions_manage on public.shop_member_permissions;
create policy member_permissions_manage on public.shop_member_permissions
for all to authenticated
using (app_private.has_shop_role(shop_id, array['owner','admin']::text[]))
with check (app_private.has_shop_role(shop_id, array['owner','admin']::text[]));
