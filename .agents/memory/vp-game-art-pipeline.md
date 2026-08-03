---
name: vp_game art pipeline for the 3D game
description: How original vp_game 2D art is reused as textures in the LibGDX 3D game
---

Original vp_game art (Ayan 8-direction sprites, school tilesets, kernel-panic robot) is copied/cropped into `java-game/assets/textures/` (ayan/, tiles/, robot.png) and loaded via a shared `GameAssets` cache (nearest filter, repeat wrap).

**Why:** the source art lives in `extracted/vp_game/.../src/main/resources/` as 32px-tile atlases; single-tile PNGs were cropped out with ImageMagick because LibGDX repeat-wrap tiling needs standalone textures, not atlas regions.

**How to apply:** characters are alpha-blended billboard quads anchored at their base (translate y≈0, not center); surfaces use `GameAssets.texturedBox` with UV repeats ≈ world size. Materials keep a white ColorAttribute so existing tint/highlight code still works. Re-crop tiles from the tilesets if more props are needed.
