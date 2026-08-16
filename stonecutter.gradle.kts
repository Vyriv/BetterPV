plugins {
	id("dev.kikugie.stonecutter")
	id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT" apply false
}

stonecutter active "26.1.2"

stonecutter parameters {
	replacements {
		// Mojang split screen/HUD ownership off Minecraft in 26.2: Minecraft#screen
		// and Minecraft#setScreen are gone, replaced by Gui#screen()/Gui#setScreen.
		// Canonical source (26.1.2) still targets the old Minecraft-owned API.
		// Regex (not string()) because "client.screen" is also a plain substring of
		// Fabric packages like "api.client.screen" — the negative lookbehind keeps
		// those package/import paths intact.
		regex(current.parsed >= "26.2") {
			replace(
				"(?<!\\.)\\bclient\\.screen\\b" to "client.gui.screen()",
				"client\\.gui\\.screen\\(\\)" to "client.screen"
			)
			replace(
				"\\bclient\\.setScreen\\(" to "client.gui.setScreen(",
				"client\\.gui\\.setScreen\\(" to "client.setScreen("
			)
			replace(
				"\\bminecraft\\.setScreen\\(" to "minecraft.gui.setScreen(",
				"minecraft\\.gui\\.setScreen\\(" to "minecraft.setScreen("
			)
			replace(
				"Minecraft\\.getInstance\\(\\)\\.setScreen\\(" to "Minecraft.getInstance().gui.setScreen(",
				"Minecraft\\.getInstance\\(\\)\\.gui\\.setScreen\\(" to "Minecraft.getInstance().setScreen("
			)
			replace(
				"client\\.gui\\.getTabList\\(\\)" to "client.gui.hud.getTabList()",
				"client\\.gui\\.hud\\.getTabList\\(\\)" to "client.gui.getTabList()"
			)
			replace(
				"client\\.gui\\.getChat\\(\\)" to "client.gui.hud.getChat()",
				"client\\.gui\\.hud\\.getChat\\(\\)" to "client.gui.getChat()"
			)

			// 26.2 collapsed per-color dye constants into Items.DYE : ColorCollection<Item>.
			// Match longer ids first (LIGHT_GRAY before GRAY).
			replace(
				"(?<=Items\\.)LIGHT_GRAY_DYE\\b" to "DYE.lightGray()",
				"DYE\\.lightGray\\(\\)" to "LIGHT_GRAY_DYE"
			)
			replace(
				"(?<=Items\\.)GRAY_DYE\\b" to "DYE.gray()",
				"DYE\\.gray\\(\\)" to "GRAY_DYE"
			)
			replace(
				"(?<=Items\\.)LIME_DYE\\b" to "DYE.lime()",
				"DYE\\.lime\\(\\)" to "LIME_DYE"
			)
			replace(
				"(?<=Items\\.)YELLOW_DYE\\b" to "DYE.yellow()",
				"DYE\\.yellow\\(\\)" to "YELLOW_DYE"
			)
			replace(
				"(?<=Items\\.)LIGHT_GRAY_STAINED_GLASS_PANE\\b" to "STAINED_GLASS_PANE.lightGray()",
				"STAINED_GLASS_PANE\\.lightGray\\(\\)" to "LIGHT_GRAY_STAINED_GLASS_PANE"
			)
			replace(
				"(?<=Items\\.)GRAY_STAINED_GLASS_PANE\\b" to "STAINED_GLASS_PANE.gray()",
				"STAINED_GLASS_PANE\\.gray\\(\\)" to "GRAY_STAINED_GLASS_PANE"
			)
			replace(
				"(?<=Items\\.)BLACK_STAINED_GLASS_PANE\\b" to "STAINED_GLASS_PANE.black()",
				"STAINED_GLASS_PANE\\.black\\(\\)" to "BLACK_STAINED_GLASS_PANE"
			)
		}

		// Minecraft#getVersionType() is gone in 26.2.
		string(current.parsed >= "26.2") {
			replace(
				"client.getVersionType()",
				"(net.minecraft.SharedConstants.getCurrentVersion().stable() ? \"release\" : \"snapshot\")"
			)
		}
	}
}
