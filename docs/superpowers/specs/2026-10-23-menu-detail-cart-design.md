# Phase 3 Design: Menu Detail Dialog + Cart

Status: design spec, pending review. Implementation plan comes after approval.
Scope: PRD [4.4 Menu Detail Dialog](../../PRD-pos.md) and [4.5 Selected Items / Cart](../../PRD-pos.md). Visuals: [design-reference.md](../../design-reference.md) ("Menu Detail Dialog", "Dashboard" cart pane). Where the design reference and the PRD conflict, the PRD wins (decisions called out inline).

## 1. Intent and success criteria

**Intent.** Let the cashier tap a catalog item, configure it (variant, modifiers, quantity), put it in an order, and manage that order in a cart, on both phone and tablet. The configuration options for every item come from Supabase, per item, so the admin website can change them without an app release.

**Agreed decisions (from brainstorming).**
- Option configuration uses **normalized tables** (`option_groups` + `options`), not a JSONB column.
- The cart lives **in memory only** in this phase. Persistence arrives with transactions (Phases 4-5).
- No tax/service charge and no payment in this phase.

**Success criteria.**
1. Tapping an available menu card opens the dialog; unavailable items cannot open it.
2. The dialog renders exactly the groups configured for that item: variant cards, single-select (radio) and multi-select (checkbox) modifier groups, "Wajib"/"Opsional" tags.
3. "Tambah ke Pesanan" stays disabled until every required group has a selection.
4. The dialog shows a live line total: (base price + variant delta + modifier deltas) x quantity.
5. The cart shows lines with name, variant/modifier summary, quantity, line total; supports +/-, delete, edit (tap row), and Clear all with confirmation; shows a live subtotal.
6. Editing a line pre-fills the dialog and saves in place (no duplicate line).
7. Out-of-stock options cannot be selected.
8. Tablet: cart pane next to the catalog. Phone: persistent count/total bar that opens the cart in a bottom sheet.
9. Menu cards show an in-cart quantity badge.
10. Everything works offline from the Room cache (options are cached like the rest of the catalog).

## 2. Supabase schema (Option A)

Today Supabase has only `categories` and `menu_items`; `menu_items` has no `description` (the mockup dialog shows one). This section is the migration to apply (written here, **not yet executed**). It is owned by the admin side of the project, so the admin website must be told about these tables before it edits them.

Modelling rules:
- A **variant** is a group with `kind = 'variant'`: always single-select and required, at most one per item. Its options carry `price_delta` relative to the item's base price (Regular = 0, Large = +10000). The dialog displays the absolute price (base + delta) on the variant card.
- A **modifier** group has `kind = 'modifier'`; it may be single or multi-select, required or optional.
- `price_delta` is in whole Rupiah and may not be negative in this phase (check constraint), to keep line totals monotonic and avoid sub-zero edge cases.
- Deleting an item or group cascades to its groups/options.

```sql
-- 1. Item description (nullable; the dialog hides the line when null)
alter table public.menu_items
  add column if not exists description text;

-- 2. Option groups
create table public.option_groups (
  id           uuid primary key default gen_random_uuid(),
  menu_item_id uuid not null references public.menu_items(id) on delete cascade,
  name         text not null,
  kind         text not null check (kind in ('variant', 'modifier')),
  selection    text not null check (selection in ('single', 'multi')),
  is_required  boolean not null default false,
  sort_order   integer not null default 0,
  created_at   timestamptz not null default now(),
  constraint variant_is_single_and_required
    check (kind <> 'variant' or (selection = 'single' and is_required))
);

create index option_groups_menu_item_id_idx on public.option_groups (menu_item_id);
create unique index option_groups_one_variant_per_item
  on public.option_groups (menu_item_id) where kind = 'variant';

-- 3. Options
create table public.options (
  id           uuid primary key default gen_random_uuid(),
  group_id     uuid not null references public.option_groups(id) on delete cascade,
  name         text not null,
  price_delta  numeric not null default 0 check (price_delta >= 0),
  sort_order   integer not null default 0,
  is_available boolean not null default true,
  created_at   timestamptz not null default now()
);

create index options_group_id_idx on public.options (group_id);

-- 4. RLS: read-only for signed-in cashiers. Writes happen only via the admin website
--    (service role / admin policies, defined on that side).
alter table public.option_groups enable row level security;
alter table public.options       enable row level security;

create policy "Authenticated read access to option_groups"
  on public.option_groups for select to authenticated using (true);
create policy "Authenticated read access to options"
  on public.options for select to authenticated using (true);
```

Note: the existing `categories`/`menu_items` policies allow `anon` as well. The new tables are deliberately stricter (`authenticated` only). The Dashboard always fetches with the cashier's session, so this works; the verification step must confirm options actually load (an unauthenticated client would silently get zero rows).

**Seed data** (matches the 9 current items; idempotence is not needed, it runs once on a fresh table):

```sql
do $$
declare gid uuid;
begin
  -- Ayam Katsu: Ukuran (variant), Tingkat Pedas (single, required), Tambahan (multi)
  insert into public.option_groups (menu_item_id, name, kind, selection, is_required, sort_order)
    select id, 'Ukuran', 'variant', 'single', true, 0 from public.menu_items where name = 'Ayam Katsu' limit 1
    returning id into gid;
  insert into public.options (group_id, name, price_delta, sort_order) values
    (gid, 'Regular', 0, 0), (gid, 'Large', 10000, 1);

  insert into public.option_groups (menu_item_id, name, kind, selection, is_required, sort_order)
    select id, 'Tingkat Pedas', 'modifier', 'single', true, 1 from public.menu_items where name = 'Ayam Katsu' limit 1
    returning id into gid;
  insert into public.options (group_id, name, price_delta, sort_order) values
    (gid, 'Tidak pedas', 0, 0), (gid, 'Sedang', 0, 1), (gid, 'Pedas', 0, 2);

  insert into public.option_groups (menu_item_id, name, kind, selection, is_required, sort_order)
    select id, 'Tambahan', 'modifier', 'multi', false, 2 from public.menu_items where name = 'Ayam Katsu' limit 1
    returning id into gid;
  insert into public.options (group_id, name, price_delta, sort_order) values
    (gid, 'Extra Keju', 5000, 0), (gid, 'Extra Telur', 4000, 1);

  -- Nasi Goreng Spesial: Tingkat Pedas (single, optional), Tambahan (multi)
  insert into public.option_groups (menu_item_id, name, kind, selection, is_required, sort_order)
    select id, 'Tingkat Pedas', 'modifier', 'single', false, 0 from public.menu_items where name = 'Nasi Goreng Spesial' limit 1
    returning id into gid;
  insert into public.options (group_id, name, price_delta, sort_order) values
    (gid, 'Sedang', 0, 0), (gid, 'Pedas', 0, 1);

  insert into public.option_groups (menu_item_id, name, kind, selection, is_required, sort_order)
    select id, 'Tambahan', 'modifier', 'multi', false, 1 from public.menu_items where name = 'Nasi Goreng Spesial' limit 1
    returning id into gid;
  insert into public.options (group_id, name, price_delta, sort_order, is_available) values
    (gid, 'Extra Telur', 4000, 0, true), (gid, 'Extra Ayam', 6000, 1, true), (gid, 'Kerupuk', 2000, 2, false);

  -- Es Teh Manis: Ukuran (variant), Gula (single, optional)
  insert into public.option_groups (menu_item_id, name, kind, selection, is_required, sort_order)
    select id, 'Ukuran', 'variant', 'single', true, 0 from public.menu_items where name = 'Es Teh Manis' limit 1
    returning id into gid;
  insert into public.options (group_id, name, price_delta, sort_order) values
    (gid, 'Regular', 0, 0), (gid, 'Large', 3000, 1);

  insert into public.option_groups (menu_item_id, name, kind, selection, is_required, sort_order)
    select id, 'Gula', 'modifier', 'single', false, 1 from public.menu_items where name = 'Es Teh Manis' limit 1
    returning id into gid;
  insert into public.options (group_id, name, price_delta, sort_order) values
    (gid, 'Normal', 0, 0), (gid, 'Less sugar', 0, 1);

  update public.menu_items set description = 'Ayam goreng tepung renyah dengan saus katsu dan nasi hangat.' where name = 'Ayam Katsu';
  update public.menu_items set description = 'Nasi goreng dengan telur, ayam suwir, dan acar.' where name = 'Nasi Goreng Spesial';
  update public.menu_items set description = 'Teh manis dingin.' where name = 'Es Teh Manis';
end $$;
```

The seed deliberately includes one unavailable option (`Kerupuk`, `is_available = false`), a required modifier group (Tingkat Pedas on Ayam Katsu), an optional single-select group, and items with no groups at all (e.g. Kopi Hitam), so every dialog state can be exercised on real data.

## 3. Data layer

### 3.1 Domain models (`data/catalog`)

```kotlin
data class MenuItem(
    val id: String, val categoryId: String, val name: String, val price: Double,
    val imageUrl: String?, val isAvailable: Boolean,
    val description: String? = null,           // new, defaulted: existing callers/tests keep compiling
)

enum class OptionKind { VARIANT, MODIFIER }
enum class SelectionMode { SINGLE, MULTI }

data class OptionGroup(
    val id: String, val menuItemId: String, val name: String,
    val kind: OptionKind, val selection: SelectionMode,
    val isRequired: Boolean, val sortOrder: Int,
    val options: List<Option>,                 // sorted by sortOrder
)
data class Option(
    val id: String, val groupId: String, val name: String,
    val priceDelta: Long, val sortOrder: Int, val isAvailable: Boolean,
)
```

Money convention for new code: **whole Rupiah as `Long`** (`price.roundToLong()` at the boundary from the existing `Double` price). Cart arithmetic never touches floating point. `formatRupiah` gains a `Long` overload.

### 3.2 Remote (`data/remote`)

- `MenuItemDto` gains `@SerialName("description") val description: String? = null` (default keeps JSON without the column decodable).
- New `OptionGroupDto(id, menu_item_id, name, kind, selection, is_required, sort_order)` and `OptionDto(id, group_id, name, price_delta, sort_order, is_available)`. `price_delta` is decoded as `Double` (Postgres `numeric`) and converted to `Long`.
- `CatalogRemoteDataSource` gains `fetchOptionGroups()` and `fetchOptions()`; the Supabase implementation uses the same flat `from("...").select().decodeList()` style as today. Two flat queries (not a nested select) keep the full-replace logic uniform.
- Unknown `kind`/`selection` strings (should be impossible given the check constraints) cause that group to be skipped during mapping, never a crash.

### 3.3 Local (`data/local`)

New entities (no foreign keys, consistent with existing tables; indices only):

```kotlin
@Entity(tableName = "option_groups", indices = [Index("menuItemId")])
data class OptionGroupEntity(@PrimaryKey val id: String, val menuItemId: String, val name: String,
    val kind: String, val selection: String, val isRequired: Boolean, val sortOrder: Int)

@Entity(tableName = "options", indices = [Index("groupId")])
data class OptionEntity(@PrimaryKey val id: String, val groupId: String, val name: String,
    val priceDelta: Long, val sortOrder: Int, val isAvailable: Boolean)
```

- `MenuItemEntity` gains `val description: String?`.
- `OptionGroupDao`: `observeForItem(menuItemId): Flow<List<OptionGroupWithOptions>>` (`@Transaction`, `@Relation(parentColumn = "id", entityColumn = "groupId")`, ordered by `sortOrder`), `upsertAll`, `clear`.
- `OptionDao`: `upsertAll`, `clear`.

**Room version 1 -> 2 with an explicit migration** (not destructive: the cache must survive an app update so a cashier opening the app offline still sees the catalog):

```sql
ALTER TABLE menu_items ADD COLUMN description TEXT;
CREATE TABLE IF NOT EXISTS option_groups (id TEXT NOT NULL, menuItemId TEXT NOT NULL, name TEXT NOT NULL,
  kind TEXT NOT NULL, selection TEXT NOT NULL, isRequired INTEGER NOT NULL, sortOrder INTEGER NOT NULL, PRIMARY KEY(id));
CREATE INDEX IF NOT EXISTS index_option_groups_menuItemId ON option_groups (menuItemId);
CREATE TABLE IF NOT EXISTS options (id TEXT NOT NULL, groupId TEXT NOT NULL, name TEXT NOT NULL,
  priceDelta INTEGER NOT NULL, sortOrder INTEGER NOT NULL, isAvailable INTEGER NOT NULL, PRIMARY KEY(id));
CREATE INDEX IF NOT EXISTS index_options_groupId ON options (groupId);
```

`DatabaseModule` registers `MIGRATION_1_2`; `AppDatabase` lists the two new entities and DAOs. After the upgrade, cached items have `description = null` and no option groups until the first successful refresh; until then a tapped item opens a dialog with only the quantity stepper. This is acceptable and self-heals on the next refresh.

### 3.4 Repository

`CatalogRepository` additions: `fun observeOptionGroups(menuItemId: String): Flow<List<OptionGroup>>`. `refresh()` now fetches categories, menu items, option groups, and options; **all four fetches must succeed** before anything is written. The write is one `runInTransaction` that clears and upserts all four tables (the full-replace contract is unchanged: no stale server-deleted rows, no partial state). Any exception leaves the cache untouched and returns `RefreshResult.Failure`, exactly as today.

## 4. Cart (in memory)

### 4.1 Model

```kotlin
data class SelectedOption(val optionId: String, val groupId: String, val name: String, val priceDelta: Long, val kind: OptionKind)

data class CartLine(
    val lineId: String,                 // UUID, identity for UI/edit/remove
    val menuItemId: String,
    val name: String,                   // snapshot
    val basePrice: Long,                // snapshot
    val selections: List<SelectedOption>, // snapshot, sorted by group sortOrder then option sortOrder
    val quantity: Int,                  // >= 1 always
) {
    val configKey: String               // menuItemId + sorted selected optionIds
    val unitPrice: Long                 // basePrice + sum(selections.priceDelta)
    val lineTotal: Long                 // unitPrice * quantity
    val summary: String                 // e.g. "Large · Extra Keju, Extra Telur" (variant first, then modifiers)
}
```

Lines hold **snapshots** so a mid-order catalog refresh can never change what is already in the cart or its totals.

### 4.2 `CartStore` (`@Singleton`, pure Kotlin, in memory)

Exposes `StateFlow<List<CartLine>>` and derived values (`subtotal: Long`, `totalQuantity: Int`, `quantityByMenuItem: Map<String, Int>`). It is a singleton rather than a screen-scoped ViewModel field so that it survives navigation and rotation and so Phase 4's payment screen reads the same cart. Process death clears it (accepted, see section 8).

Operations:
- `add(line)`: if a line with the same `configKey` exists, **merge** by adding quantities; otherwise append.
- `replace(lineId, newSelections, newQuantity)` (edit in place): updates that line; if the new `configKey` collides with a *different* line, merge into that line and drop the edited one (no duplicates, per PRD 4.4).
- `setQuantity(lineId, qty)`: `qty <= 0` removes the line (PRD 4.5 "reaching quantity 0 via decrement removes the line"). **This overrides the design reference**, which shows decrement disabled at 1 with a separate trash button; the trash button is kept too (PRD requires an explicit delete).
- `remove(lineId)`, `clear()`.
- Logout (`LogoutViewModel`) calls `clear()` so one cashier's order never leaks to the next session.

### 4.3 `OrderDraft` (pure, unit-testable)

The dialog's working state, independent of Android:

```kotlin
data class OrderDraft(val item: MenuItem, val groups: List<OptionGroup>,
                      val selected: Map<String /*groupId*/, Set<String /*optionId*/>>, val quantity: Int = 1)
```
- `toggle(groupId, optionId)`: single group -> replace selection; multi group -> add/remove. Ignores options with `isAvailable = false`.
- `missingRequiredGroups: List<OptionGroup>` (required groups with an empty selection); `canSubmit = missingRequiredGroups.isEmpty()`.
- `unitPrice`, `lineTotal` as in 4.1; `quantity` coerced to `>= 1` (decrement disabled at 1 in the dialog, per PRD 4.4).
- `toCartSelections()` builds the `SelectedOption` snapshot list.
- Required groups start **unselected** (the mockup's "required not filled" state shows Add disabled with a hint); nothing is silently preselected.

### 4.4 `CartViewModel` (`@HiltViewModel`)

Owns UI state for the dialog and the cart actions; both `DashboardScreen` panes use the same instance.
- `dialog: DialogState?` where `DialogState = New(item) | Edit(lineId)`, plus the current `OrderDraft`. Groups come from `repository.observeOptionGroups(itemId)`; the draft is (re)built when groups load.
- Actions: `onMenuItemClicked(item)` (ignored when `!item.isAvailable`), `onLineClicked(lineId)`, `toggleOption`, `incrementQuantity`/`decrementQuantity` (dialog), `confirmDialog()` (add or replace), `dismissDialog()`, `incrementLine`/`decrementLine`/`removeLine`, `requestClearAll()`/`confirmClearAll()`/`cancelClearAll()`.
- Edit pre-fill: selections are resolved **by option id against the current catalog groups**. An option that no longer exists is dropped; the required-group check then applies, so a stale line can't be saved in an invalid state.
- Tapping a cart row whose menu item is no longer in the catalog does nothing (nothing to render); if the item still exists but is now unavailable, editing is allowed (the cashier can still reduce or fix the line).

## 5. UI

Reuses the existing theme tokens and the Canvas glyph approach (no icon library). New glyphs as needed: minus, plus, trash, chevron/back are drawn in `ui/components/Glyphs.kt`; check mark for checkboxes.

### 5.1 Menu Detail Dialog (new `ui/dashboard/MenuDetailDialog.kt`)

Compose `Dialog` (`usesPlatformDefaultWidth = false`), scrim `rgba(28,27,25,0.5)`, 10dp radius, the system's only elevated surface.
- **Wide (>= 600dp available):** `min(840dp, width - 32dp)` x `min(660dp, height - 32dp)`. Left column 296dp, background `#FAF9F6`, right border: 4:3 image, name (22sp/700), description (15sp, secondary; hidden when null), base-price line. Right column: 56dp header ("Atur pesanan" + close), scrollable groups.
- **Narrow (phone):** full width, single scrolling column: 16:9 image, name, description, groups. Same footer.
- **Variant group** (if the item has one): section label + "Wajib" tag (accent tint bg, accent-dark text) + "Pilih 1" hint; options as side-by-side cards (name 16sp/600, absolute price, delta/"Dipilih"); selected = accent tint bg + 2dp accent border. Wraps to a second row when three or more options do not fit.
- **Modifier groups:** label + "Wajib"/"Opsional" tag + hint ("Pilih 1" / "Pilih beberapa"); rows 52dp min height under a top divider; checkbox (6dp rounded square) for multi, radio (circle with dot) for single; selected row label is semibold; price delta right-aligned in secondary text (`+Rp5.000`, blank when 0).
- **Unavailable option:** dimmed (40%), not selectable, small "Habis" tag in error color.
- **Footer (84dp):** quantity stepper (48dp buttons, decrement disabled at 1), a stacked hint + live total (26sp/700), "Batal" (secondary), primary button "Tambah ke Pesanan" (New) or "Simpan Perubahan" (Edit), disabled while `!canSubmit`. The hint line shows `Pilih {group names}` in `ErrorRed` while blocked, otherwise a neutral "Total".
- Tapping the scrim or the close button dismisses without changes.

### 5.2 Cart pane

Extract the cart into `CartPanel` (new `ui/dashboard/CartPanel.kt`), used by both layouts, replacing the empty placeholder `CartPane` from the styling pass (its empty state is kept when the cart is empty).
- Header: "Pesanan"; "Kosongkan" text button appears only when the cart has lines. It opens a confirmation `AlertDialog` ("Kosongkan pesanan?" / Batal, Kosongkan); the order-number chip from the mockup is **omitted** until order numbers exist (Phase 4).
- Rows (12dp gap, 12/20dp padding, bottom divider): name (16sp/600), summary line (secondary), unit price; right column: line total (17sp/700, tabular), then trash button, decrement, quantity, increment (44dp targets). Tapping the row body opens the Edit dialog.
- Footer: Subtotal, "Jumlah item", then a divider and "Total" (32sp/700). In this phase Total equals Subtotal (tax arrives in Phase 4, which will insert the tax line between them). "Bayar" is a full-width 56dp primary button, disabled when the cart is empty. When enabled it calls an `onCheckout` callback that the nav graph wires to a no-op in this phase; payment navigation is Phase 4. This is a known, temporary dead-end.
- A line whose menu item became unavailable or disappeared after a refresh shows a small error-colored note "Menu tidak tersedia lagi". Whether this blocks checkout is a Phase 4 decision (payment spec); this phase only surfaces the note.

### 5.3 Layout by width (existing 840dp breakpoint)

- **Tablet (>= 840dp):** fixed 440dp cart pane to the right of the catalog (as today).
- **Phone (< 840dp):** a persistent bottom bar over the catalog: "{N} item · {Rp total}" and a "Lihat Pesanan" button (hidden when the cart is empty), which opens a `ModalBottomSheet` hosting the same `CartPanel`. The grid's bottom content padding grows to clear the bar. The sheet closes automatically when the cart becomes empty.

### 5.4 Menu cards

`MenuItemCard` gains `inCartQuantity: Int`. When `> 0`: accent circular badge (28dp, top-right) with the quantity, and the card border becomes accent + 1dp ring (the design reference's "in cart" state). The card `onClick` calls `CartViewModel.onMenuItemClicked`. Items with no option groups still open the dialog (quantity + add); a one-tap fast path is deliberately not added.

## 6. Error handling and edge cases

| Case | Behaviour |
|---|---|
| Item `isAvailable = false` | Card not clickable (already true); `onMenuItemClicked` also guards, so the dialog can never open. |
| Required group unmet | Add/Save disabled; hint names the missing groups. |
| Option `isAvailable = false` | Visible, dimmed, "Habis" tag, cannot be toggled; never included in a draft or pre-fill. |
| Item with zero groups | Dialog shows quantity only; Add enabled immediately. |
| Group with zero available options and `isRequired = true` | Would make the item unorderable. Rule: such a group is **not enforced** (treated as optional) and shown with the options dimmed, so a data-entry mistake in the admin site cannot block sales. |
| Groups still loading from Room | Footer button disabled until the first emission (instant in practice; Room is local). |
| Edit line whose option vanished | Option dropped on pre-fill; required check re-applies. |
| Edit produces a config equal to another line | Lines merge, edited line removed. |
| Decrement to 0 in cart | Line removed (PRD 4.5). |
| Catalog refresh mid-order | Cart lines are snapshots; unaffected. |
| Logout | Cart cleared. |
| Offline | Options come from Room; nothing in this phase hits the network except the existing `refresh()`. |

## 7. Testing (JVM only, no emulator)

Follows the existing suite style (JUnit, coroutines-test, Turbine, Robolectric for Room).
- `OrderDraftTest`: single vs multi toggling, required-group gating, unavailable option ignored, price math (variant + modifiers x quantity), quantity floor, empty-required-group rule.
- `CartStoreTest`: add, merge on equal `configKey`, `replace` in place, `replace` collision merge, `setQuantity(0)` removes, delete, clear, derived subtotal/count/`quantityByMenuItem`, snapshot immutability.
- `CartLineTest`: `configKey` is order-independent, `summary` ordering and format.
- `CartViewModelTest` (fake `CatalogRepository`): unavailable item does not open the dialog; New -> toggle -> confirm adds a line; Edit pre-fills and updates in place; stale option dropped on edit; clear-all needs confirmation; logout clears the store.
- `OptionGroupDaoTest` (Robolectric, like `MenuItemDaoTest`): relation ordering by `sortOrder`, `clear`, per-item filtering.
- `AppDatabaseMigrationTest` (Robolectric): create the version-1 schema with raw SQL, open `AppDatabase` with `MIGRATION_1_2`, assert Room's schema validation passes and the old rows survive.
- `CatalogRepositoryTest` additions: refresh writes all four tables atomically; a failing options fetch leaves the previous cache intact and returns `Failure`; unknown `kind` is skipped.
- Manual verification after implementation (needs a device): dialog on phone and tablet, seed data states from section 2, bottom sheet behaviour. Compose UI rendering is not covered by automated tests in this phase (no instrumented tests exist yet).

## 8. Out of scope (explicit)

- Tax/service charge, grand total, payment dialog, success state (Phase 4). "Bayar" does not navigate.
- Persisting the cart across process death, and any transaction/offline-sync writes (Phases 4-5).
- Featured menu row (PRD 4.12), search/category changes, receipt printing, history.
- Admin website changes for managing groups/options (separate project; this spec only defines the shared schema contract).
- Negative price deltas, per-option quantity limits (min/max selections), option images, and combo/bundle pricing.
- Instrumented/Compose UI tests.
