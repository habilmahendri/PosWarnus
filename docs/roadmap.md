# POS WarNus — Roadmap

Status as of 2026-10-23. Source of truth for scope is [PRD-pos.md](PRD-pos.md); this file only orders the work. Each phase gets its own brainstorm -> spec -> plan -> implementation cycle under `docs/superpowers/`.

## Phases

| Phase | Scope | PRD | Status |
|---|---|---|---|
| 1 | Splash -> Login -> Dashboard -> Logout | 4.1, 4.2 | Done |
| 2 | Room cache + Catalog/Dashboard | 4.3 | Done |
| 3 | Menu Detail Dialog (variants/modifiers) + in-memory Cart | 4.4, 4.5 | Done (merged to `main`; tablet verified on emulator, phone layout not yet) |
| 3b | Featured Menu row + polish of Phases 1-3 (see below) | 4.12 | Next |
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

## Open decisions (before Phase 4)

- **Rounding:** round the grand total to the nearest Rp100 / Rp500? (affects totals, change and receipt.)
- **Change breakdown:** show a suggested bill/coin breakdown for the change on the payment screen?
- **Product data to fill:** all `menu_items.image_url` are currently null, so catalog photos need to be provided by whoever manages the catalog.

## Backlog (unscheduled ideas, not in the PRD)

Shift close-out and cash in/out log, WhatsApp / QR-review receipts, queue number and "ready" status, per-item notes and order type, one-tap add for items without required options, remember last-chosen options, abbreviation search, automatic cash-drawer open, manual discounts, parked orders, loyalty stamps by phone number, quick PIN switch between cashiers, customer-facing display, QR table self-order, barcode scanning for packaged goods. Non-cash payment (QRIS) is a PRD non-goal today.
