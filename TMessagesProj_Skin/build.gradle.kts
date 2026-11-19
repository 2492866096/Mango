import com.android.build.api.variant.HostTestBuilder

// The redesigned presentation layer. It draws with Compose and knows nothing
// about Telegram's engine, so its screens can be rendered from fake data on a
// plain JVM (see the screenshotTest suite) without building the app.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

repositories {
    google()
    mavenCentral()
}

val composeBom = "androidx.compose:compose-bom:2026.09.00"

android {
    namespace = "org.telegram.skin"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        // The app also builds a "standalone" type; give it a match here.
        create("standalone") {
            initWith(getByName("release"))
        }
    }

    testOptions {
        // Preview rendering resolves fonts and other resources through them.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Pass -Pskin.regen=true to rewrite the generated legacy theme files.
            it.systemProperty("skin.regen", providers.gradleProperty("skin.regen").getOrElse("false"))
        }
        screenshotTests.create("screenshotTest") {
            engineVersion = "0.0.1-alpha17"
            targetVariants.add("debug")
            dependencies {
                implementation(project.dependencies.platform(composeBom))
                implementation("androidx.compose.ui:ui-tooling")
                implementation("com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha17")
            }
        }
    }
}

androidComponents {
    // AGP 9 only creates unit tests for the tested build type, but the preview
    // renderer behind the screenshot suite is set up for every variant and
    // refuses to configure without them.
    beforeVariants { variant ->
        variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}

dependencies {
    implementation(platform(composeBom))
    implementation("androidx.compose.foundation:foundation")
    testImplementation("junit:junit:4.13.2")
}
