# LoneBot â€” AI share (subset)

Private mirror voor code-review / AI. **Geen launcher**, geen start-bats, geen accounts/2FA/secrets.

## Inhoud

- `api/`, `sdk/`
- `client/` â€” RuneLite plugin + `com.lonebot.client` (zonder `com.lonebot.launcher`)
- `script-*`, `example-plugin/`
- `docs/` (o.a. API-blauwdruk)
- Gradle root-bestanden (context)

## Niet inbegrepen

- Launcher UI / Jagex OAuth / 2FA dashboard (`com.lonebot.launcher`)
- `Start-LoneBot.bat`, `Restart-*.bat`, `Rebuild-*.bat`
- `update/`, `tools/`, `backups/`, `reports/`, accounts JSON

## Bijwerken (vanaf jouw LoneBot-repo)

```powershell
.\Sync-AiShare.ps1 -Commit
# of met push (remote moet bestaan):
.\Sync-AiShare.ps1 -Commit -Push
```

Gegenereerd: 2026-09-30 21:03
