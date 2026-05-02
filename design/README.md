<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# AppManagerNG Premium Facelift Design Folder

Read in this order:

1. [`audit/0-recon.md`](audit/0-recon.md) - current theme, color, typography, icon, motion, density, layout, manifest, and brand inventory.
2. [`spec/1-design-system.md`](spec/1-design-system.md) - v2 palette, typography, spacing, elevation, iconography, motion, and component stubs.
3. [`impl/values/themes-v2.xml`](impl/values/themes-v2.xml) - reference v2 theme parent and style mappings.
4. [`impl/values/colors-v2.xml`](impl/values/colors-v2.xml) - reference light, dark, AMOLED, semantic, and brand palette.
5. [`impl/values/dimens-v2.xml`](impl/values/dimens-v2.xml) - reference spacing, radius, elevation, icon, type, and row density tokens.
6. [`impl/layout/item_main_v2.xml`](impl/layout/item_main_v2.xml) - drop-in main-list row reference preserving `MainRecyclerAdapter` IDs.
7. [`impl/layout/activity_main_v2.xml`](impl/layout/activity_main_v2.xml) - drop-in MainActivity shell reference preserving toolbar, list, status, empty state, selection view, and chip IDs.
8. [`plan/3-rollout.md`](plan/3-rollout.md) - four-release rollout with scope, risk, rollback, and screenshot checks.
9. [`audit/4-painpoints.md`](audit/4-painpoints.md) - dated surface inventory and proposed fixes.

Implementation rule: treat these files as design/reference artifacts first. Copy pieces into app resources incrementally behind the preview toggle described in the rollout plan, then verify each migrated surface before expanding the toggle.
