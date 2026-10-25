-- Fase 6c follow-up: verify_training_pin always failed because pgcrypto lives in `extensions`.
--
-- APPLIED to the live project on 2026-10-25 with the project owner's explicit approval.
--
-- Why: 20261025000000_add_training_mode_pin.sql installs pgcrypto with a bare
-- `create extension if not exists pgcrypto;`, which on this Supabase project resolves into the
-- `extensions` schema (confirmed: `select extnamespace::regnamespace from pg_extension where
-- extname = 'pgcrypto'` returns `extensions`), not `public`. The function was declared with
-- `set search_path = public`, so `crypt()`/`gen_salt()` were unreachable from inside it. Reproduced
-- live: calling `verify_training_pin('123456')` as the test cashier, with the correct PIN already
-- hashed and stored, failed with `42883: function crypt(text, text) does not exist` — meaning every
-- PIN, correct or not, was rejected. The app's fail-closed design meant this surfaced only as
-- "PIN salah" to the cashier, never as a crash or a misleading success.
--
-- This version adds `extensions` to the function's search_path, after `public`, so an
-- attacker-controlled `public` object can't shadow pgcrypto's functions ahead of the trusted one.
-- No other behavior changes.
--
-- Safe to re-apply: `create or replace`.

create or replace function public.verify_training_pin(p_pin text)
returns boolean
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_hash text;
begin
  if auth.uid() is null then
    raise exception 'authentication required' using errcode = '42501';
  end if;

  select pin_hash into v_hash from public.training_mode_pin where id = 1;
  if v_hash is null then
    return false; -- belum diisi pemilik: anggap semua PIN salah
  end if;

  return v_hash = crypt(p_pin, v_hash);
end;
$$;

revoke all on function public.verify_training_pin(text) from public, anon;
grant execute on function public.verify_training_pin(text) to authenticated;
