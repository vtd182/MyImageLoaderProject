// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
}

group = (findProperty("GROUP") as String?) ?: "com.example.imageloader"
version = (findProperty("VERSION_NAME") as String?) ?: "0.1.0-SNAPSHOT"
