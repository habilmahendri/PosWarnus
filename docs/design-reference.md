# POS WarNus — Design Reference

Extracted from the "RestoPOS Tablet" design prototype (Claude Artifact `JjFZB66sa11r7k1Cbetivz`, a tablet-landscape 1280×800 HTML/CSS mockup). This is a visual/layout reference for implementing the Compose UI — not literal code to port. All Indonesian copy strings below are taken directly from the mockup and can be reused verbatim.

## 1. Design Tokens

### Colors

| Token | Hex | Role |
|---|---|---|
| Accent | `#C2410C` | Primary brand/action color (buttons, selected states, header icon bg) |
| Accent dark | `#9A3412` | Accent hover/pressed, text-on-tint emphasis |
| Accent tint | `#FDEEE4` | Selected-item background (variant chosen, payment method selected) |
| Background | `#F3F2EE` | App/page background |
| Sunk | `#EAE7DF` | Pressed-state background for secondary controls |
| Line | `#E3E0D8` | Default 1px borders/dividers |
| Line strong | `#CBC7BB` | Borders on interactive controls (inputs, outlined buttons, steppers) |
| Text | `#1C1B19` | Primary text |
| Text secondary | `#5C5951` | Secondary/muted text, labels, hints |
| Surface | `#FFFFFF` | Cards, panels, dialogs, headers |
| Success | `#15803D` | Change due, success icon/text |
| Success tint | `#E5F3E8` | Success icon circle background |
| Error | `#B91C1C` | Out-of-stock tag, validation errors |
| Error tint | `#FCEBEA` | Error input focus ring background |

Splash screen inverts this: full-bleed accent (`#C2410C`) background with white content.

### Typography

Single font family: **Inter** (400/500/600/700). Fallback stack: system-ui, -apple-system, "Segoe UI", sans-serif. All monetary/numeric values use **tabular numerals** (`fontFeatureSettings = "tnum"` in Compose, or a monospace-digit variant) so price columns align — format as `Rp25.000` (no space, thousands dot, no decimals).

| Size/Weight | Usage |
|---|---|
| 44sp / 700 | Grand total on payment screen |
| 32sp / 700 | Cart footer total |
| 28sp / 700 | Login "Masuk" heading |
| 26sp / 700 | Detail dialog line total |
| 24sp / 700 | Success heading |
| 22sp / 700 | Detail dialog item name, quantity stepper number |
| 18sp / 700 | Section headers ("Pesanan", "Pengaturan", panel titles) |
| 17sp / 700 | Login section subtitle, cart line price, success row emphasis |
| 16sp / 600 | Menu item name, cart item name, button label default |
| 15sp / 600 | Body default, option labels |
| 14sp / 500 | Hints, secondary captions |
| 13sp / 600 | Section labels (uppercase-weight, not literal uppercase), "sec" style |
| 12sp / 600 | Small tags ("Wajib" badge) |

Base body text: 15sp, line-height ≈1.35, color `text`.

### Spacing Scale

4 · 8 · 12 · 16 · 24 · 32 · 48 (dp). Most component padding uses 12/16/20/24; section gaps use 16/24.

### Radius Scale

- **6dp** — all controls: buttons, inputs, chips/tabs, stepper buttons, cash keypad keys.
- **10dp** — cards, dialogs, panels.
- Circular (50%) — icon circles, radio indicators, switch track/thumb, badges (pill shape via large radius).

### Elevation

Flat design overall — surfaces are separated by 1dp borders (`line`), not shadows. The **only** shadow in the whole system is the modal dialog scrim/elevation: `0 16px 48px rgba(28,27,25,0.28)` behind a dark `rgba(28,27,25,0.5)` scrim overlay. In Compose terms: dialogs get a real elevated `Surface`/`Dialog`; everything else is flat with `1.dp` `BorderStroke`.

### Touch Targets

Minimum 44dp for any tappable control. Primary action buttons are 56dp tall ("btn-lg").

## 2. Per-Screen Breakdown

### Splash Screen

- Full-bleed accent-colored (`#C2410C`) background, white content, centered vertically and horizontally.
- Logo: white rounded-square (7dp corner) app icon mark, 72dp, centered.
- App name "RestoPOS" below logo, 44sp/700, white, tight letter-spacing.
- Tagline below that: "Fast. Simple. Efficient.", 18sp/500, ~92% opacity.
- Below a gap: a thin (3dp height, 160dp wide) indeterminate progress bar — rounded track at 28% white opacity, a sliding white segment animating left-to-right on loop.
- Bottom-anchored caption, 32dp from bottom: "Memuat menu dan pengaturan kasir" (loading menu and cashier settings), 13sp, 85% opacity.
- No session/auth logic on this screen (consistent with the PRD) — it's pure branding + a loading indicator.

### Login

Two-pane layout, full screen, no header:
- **Left pane** (480dp wide, fixed): solid accent background, white text, padding 48dp, content justified space-between top/bottom.
  - Top: small logo mark (40dp) + "RestoPOS" wordmark (22sp/700) side by side.
  - Bottom block: outlet name as H1 ("Warung Nusantara", 36sp/700, tight line-height) + subtitle "Masuk untuk memulai shift kasir." (17sp, 92% opacity).
- **Right pane** (flex-fill, centered content, background color): a 400dp-wide form column, centered.
  - Heading "Masuk" (28sp/700) + subtitle "Gunakan akun kasir yang diberikan manajer." (text-secondary).
  - Email field: labeled input (label 13sp/600 uppercase-weight style = `sec` class), type email, placeholder "nama@restoran.id". Inline error text below in error-red when invalid (e.g. "Masukkan email." / "Format email tidak valid.").
  - Password field: same style, placeholder "Masukkan kata sandi", with a show/hide eye-icon toggle button inset on the right (44dp square tap target). Inline error below (e.g. "Masukkan kata sandi.").
  - Disabled state on both fields while `loggingIn` (submit in flight).
  - Form-level error banner (not inline field error) for auth failures: a full-width red-tinted alert box, rounded 6dp, red text, e.g. "Email atau kata sandi salah. Periksa lalu coba lagi." — appears below the fields, above the submit button.
  - Submit button: full-width, 56dp tall primary button, label "Masuk" / "Memverifikasi…" with a spinner icon while loading; disabled while loading.
  - Below the button: centered helper text "Lupa kata sandi? Hubungi manajer." (no self-service reset, consistent with PRD non-goals).
- Field style everywhere: 52dp height, 1px `line-strong` border, 6dp radius, 16px horizontal padding, 16sp text; focus state = accent border + 3px accent-tint glow ring; error state = red border + red-tint glow ring.

### Dashboard (Catalog + Cart) — "POS" screen

Full-screen split layout, **no padding around the edges** (both panes flush):
- **Left pane** — catalog, fixed width 840dp at the reference tablet size (this is the primary content region; on the real responsive app this is the flexible/majority region, cart becomes the fixed-width side panel).
  - **Header bar** (56dp, white, bottom border): square app icon (32dp) + outlet name/cashier line (two-line: "Warung Nusantara" 16sp/600, "RestoPOS · {cashier name}" 13sp secondary) on the left; spacer; a 320dp search field with a leading magnifier icon and a trailing clear (×) button that appears once text is typed, placeholder "Cari menu..."; a settings gear icon-button on the far right (44dp, shows a small green dot badge top-right when a printer is paired).
  - **Category row** (just below header, horizontal row, 8dp padding): pill/tab buttons per category plus an "Semua" (All) tab, 44dp tall, 6dp radius, selected = filled accent background + white bold text, unselected = secondary text, hover = subtle sunk background.
  - **Catalog grid**: scrollable, 4-column grid (`repeat(4, 1fr)`, 12dp gap), each cell a **menu card**:
    - Card: white, 1px `line` border, 10dp radius, overflow-hidden column layout.
    - Image: 16:9 aspect ratio food photo filling card width.
    - Out-of-stock overlay: image dimmed to 40% opacity + a white pill tag top-left reading "Habis" (sold out) in red text with a light-red outline; the whole card becomes non-interactive (disabled, no press feedback).
    - In-cart badge: small circular accent badge top-right of the image showing the quantity already in cart (only when item has ≥1 in cart); card border also switches to accent-colored + 1px accent ring to show "selected"/in-cart state.
    - Below image: name (2-line clamp, 15sp/600, min-height reserved so cards align even with 1-line names) then price row: optional "dari " (from) prefix in 13sp secondary before the price, price itself 16sp/700 tabular.
    - Press feedback: slight background tint + 1.5% scale-down on press (purely a hover/press micro-interaction, not required in Compose but nice-to-have via a scale animation on press).
  - **Empty search state**: centered, a muted magnifier-with-slash icon, "Menu tidak ditemukan" (menu not found) heading, helper line "Coba kata kunci lain atau pilih kategori Semua." (try another keyword or pick All), and a secondary "Hapus pencarian" (clear search) button.
- **Right pane / cart** — fixed width 440dp, white background, left border divider.
  - **Header** (56dp): "Pesanan" (Order) title 18sp/700 + an order-number chip (muted pill, e.g. "#..."), spacer, a text-only "Kosongkan" (clear all) button that only appears when the cart has items.
  - **Empty cart state**: centered receipt/bag icon, "Belum ada pesanan" (no order yet) heading, helper "Pilih menu di sebelah kiri untuk mulai menambahkan item." (pick a menu on the left to start adding items).
  - **Cart line rows**: each row has gap 12dp, padding 12/20dp, bottom-border divider. Left column: item name (16sp/600, wraps), optional modifier/variant summary line below in secondary hint text (e.g. "Large · Extra Cheese"), then a smaller line showing unit price. Right column (right-aligned): line subtotal (17sp/700 tabular) on top, then a horizontal control row below: a danger-colored trash icon-button (removes the line outright) + a decrement stepper button (44dp square, disabled at qty 1 — going below removes the line per cart semantics) + the quantity number (centered, 36dp wide) + an increment stepper button.
  - **Footer** (fixed, top border): Subtotal row, item-count row (both secondary 15sp text, value right-aligned tabular), then a divider, then **Total** row (16sp/600 label, 32sp/700 big tabular value), then a full-width 56dp primary "Bayar" (Pay) button — disabled when cart is empty.

### Menu Detail Dialog (Modifier, Variant, Quantity)

A centered modal dialog over a 50%-opacity dark scrim, dismissible by tapping the scrim. Dialog itself: 840×660dp, white, 10dp radius, drop shadow (`0 16px 48px rgba(28,27,25,.28)`), two-column body + fixed footer.

- **Left column** (296dp fixed, light `#FAF9F6` background, right border divider):
  - 4:3 food image at the top, full-bleed.
  - Below: item name (22sp/700), short description (15sp secondary, relaxed line-height), and a base-price line ("mulai dari" style label + bold price).
- **Right column** (flex-fill):
  - Small header bar (56dp) with "Atur pesanan" (configure order) label and a close (×) icon-button.
  - Scrollable body with per-item-config sections, each matching this plan's PRD requirement of "per-item modifier/variant config from Supabase":
    - **Variant group** (e.g. "Ukuran"/Size) — shown only if the item has one: section label + a red "Wajib" (required) tag + "Pilih 1" (pick one) hint. Options rendered as side-by-side **variant cards** (not a radio list): each a rounded-6dp box showing the option name (16sp/600), its absolute price (15sp), and a delta hint ("+Rp10.000" or "Dipilih"/selected when it's the current pick). Selected state = accent-tint background + 2px accent inset border.
    - **Modifier groups** (0-N, each independently required/optional, single/multi-select per the item's own config) — section label + a tag showing "Wajib"/"Opsional" + a hint like "Pilih 1" or "Pilih beberapa". Options render as a **vertical option list** below a top divider, each row 52dp min-height: a checkbox-style (rounded-6dp box with checkmark) or radio-style (circle with filled dot) indicator depending on single/multi, the option label (bold when selected), and its price delta right-aligned in secondary text. Checkbox = multi-select group, radio = single-select group — this directly matches the PRD's "variant = single-select, modifier = multi-select, per-item configurable" requirement.
  - **Footer bar** (84dp, top border): a quantity stepper (48dp buttons, decrement disabled at minimum 1) on the left; center-right a stacked hint line (e.g. required-selection warning in accent/red text when a required group is unmet) above a large live-computed line total (26sp/700); then "Batal" (cancel, secondary button) and "Tambah ke Pesanan" (add to order, primary button, 56dp) — the Add button is **disabled** while any required group is unsatisfied, matching the PRD's "required group blocks Add to cart" rule.

### Payment Dialog (Cash)

Full-screen (not a modal — a dedicated screen/route in this mockup), 3-zone layout:
- **Header** (56dp, white): logo + "Pembayaran" (Payment) title + order-number chip.
- **Left panel** (400dp fixed, white, right border): "Ringkasan pesanan" (order summary) + item count; scrollable line-item list (name, modifier/variant summary, "qty × unit price", line subtotal); footer block with Subtotal and bold Total.
- **Center/right area** (flex, padded 24dp, two columns):
  - **Left column** (flex-fill):
    - "Metode pembayaran" (payment method) section — a single selectable row showing a cash icon, "Tunai" (Cash) label, and a checked radio indicator; styled as always-selected/accent-tinted since cash is the only method (per PRD, cash-only — no need to build a real selector, just show it as the fixed, visibly-selected option, consistent with "Non-cash payment methods" being a PRD non-goal).
    - "Total tagihan" (total due) — label + a very large 44sp/700 total amount.
    - "Uang diterima" (cash received) — labeled input showing the typed amount right-aligned, 64dp tall, 30sp/700 bold tabular text, format "Rp0" placeholder. Below it: either a hint line ("Pilih nominal atau ketik jumlah uang yang diterima.") or, when insufficient, a red error line ("Uang diterima kurang {amount}" — kembalian/shortfall messaging) and a red-ringed input border.
    - "Kembalian" (change) row — label + large 32sp/700 value colored **success green** when ≥0 (the mockup always shows a computed value here, colored via a `changeColor` token that would be red/neutral when negative in a real build, though per this plan's spec the Pay button is simply disabled until sufficient, so a negative display state may not be reachable).
  - **Right column** (368dp fixed): "Nominal cepat" (quick amounts) — a 2-column grid of quick-cash preset buttons (56dp tall, rounded-6dp, selected state = accent-tint + 2px accent border), one of which is flagged "Uang pas" (exact change) with a small caption; below that a 3-column numeric keypad (12 keys: 0-9, a backspace/clear key with a left-arrow icon, presumably a decimal/000 key) for manual cash entry, each key 56dp.
- **Footer** (88dp, top border): "Kembali" (back) secondary button with a back-arrow icon on the left; a wide (320dp min) primary "Bayar"/"Konfirmasi Pembayaran" button on the right showing a spinner + "Memproses…" label while processing — both disabled appropriately (back disabled while processing; confirm disabled while cash is insufficient).

### Payment Success

Full-screen, centered content:
- Header: logo + outlet name only (56dp, white, border-bottom).
- Centered white card (520dp wide, 1px border, 10dp radius, generous padding):
  - Top row: a 56dp circular green-tinted icon with an animated checkmark draw-in, next to "Pembayaran Berhasil!" (Payment Successful!) heading (24sp/700) + subtitle "Transaksi berhasil diselesaikan." (transaction completed successfully).
  - A key-value list below a divider: Nomor pesanan (order #), Metode pembayaran (= Tunai), Total dibayar (total paid), Uang diterima (cash received), **Kembalian** (change — emphasized row, 24sp/700, green), Waktu transaksi (timestamp). Each row is a baseline-aligned label/value pair with a bottom divider (`row-kv`, min-height 44dp, last row no divider).
  - Two full-width-split action buttons: "Lihat Ringkasan" (view summary, secondary, flex 1) and "Pesanan Baru" (new order, primary, flex 1.3 — i.e. visually slightly wider/more prominent) — "Pesanan Baru" is the action that returns to Dashboard per this plan's PRD (clears cart, back to catalog).
  - "Lihat Ringkasan" opens a **Summary dialog** (modal, 560dp wide): header with transaction number, close button; scrollable line-item list identical in style to the payment summary list; footer recap (item count, total, cash received, change, time) + a single full-width "Tutup" (close) primary button.

### Settings / Printer Pairing (Phase 6 reference — not in this plan's current scope, but captured for later)

Full-screen with a back-arrow header (not a modal), two-column body:
- **Left panel (flex-fill)**: "Printer Bluetooth" section — current/paired printer shown as a row with a printer icon (colored by connection state), name, a colored status dot + status text, and (once connected) a "Tes" (test print) button + a text-only "Putuskan" (disconnect) link. Below: a "Perangkat di sekitar" (nearby devices) row with a "Cari Printer" (scan) button (icon + spinner while scanning). Scan states: idle (empty-state prompt), scanning (centered spinner + "Mencari perangkat…"), and a results list of discovered devices — each a row with device icon, name, signal/type subtitle, an error line if pairing failed, a "Terhubung" (connected) pill tag if already connected, or a connect button (with its own per-row spinner while connecting).
- **Right panel (380dp fixed)**: "Pengaturan struk" (receipt settings) — paper width segmented control (2 options, e.g. 58mm/80mm) styled as selectable pill segments; an "Cetak otomatis" (auto-print) toggle row with a label/description and an iOS-style switch control.

## 3. Compose / Material3 Implementation Notes

- **No direct Material3 component for the variant-card selector** (the side-by-side "Regular/Large" boxes in the detail dialog) — build as a custom `Row` of selectable `Surface`/`Box` composables with manual selected-state styling (accent-tint background + border), not `FilterChip`/`RadioButton`.
- **Modifier option rows** map reasonably well to a custom row using `Checkbox`/`RadioButton`-equivalent custom indicators (the mockup's checkbox/radio indicators are custom-styled squares/circles, not default Material ones) — a `Checkbox`/`RadioButton` with heavy color/shape overrides, or fully custom icon-based indicators, both work; prefer matching the visual (6dp-radius square vs. perfect circle, filled accent when on) over using stock Material shapes as-is.
- **Category tabs** are visually closer to `FilterChip`/segmented buttons than Material3's `TabRow` (no underline indicator, filled-pill selected state) — build with a `Row` of custom toggle buttons or `FilterChip`s with accent colors, not `TabRow`.
- **Cash quick-amount grid + numeric keypad** has no Material3 equivalent — fully custom `Button`/`Surface` grid.
- **The only modal surface** (menu detail dialog, summary dialog) should use Compose's `Dialog`/`AlertDialog` base (for scrim + elevation) with fully custom content, sized explicitly rather than using default AlertDialog padding/button conventions.
- **Cards** (`Card` composable with `border = BorderStroke(1.dp, lineColor)`, `shape = RoundedCornerShape(10.dp)`, no elevation/shadow — set `elevation = CardDefaults.cardElevation(0.dp)`) match the flat, border-only aesthetic.
- **Buttons**: primary = filled `Button` with accent container color, 6dp shape, 56dp height for `btn-lg` / 48dp default; secondary = `OutlinedButton` with `line-strong` border; disabled styling (sunk background, muted text) should be set explicitly via `ButtonDefaults.buttonColors(disabledContainerColor = ...)` since Material3 defaults won't match this palette.
- **Tabular numerals** for all money/quantity text: apply `TextStyle(fontFeatureSettings = "tnum")` or use a `platformTextStyle` ensuring fixed-width digits — needed everywhere a price or quantity is shown so columns align.
- **The switch control** (auto-print toggle) maps directly to Material3 `Switch` with custom track/thumb colors to match the mockup's accent-colored "on" track.
- Overall theme: a custom Material3 `ColorScheme` built from the token table above (not a stock `dynamicColorScheme`), with `Shapes` set to 6dp (small/controls) and 10dp (medium/cards-dialogs) corner radii, and `Typography` built entirely around Inter (bundle as a Compose downloadable/embedded font — Inter isn't a system font on Android) at the sizes listed in §1.
