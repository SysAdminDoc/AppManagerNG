# Logo Prompt Pack

Project: AppManagerNG

Description: The next-generation Android package manager - all the power, half the friction.

Review decision: Refreshing generated repo-root logo prompt file. Existing logo/icon asset signal: 25 matching files found; use this prompt pack to replace weak or inconsistent branding. Existing nested prompt pack found, but this root file is the copy-ready repo entry point.

Design direction: Premium system utility branding using modular system tiles, sorted paths, clean toggles, check rails, and utility glyphs. Palette guidance: graphite, bright blue, silver, and precise green. Keep the mark high-contrast on both light and dark surfaces, built from clean SVG-friendly shapes with confident negative space.

Trademark guardrail: No third-party brand marks are needed.

Use any one of the prompts below as a copy-ready image-generation request. Each prompt is written for a true transparent PNG, not a fake checkerboard or flattened background.

## Minimal Icon

```text
Project: AppManagerNG. Description: The next-generation Android package manager - all the power, half the friction.
Create a premium minimal software icon for AppManagerNG. Use modular system tiles, sorted paths, clean toggles, check rails, and utility glyphs as the visual language, reduced to a flat, memorable glyph that remains recognizable at 16px through 128px. Use crisp vector-like geometry, balanced negative space, high contrast, and the palette direction graphite, bright blue, silver, and precise green. No third-party brand marks are needed. No text, letters, numbers, UI screenshots, product mockups, or app-window frames.
Background/output requirements: The final image must be a true transparent PNG in RGBA format with a real alpha channel. Everything outside the main icon/logo must be fully transparent, alpha = 0. Do not render a checkerboard pattern. Do not render a white, gray, black, colored, or textured background. Do not simulate transparency. Only the main icon/logo should contain visible pixels. If the generated image includes a checkerboard or any visible background, remove it with image processing and export a corrected transparent PNG artifact. Final output: 512x512 PNG, RGBA, true transparent background, alpha channel enabled, no checkerboard, no solid background, no watermark.
```

## App Icon

```text
Project: AppManagerNG. Description: The next-generation Android package manager - all the power, half the friction.
Create a premium app icon mark for AppManagerNG. The icon should feel like polished commercial software: dimensional but restrained, legible at launcher size, with a strong central silhouette based on modular system tiles, sorted paths, clean toggles, check rails, and utility glyphs. Use graphite, bright blue, silver, and precise green; avoid muddy gradients, clip-art effects, mascot art, and decorative clutter. Do not bake in a full-canvas square, circle, pill, oval, or platform mask; the transparent PNG should contain only the logo mark. No third-party brand marks are needed. No text.
Background/output requirements: The final image must be a true transparent PNG in RGBA format with a real alpha channel. Everything outside the main icon/logo must be fully transparent, alpha = 0. Do not render a checkerboard pattern. Do not render a white, gray, black, colored, or textured background. Do not simulate transparency. Only the main icon/logo should contain visible pixels. If the generated image includes a checkerboard or any visible background, remove it with image processing and export a corrected transparent PNG artifact. Final output: 512x512 PNG, RGBA, true transparent background, alpha channel enabled, no checkerboard, no solid background, no watermark.
```

## Wordmark

```text
Project: AppManagerNG. Description: The next-generation Android package manager - all the power, half the friction.
Create a premium transparent wordmark for the exact text "AppManagerNG". Use custom typography that feels precise, modern, and product-grade, with one small integrated symbol inspired by modular system tiles, sorted paths, clean toggles, check rails, and utility glyphs. Keep letters readable at README-header size and app-about-screen size. Use graphite, bright blue, silver, and precise green. No third-party brand marks are needed. Text is allowed only for this wordmark prompt.
Background/output requirements: The final image must be a true transparent PNG in RGBA format with a real alpha channel. Everything outside the main icon/logo must be fully transparent, alpha = 0. Do not render a checkerboard pattern. Do not render a white, gray, black, colored, or textured background. Do not simulate transparency. Only the main icon/logo should contain visible pixels. If the generated image includes a checkerboard or any visible background, remove it with image processing and export a corrected transparent PNG artifact. Final output: 512x512 PNG, RGBA, true transparent background, alpha channel enabled, no checkerboard, no solid background, no watermark.
```

## Emblem

```text
Project: AppManagerNG. Description: The next-generation Android package manager - all the power, half the friction.
Create a premium emblem logo for AppManagerNG suitable for README headers, splash screens, and installer/about dialogs. Build a compact badge-like composition from modular system tiles, sorted paths, clean toggles, check rails, and utility glyphs, but do not use a filled background plate, shield cliche, full-canvas container, pill, oval, or seal. The silhouette should be distinctive, balanced, and credible for professional software. Use graphite, bright blue, silver, and precise green. No third-party brand marks are needed. No text.
Background/output requirements: The final image must be a true transparent PNG in RGBA format with a real alpha channel. Everything outside the main icon/logo must be fully transparent, alpha = 0. Do not render a checkerboard pattern. Do not render a white, gray, black, colored, or textured background. Do not simulate transparency. Only the main icon/logo should contain visible pixels. If the generated image includes a checkerboard or any visible background, remove it with image processing and export a corrected transparent PNG artifact. Final output: 512x512 PNG, RGBA, true transparent background, alpha channel enabled, no checkerboard, no solid background, no watermark.
```

## Abstract Mark

```text
Project: AppManagerNG. Description: The next-generation Android package manager - all the power, half the friction.
Create an abstract premium software logo mark for AppManagerNG. Express the product purpose through symbolic geometry rather than literal screenshots: modular system tiles, sorted paths, clean toggles, check rails, and utility glyphs. The result should look like a modern SaaS/app icon system asset, with strong negative space, clean edges, and a silhouette that survives monochrome conversion. Use graphite, bright blue, silver, and precise green. No third-party brand marks are needed. No text.
Background/output requirements: The final image must be a true transparent PNG in RGBA format with a real alpha channel. Everything outside the main icon/logo must be fully transparent, alpha = 0. Do not render a checkerboard pattern. Do not render a white, gray, black, colored, or textured background. Do not simulate transparency. Only the main icon/logo should contain visible pixels. If the generated image includes a checkerboard or any visible background, remove it with image processing and export a corrected transparent PNG artifact. Final output: 512x512 PNG, RGBA, true transparent background, alpha channel enabled, no checkerboard, no solid background, no watermark.
```

## Verification

After generation, verify the PNG is real RGBA with alpha transparency:

```bash
magick identify -format '%[channels]' icon.png
```

Valid results include `rgba`, `srgba`, or `graya`. A flat `rgb` result means transparency was lost and the image must be regenerated or post-processed.
