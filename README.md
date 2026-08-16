# BetterPV
SkyBlock Profile Viewer for Fabric

A client-side Hypixel SkyBlock profile viewer for Minecraft **26.1.2** and **26.2**. Peek at a profile without leaving the game: yours with `/pv`, or anyone else's with `/pv <username>`.

Use the buttons along the top to switch pages. When a page has categories, the buttons on the left switch between them.

## What it's for
- Networth, weight, skills, and progress at a glance
- Inventories, pets, auctions, and more island pages as they land
- Clean enough to screenshot and share

## Credits
- **[NotEnoughUpdates Repo](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO)**: item data, leveling tables, and other SkyBlock reference data
- **[SkyCofl](https://sky.coflnet.com/)**: auction history
- **[EliteBot](https://elitebot.dev/)**: garden contests and farming weight

## Status
Work in progress. Home, dungeons, inventories, pets, auctions, collections, and minions are in; more pages (garden, mining, and others) are still coming.

Fabric 26.1.2 / 26.2 · Client-side

## Building
Builds both Minecraft versions into `dist/`:

```powershell
.\gradlew.bat :26.1.2:buildAndCollect :26.2:buildAndCollect
```

Run a specific version:

```powershell
.\gradlew.bat :26.1.2:runClient
.\gradlew.bat :26.2:runClient
```
 
 ## About
 BetterPV is developed and maintained by **vyriv** (Minecraft IGN: `catgirllivid` / `vyriv`).
