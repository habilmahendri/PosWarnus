-- Phase 5: offline sync of transactions (write-only tables + idempotent submit_transaction RPC).
--
-- Applied manually by the project owner; NOT executed by the app or any build step, and NOT yet
-- applied to the live project. NOT idempotent: re-running the table, policy or function creation
-- statements fails; the function is written with `create or replace` only so a fixed function body
-- can be re-applied on its own.
-- Until it is applied the app keeps working and shows "N menunggu sinkron".
--
-- Source: docs/superpowers/specs/2026-10-24-offline-sync-design.md, section 4.1.

create table public.transactions (
  id               uuid primary key,
  created_at       timestamptz not null,
  cashier_id       uuid not null references auth.users (id),
  subtotal         bigint not null check (subtotal >= 0),
  tax_label        text not null,
  tax_basis_points int not null check (tax_basis_points >= 0),
  tax_amount       bigint not null check (tax_amount >= 0),
  grand_total      bigint not null check (grand_total >= 0),
  cash_received    bigint not null check (cash_received >= 0),
  change_amount    bigint not null check (change_amount >= 0),
  is_training      boolean not null default false check (not is_training),
  received_at      timestamptz not null default now(),
  check (grand_total = subtotal + tax_amount),
  check (cash_received >= grand_total),
  check (change_amount = cash_received - grand_total)
);

create index transactions_created_at_idx on public.transactions (created_at desc);
create index transactions_cashier_created_idx on public.transactions (cashier_id, created_at desc);

create table public.transaction_lines (
  id             text primary key,
  transaction_id uuid not null references public.transactions (id) on delete cascade,
  cashier_id     uuid not null references auth.users (id),
  menu_item_id   uuid not null,
  name           text not null,
  base_price     bigint not null check (base_price >= 0),
  quantity       int not null check (quantity > 0),
  selections     jsonb not null default '[]'::jsonb
);

create index transaction_lines_transaction_idx on public.transaction_lines (transaction_id);

alter table public.transactions enable row level security;
alter table public.transaction_lines enable row level security;

-- Write-only for cashiers: INSERT of their own rows, nothing else. No select/update/delete policy exists.
create policy "Cashiers insert own transactions"
  on public.transactions for insert to authenticated
  with check (cashier_id = (select auth.uid()));

create policy "Cashiers insert own transaction lines"
  on public.transaction_lines for insert to authenticated
  with check (cashier_id = (select auth.uid()));

revoke all on public.transactions, public.transaction_lines from anon;

create or replace function public.submit_transaction(p jsonb)
returns void
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_id      uuid := (p ->> 'id')::uuid;
  v_cashier uuid := (p ->> 'cashier_id')::uuid;
  v_rows    int;
begin
  if coalesce(jsonb_array_length(p -> 'lines'), 0) = 0 then
    raise exception 'a transaction needs at least one line';
  end if;

  insert into public.transactions (
    id, created_at, cashier_id, subtotal, tax_label, tax_basis_points,
    tax_amount, grand_total, cash_received, change_amount
  ) values (
    v_id, (p ->> 'created_at')::timestamptz, v_cashier, (p ->> 'subtotal')::bigint, p ->> 'tax_label',
    (p ->> 'tax_basis_points')::int, (p ->> 'tax_amount')::bigint, (p ->> 'grand_total')::bigint,
    (p ->> 'cash_received')::bigint, (p ->> 'change_amount')::bigint
  )
  on conflict (id) do nothing;

  get diagnostics v_rows = row_count;
  if v_rows = 0 then
    return; -- already uploaded earlier: write nothing, report success
  end if;

  insert into public.transaction_lines (
    id, transaction_id, cashier_id, menu_item_id, name, base_price, quantity, selections
  )
  select l ->> 'id', v_id, v_cashier, (l ->> 'menu_item_id')::uuid, l ->> 'name',
         (l ->> 'base_price')::bigint, (l ->> 'quantity')::int, coalesce(l -> 'selections', '[]'::jsonb)
  from jsonb_array_elements(p -> 'lines') as l;
end;
$$;

revoke all on function public.submit_transaction(jsonb) from public, anon;
grant execute on function public.submit_transaction(jsonb) to authenticated;
