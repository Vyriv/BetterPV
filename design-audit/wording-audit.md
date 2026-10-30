# BetterPV wording audit

Scope: every player-facing string in `src/main/java` and `assets/betterpv/lang/en_us.json`. Comments, logger output, tests, regexes, item/mob/NPC names and API keys were excluded unless they reach the UI.

## Totals

| | Count |
|---|---|
| Java string literals scanned | 2,659 |
| Lang keys scanned | 169 |
| Player-facing prose and label strings read in context | 1,115 (538 sentences/tooltips, 577 short labels) |
| Issues found | 103 |
| Changed or removed | 95 |
| Left as-is, needs approval | 8 |

"Meaning" column: **No** = same information, just worded better. **Minor** = small nuance dropped or added, explained in the reason. **Yes** = not applied, listed under approval.

## Garden

| File:line | Current | Proposed | Reason | Meaning |
|---|---|---|---|---|
| `JacobPage.java:324` | Contest history loading | Loading contests... | Matches the "Loading contests..." text on the same page. | No |
| `JacobPage.java:329` | No recorded contests for this crop | No contests for this crop | "Recorded" is filler. | No |
| `JacobPage.java:338` | None recorded | None | Filler. The unclaimed line below already explains why. | No |
| `JacobPage.java:342` | 10 unclaimed, placement unknown | 10 unclaimed contests | Requested. Unclaimed already implies no placement. | No |
| `JacobPage.java:344` | Best of recorded history only | (removed) | Requested. Defensive, says nothing useful. | Minor |
| `GardenSummaryPage.java:59` | Garden data unavailable: `<error>` | `<error>` | Error strings now read "Couldn't load garden" on their own, so the prefix doubled up. | No |
| `GardenSummaryPage.java:79` | Calculated stats | Stats | "Calculated" is technical. The "Estimate, hover for details" note under it keeps the caveat. | No |
| `MiningSummaryPage.java:79` | Calculated stats | Stats | Same as the Garden card. | No |
| `GardenSummaryPage.java:117` | Medal counts are unspent medals | Unspent medals only | Shorter, same meaning. | No |
| `CropsPage.java:191` | Unpurchased levels only, all upgrades | Remaining levels, all upgrades | Simpler. | No |
| `CropsPage.java:235` | Price depends on Basic plots owned | Cost scales with Basic plots owned | "Cost" matches the "Unlock cost" row above. | No |
| `GardenSnapshot.java:407` | Garden load failed | Couldn't load garden | Plain error wording. | No |
| `ProfileScreenDataLoader.java:254` | Garden fetch failed | Couldn't load garden | Technical. | No |
| `ProfileScreenDataLoader.java:266` | Garden parse failed | Couldn't load garden | Technical. "Parse" means nothing to players. | No |
| `ProfileScreenDataLoader.java:321` | Contest parse failed | Couldn't load contests | Technical. | No |
| `ProfileScreenDataLoader.java:367` | Weight parse failed | Couldn't load weight | Technical. | No |
| `ProfileScreenDataLoader.java:234,280,335` | Missing profile id | No profile selected | Developer wording. | No |
| `lang sub.garden_summary` | Overview (Trial) | (unchanged in the audit pass) | See approval list and decisions. | - |
| `lang garden.elite_credit`, `GardenOverviewPage.java:336` | Contest history provided by eliteskyblock.com / Provided by eliteskyblock.com | (unchanged) | Required credit, fine as written. | - |

## Home, Misc Stats, Records

| File:line | Current | Proposed | Reason | Meaning |
|---|---|---|---|---|
| `HomeLeftColumn.java:556` | Calculated from profile data. | (removed) | Explains how BetterPV works rather than the stat. The "Excludes held item..." lines below keep the real limitation. | No |
| `HomeLeftColumn.java:556` | Not available from the API. | Not in the API | Shorter, no period, consistent with other meta lines. | No |
| `SkillStatRows.java:24` | Calculated from profile data. | (removed) | Same as above. Used by Mining and Garden overview stats. | No |
| `SkillStatRows.java:29` | Not available from the API. | Not in the API | Same as above. | No |
| `HomeTooltips.java:116` | No recent transactions (API off or empty) | No transactions, or Banking API off | Names the actual API setting. Keeps both possibilities. | No |
| `MiscStatsPage.java:281` | Loaded with player rank | Not loaded yet | Old text described load order. Players only need to know it isn't ready. | No |
| `MiscStatsPage.java:318` | No deaths recorded / Total kills / total deaths | (removed) | The Kills, Deaths and K/D rows sit directly above. Explaining the formula is noise. | No |
| `MiscStatsPage.java:741` | No kills recorded (empty list state) | No kills | "Recorded" filler. | No |
| `RecordsPage.java:154` | Best Recorded Spooky Festival | Best Spooky Festival | "Recorded" wording again. The incomplete-history case still shows "History covers X / Y candy". | No |
| `ProfileViewerScreen.java:237` | Profile fetch failed | Couldn't load profile | Technical. | No |
| `ProfileViewerScreen.java:622` | Loading Data | Loading data | Sentence case like every other loading state. | No |
| `lang screen.flip_tip` | Tip: some panels are clickable to display more data! | Tip: some panels can be clicked for more info | Less clunky, drops the exclamation mark. | No |
| `HomeLeftColumn.java:558`, `MiningSummaryPage.java:24`, `GardenSummaryPage.java:20` | Excludes held item/tool, set bonuses, temporary buffs and location bonuses | (unchanged) | Real limitation, already reads fine. | - |
| `HomeSbXpOverlay.java:276` | Coming soon | (unchanged) | Placeholder for the emblem list. Fine until that ships. | - |

## Storage, Loadouts, Networth, Item values

| File:line | Current | Proposed | Reason | Meaning |
|---|---|---|---|---|
| `InventoryPage.java:1076,1094` | Heart of the Mountain Preset 1 | HOTM Preset 1 | Requested. | No |
| `InventoryPage.java:1079,1097` | Heart of the Forest Preset 1 | HOTF Preset 1 | Requested. | No |
| `InventoryPage.java:1082-1083` | Mining and Foraging tabs show the / currently selected preset. | HOTM/HOTF tabs show the active preset | One line instead of two. Keeps the warning that the tabs may not match this loadout. | No |
| `InventoryPage.java:1100` | No skill tree preset saved | No HOTM/HOTF preset | Names the actual trees. | No |
| `InventoryPage.java:889` | 12 without a price | 12 unpriced | Matches "Unpriced" used in Minions and elsewhere. | No |
| `lang inv.state.not_in_api` | Not available in API data | Not in the API | Shorter. | No |
| `lang inv.search_hint` | Search for item | Search items | Matches "Search families..." and "Search player...". | No |
| `NetworthBreakdown.java:129` | Prices: BetterPV price API, SkyHelper fallback | Prices: BetterPV, SkyHelper fallback | "price API" is implementation detail. Source is still credited. | No |
| `NetworthCalculator.java:146` | Inventory API may be disabled | Inventory API may be off | Matches "API off" wording used elsewhere. | No |
| `ItemValueOverlay.java:169` | No priced components. | No priced components | No trailing period on single-line states. | No |
| `NetworthBreakdown.java:133` | I do not condone IRL Trading | (unchanged) | See approval list. | - |
| `ItemTooltipEnricher.java:132` | Estimated value: | (unchanged) | Value really is an estimate. | - |

## Mining and Foraging

| File:line | Current | Proposed | Reason | Meaning |
|---|---|---|---|---|
| `MiningOverviewPage.java:256` | Derived | From crystals | "Derived" doesn't say from what. The count comes from crystals placed. | No |
| `GlacitePage.java:312` | Need for tier 3 | Tier 3 needs | Reads naturally. | No |
| `HotfPage.java:109` | Daily Tree Gifts | Tree gifts | Title Case clashed with "Trees cut" right above it. "Daily" is already the section header. | No |
| `HotfPage.java:272` | Not in the NEU tree layout yet, max level unknown | Max level unknown | NEU layout is an implementation detail. The real limitation stays. | No |

## Essence

| File:line | Current | Proposed | Reason | Meaning |
|---|---|---|---|---|
| `EssencePage.java:185` | Shop data is still loading. | Loading shop... | Standard loading wording. | No |
| `EssencePage.java:193` | Every perk maxed | All perks maxed | More natural. | No |
| `EssencePage.java:203` | Balance covers every remaining tier | Enough to max | Says the same thing in three words. | No |
| `EssencePage.java:207` | 1 perk has no cost data and is not counted. | 1 perk has an unknown cost, not counted | Shorter. Still says the total is incomplete. | No |
| `EssencePage.java:208` | N perks have no cost data and are not counted. | N perks have unknown costs, not counted | Same. | No |

## Events

| File:line | Current | Proposed | Reason | Meaning |
|---|---|---|---|---|
| `BingoPage.java:83` | Sum across all bingo events | All bingo events | "Total" is already in the title. | No |
| `ChocolatePage.java:399` | No API data | No data | Shorter. Tooltip still names the API. | No |
| `ChocolatePage.java:401` | This profile has no Chocobit data in the API | No Chocobit data in the API | Drops "This profile has". | No |
| `ChocolatePage.java:519` | , no expiry recorded | , expiry unknown | Clearer that it's missing data, not "never expires". | No |
| `ChocolatePage.java:526` | SkyBlock years. Current: Year 412 | Current year: 412 | Reads as a sentence fragment plus a label. | No |
| `ProfileScreenDataLoader.java:485,519` | Bingo fetch failed / Bingo parse failed | Couldn't load bingo | Technical. | No |
| `ChocolatePage.java:222,493` | Uncollected eggs from rabbit hitmen / waiting for hitmen | (unchanged) | Fine. | - |

## Dungeons

| File:line | Current | Proposed | Reason | Meaning |
|---|---|---|---|---|
| `DungeonChestsPage.java:178` | Hypixel keeps only recent runs here | No recent runs | Empty states should say what's empty. "Recent" keeps the limit. | Minor |
| `DungeonCalcOverlay.java:222` | Timed out (hit run cap). | Hit the run cap | Drop the period and the double statement. | No |
| `DungeonCalcOverlay.java:224` | Selected runs per class: | Runs per class: | "Selected" is filler. | No |
| `DungeonCalcOverlay.java:258` | Already at or above target. | Already at target | Shorter. | No |
| `DungeonCalcOverlay.java:260` | No runnable floors found. | No floors available | Less technical. | No |
| `lang dungeons.master` | Mastermode | Master Mode | Hypixel spells it "Master Mode". | No |
| `lang dungeons.master_floor` | Mastermode Floor %s | Master Mode Floor %s | Same. The label is width-trimmed already. | No |
| `lang dungeons.calc.level_hint` | EG: 50/C55 | e.g. 50/C55 | Normal abbreviation. Field text is width-trimmed. | No |
| `lang dungeons.calc.class_hint` | EG: M55/Bers 45/CA50 | e.g. M55/Bers 45/CA50 | Same. | No |
| `lang dungeons.calc.level_help` | Put the catacombs level you want (including overflow like C55) into the box and press calculate, auto detects modifiers | Enter the Cata level you want (overflow like C55 works) and press Calc. Modifiers are detected automatically. | Comma splice, wrong button name. | No |
| `lang dungeons.calc.class_help` | Put in either your desired class level (overflow OK, e.g. Mage 55) or CA50. CA50 stays capped at 50 and plans equal runs across classes | Enter a class level (overflow OK, e.g. Mage 55) or CA50. CA50 caps at 50 and splits runs evenly across classes. | Tighter. | No |
| `DungeonCalcOverlay.java:219,264` | (S+ est.) | (unchanged) | Real estimate. | - |

## Crimson

| File:line | Current | Proposed | Reason | Meaning |
|---|---|---|---|---|
| `AbiphonePage.java:172` | Name: missing contacts A to Z | Name: A to Z | The list already only shows missing contacts. | No |
| `AbiphonePage.java:173-174` | Cheapest: lowest estimated coin cost / first, unpriced requirements last | Cheapest: lowest cost first, unpriced last | One line instead of two. | No |
| `AbiphonePage.java:285` | No coin estimate for this requirement | Cost unknown | Shorter. Matches "Est. cost:" on priced contacts. | No |
| `CrimsonKuudraCard.java:227-229` | 1 level = 100 Kuudra Score. / Kuudra Score is calculated as follows: / basic → 0.5, hot → 1, burning → 2, fiery → 4, infernal → 8 | 1 level = 100 Kuudra Score / Score per completion: / Basic 0.5, Hot 1, Burning 2, Fiery 4, Infernal 8 | Drops "calculated as follows". Names tiers as in game. | No |
| `CrimsonKuudraCard.java:226` | The level in front of your nickname is your Kuudra Level. | (unchanged) | See approval list. | - |

## Pets, Museum, Collections, Auctions

| File:line | Current | Proposed | Reason | Meaning |
|---|---|---|---|---|
| `PetsPage.java:222` | No rules configured | No rules set | Less formal. | No |
| `MuseumPage.java:322` | Open this tab to load museum | Museum not loaded | Was shown while already on the tab. | No |
| `ProfileScreenDataLoader.java:377,427` | Missing profile id / Museum parse failed | No profile selected / Couldn't load museum | Technical. | No |
| `lang collections.minions_all_crafted` | Every minion tier crafted | All tiers crafted | Shorter, it's on the Minions page. | No |
| `lang collections.minions_cost_note` | Materials only, from BetterPV prices | Material cost only | Source detail isn't useful here. "Only" keeps the caveat. | Minor |
| `lang auctions.all_profiles_tip` | SkyCofl history is per account, not per profile. | SkyCofl history covers all profiles | Shorter, no period. | No |
| `lang auctions.all_profiles_tip2` | Hover a row to see which profile it came from. | Hover a row to see its profile | Shorter. | No |
| `lang auctions.all_profiles_tip3` | Dimmed rows belong to another profile. | Dimmed rows are from other profiles | No period. | No |
| `lang auctions.all_profiles_tip4` | Profile lifetime stats are tracked by Hypixel. | Lifetime stats come from Hypixel | Shorter. | No |
| `lang auctions.profile_unknown` | Profile not verified | Unknown profile | Matches "This profile" / "Another profile". | No |
| `lang auctions.fees` | AH tax / fees paid | AH fees paid | The tax is the fee. | No |

## Chat messages

| File:line | Current | Proposed | Reason | Meaning |
|---|---|---|---|---|
| `BetterPVClient.java:137` | BetterPV: another mod kept /pv. Use /betterpv pv (or /betterpv pv <name>). | BetterPV: another mod uses /pv. Use /betterpv pv [name] instead. | Nested parentheses. `[name]` shows it's optional. | No |
| `BetterPvSessionAuth.java:88` | BetterPV authentication is temporarily rate-limited by Minecraft. Wait a minute before retrying /pv. BetterPV will not retry authentication until the cooldown expires. | BetterPV authentication is rate-limited by Minecraft. Wait a minute before retrying /pv. | Third sentence repeats the second. | No |
| `BetterPvSessionAuth.java:89` | BetterPV authentication deferred until your Minecraft server connection is ready. | BetterPV authentication will start once your server connection is ready. | "Deferred" is formal. | No |
| `BetterPvSessionAuth.java:91` | BetterPV authentication is temporarily paused. Wait a minute before retrying /pv. | BetterPV authentication is paused. Wait a minute before retrying /pv. | "Temporarily" plus "wait a minute" says it twice. | No |
| `BetterPvSessionAuth.java:84-87,90,92-94` | ...could not authenticate /pv (missing JWT) etc. | (unchanged) | See approval list. | - |

## Needs approval (not changed)

1. **"(Trial)" tab labels** (`lang sub.garden_summary`, `sub.mining_summary`, `sub.fishing_trophies`). They mark the opt-in trial layouts. Drop the suffix once trials graduate.
2. **"I do not condone IRL Trading"** (`NetworthBreakdown.java:133`). This is a personality line under the IRL value estimate. Keep, or replace with something like "Estimate only, IRL trading is bannable".
3. **Kuudra nickname line** (`CrimsonKuudraCard.java:226`). It claims the level shows "in front of your nickname", but Hypixel doesn't do that and BetterPV shows it as "[Level: n]". It probably came from another mod. Suggest removing it, but it's your call.
4. **Auth failure codes** (`BetterPvSessionAuth.java:84-94`). Text such as "(missing JWT)" and "(auth HTTP error)" is technical, but it works as a support code when users report problems. Suggest keeping.
5. **"Coming soon"** (`HomeSbXpOverlay.java:276`). Fine as a placeholder, but it ships to players.
6. **Easter eggs** (`LoadingEgg`, `FakeBanScreen`). The casual typos ("Im", "Dont", "whats") and the fake Hypixel ban text are intentional, so they weren't touched.
7. **Unused lang keys** (`dungeons.calc_placeholder` "calc soon", `home.skills` "SKILLS", `home.slayers` "SLAYERS"). Not referenced anywhere. Could be deleted, but that's cleanup rather than wording.
8. **Ellipsis style.** Loading text mixes "..." and "…" across pages. Both render fine, and unifying them would touch about 20 strings for no visible gain.

## Decisions

Reviewed 2026-10-11. Applied in a separate commit after the audit changes.

| # | Item | Decision | Result |
|---|---|---|---|
| 1 | "(Trial)" tab labels | Remove the suffix. Trial flags and original screens stay. | Fishing tab: "Trophies (Trial)" becomes "Trophies". Garden and Mining summary tabs become "Summary", because they sit next to the original "Overview" tab and would otherwise share its hover label. |
| 2 | IRL trading disclaimer | Keep | Unchanged |
| 3 | Kuudra nickname line | Remove | `CrimsonKuudraCard.java` line removed. The score tooltip starts at "1 level = 100 Kuudra Score". |
| 4 | Auth failure codes | Keep for troubleshooting | Unchanged |
| 5 | SkyBlock XP "Coming soon" | Keep for now | Unchanged |
| 6 | Easter eggs | Keep wording | Unchanged |
| 7 | Unused lang keys | Remove after confirming no references | `home.skills`, `home.slayers` and `dungeons.calc_placeholder` had no references in code or resources and are removed. |
| 8 | Ellipsis style | Standardise loading indicators to "..." | 28 loading strings and placeholders in 16 files now use "...". This includes the bare "…" pending values for networth, weight, skill average and the guild/status buttons. The weight placeholder check in `HomeLeftColumn` changed with its producer. Search hints, truncation ellipses, comments and logs keep "…". |
