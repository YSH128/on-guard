// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("de.undercouch:gradle-download-task:5.4.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}