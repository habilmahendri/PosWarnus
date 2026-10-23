# Phase 4 — Tax, Cash Payment, Payment Success, Local Transactions: Design

Date: 2026-10-23. PRD: 4.6, 4.7, 4.8, part of 4.9. Roadmap: Phase 4.

## 1. Goal and scope

Make the cart's "Bayar" button live: show subtotal / tax / total in the cart, take a cash payment with live change, persist the completed sale locally (offline-first), and show a success state. The sale is always written to Room first; uploading is Phase 5.

In scope: tax config (Supabase table + Room cache), `OrderTotals`, cart footer, payment dialog with success state, `transactions` and `transaction_lines` in Room (DB v4), Supabase migration file for `tax_settings`.

Out of scope (later phases): sync queue / sync status / Supabase transaction tables (5), real printing and Training Mode toggle (6), history screen (7), suggestions (8). "Cetak struk" is shown disabled ("segera hadir").

## 2. Decisions (from brainstorming)

| Topic | Decision |
|---|---|
| Rounding | None. `grandTotal = subtotal + taxAmount`, all whole Rupiah. |
| Cash input | Digit-only field with thousands separators (max 9 digits) plus quick buttons. |
| Quick buttons | "Uang pas" always. Plus up to 3 distinct amounts: `ceil(total, d)` for `d` in {5.000, 10.000, 50.000, 100.000}, keeping only values strictly greater than the total, ascending. Example: total 43.000 -> 45.000, 50.000, 100.000. |
| Change breakdown | Not shown; change is a single number. |
| Tax | One row in `tax_settings`: label "Pajak", percent 10 (owner can edit in Supabase). |
| UI shape | Dialog over the Dashboard with two states (Payment -> Success), not a new route. |
| Unavailable lines | Do not block payment; the cart row already warns. |

## 3. Data

### 3.1 Supabase (owner applies manually, like earlier migrations)

`supabase/migrations/20261023200000_add_tax_settings.sql`:

- `public.tax_settings(id int primary key default 1 check (id = 1), label text not null default 'Pajak', percent numeric(5,2) not null check (percent >= 0 and percent <= 100))`, one row `(1, 'Pajak', 10)`.
- RLS on; `select` for `authenticated` only; no write policy for the app.
- Not idempotent (documented in a header comment, like the earlier migrations). Not executed by the app or by this work; applying it to the live project needs the owner's go-ahead.

### 3.2 Room v4 (`MIGRATION_3_4`, schema JSON exported)

- `tax_settings(id INTEGER PK, label TEXT NOT NULL, basisPoints INTEGER NOT NULL)`: `basisPoints = round(percent * 100)` (10 -> 1000). Single row, replaced on refresh. Absent row = never cached.
- `transactions(id TEXT PK /*UUID*/, createdAt INTEGER /*epoch ms*/, cashierId TEXT NOT NULL, subtotal INTEGER, taxLabel TEXT, taxBasisPoints INTEGER, taxAmount INTEGER, grandTotal INTEGER, cashReceived INTEGER, changeAmount INTEGER, isTraining INTEGER NOT NULL DEFAULT 0)`.
- `transaction_lines(id TEXT PK, transactionId TEXT NOT NULL FK -> transactions ON DELETE CASCADE, menuItemId TEXT, name TEXT, basePrice INTEGER, quantity INTEGER, selectionsJson TEXT NOT NULL)` with an index on `transactionId`. `selectionsJson` is the serialized `List<SelectedOption>` snapshot (kotlinx.serialization, which supabase-kt already brings in; it must round-trip in a test).
- `isTraining` is always `false` in this phase; Phase 6 branches on it.

### 3.3 Tax repository

`TaxRepository` (interface + impl, Hilt-provided like `CatalogRepository`):
- `taxConfig: Flow<TaxConfig?>` from Room (`TaxConfig(label, basisPoints)`); `null` = never cached.
- `refresh()` fetches the row from Supabase and replaces the cached row. Failure (network, no session) leaves the cache untouched and is swallowed with a log, never thrown to the UI.
- Called alongside the catalog refresh that already runs after login/dashboard load.

## 4. Calculation

`OrderTotals.of(subtotal: Long, tax: TaxConfig?)`:
- `tax == null` -> label "Pajak", 0 bps, `taxAmount = 0`, flag `taxMissing = true` (drives the small warning).
- `taxAmount = (subtotal * bps + 5000) / 10000` (half up, Long arithmetic only).
- `grandTotal = subtotal + taxAmount`.
- Display label: `"$label ${percent}%"` where percent drops a trailing `.0` (10, 11, 7.5).

`CashQuickAmounts.of(total: Long): List<Long>` implements the rule in section 2.

Payment validity: `cashReceived >= grandTotal` and `grandTotal > 0`. `change = cashReceived - grandTotal`.

## 5. UI behaviour

### 5.1 Cart (tablet panel and phone sheet / bottom bar)
- Footer: Subtotal, tax line ("Pajak 10%" + amount), Total (emphasized). Phone bottom bar shows the grand total.
- "Bayar" enabled when the cart is non-empty. If `taxMissing`, a small non-blocking line "Pajak belum dimuat" appears under the tax row.

### 5.2 Payment dialog
Opened by "Bayar". On open it freezes: `OrderTotals`, a new transaction UUID, and the cart lines. Later tax refreshes or cart changes do not alter an open dialog.
- Shows total, cash field, quick buttons, live change (or "Uang kurang Rp X" while short).
- "Konfirmasi" disabled while cash < total. A tap sets an in-flight guard so a double tap writes once.
- On success: transitions to Success in the same dialog. On failure: inline error "Gagal menyimpan transaksi. Coba lagi.", entered cash kept, retry reuses the same UUID (write is `insert` with `OnConflictStrategy.IGNORE` semantics, so a retry after a half-seen success cannot duplicate).
- Dismiss (back / outside tap) while in Payment state closes the dialog and keeps the cart.

### 5.3 Success state
- Large total, cash received, change; "Cetak struk" disabled ("segera hadir"); "Selesai".
- Back and outside tap are ignored. "Selesai" clears the cart (`CartStore.clear`) and closes the dialog.
- Logout while the dialog is open closes it and clears the cart (existing logout behaviour); a transaction already written stays in Room.

## 6. Components

- `data/tax/`: `TaxConfig`, `TaxRepository`, remote DTO + data source, Room entity/DAO.
- `data/transaction/`: `TransactionRepository.save(snapshot, cashReceived): Result`, entities, DAO with an atomic `@Transaction` insert, JSON snapshot codec.
- `ui/cart/OrderTotals.kt`, `CashQuickAmounts.kt` (pure, unit-tested).
- `ui/payment/PaymentDialog.kt` + `PaymentViewModel` (state machine: Idle -> Paying(error?) -> Success), scoped like `CartViewModel` (dashboard back-stack entry). `CartPanel` footer and the phone bar gain the totals; `DashboardScreen` hosts the dialog.
- `di/`: modules for the tax repository and transaction repository; migration registered in `DatabaseModule`.

## 7. Testing

- Unit: `OrderTotals` (0 subtotal, 0 bps, half-up cases such as 1 bps rounding, 10%, 7.5%, missing config), `CashQuickAmounts` (43.000, exact multiples, > 100.000, 0), cash validation and change, `PaymentViewModel` (confirm disabled when short, double tap writes once, failure keeps cash and retry reuses the UUID, success clears nothing until Selesai).
- Room/Robolectric: migration 3->4 keeps existing data and creates the new tables; `save` writes header + lines atomically (a failing line insert leaves no header); selections JSON round-trips.
- `TaxRepository`: refresh stores bps, failed refresh keeps the previous cache, never-cached emits null.
- Emulator (tablet + phone): add items -> Bayar -> quick amount -> Konfirmasi -> Selesai; airplane mode sale; short cash keeps Konfirmasi disabled; total matches subtotal + 10%.

## 8. Risks and notes

- Live Supabase has no `tax_settings` until the owner applies the migration; the app must work (0% + warning) before that.
- Two successful confirms with different UUIDs would be two sales; the in-flight guard and one UUID per dialog open prevent accidental duplicates.
