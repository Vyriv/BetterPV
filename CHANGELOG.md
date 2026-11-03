# Changelog

## 1.2.1 - Rankings, armour colours and a smarter Missing Accessories list

BetterPV now tracks the Minecraft versions people actually play: 26.3 is supported and 26.1.2 is dropped.

Home gains a Rankings sub-tab with every Elite leaderboard placement for the viewed profile. Leather armour tooltips now name the colour, and the Missing Accessories list was rebuilt to plan real upgrade paths across every storage location.

Jacob contest history no longer depends on Elite. It is read straight from Hypixel profile data, with the SkyBlock year fixed so dates and sorting are correct.

### Added
- **Home > Rankings** sub-tab (`/pv rankings`, also `ranking`, `leaderboard` and `leaderboards`)
  - Every Elite leaderboard placement for the viewed profile in one list, with no Top 1,000 limit, best rank first
  - Coloured rank numbers: #1 aqua, #2 gold, #3 light purple, #4 to #1,000 white, #1,001 and beyond grey
  - Category colours and a matching icon per leaderboard, including the correct head for each pest
  - Icons drawn at native item size so they stay as sharp as normal item icons
  - Rank numbers and values are left-aligned in their own columns
  - Hover shows the exact value and the monthly or weekly interval
  - Clickable "Powered by Elite" credit linking to eliteskyblock.com
  - Loaded only while the sub-tab is open, with a retry on errors
- **Leather armour colours** in item tooltips
  - Hex under the item name, labelled Original, Exotic, Crystal, Fairy, Spook, Glitched or the dye name (for example "Holly Dye")
  - Seymour pieces show their collector tier, closest reference colour and ΔE
  - Very dark colours are lightened just enough to stay readable
  - Shown once when the lore already states the hex
- **Friends List** (`/f list`) hovers keep Hypixel's details ("Last seen...", "Friends for...") and only replace the click action with BetterPV's
- **Party join** chat line gains **[Dungeons]** and **[Kuudra]** shortcuts next to "Click to open X's pv."
- **Missing Accessories**
  - Hotspot family: Talisman, Ring, Artifact
  - Powder family: One, Few, Many, People (NEU lists this chain in reverse)
  - Sort toggle between coins per Magical Power and net price
- **Essence perk descriptions** for Gold, Diamond, Fossil, Forest, Safari and Crimson
- Support for **Minecraft 26.3** (Fabric Loader `>=0.19.3`, Fabric API `0.165.0+26.3`)

### Changed
- **Home Coins stat**: the Bank line is now a combined `Coins: 86.2m / 1.9b` line (purse / bank, one decimal). Hover shows the exact Purse and Bank amounts
- **Missing Accessories** rebuilt
  - Owned accessories are found in the Accessory Bag, Ender Chest, Personal Vault and Time Pocket. Only the bag counts towards Magical Power
  - Moving an owned accessory into the bag is a free step showing its MP gain
  - Upgrades are costed from NEU recipes, Dwarven Forge, NPC shops and trades, including skipped tiers and the cheaper of buying or crafting extra copies
  - Recombobulating is planned when it adds MP, and Rift Prism imbuing and Pandora's Box are handled
  - Follow-up steps are indented directly under the step they depend on
  - Clearer tooltips with price status (priced, stale, soulbound, gameplay progression or unknown)
- **Jacob contests** come from Hypixel profile data (`jacobs_contest.contests`) instead of Elite
  - Contests with fewer than 100 crops collected are ignored, as in game
  - Missing medals are derived from the placement and participant count
  - Medal cutoffs at bracket edges are regression-tested
- **Elite** requests use `api.eliteskyblock.com` (was `api.elitebot.dev`) with a `BetterPV/<version>` User-Agent, and credits link to [eliteskyblock.com](https://eliteskyblock.com)
- **Prices** come from BetterPV's own price feed (`api.vyriv.dev`) with SkyHelper as a fallback
  - Recommendations ignore prices older than 30 minutes (BetterPV) or 90 minutes (SkyHelper)
  - Sacks and materials use the bazaar sell price, so a rare auction listing cannot inflate them
- **Pets**: the missing list also flags pets owned below their highest rarity, for example "Missing Legendary"
- **Networth** tooltip no longer has the "Hold L-shift" per-item breakdown or the "Prices:" source line
- **Name cosmetics**: faster matching and caching, and compatibility with font mods that reuse text objects
- **Kuudra checklist** counts starred Bonemerang and Last Breath
- Hypixel item data also loads when a profile opens, so armour colour labels appear without a restart

### Fixed
- Jacob contest dates: Hypixel's 0-indexed contest year now maps to the correct SkyBlock year
- Home mannequin shows armour skins and dyes, and no longer turns skull helmets into diamond helmets
- Missing Accessories no longer shows inverted Powder upgrades (BetterPV families take priority over NEU)
- Magical Power counts the Rift Prism correctly whether imbued or still in the bag, and recognises Divine rarity
- Item and price data no longer stay unloaded during a Mojang login cooldown when BetterPV is already signed in

### Removed
- Support for **Minecraft 26.1.2**, focusing on 26.2 and 26.3
- Elite contest history fetch and the Jacob tab Elite credit (Elite remains for farming weight and Rankings)
- Duplicate essence flip panels on the Dungeons, Mining, Glacite and Crimson pages, plus the Wither/Undead and Forest essence flips
- Bank transaction list from the Home bank hover. Hypixel only reports co-op bank transactions, which were months out of date

### Supported versions
- Minecraft 26.2
- Minecraft 26.3
