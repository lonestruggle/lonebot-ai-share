# LoneBot — Walk & Camera (export voor review / perfectioneren)

**Versie context:** LoneBot ~0.3.111  
**Bronmap in repo:** `sdk/src/main/java/net/storm/sdk/movement/`  
**Deze export:** kopie van de kernbestanden + deze beschrijving.

Gebruik: geef deze map (of zip) + dit bestand aan een andere AI met de vraag om lopen/camera menselijker, stabieler en minder spammy te maken — zonder de bestaande architecture te breken.

---

## Wat is dit?

Dit is het **bewegings-subsysteem** van LoneBot (OSRS RuneLite-fork / Storm-compat SDK):

1. **Pathfinding → stappen** — globaal pad berekenen, dan per stap een tile klikken.
2. **Walk-klik** — hoe die stap op de client geland wordt (minimap / canvas / invoke).
3. **Walk-camera** — yaw/pitch/zoom bijsturen vóór/tijdens lopen zodat het pad zichtbaar is en klikken betrouwbaar zijn.

Scripts roepen doorgaans `MovementHelper.walkTo(WorldPoint)` (of `TilePath.walk()`). Daarna:

```
MovementHelper.walkTo(dest)
  → GlobalPathfinder / Pathfinder (pad met avoid zones)
  → TilePath.walk()  (= TilePathWalker.walk)
       → WalkCamera.prepareForWalk(dest)     // camera
       → WalkClickHelper.clickToward(step)   // klik
```

---

## Wat zou het moeten doen? (doelgedrag)

### Lopen
- Betrouwbaar van A → B zonder vast te lopen in kasteel/dining/potato no-walk.
- **Minst mogelijk klik-spam:** wacht tot dicht bij de gele destination-flag vóór herklik (zeker op canvas/far).
- **Korte hops** mogen sneller; lange hops mogen meer cooldown.
- Canvas-walk: bij voorkeur **echte left-click “Walk here”** (zichtbaar voor tile-plugins), niet blind boom/NPC aanvallen.
- Als hover Chop/Attack/Talk is → **invoke Walk** i.p.v. left-click.
- Twin-click vooral op **minimap**, niet spam op canvas.

### Camera
- Bij lopen: camera zo dat bestemming **vooruit op scherm** staat (FOLLOW), genoeg **pitch/zoom** voor canvas-klikken.
- **Niet** elke tick draaien: throttle (~1.8s), yaw alleen als “ahead%” te laag is.
- Menselijk: bij voorkeur **1× MMB-drag** (yaw+pitch samen), zoom via wheel/script — geen harde setYaw-flips.
- Default pitch doel ~**3064** (client `getCameraPitch()` op deze client; range tot 4096), niet classic RL 128–383.
- Skip als al goed genoeg gericht (`visibilitySkipPercent` / face%/ahead%).

### Anti-ban / humanisatie
- Random delays, geen vaste robot-tijden.
- Geen off-screen spam-klik loops.
- Camera startpunten buiten inv/minimap/chat.

---

## Bestanden (rol)

| Bestand | Regels (approx) | Rol |
|---------|-----------------|-----|
| `MovementHelper.java` | ~310 | High-level API: `walkTo`, Lumbridge avoid, potato/excluded, pad cachen |
| `TilePathWalker.java` | ~170 | Loopt een `TilePath`: kiest next step, flag-patience, roept camera + click |
| `WalkClickHelper.java` | ~685 | Minimap/canvas/invoke klik-strategie, far-canvas, twin-click, hover-check |
| `WalkClickSettings.java` | ~230 | Runtime toggles (panel/config): step size, reclick, far-canvas, etc. |
| `WalkCamera.java` | ~793 | Camera toepassen: MMB-drag, yaw/pitch/zoom, ahead-score, preview/test |
| `WalkCameraSettings.java` | ~144 | yaw mode FOLLOW/CONTRA/OFFSET, pitchTarget, zoom%, skip% |
| `Movement.java` | klein | Destination-flag helpers (`getDestination`) |
| `WalkUiZones.java` | klein | UI-zones vermijden bij canvas-klik |

Pathfinder zelf zit in `movement/pathfinder/` (niet in deze export tenzij je die ook wilt).

Config/UI (niet gekopieerd, wel relevant):
- `LoneBotConfig` — `walkCam*`, `walk*` keys  
- `LoneBotPanel` — sliders/tests (“Walk+camera”, pitch, zoom, rapport-kopie)

---

## Bekende pijnpunten / review-vragen voor de andere AI

1. **Camera spam vs visibility** — throttle + ahead% vs “blijft scheef staan”.
2. **Pitch 3064** — is de schaal/client-API consistent met MMB dragY-scaling?
3. **Far-canvas reclick** — `canvasReclickWithinTiles` vs flag proximity vs korte hops.
4. **Left-click Walk here vs invoke** — hover-detectie betrouwbaar genoeg?
5. **Twin-click** — alleen minimap; canvas mag geen double-spam.
6. **MMB human drag** — 1 pass genoeg? Fallback setYaw te abrupt?
7. **Interaction met NoWalkZones / ExcludedTiles** — path avoid vs click target.

---

## Prompt-suggestie voor de andere AI

> Hier is LoneBot’s walk+camera stack (Java, RuneLite Client API).  
> Doel: menselijk lopen + camera zonder klik-/draai-spam.  
> Lees README.md, daarna de 6 kernbestanden.  
> Lever: (1) diagnose huidige zwaktes, (2) gerichte patches met diffs, (3) geen grote refactor van pathfinder tenzij nodig.  
> Behoud: WalkClickSettings/WalkCameraSettings als tunables; overlay debug-strings.

---

## File list in deze map

- `README.md` (dit bestand)
- `MovementHelper.java`
- `TilePathWalker.java`
- `WalkClickHelper.java`
- `WalkClickSettings.java`
- `WalkCamera.java`
- `WalkCameraSettings.java`
- `Movement.java`
- `WalkUiZones.java`
