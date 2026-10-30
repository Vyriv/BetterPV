# Changelog

## 1.2.16 — Jacob contest key year

Hypixel contest keys store a 0-indexed year; parsing now applies `year + 1` for the SkyBlock calendar year (same as farming-weight `SkyBlockTime.from(year + 1, month, day)`).

### Fixed
- Jacob contest dates: 0-indexed key year → SkyBlock year (`+ 1`) when deriving unix timestamps

## 1.2.15 — Home mannequin armor preview

Home player preview keeps skull helmets as textured heads, promotes body pieces to real armor for CIT, and stamps live `ExtraAttributes.skin` / `dye_item` so armor cosmetics show on the mannequin.

### Fixed
- Mannequin: preserve armor skins/dyes; do not turn skull helmets into diamond helmets

## 1.2.14 — Jacob medal bracket boundaries

Documented and regression-tested Jacob medal derivation at bracket edges for 0-based `claimed_position` (Hypixel +1 slot / Elite `<= floor(n * pct)`).

### Changed
- Clarify medal cutoffs; add inclusive boundary tests so `<` or 1-based comparisons cannot silently regress

## 1.2.13 — Elite User-Agent

Elite API requests now send an identifiable `BetterPV/<version>` User-Agent.

### Changed
- Elite API client: set `User-Agent` to `BetterPV/<mod version>` on weight requests

## 1.2.12 — Jacob contests from Hypixel

Jacob contest history no longer depends on Elite’s contests API. The Jacob tab reads participations straight from Hypixel profile data (`jacobs_contest.contests`), with dates derived from SkyBlock contest keys so the list still sorts newest-first.

### Changed
- Contest history source: Hypixel SkyBlock profile data instead of Elite `/contests`
- Contest timestamps inferred from Hypixel contest keys (`year:month_day:crop`) for relative dates and sorting
- Contests with fewer than 100 crops collected are ignored (same as in-game)
- Missing `claimed_medal` is derived from `claimed_position` / `claimed_participants`

### Removed
- Elite contest history fetch, parse path, and Jacob tab credit line (Elite remains for farming weight only)

## 1.2.11 — Elite attribution update

### Changed
- Elite attribution credits now link to [eliteskyblock.com](https://eliteskyblock.com) (was elitebot.dev)

## 1.2.1 — Minecraft support update

BetterPV now tracks the Minecraft versions people actually play. No fancy feature work this time — just keeping the mod on the right releases.

### Added
- Support for **Minecraft 26.3** (Fabric Loader `>=0.19.3`, Fabric API `0.165.0+26.3`)
- 26.3 compile fixes: authlib `SessionService` (was `MinecraftSessionService`) and `InputConstants.isKeyDown(int)`

### Removed
- Support for **Minecraft 26.1.2** — focusing on 26.2 and 26.3

### Supported versions
- Minecraft 26.2
- Minecraft 26.3
