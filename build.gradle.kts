// A common plugin classloader is required by Kotlin's Android/JVM plugins.
// Applying none of these at the root keeps the encoder-only task independent of an SDK.
plugins {
    id("com.android.application") apply false
    kotlin("jvm") apply false
    kotlin("android") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
}
