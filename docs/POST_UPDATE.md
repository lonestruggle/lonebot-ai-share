# LoneBot — post-update (na OSRS / RuneLite)

LoneBot is een **RuneLite-shell** (Maven `net.runelite:client` + `runelite-api`).  
Na een game- of RuneLite-update draai je deze pipeline. Die doet **geen** eigen gamepack-deob/mixin/packet-remap — dat doet RuneLite. Jullie checken compile + API/ID-breuken.

## Wanneer draaien

- RuneLite heeft een nieuwe release / OSRS net geüpdatet
- Client start niet of plugins gedragen zich vreemd na update
- Voor je een LoneBot-release uitrolt naar vrienden

## Commando

```bat
cd C:\Users\lonestruggle\Desktop\lonebot-client
scripts\post-update.bat
```

Opties:

```bat
scripts\post-update.bat -PackRelease
scripts\post-update.bat -CombatBotRoot "C:\Users\lonestruggle\Desktop\storm2-example-plugin-main\example-looped-plugin"
scripts\post-update.bat -SkipCompile
scripts\post-update.bat -SkipPin
```

| Flag | Betekenis |
|------|-----------|
| `-PackRelease` | Na OK-compile: `pack-release.bat` (zip voor GitHub Release) |
| `-CombatBotRoot` | Pad naar CombatBot Java-root of storm2-project |
| `-SkipCompile` | Alleen resolve/diff/scan |
| `-SkipPin` | `libs.versions.toml` niet wijzigen |

## Wat het script doet

1. Leest huidige pin uit `gradle/libs.versions.toml`
2. Resolve Maven **release** van `runelite-api` (`repo.runelite.net`)
3. Pindt die concrete versie in `libs.versions.toml` (geen `latest.release`)
4. `gradlew --refresh-dependencies :api:compileJava :sdk:compileJava :client:compileJava`
5. Diff `runelite-api` jar (oud vs nieuw): `ComponentID` / `MenuAction` / `gameval` / `interfaces.toml`
6. Scant hardcoded IDs in `sdk/`, `api/`, optioneel CombatBot → CSV
7. **Named ID verify** — `ComponentID.X` / `ObjectID.Y` / … nog aanwezig in huidige RL jar?
8. Schrijft rapport onder `reports/`
9. Optioneel: pack release

## Smoke-checklist (in-game)

Post-update kan **niet** bewijzen dat bank/walk/combat live werkt. Daarvoor:

1. Start LoneBot + **test-account**
2. Dubbelklik bureaublad **LoneBot Smoke-Checklist** (of `scripts\smoke-checklist.bat`)
3. Per stap: **O** = OK, **F** = FAIL, **S** = SKIP
4. Rapport: `reports/smoke-YYYYMMDD-HHmm.md`

| Wat | Automatisch? |
|-----|----------------|
| Compile + RL pin | post-update |
| Named constants weg/hernoemd | post-update named-id-verify |
| Losse ints (819, 203, …) nog juist | smoke / RL-diff interfaces |
| Walk, bank, dialog, combat | smoke-checklist |

## Exit codes

| Code | Betekenis |
|------|-----------|
| `0` | LoneBot compile OK (API-diffs / ID-hits zijn warnings) |
| `1` | LoneBot compile FAIL — fix API-breaks eerst |

Smoke-checklist: `0` = geen FAIL; `1` = minstens één FAIL.

## Rapporten

Alles in `reports/` (gitignored):

| Bestand | Inhoud |
|---------|--------|
| `update-YYYYMMDD-HHmm.md` | Samenvatting |
| `rl-api-diff.txt` | +/−/~ in API-artefacts |
| `hardcoded-ids.csv` | Alle hits om te verifiëren |
| `named-id-verify.txt` | Missing named RL constants |
| `smoke-*.md` | Jouw in-game checklist-resultaten |
| `last-runelite-version.txt` | Vorige pin (voor volgende diff) |
| `compile-*.log` | Gradle output |

## FAIL — wat nu?

1. Open `reports/compile-*.log` — welke class/methode breekt?
2. Fix LoneBot `sdk`/`api`/`client` tegen nieuwe RuneLite API.
3. Script opnieuw draaien tot exit `0`.
4. Bekijk `rl-api-diff.txt`, `named-id-verify.txt`, `hardcoded-ids.csv`.
5. Smoke-checklist in-game (`LoneBot Smoke-Checklist`).
6. Release: bump `LoneBotBootstrapPlugin.VERSION` + `update/version.json`, dan `scripts\pack-release.bat` (of `-PackRelease`).

## Scope-grenzen

- Geen bytecode-updater / packet-order remap
- Geen automatische ID-rewrites in CombatBot (alleen detectie)
- Geen GitHub push / Release create
- Smoke is handmatig: geen headless in-game bot-test
