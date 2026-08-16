pluginManagement {
	repositories {
		maven("https://maven.fabricmc.net/")
		maven("https://maven.kikugie.dev/snapshots")
		mavenCentral()
		gradlePluginPortal()
	}
}

plugins {
	id("dev.kikugie.stonecutter") version "0.9"
}

rootProject.name = "BetterPV"

stonecutter.create(rootProject) {
	version("26.1.2").buildscript = "build.gradle"
	version("26.2").buildscript = "build.gradle"
	vcsVersion = "26.1.2"
}
