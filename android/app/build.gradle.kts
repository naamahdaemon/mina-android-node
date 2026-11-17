import groovy.json.JsonOutput
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.minanode.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.minanode.android"
        minSdk = 33
        targetSdk = 36
        val buildVersionCode = providers.environmentVariable("APK_VERSION_CODE").orElse("1").get().toInt()
        require(buildVersionCode in 1..2_100_000_000) { "Invalid APK_VERSION_CODE" }
        versionCode = buildVersionCode
        versionName = providers.environmentVariable("APK_VERSION_NAME").orElse("0.2.local").get()
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("String", "SOURCE_COMMIT", "\"${providers.environmentVariable("GITHUB_SHA").orNull ?: "local"}\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs.create("distribution") {
        providers.environmentVariable("MINA_SIGNING_KEYSTORE").orNull?.let { storeFile = file(it) }
        storePassword = providers.environmentVariable("MINA_SIGNING_STORE_PASSWORD").orElse("android").get()
        keyPassword = providers.environmentVariable("MINA_SIGNING_KEY_PASSWORD").orElse("android").get()
        keyAlias = providers.environmentVariable("MINA_SIGNING_ALIAS").orElse("androiddebugkey").get()
    }
    buildTypes.getByName("release") {
        isDebuggable = false
        isJniDebuggable = false
        isMinifyEnabled = false
        if (providers.environmentVariable("MINA_SIGNING_KEYSTORE").isPresent) {
            signingConfig = signingConfigs.getByName("distribution")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = true; keepDebugSymbols += "**/*.so" } }
    lint { abortOnError = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250107")
}

// Resolve the actual release runtime graph, including transitive Android variants.
// This task only resolves metadata; it never needs signing secrets or native binaries.
tasks.register("exportReleaseDependencies") {
    val output = layout.buildDirectory.file("reports/licenses/release-dependencies.json")
    outputs.file(output)
    outputs.upToDateWhen { false }
    doLast {
        val runtime = configurations.getByName("releaseRuntimeClasspath")
        val components = runtime.incoming.resolutionResult.allComponents.map { it.id }
            .filterIsInstance<ModuleComponentIdentifier>()
        val poms = dependencies.createArtifactResolutionQuery().forComponents(components)
            .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java).execute()
            .resolvedComponents.associate { component ->
                component.id.displayName to component.getArtifacts(MavenPomArtifact::class.java)
                    .filterIsInstance<ResolvedArtifactResult>().single().file.absolutePath
            }
        val rows = runtime.resolvedConfiguration.resolvedArtifacts
            .groupBy { it.moduleVersion.id }.map { (id, artifacts) ->
                mapOf("coordinate" to "${id.group}:${id.name}:${id.version}",
                    "pom" to requireNotNull(poms["${id.group}:${id.name}:${id.version}"]) { "Missing POM for $id" },
                    "artifacts" to artifacts.map { it.file.absolutePath }.sorted())
            }.sortedBy { it["coordinate"].toString() }
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(rows)) + "\n")
        }
    }
}
