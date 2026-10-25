# PRD: POS WarNus — Cashier Order App

## 1. Summary

POS WarNus is an Android cashier (point-of-sale) app for a single outlet ("WarNus"). A cashier logs in, browses a product catalog pulled from Supabase, builds an order in a cart, applies modifiers/variants per item, checks out with cash payment (with change calculation), and optionally prints a receipt to a paired Bluetooth thermal printer. A separate admin website (different project, out of scope here) manages catalog, categories, stock, and tax/service-charge configuration in the same Supabase backend.

## 2. Goals

- Let a cashier complete a full order → payment cycle quickly on either phone or tablet.
- Keep the catalog, stock, and transaction history as the single source of truth in Supabase, shared with the future admin website.
- Support cash-only payment with correct, validated change calculation.
- Work reliably even with intermittent connectivity (offline-first with sync).

## 3. Users

- **Primary user: Cashier.** The only role in this app (see Non-Goals). Authenticates via Supabase Auth before using the app.
- **Indirect user: Admin**, via the separate admin website — manages menu items, categories, stock flags, and tax/service-charge percentage. Not part of this app's scope, but this PRD's data assumptions must stay compatible with it.

## 4. Scope / Features

### 4.1 Splashscreen

Branding screen shown on app cold start, fixed short duration (~1–2s), no data loading or session check during this screen.

**Acceptance criteria**
- Splash is shown for a fixed duration on cold start, then always navigates to Login.
- Splash does not fetch catalog or check session state — that happens after navigating past splash.
- Splash is not shown again on configuration change (e.g., rotation) within the same app session.

### 4.2 Login (Supabase Auth)

Cashier signs in before reaching the dashboard. No self-registration in this app (accounts are provisioned via the admin website/Supabase directly).

**Acceptance criteria**
- Login screen collects credentials and authenticates against Supabase Auth.
- On success, navigates to Dashboard; session persists across app restarts until explicit logout or token invalidation.
- On failure, shows an inline error message; does not navigate.
- A logged-in cashier relaunching the app skips Login and goes straight to Dashboard (valid session).
- Logout is reachable from the Dashboard and returns to Login, clearing local session state.

### 4.3 Dashboard — Catalog Browsing

Main screen, responsive for phone (portrait) and tablet (landscape):
- Tablet/landscape: catalog (left/center) and selected-items cart (right) shown side by side.
- Phone/portrait: catalog is the primary view; cart is reachable as a bottom sheet or separate tab, since side-by-side doesn't fit.

Catalog shows each menu item as a card with **image, name, price**. Items can be filtered by **category** (dynamic chips/tabs sourced from Supabase, including an "All" option) and by **search** (matches menu name only, case-insensitive substring).

**Acceptance criteria**
- Catalog items load from Supabase and render image, name, and price per item.
- Category chips/tabs are generated from Supabase category data, not hardcoded; selecting one filters the catalog to that category; an "All" option shows everything.
- Typing in the search field filters the currently visible catalog (combined with the active category filter) by name match.
- A menu item flagged out-of-stock (from Supabase) is visually distinguished (e.g., dimmed/badge) and cannot be added to the cart.
- Empty search/category result shows an explicit empty state, not a blank screen.
- On phone layout, the cart is reachable from the Dashboard without navigating away from the catalog (bottom sheet/tab), and shows the current item count/total as a persistent indicator.
- Admin-featured items are surfaced in a dedicated row above the grid (see 4.12).

### 4.4 Menu Detail Dialog (Modifier, Variant, Quantity)

Tapping a catalog item (or a cart item to edit) opens a dialog with the item's **modifier** and **variant** options and an adjustable **quantity**. Each menu item defines its own modifier/variant configuration in Supabase (per-item config: which groups apply, single vs. multi-select, required vs. optional, and per-option price delta) — the UI renders whatever that item's config says rather than assuming a fixed shape.

**Acceptance criteria**
- Dialog reads modifier/variant group definitions for the specific item from Supabase and renders each group as single-select or multi-select per its config.
- A group marked required must have a selection before the "Add to cart" / "Save" action is enabled.
- Quantity has a stepper (+/-) defaulting to 1, with a minimum of 1 (decrementing below 1 removes intent to add, i.e. disables further decrement rather than going to 0).
- The dialog shows a live computed line total (base price + selected modifier price deltas) × quantity.
- Confirming the dialog with an out-of-stock item is not possible (dialog cannot be opened for out-of-stock items, see 4.3).
- Re-opening the dialog from an existing cart line pre-fills the previously selected modifiers/variant/quantity, and saving updates that cart line in place rather than adding a duplicate.

### 4.5 Selected Items / Cart Section

The right-side (tablet) or bottom-sheet (phone) panel listing items currently in the order.

**Acceptance criteria**
- Each cart line shows name, selected variant/modifiers summary, quantity, and line total.
- Quantity can be adjusted directly in the cart via +/- without reopening the detail dialog; reaching quantity 0 via decrement removes the line.
- Each cart line has an explicit delete action to remove it regardless of quantity.
- Tapping a cart line (outside the quantity stepper/delete control) reopens the Menu Detail Dialog pre-filled for editing modifier/variant (4.4).
- A "Clear all" action empties the entire cart after a confirmation step (destructive action).
- The cart shows a running subtotal, tax/service charge (see 4.7), and grand total, updating live as lines change.
- The cart's checkout action is disabled when the cart is empty.

### 4.6 Tax / Service Charge

A single fixed percentage, sourced from Supabase (settings/config table), applied to the order subtotal.

**Acceptance criteria**
- The percentage value is fetched from Supabase, not hardcoded, and cached for use within the current session.
- Cart/checkout totals display subtotal, the tax/service charge line (showing the percentage and computed amount), and grand total.
- If the config fetch fails, the app falls back to the last successfully cached percentage (consistent with offline-first behavior in 4.9); if none cached yet, defaults to 0% and surfaces a non-blocking warning.

### 4.7 Payment Dialog (Cash Only)

Triggered from the cart's checkout action. Single payment method: cash.

**Acceptance criteria**
- Dialog shows the grand total and an input for "cash received."
- Change is computed live as `cash received − grand total` and displayed.
- The confirm/pay action is disabled while cash received < grand total (per decided edge-case handling — no negative-change confirmations allowed).
- Confirming payment creates a transaction record (order lines, modifiers/variants, quantities, subtotal, tax amount, grand total, cash received, change, timestamp, cashier id) and persists it (locally first, then synced to Supabase per 4.9).
- On successful persistence, the Payment Dialog transitions to the Success state (4.8); on persistence failure, an error is shown and the cashier can retry without losing entered values.

### 4.8 Payment Success

Confirmation state after a successful transaction.

**Acceptance criteria**
- Shows grand total, cash received, and change due, clearly, as the primary confirmation content.
- Offers a "Print receipt" action (4.10) and a "Done" action.
- "Done" (or auto-dismiss, if designed that way) clears the cart and returns to the Dashboard, ready for the next order.
- Declining to print does not block completing the order — printing is optional per transaction.

### 4.9 Offline-First with Sync

The app must remain usable for order-taking and payment when connectivity is intermittent, syncing to Supabase in the background.

**Acceptance criteria**
- Catalog, categories, stock flags, and tax config are cached locally after the first successful fetch and served from cache when offline, with a visible "offline"/"last synced at" indicator when not live.
- A completed cash transaction (4.7) is written to local storage immediately and queued for sync; the cashier is never blocked from completing a sale solely due to lack of connectivity.
- Queued transactions sync to Supabase automatically when connectivity returns, without duplicate submission on retry (idempotent sync, e.g. via a locally generated transaction UUID).
- If the local cache has never been populated (fresh install, first launch offline), the app shows an explicit "can't load catalog, check connection" state instead of an empty catalog.
- Stock/catalog/tax edits made on the admin website while this device is offline are not guaranteed to be reflected until the next successful sync — this staleness window is expected and acceptable, not treated as a bug.

### 4.10 Receipt Printing (Bluetooth Thermal Printer)

**Acceptance criteria**
- A Settings screen lets the cashier scan, select, and pair a Bluetooth thermal printer once; the selection is persisted locally and reused for all subsequent prints without re-pairing.
- "Print receipt" (from 4.8) sends a formatted receipt (outlet name, date/time, line items with modifiers/variant/qty/price, subtotal, tax/service charge, grand total, cash received, change) to the saved printer.
- If no printer is paired yet, "Print receipt" routes the cashier to the printer-selection step instead of failing silently.
- If the paired printer is unreachable/off at print time, an error is shown with a retry option; this does not affect the already-completed transaction record.

### 4.11 Transaction History (In-App)

A simple screen, separate from the admin website, listing transactions made from this device/session.

**Acceptance criteria**
- Lists transactions (most recent first) with date/time, grand total, and payment summary.
- Tapping a transaction shows its line-item detail (items, modifiers/variants, quantities, subtotal, tax, grand total, cash received, change).
- Includes transactions still pending sync (per 4.9), visually marked as "pending sync" until confirmed synced.
- Does not include editing, refund, or void actions (see Non-Goals).

### 4.12 Featured Menu (Admin-Curated Recommendations)

A lightweight recommendation feature to nudge sales toward the menu items the outlet wants to push. The admin marks items as featured on the admin website; this app only reads the flag and surfaces those items prominently in the catalog. No sales-history analysis is involved (data-driven recommendations such as "frequently bought together" are a possible later enhancement and out of scope here).

**Acceptance criteria**
- Each menu item in Supabase has a boolean `is_featured` flag (default `false`), set only by the admin website; this app never writes it.
- The Dashboard catalog (4.3) shows a "Menu Unggulan" row above the main grid, listing all available featured items; the row is hidden entirely when no featured items exist.
- Featured items still appear in the main grid under their normal category; the row is an additional shortcut, not a move.
- Tapping a featured item behaves exactly like tapping it in the main grid (opens the Menu Detail Dialog, 4.4).
- Featured items follow the same availability rules as the rest of the catalog: an out-of-stock featured item is excluded from the row (or dimmed and not tappable) and cannot be added to the cart.
- The row respects the active search query; while a search is active it is hidden, so results come only from the main grid. Selecting a category does not hide the row.
- The `is_featured` flag is cached locally with the rest of the catalog (4.9) and served from cache when offline.

### 4.13 Training Mode

A practice mode that lets a new cashier (or someone giving a demo) run the entire ordering flow end to end without creating real transactions. Catalog, categories and modifiers/variants are the live data, so practice matches what the cashier will actually see; only the outcome is discarded.

**Acceptance criteria**
- Settings has a "Mode Pelatihan" toggle. Turning it off clears the current cart.
- While active, a persistent, high-contrast banner ("MODE PELATIHAN – transaksi tidak disimpan") is visible on every screen from Dashboard through Payment Success, so it cannot be mistaken for a real sale.
- The whole flow works exactly as in normal mode: browse, Menu Detail Dialog (4.4), cart (4.5), tax/service charge (4.6), cash payment with change (4.7) and the success state (4.8).
- Completing a payment in training mode does NOT write a transaction record, does NOT enter the sync queue (4.9), and never reaches Supabase. Training sales never appear in Transaction History (4.11) or in any end-of-shift totals.
- A receipt printed in training mode (4.10) is clearly marked "LATIHAN – BUKAN TRANSAKSI" so it cannot be confused with a real receipt.
- Training mode is switched off automatically on logout and on a cold app start, so a cashier never serves a real customer in it by accident.
- Training history is not kept: there is no way to review past practice runs (see Non-Goals).
- The training flag is part of the in-memory/transaction model from the start of Phase 4, so the payment and sync layers can skip persistence without a later retrofit.

**Owner-PIN gate (fraud control)**

Because a training sale is never recorded anywhere, a dishonest cashier could otherwise switch it on, ring up a real paying customer while it's active, and pocket the cash with no trace. To close that hole:

- Turning the toggle ON does not activate training mode immediately: it opens a PIN dialog asking for a separate owner PIN (distinct from the cashier's own login). Turning it OFF is immediate, no PIN required.
- The PIN is never stored on the device or checked client-side: entry is verified against a hashed PIN held in Supabase via a server-side (SECURITY DEFINER) RPC, the same pattern as `submit_transaction` (4.9). The app never sees the hash.
- Verifying the PIN requires network. If the device is offline, the dialog shows "Perlu koneksi untuk verifikasi PIN" and training mode stays off — there is no offline fallback or cached PIN.
- A wrong PIN shows "PIN salah" and training mode stays off. (Rate-limiting/lockout after repeated wrong attempts is out of scope for this pass — see Non-Goals.)
- The owner sets or changes the PIN directly in Supabase (SQL), not from inside the app.
- Every transition (`enable` and `disable`) is written to an audit log in Supabase with the cashier id and timestamp, so the owner can review who turned training mode on and when even after the fact. Cashiers can only insert their own audit rows — they cannot read or alter the log or the PIN table.

### 4.14 Frequently Bought Together Suggestions

A small, ignorable suggestion in the cart that nudges the cashier toward items customers usually order together with what is already in the cart (e.g. "Es Teh Manis" with "Nasi Goreng Spesial"). It is computed on the device from this device's own completed transactions; no server, admin input or machine learning is involved. It depends on stored transactions, so it ships after payment and local transaction storage (4.7, 4.9).

**Acceptance criteria**
- The cart (4.5) shows a "Sering dibeli bareng" row with 1–3 suggestion cards (name, price, add button) below the line items, only when at least one suggestion qualifies; otherwise nothing is shown.
- A suggestion is derived from how often two menu items appear in the same completed transaction: for a trigger item already in the cart, a candidate qualifies when the trigger appears in at least 20 completed transactions and the candidate appears alongside it in at least 30% of them. Both thresholds are named constants so they can be tuned without redesign.
- Candidates are ranked by that share (highest first); the top 3 that are not already in the cart and are currently available are shown. With several items in the cart, a candidate's rank is its best share over the cart's trigger items.
- Tapping a suggestion's add button behaves exactly like tapping that item in the catalog (opens the Menu Detail Dialog (4.4) when the item has required options, otherwise adds it with quantity 1). It never changes the cart on its own and never changes totals until the cashier acts.
- The row can be dismissed for the current order and does not block checkout or any other control.
- Only completed, non-training transactions on this device are counted (training sales, 4.13, are excluded). Items that no longer exist in the catalog are ignored.
- Suggestions are computed from local data and cached; they work offline, and are refreshed when the app starts and after every 50 newly completed transactions.
- With too little history (no trigger item meets the thresholds) the feature stays silent, with no placeholder or empty state.
- The suggestion row appears in the cart only, not inside the Menu Detail Dialog.

## 5. Non-Goals

Explicitly out of scope for this PRD:

- **Multi-outlet / multi-branch support.** One app instance serves exactly one outlet; no outlet switching.
- **Refund / void of completed transactions.** No cancellation or reversal flow from this app.
- **Roles/permissions beyond "cashier."** Every authenticated user is treated identically; no manager/admin-only features inside this app. (The Training Mode owner PIN in 4.13 is a single shared secret checked server-side, not a user role or account — it does not add a second login tier.)
- **Catalog/category/stock/tax management UI.** Entirely owned by the separate admin website project; this app only reads that data (and writes transactions).
- **Non-cash payment methods** (card, QR/e-wallet, split payment) — cash only for this PRD.
- **Cross-device/cross-outlet sales reporting or analytics** — the in-app history (4.11) is local-to-device and transaction-level only; aggregate reporting lives on the admin website.
- **Self-registration / account management UI** inside this app (sign-up, password reset flows) — accounts are provisioned externally.
- **Training-run history or scoring.** Training mode (4.13) discards its sales; reviewing or grading past practice runs is out of scope.
- **Owner-PIN management UI, lockout, and real-time alerting.** The Training Mode owner PIN (4.13) is set/changed directly in Supabase, not from inside the app; there is no rate-limit/lockout after repeated wrong attempts and no push/SMS alert when training mode is switched on — the Supabase audit log is reviewed manually by the owner.
- **Machine-learning, seasonal or cross-device recommendation models, and admin-curated suggestion pairs.** Suggestions (4.14) are a simple per-device co-occurrence count; an admin-managed fallback for the cold-start period is a possible later enhancement, not part of this PRD.

## 6. Key Risks / Trade-offs Called Out

- **Offline-first with sync is the single biggest driver of implementation complexity** in this PRD (local persistence layer, conflict/staleness handling, idempotent sync, cache invalidation for catalog/stock/tax). It was chosen deliberately over a simpler "cache + block checkout when offline" model to guarantee cashiers can always take orders. Implementation planning should treat the local-first data layer as its own foundational piece, built and tested before the UI features that depend on it (catalog display, cart, payment).
- **Per-item modifier/variant configuration** (rather than one fixed modifier/variant shape for all items) adds schema and UI-rendering flexibility but requires the Supabase schema (owned by the admin side) and this app's rendering logic to agree on a stable config contract up front.
- **Bluetooth thermal printing** is a hardware-integration surface with device/vendor variability; acceptance criteria above scope it to "pair once, print, handle failures gracefully," not broad printer-model compatibility guarantees.
