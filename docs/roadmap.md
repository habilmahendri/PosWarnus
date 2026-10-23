# POS WarNus — Roadmap

Status as of 2026-10-23. Source of truth for scope is [PRD-pos.md](PRD-pos.md); this file only orders the work. Each phase gets its own brainstorm -> spec -> plan -> implementation cycle under `docs/superpowers/`.

## Phases

| Phase | Scope | PRD | Status |
|---|---|---|---|
| 1 | Splash -> Login -> Dashboard -> Logout | 4.1, 4.2 | Done |
| 2 | Room cache + Catalog/Dashboard | 4.3 | Done |
| 3 | Menu Detail Dialog (variants/modifiers) + in-memory Cart | 4.4, 4.5 | Done (merged to `main`; verified on tablet and phone emulators) |
| 3b | Featured Menu row + polish of Phases 1-3 (see below) | 4.12 | Done (merged to `main`; `is_featured` applied to the live Supabase and verified on the tablet emulator) |
| 4 | Tax/service charge, cash Payment, Payment Success, local transaction storage (UUID, `isTraining` flag from day one), "Bayar" goes live | 4.6, 4.7, 4.8, part of 4.9 | Done (merged to `main` locally, not pushed); `tax_settings` applied to the live Supabase and verified on the tablet emulator |
| 5 | Offline sync engine: send queue, idempotent upsert, offline / last-synced indicator, transactions tables in Supabase | rest of 4.9 | Done (merged to `main` locally, not pushed); `20261024000000_add_transactions_sync.sql` applied to the live Supabase on 2026-10-24, but uploads still fail until the proposed fix `20261024100000_fix_submit_transaction_security_definer.sql` (or a SELECT policy) is decided and applied; no sale has been uploaded yet |
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

Deliberate deviation from PRD 4.12 ("selecting a category does not hide the row"): when the selected category has no items, the featured row is not shown, only the empty state.

Update (2026-10-23, later): merged to `main`; `supabase/migrations/20261023100000_add_menu_items_is_featured.sql` was applied to the live project and "Ayam Katsu" / "Nasi Goreng Spesial" were marked featured. The "Menu Unggulan" row was then seen on the tablet emulator with live data (two cards above the grid). Known quirk: if the featured row first appears while the grid is already scrolled (e.g. the first refresh after items are marked), it sits above the viewport until the user scrolls up.

Still NOT verified: the API 24-25 launcher icon (no AVD), a wrong-password error banner (not tried), Android 12+ themed (monochrome) icon, the featured row hiding while searching / keeping on category (covered by unit tests only).

Owner actions still pending:
1. Fill `menu_items.image_url` (all currently null, so cards show a grey placeholder).
2. Nothing has been pushed to a remote.

Known leftovers (minor): header title/subtitle lack ellipsis; pre-existing `LoginScreenTest` (androidTest) asserts the text "Login" while the UI says "Masuk" and has two lint errors; `CatalogRepository` refresh failure message is raw/English (not shown in the UI today).

## Phase 4 status (2026-10-24)

Spec: `docs/superpowers/specs/2026-10-23-cash-payment-local-transactions-design.md`; plan: `docs/superpowers/plans/2026-10-23-pos-cash-payment-local-transactions.md`. Decisions: no total rounding, quick cash buttons ("Uang pas" + up to 3 round amounts), change shown as a number only, tax "Pajak" 10% from `tax_settings`.

Implemented on the branch: `OrderTotals` / `CashMath`, Room v4 (`tax_settings`, `transactions`, `transaction_lines`, `MIGRATION_3_4`, schema `4.json`), `TaxRepository`, `TransactionRepository` (atomic, idempotent on the UUID), `PaymentViewModel`, cart footer with tax line and grand total, a full-screen `PaymentDialog` following the owner's design image (order summary | method card, total, cash and change | quick amounts and numpad, with a Kembali / Konfirmasi Pembayaran bar; one column on narrow screens) and a Success state that only "Selesai" closes, `AuthRepository.currentUserId()` (cached after login so offline sales survive an expired token). Deliberately omitted from the design image: the "ORD-001" badge, because the app has no order numbers yet (backlog: queue number).

Verified on the tablet emulator (live Supabase, tax table absent): footer shows "Pajak 0%" + "Pajak belum dimuat"; quick amounts for Rp28.000 are Rp30.000 / Rp50.000 / Rp100.000; Rp27.999 shows "Uang kurang Rp1" with Konfirmasi disabled; "Uang pas" and Rp50.000 (change Rp22.000) complete a sale; back and outside tap do not close Success; "Selesai" empties the cart; a sale made with Wi-Fi off completes; the pulled database holds both sales with `isTraining = 0` and the option snapshot. Phone-size layout (1080x2400 via `wm size`): bar, cart sheet and dialog fit in portrait. The first dialog version had a text field whose caret landed mid-number and whose keyboard hid the change line; the redesign replaces the field with an in-app numpad, so the system keyboard never opens. The redesigned screen was re-checked on the tablet (numpad 3, 0, 000 = Rp30.000, change Rp2.000, selected chip highlighted, sale saved) and at phone size (single column fits; the confirm label shortens to "Konfirmasi"). The whole-branch review found one Critical (cashier id lost once the token expired offline), fixed with tests; its Minor items are listed in the final report.

Update (2026-10-24, later): `20261023200000_add_tax_settings.sql` was applied to the live project (table, RLS on with one authenticated-read policy, row `1 / Pajak / 10.00`). On the tablet emulator the cart footer then showed "Pajak 10%" Rp2.800 and Total Rp30.800 with no warning, the payment screen's summary and quick amounts followed the taxed total, and a sale paid with Rp35.000 (change Rp4.200) was stored with `taxBasisPoints = 1000`, `taxAmount = 2800`, `grandTotal = 30800`, `isTraining = 0`, and the cached `tax_settings` row held 1000 bps.

NOT verified: a failed save showing the error and retrying (covered by unit tests only); landscape phone with the keyboard open; a physical device; TalkBack.

Owner actions: change the tax label or percent with `update public.tax_settings set percent = ... where id = 1;` when needed. Known leftovers: the two lint errors in the androidTest `LoginScreenTest`; "Cetak struk" is intentionally disabled until Phase 6.

## Phase 4 contents (Tax/service charge, cash Payment, local transaction storage)

PRD: 4.6, 4.7, 4.8, part of 4.9.

- Tax/service charge: read the percentage from a Supabase settings/config table, cache the last-successful value for use within the session and as an offline fallback (4.6).
- Cart: add live subtotal / tax line (showing percentage + amount) / grand total display (4.5, 4.6).
- Payment Dialog: cash-received input, live change = cash − grand total, confirm disabled while cash < grand total, no negative-change confirmations (4.7).
- New Room entity for a completed transaction: order lines with modifier/variant snapshot, quantities, subtotal, tax amount, grand total, cash received, change, timestamp, cashier id, a locally generated UUID, and an `isTraining` flag — wired into the model and into the payment/local-write path **now**, even though the Training Mode toggle/banner only ship in Phase 6, so persistence can already branch on it without a later retrofit (per PRD 4.13).
- On confirm: write the transaction to Room; on failure show an error and let the cashier retry without losing the entered cash value; on success transition to Payment Success (4.8).
- Payment Success screen: grand total / cash received / change; "Print receipt" action present but inert (wired to real Bluetooth printing in Phase 6) and "Done" (clears cart, returns to Dashboard).
- Resolve the two open decisions below before/while specing this phase, since they affect the payment math and receipt content: total rounding, and whether to show a change bill/coin breakdown.
- Explicitly deferred to Phase 5: the sync queue / sync-status field and any upload logic — Phase 4 only writes the row locally with its UUID and `isTraining` already in place.

## Phase 5 status (2026-10-24)

Spec: `docs/superpowers/specs/2026-10-24-offline-sync-design.md`; plan: `docs/superpowers/plans/2026-10-24-pos-offline-sync.md`. Built unattended: the user delegated execution while away, so the spec and plan were decided by the controller with the defaults offered earlier (write-only RLS for cashiers, WorkManager, unbounded retry with backoff, no FAILED state) and were not reviewed by the user. Every task had a Sonnet implementer and a Sonnet reviewer.

Implemented on the branch: Room v5 (`syncStatus` / `syncedAt` / `syncAttempts` / `lastSyncError` on `transactions`, `MIGRATION_4_5`, schema `5.json`, DAO queries), `TransactionSyncer` (keyset-paged, idempotent, per-cashier, stops on offline/session errors, records `offline:` / `session:` / `rejected:` without leaking URLs or tokens), `SupabaseTransactionRemoteDataSource` (one atomic `submit_transaction` RPC), `TransactionSyncWorker` + `WorkManagerSyncScheduler` (one-time REPLACE on every save and dashboard refresh, 15-minute periodic safety net, CONNECTED constraint, exponential backoff), `AuthRepository.ensureSessionForSync()` (restores a lost session without ever clearing the stored one), the sync-status pill in the catalog header (`Offline` / `N menunggu sinkron` / `Tersinkron HH:mm`), and the SQL file `supabase/migrations/20261024000000_add_transactions_sync.sql`.

Verified on the tablet emulator against the live Supabase project, which does NOT have the new tables or function (the expected state): upgrade from the Phase 4 build over its real v4 database (`user_version` 4 -> 5, all 5 sales kept, every one `PENDING`, no crash); on first launch the sync ran and recorded `rejected: ... Could not find the function public.submit_transaction` with `syncAttempts` growing, and the stored errors contain no `https://`, `Authorization`, `Bearer` or `apikey`; a new sale completed and the pill read "6 menunggu sinkron" (not "Offline"); with Wi-Fi off the pill and banner read "Offline", a sale still completed and was stored, and a row recorded `offline: HttpRequestTimeoutException`; with Wi-Fi back, tapping the banner retried and the pill returned to "N menunggu sinkron"; phone size (1080x2400): pill and "Keluar" fit, the brand title ellipsizes first. One layout bug was found here and fixed: in the wide header the brand title and a `Spacer` shared the leftover width, so "Warung Nusantara" was cut to "Warung ..." once the pill appeared.

NOT verified: any real upload (the SQL is not applied); the idempotent re-submit; "authenticated cannot read"; the "Tersinkron HH:mm" pill on a device; killing the app between save and upload; fresh install with no cache and no network (spec 8 step 5; covered only by the existing Phase 2 unit tests); Doze / battery-saver timing of the 15-minute periodic request; a physical device; TalkBack.

Update (2026-10-24, later): the owner approved applying `20261024000000_add_transactions_sync.sql`; it was applied (both tables, RLS on with one INSERT policy each, `submit_transaction` executable by `authenticated` only, 0 rows). The first real upload attempts from the emulator reached the function and were rejected with 42501 "new row violates row-level security policy for table transactions". Reproduced on the server as role `authenticated` with the cashier's JWT claims inside a rolled-back transaction: a plain INSERT passes, `INSERT ... ON CONFLICT (id) DO NOTHING` fails, because Postgres applies SELECT policies to an INSERT with ON CONFLICT and cashiers deliberately have none. The spec/plan assumption (and two reviews) missed this; only the live server showed it. The fix changes the function to `security definer` with an explicit `cashier_id = auth.uid()` check (proposed in `supabase/migrations/20261024100000_fix_submit_transaction_security_definer.sql`, NOT applied: the permission check blocked it as a security-model change and it needs the owner's decision); the alternative is a SELECT-own-rows policy. Nothing was lost: the 7 sales stay `PENDING` locally with `rejected:` errors and will upload once the server side is fixed.

Known behaviour: after the network comes back the pill can keep saying "Offline" until the next sync attempt (a sale, opening the dashboard, tapping the banner, or the 15-minute periodic run), because a row keeps its last `offline:` error until it is retried.

Owner action: apply `supabase/migrations/20261024000000_add_transactions_sync.sql` to the live project (not idempotent), then re-run spec section 8 scenarios 1, 2 and 4 against it: the pending sales upload, the pill reads "Tersinkron HH:mm", the server has one row per sale and a second `submit_transaction` with the same id adds nothing. The Phase 4 emulator test sales will upload too (test data). Known leftovers: `AuthRepositoryImpl.isLoggedIn()` clears the stored session on any failed restore including a network timeout at app start (Phase 1 bug, spec 9, track separately); the two androidTest `LoginScreenTest` lint errors.

## Phase 5 contents (Offline sync engine)

PRD: rest of 4.9.

- Sync status on each local transaction (e.g. pending / synced / failed) and a queue/worker that uploads pending, non-training rows.
- Idempotent upsert to new Supabase `transactions` + line-item tables, keyed by the local UUID so retries never duplicate.
- Connectivity-triggered + periodic retry (WorkManager fits the existing Room-based stack) when connectivity returns.
- Offline / "last synced at" indicator, extended from the catalog cache's existing cached-state handling to cover transaction sync too.
- "Can't load catalog, check connection" state for a fresh install that's never populated the cache (catalog half of 4.9, if not already covered by Phase 2's caching).
- New Supabase tables for transactions/lines: needs an owner-action migration step, same pattern as 3b's `is_featured` migration.
- No UI change to the Payment dialog itself — sync happens behind what Phase 4 already built.

## Phase 6 contents (Settings, Bluetooth receipt printing, Training Mode)

PRD: 4.10, 4.13.

- New Settings screen and its entry point from Dashboard (first screen of this kind — decide where it lives in the nav).
- Bluetooth: scan/select/pair a printer once, persist the selection, reuse it for all later prints without re-pairing.
- Formatted receipt (outlet name, date/time, lines with modifier/variant/qty/price, subtotal, tax/service charge, grand total, cash received, change) sent to the saved printer; wires the Phase 4 "Print receipt" button to a real action.
- If no printer paired yet, "Print receipt" routes to the pairing step instead of failing silently; if the paired printer is unreachable, show an error with retry that doesn't affect the already-completed transaction.
- "Mode Pelatihan" toggle in Settings: confirmation on enabling, clears the cart on disabling.
- Persistent high-contrast "MODE PELATIHAN" banner from Dashboard through Payment Success while active.
- Turn on the `isTraining` branch already built in Phase 4: when active, payment success does not persist the transaction and never enters the Phase 5 sync queue.
- Receipt printed in training mode is marked "LATIHAN – BUKAN TRANSAKSI".
- Training Mode auto-off on logout and on cold app start.

## Phase 7 contents (Transaction History)

PRD: 4.11.

- History screen (new nav entry) listing local transactions most-recent-first: date/time, grand total, payment summary.
- Detail view per transaction reusing the Phase 4 transaction/line snapshot (items, modifiers/variants, quantities, subtotal, tax, grand total, cash received, change).
- "Pending sync" badge driven by the Phase 5 sync-status field.
- Training transactions never appear here since Phase 6 already skips persisting them — no extra filtering needed beyond that invariant.
- No edit/refund/void actions (non-goal).

## Phase 8 contents (Frequently bought together)

PRD: 4.14.

- On-device co-occurrence computation over completed, non-training transactions (Phase 4/7's data): named, tunable constants for the thresholds (trigger item in ≥20 completed transactions; candidate co-occurs in ≥30% of them).
- Rank candidates by best share across the cart's trigger items; top 3, excluding items already in the cart or currently unavailable; ignore items no longer in the catalog.
- Cache computed suggestions; refresh on app start and after every 50 newly completed transactions (needs a running counter).
- Cart UI: "Sering dibeli bareng" row (1–3 cards: name, price, add button), shown only when at least one candidate qualifies, otherwise nothing (no empty state).
- Add button behaves exactly like tapping the item in the catalog (opens Menu Detail Dialog if it has required options, else adds qty 1); never changes the cart/totals until the cashier acts.
- Row can be dismissed for the current order without blocking checkout.

## Open decisions (before Phase 4)

- **Rounding:** round the grand total to the nearest Rp100 / Rp500? (affects totals, change and receipt.)
- **Change breakdown:** show a suggested bill/coin breakdown for the change on the payment screen?
- **Product data to fill:** all `menu_items.image_url` are currently null, so catalog photos need to be provided by whoever manages the catalog.

## Backlog (unscheduled ideas, not in the PRD)

Shift close-out and cash in/out log, WhatsApp / QR-review receipts, queue number and "ready" status, per-item notes and order type, one-tap add for items without required options, remember last-chosen options, abbreviation search, automatic cash-drawer open, manual discounts, parked orders, loyalty stamps by phone number, quick PIN switch between cashiers, customer-facing display, QR table self-order, barcode scanning for packaged goods. Non-cash payment (QRIS) is a PRD non-goal today.
