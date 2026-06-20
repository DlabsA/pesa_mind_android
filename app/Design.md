# PesaMind Design System

> Version 1.0 — Light & Dark Mode · Conrad Kash / dlabs

---

## Logo Review

The PesaMind mark is a two-element lockup: a **deep forest-green asterisk** (`#003934`) anchoring the bottom-left, paired with a **lime accent bracket** (`#9fe870` family) at the top-right. The asterisk is a universally understood reference — transactions, footnotes, multiplicity — reframed as the brand's radiating energy. The bracket evokes a UI corner handle: a nod to the product being a digital interface. Together they read "financial clarity through technology."

**What works well:**
- The asymmetric tension between the heavy mark and the weightless bracket is distinctive and memorable.
- The two-green system (deep vs. acid) is sophisticated; it avoids the naive "money = green" cliché by making forest green the primary identity colour, with lime reserved as an accent.
- The canvas colour (`#f8f9f0` — warm off-white) is already in the logo, giving a ready-made light-mode surface.
- The mark scales cleanly: both elements are geometric, stroke-weight consistent, and work at 16 px favicon scale.

**Recommendations:**
- The horizontal gap between the asterisk stem and the bracket feels slightly wide at small sizes; optically tighten it by ~10% in a future iteration.
- Ensure the bracket arm weight matches the asterisk stroke weight exactly — currently the bracket reads ~1 px lighter.
- The background square (the app icon's rounded rectangle) uses `#f8f9f0`; confirm this matches your canvas token exactly so the icon feels at home on a light-mode screen.

---

## Overview

PesaMind is a personal finance product built for the Ugandan market — UGX, MTN/Airtel mobile money, East African UX. The visual identity is anchored by two decisions made in the logo: **deep forest primary** and **lime accent**. The design language extends those two decisions into a full token system with light and dark modes, a financial semantic palette (income / expense / savings), and component chrome that feels native to both Android Jetpack Compose and SwiftUI.

**Design voice:** Calm authority. Not a bank (cold, institutional), not a neobank trying too hard (cartoonish, pastel-heavy). PesaMind is the trusted friend who happens to be good with money.

---

## Color System

### Brand Primitives

| Name | Light Hex | Dark Hex | Role |
|---|---|---|---|
| `forest-900` | `#003934` | `#003934` | Primary brand — the asterisk |
| `forest-800` | `#004d45` | `#004d45` | Hover / pressed primary |
| `forest-700` | `#006b5e` | `#006b5e` | Active rings, focus outlines |
| `forest-100` | `#e6f0ef` | `#0a1f1d` | Pale tint surface |
| `lime-500` | `#9fe870` | `#9fe870` | Accent — the bracket |
| `lime-400` | `#b8f093` | `#b8f093` | Accent hover |
| `lime-200` | `#ddf7c9` | `#1a3318` | Pale lime tint |
| `canvas-warm` | `#f8f9f0` | `#0d1410` | Page/icon background |

### Semantic — Mode Tokens

All downstream components reference these semantic tokens, never raw primitives. Swap the token table at the theme boundary.

#### Light Mode

| Token | Value | Use |
|---|---|---|
| `--color-bg` | `#f8f9f0` | App / page background |
| `--color-surface` | `#ffffff` | Card, sheet, modal surface |
| `--color-surface-raised` | `#f0f4f2` | Elevated card / hover |
| `--color-border` | `#d4dbd9` | Hairline dividers |
| `--color-border-strong` | `#a3b0ae` | Input borders, focused |
| `--color-primary` | `#003934` | Primary buttons, active nav |
| `--color-primary-hover` | `#004d45` | Primary hover |
| `--color-primary-fg` | `#ffffff` | Text on primary fill |
| `--color-accent` | `#9fe870` | Lime accent — badges, highlights |
| `--color-accent-hover` | `#b8f093` | Lime hover |
| `--color-accent-fg` | `#003934` | Text on lime fill |
| `--color-text` | `#0d1410` | Primary text |
| `--color-text-secondary` | `#3d524f` | Secondary / meta text |
| `--color-text-muted` | `#6b8280` | Placeholder, captions |
| `--color-income` | `#1a7a3c` | Income positive green |
| `--color-income-bg` | `#e8f5ee` | Income badge background |
| `--color-income-fg` | `#0e4a25` | Income text on background |
| `--color-expense` | `#c0392b` | Expense red |
| `--color-expense-bg` | `#fdecea` | Expense badge background |
| `--color-expense-fg` | `#7b1a14` | Expense text on background |
| `--color-savings` | `#5a6e6c` | Savings neutral grey-green |
| `--color-savings-bg` | `#eaeeee` | Savings badge background |
| `--color-savings-fg` | `#2e3d3c` | Savings text on background |
| `--color-warning` | `#d97706` | Warning amber |
| `--color-warning-bg` | `#fef3e2` | Warning badge background |

#### Dark Mode

| Token | Value | Use |
|---|---|---|
| `--color-bg` | `#0d1410` | App / page background |
| `--color-surface` | `#162019` | Card, sheet, modal surface |
| `--color-surface-raised` | `#1e2d28` | Elevated card / hover |
| `--color-border` | `#243329` | Hairline dividers |
| `--color-border-strong` | `#3a5047` | Input borders, focused |
| `--color-primary` | `#9fe870` | Primary buttons → lime in dark (brand flip) |
| `--color-primary-hover` | `#b8f093` | Primary hover |
| `--color-primary-fg` | `#003934` | Text on primary fill (forest on lime) |
| `--color-accent` | `#003934` | Accent → forest in dark |
| `--color-accent-hover` | `#004d45` | Forest hover |
| `--color-accent-fg` | `#9fe870` | Text on forest fill |
| `--color-text` | `#e8f0ee` | Primary text |
| `--color-text-secondary` | `#9bb8b2` | Secondary / meta text |
| `--color-text-muted` | `#5a7d77` | Placeholder, captions |
| `--color-income` | `#4ade80` | Income green — brightened for dark |
| `--color-income-bg` | `#0a2e1a` | Income badge background |
| `--color-income-fg` | `#86efac` | Income text on dark background |
| `--color-expense` | `#f87171` | Expense red — lightened for dark |
| `--color-expense-bg` | `#2c0f0e` | Expense badge background |
| `--color-expense-fg` | `#fca5a5` | Expense text on dark background |
| `--color-savings` | `#94a3a0` | Savings grey-green — lightened for dark |
| `--color-savings-bg` | `#1a2523` | Savings badge background |
| `--color-savings-fg` | `#b8cac7` | Savings text on dark background |
| `--color-warning` | `#fbbf24` | Warning amber — brightened for dark |
| `--color-warning-bg` | `#2c1f08` | Warning badge background |

---

## Financial Semantic Palette — Design Rationale

PesaMind carries three financial categories. Each has its own chromatic story:

### Income — Green Family
- **Hue anchor:** `#1a7a3c` (light) / `#4ade80` (dark)
- Deliberately **distinct from the brand forest green** (`#003934`). Income green is warmer, more saturated, mid-lightness — it reads "positive signal" not "brand action."
- Never use `--color-primary` to indicate income. The primary is the brand; income is a data state.
- Badge anatomy: lime-tinted background + deep green text + optional up-arrow icon.

### Expense — Red Family
- **Hue anchor:** `#c0392b` (light) / `#f87171` (dark)
- A clean signal red — not orange-shifted (which reads "warning"), not too dark (which reads "danger/block"). It communicates spending, not catastrophe.
- In dark mode, the red is desaturated slightly toward coral to reduce eye strain on OLED.
- Badge anatomy: pale red background + deep red text + optional down-arrow icon.

### Savings — Grey-Green Family
- **Hue anchor:** `#5a6e6c` (light) / `#94a3a0` (dark)
- Savings is intentionally **neutral**. It is neither income (gaining) nor expense (spending); it is money set aside and held. The grey-green bridges both worlds without competing with either.
- Never use pure grey (e.g. `#888888`) — that reads "disabled" or "inactive." The slight green cast keeps savings feeling intentional and alive.
- Badge anatomy: cool grey-green background + dark text + optional lock/piggybank icon.

---

## Typography

### Font Stack

| Role | Family | Weight | Fallback |
|---|---|---|---|
| Display / Hero | **Plus Jakarta Sans** | 800 | `system-ui` |
| Sub-display | **Plus Jakarta Sans** | 600 | `system-ui` |
| Body | **Inter** | 400 / 500 | `system-ui` |
| Numeric / Monospace | **JetBrains Mono** | 500 | `monospace` |

**Rationale:** Plus Jakarta Sans at 800 has the geometric confidence of Wise Sans without being a clone — it has a faint East African warmth in its rounded terminals. Inter is the confirmed second face (same as Wise/Anthropic). JetBrains Mono is used **only** for currency amounts (UGX 1,234,500) so the numbers tabulate correctly and feel precise.

### Type Scale

| Token | Size | Weight | Line Height | Use |
|---|---|---|---|---|
| `display-xl` | 48px | 800 | 52px | Balance / hero number |
| `display-lg` | 36px | 800 | 42px | Screen title |
| `display-md` | 28px | 700 | 34px | Section heading |
| `display-sm` | 22px | 600 | 28px | Card heading |
| `body-lg` | 18px | 400 | 27px | Lead paragraph |
| `body-md` | 16px | 400 | 24px | Default body |
| `body-sm` | 14px | 400 | 20px | Secondary body |
| `label-md` | 14px | 600 | 20px | Form labels, nav items |
| `label-sm` | 12px | 600 | 16px | Badges, captions |
| `amount-xl` | 42px | 500 | 48px | Hero UGX amount (JetBrains Mono) |
| `amount-md` | 20px | 500 | 26px | List row amounts (JetBrains Mono) |
| `amount-sm` | 14px | 500 | 18px | Compact row amounts (JetBrains Mono) |

### Principles
- **All currency amounts in monospace.** `UGX 1,234,500` in JetBrains Mono tabulates cleanly in lists and never shifts layout.
- **Never mix weight families on the same line.** If a label and a value share a row, differentiate via size, not weight collision.
- **Sentence case everywhere.** No ALL-CAPS labels — this reads aggressive in a money app.

---

## Layout & Spacing

### Base Unit: 4 px

| Token | Value | Use |
|---|---|---|
| `spacing-1` | 4px | Icon padding |
| `spacing-2` | 8px | Inline gap |
| `spacing-3` | 12px | Component internal padding |
| `spacing-4` | 16px | Standard padding |
| `spacing-5` | 20px | Comfortable gap |
| `spacing-6` | 24px | Card padding |
| `spacing-8` | 32px | Section gap |
| `spacing-10` | 40px | Large section |
| `spacing-12` | 48px | Screen-level padding |

### Safe Areas (Mobile-first)
- **Top safe area:** Respect system status bar; do not paint behind it with surface colour.
- **Bottom safe area:** Tab bar sits above the home indicator. Use `env(safe-area-inset-bottom)`.
- **Horizontal margin:** 16 px (mobile), 24 px (tablet), 32 px (desktop web).

---

## Border Radius

| Token | Value | Use |
|---|---|---|
| `radius-sm` | 6px | Badges, chips, tags |
| `radius-md` | 10px | Inputs, small cards |
| `radius-lg` | 16px | Transaction cards |
| `radius-xl` | 20px | Bottom sheets, modal cards |
| `radius-2xl` | 28px | Hero balance card |
| `radius-full` | 9999px | Avatar, FAB, pill buttons |

**Never use 0 px radius on any user-facing card or button.** The logo's geometry is rounded at every intersection; the product chrome must echo that softness.

---

## Elevation

### Light Mode

| Level | Treatment | Use |
|---|---|---|
| 0 — Flat | No shadow, `--color-bg` surface | Page background |
| 1 — Card | `box-shadow: 0 1px 3px rgba(0,57,52,0.08), 0 1px 2px rgba(0,57,52,0.06)` | Default cards |
| 2 — Float | `box-shadow: 0 4px 12px rgba(0,57,52,0.10), 0 2px 4px rgba(0,57,52,0.06)` | Bottom sheet, popover |
| 3 — Modal | `box-shadow: 0 16px 40px rgba(0,57,52,0.14)` | Modals, full-screen sheets |

### Dark Mode
Replace all shadows with a **surface tint approach**: raise the `--color-surface` lightness by 4–6 % per elevation level instead of casting shadows (shadows are invisible on dark backgrounds).

| Level | Surface Colour |
|---|---|
| 0 | `#0d1410` |
| 1 | `#162019` |
| 2 | `#1e2d28` |
| 3 | `#263d37` |

---

## Components

### Primary Button
- Background: `--color-primary`
- Text: `--color-primary-fg`
- Padding: `12px 24px`
- Radius: `radius-full` (pill)
- Typography: `label-md` (Inter 600)
- **Dark mode:** Background flips to lime `#9fe870`, text to `#003934` — the logo's own colour inversion.

### Secondary Button
- Background: `--color-surface-raised`
- Text: `--color-text`
- Border: `1px solid --color-border-strong`
- Same padding / radius as primary.

### Transaction Row (the core list item)

```
┌─────────────────────────────────────────────────┐
│  [icon]  Merchant Name          UGX 45,000       │
│          Subcategory · Today    ▼ Expense        │
└─────────────────────────────────────────────────┘
```
- Icon: 40 × 40 px, `radius-md`, coloured by category.
- Amount: `amount-md` (JetBrains Mono 500).
- Amount colour: `--color-income` / `--color-expense` / `--color-savings` depending on type.
- Badge: `label-sm`, pill `radius-full`, uses semantic bg/fg pair.

### Balance Card (hero)

```
┌──────────────────────────────────────────────────┐
│  Total Balance           [month selector ▾]      │
│  UGX 3,450,000                                   │
│  ─────────────────────────────────────────────   │
│  ↑ Income  UGX 1.2M   ↓ Expense  UGX 780K       │
└──────────────────────────────────────────────────┘
```
- Background: `--color-primary` (forest green in light, lime in dark).
- All text: `--color-primary-fg`.
- Balance figure: `amount-xl` (JetBrains Mono 500, 42 px).
- Radius: `radius-2xl`.
- Income/Expense mini-blocks: muted `rgba(255,255,255,0.12)` fill on primary surface.

### Category Badge

Three variants — use **only** the semantic token pairs:

| Category | Background token | Text token | Prefix |
|---|---|---|---|
| Income | `--color-income-bg` | `--color-income-fg` | ↑ |
| Expense | `--color-expense-bg` | `--color-expense-fg` | ↓ |
| Savings | `--color-savings-bg` | `--color-savings-fg` | → |

### Input Field
- Background: `--color-surface`
- Border: `1px solid --color-border`
- Focus border: `2px solid --color-primary`
- Radius: `radius-md`
- Typography: `body-md` (Inter 400)
- Padding: `12px 16px`
- Label: `label-md` above the field, `--color-text-secondary`

### Bottom Sheet / Drawer
- Background: `--color-surface`
- Radius: `radius-xl` top corners only
- Handle: 36 × 4 px pill, `--color-border-strong`, centred at 8 px from top
- Elevation: Level 3

### Tab Bar (mobile)
- Background: `--color-surface`
- Active icon + label: `--color-primary`
- Inactive: `--color-text-muted`
- Top border: `1px solid --color-border`
- Active indicator: small `radius-full` pill dot, `--color-accent` colour, 4 × 4 px

---

## Iconography

- **Style:** Outlined, 2 px stroke weight, `radius-sm` rounded line caps. Never filled icons except for the active tab state.
- **Grid:** 24 × 24 px base; 20 × 20 px compact; 32 × 32 px featured.
- **Financial icons:** Use directional arrows (↑ income, ↓ expense, → transfer) consistently. Never use ± symbols.
- **Category icons:** Each spend category gets a coloured `radius-md` container. The container colour is the category hue at 15 % opacity in light mode, 20 % opacity in dark mode.

---

## Motion

| Event | Duration | Easing | Notes |
|---|---|---|---|
| Button press | 100 ms | `ease-out` | Scale 0.97 |
| Sheet open/close | 320 ms | `cubic-bezier(0.32,0.72,0,1)` | Spring-like settle |
| Number count-up | 600 ms | `ease-out` | Balance reveal on load |
| Tab switch | 200 ms | `ease-in-out` | Icon morph filled↔outlined |
| Toast enter | 250 ms | `ease-out` | Slide up from bottom |
| Toast exit | 180 ms | `ease-in` | Fade out |

**Respect `prefers-reduced-motion`.** Collapse all animations to instant when the system flag is set.

---

## Dark Mode — Implementation Notes

### The Primary Flip
The most deliberate dark-mode choice: **primary colour inverts from forest to lime**. This mirrors the logo's own two-colour story — in light mode the asterisk (forest) dominates; in dark mode the bracket (lime) takes the foreground. Practically:
- Light: primary button = `#003934` fill, white text.
- Dark: primary button = `#9fe870` fill, `#003934` text.

This is not a colour-mode hack. It is a brand decision that makes the dark mode feel intentional, not inverted.

### Surface Stack
Dark mode uses the **elevation-via-tint** pattern (see Elevation section). Each level lightens the surface by roughly 5 % in HSL lightness. Do not use `rgba(255,255,255,0.05)` overlays — they produce muddy results on the warm `#0d1410` background. Instead, use discrete hex surface tokens.

### Financial Semantics in Dark Mode
- Income green shifts from `#1a7a3c` → `#4ade80` (brighter; dark backgrounds need more chroma to achieve equal perceived saturation).
- Expense red shifts from `#c0392b` → `#f87171` (desaturated coral; pure red on near-black is aggressive).
- Savings grey-green shifts from `#5a6e6c` → `#94a3a0` (lighter; must read above the dark surface).

---

## Do's and Don'ts

### Do
- Use `--color-primary` forest green for all primary actions in light mode; lime in dark mode.
- Express income, expense, and savings exclusively via their semantic token pairs — never use the brand accent lime as an income indicator.
- Set all currency amounts in JetBrains Mono so UGX figures tabulate cleanly.
- Apply `radius-2xl` to the hero balance card — the generous curve echoes the logo's soft geometry.
- Follow the elevation-via-tint pattern in dark mode; pure drop shadows disappear on dark backgrounds.

### Don't
- Don't use `--color-primary` lime (`#9fe870`) in light mode to mean "income" — lime is the brand accent, not a data semantic.
- Don't set balance amounts in Inter or Plus Jakarta Sans — proportional fonts cause digit-width jitter in animated count-ups.
- Don't use `border-radius: 0` on any card or button; the zero-radius look is antithetical to the logo's geometric softness.
- Don't represent savings with pure grey (`#888888`) — it reads "disabled." Use the grey-green `--color-savings` family.
- Don't place the lime accent on a white background for financial success states — it will be confused with the primary action colour.
- Don't use ALL-CAPS anywhere in the UI; it reads as shouting in a context where people are already anxious about money.

---

## Accessibility

| Requirement | Guidance |
|---|---|
| Text contrast | All `--color-text` on `--color-bg`: ≥ 7:1 (WCAG AAA). All `--color-text-secondary`: ≥ 4.5:1 (AA). |
| Financial badges | Semantic badge text on badge background: ≥ 4.5:1 minimum. Verify each pair. |
| Touch targets | Minimum 44 × 44 px for all interactive elements (matches Android 48 dp guideline). |
| Focus rings | `2px solid --color-primary` offset `2px` on all focusable elements; never suppress `:focus-visible`. |
| Reduced motion | Wrap all animations in `@media (prefers-reduced-motion: no-preference)`. |
| Colour alone | Never communicate transaction type by colour only — always pair with the directional prefix icon (↑ ↓ →) and the category label. |

---

## CSS Variable Reference (Web / React)

```css
:root {
  /* Brand */
  --color-forest-900: #003934;
  --color-forest-800: #004d45;
  --color-lime-500:   #9fe870;
  --color-lime-400:   #b8f093;

  /* Surfaces */
  --color-bg:             #f8f9f0;
  --color-surface:        #ffffff;
  --color-surface-raised: #f0f4f2;

  /* Borders */
  --color-border:        #d4dbd9;
  --color-border-strong: #a3b0ae;

  /* Primary action */
  --color-primary:     #003934;
  --color-primary-hover: #004d45;
  --color-primary-fg:  #ffffff;

  /* Accent */
  --color-accent:      #9fe870;
  --color-accent-hover:#b8f093;
  --color-accent-fg:   #003934;

  /* Text */
  --color-text:           #0d1410;
  --color-text-secondary: #3d524f;
  --color-text-muted:     #6b8280;

  /* Income */
  --color-income:    #1a7a3c;
  --color-income-bg: #e8f5ee;
  --color-income-fg: #0e4a25;

  /* Expense */
  --color-expense:    #c0392b;
  --color-expense-bg: #fdecea;
  --color-expense-fg: #7b1a14;

  /* Savings */
  --color-savings:    #5a6e6c;
  --color-savings-bg: #eaeeee;
  --color-savings-fg: #2e3d3c;

  /* Warning */
  --color-warning:    #d97706;
  --color-warning-bg: #fef3e2;

  /* Radius */
  --radius-sm:   6px;
  --radius-md:   10px;
  --radius-lg:   16px;
  --radius-xl:   20px;
  --radius-2xl:  28px;
  --radius-full: 9999px;

  /* Spacing */
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 20px;
  --space-6: 24px;
  --space-8: 32px;
  --space-10: 40px;
  --space-12: 48px;
}

@media (prefers-color-scheme: dark) {
  :root {
    --color-bg:             #0d1410;
    --color-surface:        #162019;
    --color-surface-raised: #1e2d28;
    --color-border:         #243329;
    --color-border-strong:  #3a5047;
    --color-primary:        #9fe870;
    --color-primary-hover:  #b8f093;
    --color-primary-fg:     #003934;
    --color-accent:         #003934;
    --color-accent-hover:   #004d45;
    --color-accent-fg:      #9fe870;
    --color-text:           #e8f0ee;
    --color-text-secondary: #9bb8b2;
    --color-text-muted:     #5a7d77;
    --color-income:         #4ade80;
    --color-income-bg:      #0a2e1a;
    --color-income-fg:      #86efac;
    --color-expense:        #f87171;
    --color-expense-bg:     #2c0f0e;
    --color-expense-fg:     #fca5a5;
    --color-savings:        #94a3a0;
    --color-savings-bg:     #1a2523;
    --color-savings-fg:     #b8cac7;
    --color-warning:        #fbbf24;
    --color-warning-bg:     #2c1f08;
  }
}
```

---

## Kotlin / Android Token Reference

```kotlin
// Theme.kt (Material 3 + Compose)
object PesaMindColors {
    // Light
    val Primary        = Color(0xFF003934)
    val PrimaryHover   = Color(0xFF004D45)
    val Accent         = Color(0xFF9FE870)
    val Background     = Color(0xFFF8F9F0)
    val Surface        = Color(0xFFFFFFFF)
    val SurfaceRaised  = Color(0xFFF0F4F2)

    // Financial semantics
    val Income         = Color(0xFF1A7A3C)
    val IncomeBg       = Color(0xFFE8F5EE)
    val IncomeFg       = Color(0xFF0E4A25)
    val Expense        = Color(0xFFC0392B)
    val ExpenseBg      = Color(0xFFFDECEA)
    val ExpenseFg      = Color(0xFF7B1A14)
    val Savings        = Color(0xFF5A6E6C)
    val SavingsBg      = Color(0xFFEAEEEE)
    val SavingsFg      = Color(0xFF2E3D3C)

    // Dark overrides
    val DarkPrimary    = Color(0xFF9FE870)
    val DarkPrimaryFg  = Color(0xFF003934)
    val DarkBackground = Color(0xFF0D1410)
    val DarkSurface    = Color(0xFF162019)
    val DarkIncome     = Color(0xFF4ADE80)
    val DarkExpense    = Color(0xFFF87171)
    val DarkSavings    = Color(0xFF94A3A0)
}
```

---

## Swift / iOS Token Reference

```swift
// Colors.swift (SwiftUI)
extension Color {
    // Primary
    static let pesaPrimary      = Color(hex: "#003934")
    static let pesaAccent       = Color(hex: "#9FE870")
    static let pesaBackground   = Color(hex: "#F8F9F0")

    // Financial semantics
    static let pesaIncome       = Color(hex: "#1A7A3C")
    static let pesaIncomeBg     = Color(hex: "#E8F5EE")
    static let pesaIncomeFg     = Color(hex: "#0E4A25")
    static let pesaExpense      = Color(hex: "#C0392B")
    static let pesaExpenseBg    = Color(hex: "#FDECEA")
    static let pesaExpenseFg    = Color(hex: "#7B1A14")
    static let pesaSavings      = Color(hex: "#5A6E6C")
    static let pesaSavingsBg    = Color(hex: "#EAEEEE")
    static let pesaSavingsFg    = Color(hex: "#2E3D3C")
}
// Use Color assets in .xcassets with Appearance: Any + Dark for automatic mode switching.
```

---

*End of PesaMind Design System v1.0*