-- Phase 4: single-row tax / service charge config (read-only for the app).
--
-- Applied manually by the project owner; NOT executed by the app or any build step, and NOT yet
-- applied to the live project. NOT idempotent: re-running `create table` / `create policy` fails.
-- Until it is applied the app works with 0% tax and a small "Pajak belum dimuat" warning.
--
-- Source: docs/superpowers/specs/2026-10-23-cash-payment-local-transactions-design.md, section 3.1.
-- Edit the label / percent here later with `update public.tax_settings set percent = 11 where id = 1;`.

create table public.tax_settings (
  id         int primary key default 1 check (id = 1),
  label      text not null default 'Pajak',
  percent    numeric(5,2) not null check (percent >= 0 and percent <= 100),
  updated_at timestamptz not null default now()
);

alter table public.tax_settings enable row level security;

create policy "Authenticated read access to tax_settings"
  on public.tax_settings for select to authenticated using (true);

insert into public.tax_settings (id, label, percent) values (1, 'Pajak', 10);
