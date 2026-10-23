-- Phase 5 follow-up: make submit_transaction work for cashiers who have no SELECT policy.
--
-- APPLIED to the live project on 2026-10-24 with the project owner's explicit approval. It changes the
-- security model of the function from `security invoker` to `security definer`.
--
-- Why: 20261024000000_add_transactions_sync.sql was applied to the live project on 2026-10-24 and
-- every upload then failed with 42501 "new row violates row-level security policy for table
-- transactions". Reproduced on the server as role `authenticated` with the cashier's JWT claims
-- (inside a rolled-back transaction): a plain INSERT passes the INSERT policy, but
-- `INSERT ... ON CONFLICT (id) DO NOTHING` is rejected, because Postgres also applies SELECT
-- policies to an INSERT with an ON CONFLICT clause and the cashier role deliberately has none
-- (write-only). The spec's assumption that DO NOTHING needs only an INSERT policy was wrong.
--
-- This version keeps cashiers write-only: it runs as the table owner (which bypasses RLS) and checks
-- the rule RLS would have enforced itself: `cashier_id` must equal the signed-in user. It leaks
-- nothing (returns void). Table constraints still apply.
--
-- Alternative that keeps `security invoker`: add a SELECT policy so a cashier can read their own
-- rows (`create policy ... on public.transactions for select to authenticated using (cashier_id =
-- (select auth.uid()))`, same for transaction_lines). Simpler, but cashiers could then read their
-- own uploaded sales through the API.
--
-- Safe to re-apply: `create or replace`.

create or replace function public.submit_transaction(p jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id      uuid := (p ->> 'id')::uuid;
  v_cashier uuid := (p ->> 'cashier_id')::uuid;
  v_rows    int;
begin
  if auth.uid() is null or v_cashier is distinct from auth.uid() then
    raise exception 'cashier_id must be the signed-in user' using errcode = '42501';
  end if;

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
