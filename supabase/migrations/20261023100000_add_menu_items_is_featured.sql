-- Phase 3b: admin-curated featured flag (read-only for the app).
--
-- Applied manually by the project owner; not executed by the app or any build step.
-- NOT idempotent: re-running `add column` on a table that already has it fails.
--
-- Source: docs/superpowers/specs/2026-10-23-featured-menu-polish-design.md, section 3.1.
-- RLS is unchanged (existing menu_items select policies cover the new column).
-- Existing rows become false.

alter table public.menu_items
  add column is_featured boolean not null default false;

-- Optional example for marking items (NOT executed by the migration):
-- update public.menu_items set is_featured = true
--   where name in ('Nasi Goreng Spesial', 'Ayam Katsu');
