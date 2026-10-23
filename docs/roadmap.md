# POS WarNus — Roadmap

Status as of 2026-10-23. Source of truth for scope is [PRD-pos.md](PRD-pos.md); this file only orders the work. Each phase gets its own brainstorm -> spec -> plan -> implementation cycle under `docs/superpowers/`.

## Phases

| Phase | Scope | PRD | Status |
|---|---|---|---|
| 1 | Splash -> Login -> Dashboard -> Logout | 4.1, 4.2 | Done |
| 2 | Room cache + Catalog/Dashboard | 4.3 | Done |
| 3 | Menu Detail Dialog (variants/modifiers) + in-memory Cart | 4.4, 4.5 | Done (merged to `main`; verified on tablet and phone emulators) |
| 3b | Featured Menu row + polish of Phases 1-3 (see below) | 4.12 | Done on branch `feature/phase3b-featured-menu-polish`, **not merged**; waiting for owner actions below |
| 4 | Tax/service charge, cash Payment, Payment Success, local transaction storage (UUID, `isTraining` flag from day one), "Bayar" goes live | 4.6, 4.7, 4.8, part of 4.9 | Planned |
| 5 | Offline sync engine: send queue, idempotent upsert, offline / last-synced indicator, transactions tables in Supabase | rest of 4.9 | Planned |
| 6 | Settings screen, Bluetooth receipt printing, Training Mode toggle + "LATIHAN" receipt marker | 4.10, 4.13 | Planned |
| 7 | Transaction History (incl. "pending sync") | 4.11 | Planned |
| 8 | "Frequently bought together" suggestions in the cart | 4.14 | Planned |

## Why this order

- **Featured Menu in 3b:** independent of every later phase, small, and visible to cashiers immediately. It also forces the next Room migration (v3), so Room schema export is switched on there, before the heavier v4+ migrations.
- **Local transactions in Phase 4, sync in Phase 5:** a cashier must always be able to finish a sale offline, so the transaction is written locally from the first payment screen; sync is added behind it without changing the payment UI.
- **`isTraining` from Phase 4:** sync (5), history (7) and suggestions (8) then only filter on a flag that already exists instead of reshaping data.
- **Training Mode in Phase 6:** its toggle needs a Settings screen, which first appears with the printer.
- **Suggestions last:** they need completed transactions to compute from, and some history to accumulate.

## Phase 3b contents

- Featured Menu (PRD 4.12): `menu_items.is_featured` in Supabase, Room v3 migration, "Menu Unggulan" row in the catalog.
- Polish carried over from Phases 1-3 testing:
  - Login error messages in Indonesian (currently English, from `LoginViewModel`).
  - App launcher icon and Android 12+ system splash (currently the default robot on white).
  - Room `exportSchema` enabled.
  - Splash status-bar icons readable on the orange background.
  - Phone-layout check of Phase 3 (bottom bar + sheet) on an emulator.

### Phase 3b status (2026-10-23)

Done on the branch: Room schema export (v2/v3 JSON in `app/schemas`), `is_featured` data layer (Room v3, `MIGRATION_2_3`), `featuredItems` in the dashboard ViewModel, "Menu Unggulan" row, Indonesian login messages, brand launcher icon + system splash, white status-bar icons on the splash, compact catalog header, and a responsive Login layout (found broken on portrait phones during verification: the fixed 480dp brand pane hid the form).

Verified on emulators (tablet 2560x1600, phone 1344x2992): system splash and app splash, Login on tablet / phone landscape / phone portrait, Indonesian validation message, keyboard no longer covers the form, upgrade from a v2 database to v3 without data loss, compact header, Phase 3 flow on the phone (dialog, bottom bar, cart sheet, auto-close when the cart empties).

NOT verified: the "Menu Unggulan" row itself (the live Supabase has no `is_featured` column yet), the API 24-25 launcher icon (no AVD), a wrong-password error banner (not tried), Android 12+ themed (monochrome) icon.

Owner actions pending:
1. Apply `supabase/migrations/20261023100000_add_menu_items_is_featured.sql` to the live project, then mark some menu items `is_featured = true`.
2. Fill `menu_items.image_url` (all currently null, so cards show a grey placeholder).
3. Review and merge the branch (nothing has been merged or pushed).

Known leftovers (minor): header title/subtitle lack ellipsis; pre-existing `LoginScreenTest` (androidTest) asserts the text "Login" while the UI says "Masuk" and has two lint errors; `CatalogRepository` refresh failure message is raw/English (not shown in the UI today).

## Open decisions (before Phase 4)

- **Rounding:** round the grand total to the nearest Rp100 / Rp500? (affects totals, change and receipt.)
- **Change breakdown:** show a suggested bill/coin breakdown for the change on the payment screen?
- **Product data to fill:** all `menu_items.image_url` are currently null, so catalog photos need to be provided by whoever manages the catalog.

## Backlog (unscheduled ideas, not in the PRD)

Shift close-out and cash in/out log, WhatsApp / QR-review receipts, queue number and "ready" status, per-item notes and order type, one-tap add for items without required options, remember last-chosen options, abbreviation search, automatic cash-drawer open, manual discounts, parked orders, loyalty stamps by phone number, quick PIN switch between cashiers, customer-facing display, QR table self-order, barcode scanning for packaged goods. Non-cash payment (QRIS) is a PRD non-goal today.
