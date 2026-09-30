# LoneBot pathfinder transports (Shortest Path)

Bron: https://github.com/Skretzo/shortest-path `src/main/resources/transports/`

## Actief geladen
Zie `TransportTsvLoader.FILES` — object/NPC-edges met Origin+Destination
(trappen, deuren, poorten, boten, portals, agility, …).

## Niet als walk-edge
`teleportation_spells.tsv`, `teleportation_items.tsv` — geen Origin-tegel;
horen bij `WalkOptions.useTeleports` / TeleportLoader (later).

## Legacy fallback
- Bestand: `f2p_gates.legacy.tsv` (oude F2P-only subset)
- JVM: `-Dlonebot.walk.legacyTransports=true`
