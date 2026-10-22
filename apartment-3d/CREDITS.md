# Asset credits

| Asset | Source | License |
|---|---|---|
| GlamVelvetSofa, SpecularSilkPouf | [Khronos glTF-Sample-Assets](https://github.com/KhronosGroup/glTF-Sample-Assets) — © Wayfair, LLC, by Eric Chadwick | CC BY 4.0 |
| SheenChair, GlassVaseFlowers | Khronos glTF-Sample-Assets | CC0 |
| mid_century_lounge_chair, coffee_table_round_01, dining_chair_02, modern_ceiling_lamp_01, potted_plant_02/04, ceramic_vase_01/03, brass_vase_01, marble_bust_01 | [Poly Haven](https://polyhaven.com) | CC0 |
| Textures: herringbone_parquet, wood_floor, wool_boucle, rough_linen, velour_velvet, white_plaster_02, white_oak_veneer | Poly Haven | CC0 |
| Procedural trees | [ez-tree](https://github.com/dgreenheck/ez-tree) | MIT |

## Rebuilding the assets

1. `python3 tools/fetch_assets.py m <polyhaven_id>…`, `… t <texture_id>…`, `… k <KhronosModel>…` downloads the sources.
2. `gltf-transform optimize in out.glb --compress meshopt --texture-compress webp --texture-size 1024 --simplify false`
3. `node tools/set_variant.mjs file.glb champagne` picks the default fabric variant.
4. `node tools/pack_json.mjs models/*.json` drops meshopt, quantizes and stores each model as `{"glb": base64}` in a `.json` file: the artifact host does not serve `.glb`, and its CSP blocks fetching `data:` URIs and may block WebAssembly decoders.
5. Textures are converted to WebP (quality 82) with Pillow.
