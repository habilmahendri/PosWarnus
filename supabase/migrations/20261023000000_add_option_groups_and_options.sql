-- Phase 3: menu item description, option groups and options (+ seed data).
--
-- Already APPLIED MANUALLY to the live Supabase project via the Supabase MCP on 2026-10-23.
-- This file is the record of that change; it is NOT idempotent and is unsafe to re-run:
-- `create table` / `create index` / `create policy` fail on an existing schema, and the seed
-- block inserts duplicate option groups/options every time it runs.
--
-- Source: docs/superpowers/specs/2026-10-23-menu-detail-cart-design.md, section 2 (copied verbatim).

-- 1. Item description (nullable; the dialog hides the line when null)
alter table public.menu_items
  add column if not exists description text;

-- 2. Option groups
create table public.option_groups (
  id           uuid primary key default gen_random_uuid(),
  menu_item_id uuid not null references public.menu_items(id) on delete cascade,
  name         text not null,
  kind         text not null check (kind in ('variant', 'modifier')),
  selection    text not null check (selection in ('single', 'multi')),
  is_required  boolean not null default false,
  sort_order   integer not null default 0,
  created_at   timestamptz not null default now(),
  constraint variant_is_single_and_required
    check (kind <> 'variant' or (selection = 'single' and is_required))
);

create index option_groups_menu_item_id_idx on public.option_groups (menu_item_id);
create unique index option_groups_one_variant_per_item
  on public.option_groups (menu_item_id) where kind = 'variant';

-- 3. Options
create table public.options (
  id           uuid primary key default gen_random_uuid(),
  group_id     uuid not null references public.option_groups(id) on delete cascade,
  name         text not null,
  price_delta  numeric not null default 0 check (price_delta >= 0),
  sort_order   integer not null default 0,
  is_available boolean not null default true,
  created_at   timestamptz not null default now()
);

create index options_group_id_idx on public.options (group_id);

-- 4. RLS: read-only for signed-in cashiers. Writes happen only via the admin website
--    (service role / admin policies, defined on that side).
alter table public.option_groups enable row level security;
alter table public.options       enable row level security;

create policy "Authenticated read access to option_groups"
  on public.option_groups for select to authenticated using (true);
create policy "Authenticated read access to options"
  on public.options for select to authenticated using (true);

-- Seed data (matches the 9 current items; runs once on a fresh table)
do $$
declare gid uuid;
begin
  -- Ayam Katsu: Ukuran (variant), Tingkat Pedas (single, required), Tambahan (multi)
  insert into public.option_groups (menu_item_id, name, kind, selection, is_required, sort_order)
    select id, 'Ukuran', 'variant', 'single', true, 0 from public.menu_items where name = 'Ayam Katsu' limit 1
    returning id into gid;
  insert into public.options (group_id, name, price_delta, sort_order) values
    (gid, 'Regular', 0, 0), (gid, 'Large', 10000, 1);

  insert into public.option_groups (menu_item_id, name, kind, selection, is_required, sort_order)
    select id, 'Tingkat Pedas', 'modifier', 'single', true, 1 from public.menu_items where name = 'Ayam Katsu' limit 1
    returning id into gid;
  insert into public.options (group_id, name, price_delta, sort_order) values
    (gid, 'Tidak pedas', 0, 0), (gid, 'Sedang', 0, 1), (gid, 'Pedas', 0, 2);

  insert into public.option_groups (menu_item_id, name, kind, selection, is_required, sort_order)
    select id, 'Tambahan', 'modifier', 'multi', false, 2 from public.menu_items where name = 'Ayam Katsu' limit 1
    returning id into gid;
  insert into public.options (group_id, name, price_delta, sort_order) values
    (gid, 'Extra Keju', 5000, 0), (gid, 'Extra Telur', 4000, 1);

  -- Nasi Goreng Spesial: Tingkat Pedas (single, optional), Tambahan (multi)
  insert into public.option_groups (menu_item_id, name, kind, selection, is_required, sort_order)
    select id, 'Tingkat Pedas', 'modifier', 'single', false, 0 from public.menu_items where name = 'Nasi Goreng Spesial' limit 1
    returning id into gid;
  insert into public.options (group_id, name, price_delta, sort_order) values
    (gid, 'Sedang', 0, 0), (gid, 'Pedas', 0, 1);

  insert into public.option_groups (menu_item_id, name, kind, selection, is_required, sort_order)
    select id, 'Tambahan', 'modifier', 'multi', false, 1 from public.menu_items where name = 'Nasi Goreng Spesial' limit 1
    returning id into gid;
  insert into public.options (group_id, name, price_delta, sort_order, is_available) values
    (gid, 'Extra Telur', 4000, 0, true), (gid, 'Extra Ayam', 6000, 1, true), (gid, 'Kerupuk', 2000, 2, false);

  -- Es Teh Manis: Ukuran (variant), Gula (single, optional)
  insert into public.option_groups (menu_item_id, name, kind, selection, is_required, sort_order)
    select id, 'Ukuran', 'variant', 'single', true, 0 from public.menu_items where name = 'Es Teh Manis' limit 1
    returning id into gid;
  insert into public.options (group_id, name, price_delta, sort_order) values
    (gid, 'Regular', 0, 0), (gid, 'Large', 3000, 1);

  insert into public.option_groups (menu_item_id, name, kind, selection, is_required, sort_order)
    select id, 'Gula', 'modifier', 'single', false, 1 from public.menu_items where name = 'Es Teh Manis' limit 1
    returning id into gid;
  insert into public.options (group_id, name, price_delta, sort_order) values
    (gid, 'Normal', 0, 0), (gid, 'Less sugar', 0, 1);

  update public.menu_items set description = 'Ayam goreng tepung renyah dengan saus katsu dan nasi hangat.' where name = 'Ayam Katsu';
  update public.menu_items set description = 'Nasi goreng dengan telur, ayam suwir, dan acar.' where name = 'Nasi Goreng Spesial';
  update public.menu_items set description = 'Teh manis dingin.' where name = 'Es Teh Manis';
end $$;
