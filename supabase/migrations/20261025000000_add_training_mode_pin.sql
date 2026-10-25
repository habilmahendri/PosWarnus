-- Fase 6c: PIN pemilik untuk menyalakan Mode Pelatihan + log audit nyala/mati.
--
-- Diterapkan manual oleh pemilik proyek; BELUM dieksekusi oleh app atau langkah build apa pun, dan
-- BELUM diterapkan ke proyek hidup. TIDAK idempoten: menjalankan ulang `create table` / `create
-- policy` akan gagal. Sampai migrasi ini diterapkan dan baris PIN diisi (lihat di bawah), menyalakan
-- Mode Pelatihan selalu gagal dengan "PIN salah" di app — ini dianggap aman secara default.
--
-- Sumber: docs/superpowers/specs/2026-10-25-training-mode-owner-pin-design.md, bagian 4.1.

create extension if not exists pgcrypto;

create table public.training_mode_pin (
  id         int primary key default 1 check (id = 1),
  pin_hash   text not null,
  updated_at timestamptz not null default now()
);

alter table public.training_mode_pin enable row level security;
revoke all on public.training_mode_pin from anon, authenticated;
-- Tidak ada policy sama sekali: hanya fungsi SECURITY DEFINER di bawah yang bisa membacanya.
-- Baris PIN TIDAK diisi di sini. Isi setelah migrasi ini diterapkan, misalnya:
--   insert into public.training_mode_pin (id, pin_hash)
--   values (1, crypt('<PIN pilihan pemilik>', gen_salt('bf')))
--   on conflict (id) do update set pin_hash = excluded.pin_hash, updated_at = now();

create table public.training_mode_audit (
  id          uuid primary key default gen_random_uuid(),
  cashier_id  uuid not null references auth.users (id),
  action      text not null check (action in ('enable', 'disable')),
  occurred_at timestamptz not null default now()
);

create index training_mode_audit_occurred_at_idx on public.training_mode_audit (occurred_at desc);

alter table public.training_mode_audit enable row level security;

-- Tulis-saja untuk kasir: INSERT baris miliknya sendiri, tidak ada select/update/delete.
create policy "Cashiers insert own training_mode_audit rows"
  on public.training_mode_audit for insert to authenticated
  with check (cashier_id = (select auth.uid()));

revoke all on public.training_mode_audit from anon;

create or replace function public.verify_training_pin(p_pin text)
returns boolean
language plpgsql
security definer
set search_path = public
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
