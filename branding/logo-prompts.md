<!-- SPDX-License-Identifier: CC-BY-SA-4.0 -->

# AppManagerNG — Logo Prompts

5-variant logo prompt set for AppManagerNG. **Every prompt requires PNG with alpha channel (RGBA);
transparent background; no full-canvas fill of any color.** High contrast on light *and* dark
surfaces. Clean geometry, SVG-friendly. No text unless the variant is the Wordmark.

Project: **AppManagerNG** — a power-user Android package manager (component blocking, tracker
scanning, root/ADB ops, backups, batch operations) presented behind a Material 3 interface that
welcomes casual users without dumbing down for power users.

Visual cues to lean on: a stylized **package / box / cube** (the "app"), with a **gear, key, or
shield** layered to convey control or system-level reach; a **green→cyan or green→teal** palette
nodding to Android while distinguishing from upstream's red wordmark. Avoid: literal Android
mascot, anything that resembles the Material Components icon for "apps," or the standard Google
Play shopping bag.

---

## 1. Minimal icon (favicon / toolbar)

```
A flat, single-color minimalist glyph representing a stylized package/cube with a small gear or
key emblem subtly integrated into one face. Clean geometric vector style, single solid color
(neon green or teal), sharp edges, high contrast. Suitable for 16x16 to 128x128 favicon use.
Transparent background (full alpha=0 outside the glyph; absolutely no white or dark canvas fill).
PNG with alpha channel (RGBA). No text. Logo only — readable at small sizes against light or
dark UI chrome.
```

## 2. App icon (Android adaptive / launcher)

```
An Android adaptive launcher icon for "AppManagerNG" — a power-user package manager. Rounded
square format with subtle 3D depth and soft shading. Central motif: an isometric package or
cube with a stylized gear or key fused into one face, in a green-to-cyan gradient (#7BC96F →
#3FB6C7). Modern Material 3 aesthetic, clean geometry, no flat-design blandness, recognizable
at 48dp. Transparent background outside the rounded-square mask (alpha=0; do NOT fill the canvas
with white or any color). Output PNG with alpha channel (RGBA). No text in the icon.
```

## 3. Wordmark

```
A wordmark logo for "AppManagerNG" — typography only, no surrounding shape. Bold modern
sans-serif (geometric, similar to Inter or Manrope), tight letter spacing. The "NG" should be
visually distinguished — slightly heavier weight, or a subtle accent color (neon green or teal)
that picks up the brand palette. Solid opaque glyphs on a fully transparent background
(alpha=0 around and between letters; no rectangle fill). Composite-ready on light AND dark
surfaces. Output PNG with alpha channel (RGBA).
```

## 4. Emblem (README header / splash)

```
A modern emblem/badge logo for "AppManagerNG" suitable for a README header banner and splash
screen. Hexagonal or shield silhouette containing a stylized package/cube with a gear and a
small shield overlay (conveys "manager + control + protection"). Green-to-teal gradient
(#5EBE6F → #2EA8B5) with subtle inner highlight; thin clean outline. No text on the emblem
itself. Transparent background outside the badge silhouette (alpha=0; no white or dark canvas
fill). High contrast against both light and dark backgrounds. Output PNG with alpha channel
(RGBA).
```

## 5. Abstract / conceptual

```
An abstract, conceptual symbol for "AppManagerNG" — represents the idea of unifying complexity
behind a clean, friendly surface. A single elegant shape: nested or interlocking forms (e.g., a
larger rounded-square outline encircling/containing smaller geometric shapes representing apps
or modules), suggesting orchestration and control. Monochromatic with a single accent color
(neon green or teal) on the focal element. Minimal, modern, mark-like — readable at favicon
size yet visually rich at 1024px. No text. Transparent background (alpha=0; no canvas fill of
any color). Output PNG with alpha channel (RGBA).
```

---

## Generation checklist

- [ ] Generated all 5 variants
- [ ] Verified PNG alpha channel: `magick identify -format '%[channels]' <file>` returns `rgba` / `srgba` / `graya` (NOT `rgb`)
- [ ] Saved master files to `branding/` (e.g., `branding/icon-minimal.png`, `branding/app-icon.png`, `branding/wordmark.png`, `branding/emblem.png`, `branding/abstract.png`)

## Integration pass (after generation)

- [ ] Replace `docs/raw/images/icon.png` with our new app-icon variant (READme references it)
- [ ] Generate Android adaptive icon resources (`app/src/main/res/mipmap-*`) — needs round + foreground/background layers
- [ ] Generate PNG size variants: 16, 32, 48, 64, 128, 256, 512, 1024
- [ ] Generate `icon.ico` (Windows multi-res, optional — used only for any future companion desktop tools)
- [ ] Update `fastlane/metadata/android/en-US/images/icon.png` (F-Droid metadata) — only after applicationId rebrand in v0.2.0
- [ ] Update README emblem reference once `branding/emblem.png` exists
- [ ] Splash screen and About-section branding (v0.3.0 UX overhaul will sweep these)
