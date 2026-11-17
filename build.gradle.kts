plugins {
    id("com.android.application") version "8.10.1" apply false
    id("com.android.library") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.compose") version "1.7.3" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.11"
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

dependencies {
    kover(project(":shared"))
    kover(project(":app"))
}

subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
        parallel = true
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        setSource(files("src/main/java", "src/main/kotlin", "src/commonMain/kotlin", "src/androidMain/kotlin", "src/iosMain/kotlin"))
        include("**/*.kt")
        exclude("**/build/**")
        baseline.set(file("detekt-baseline.xml"))
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(true)
        }
    }

    tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
        setSource(files("src/main/java", "src/main/kotlin", "src/commonMain/kotlin", "src/androidMain/kotlin", "src/iosMain/kotlin"))
        include("**/*.kt")
        exclude("**/build/**")
        baseline.set(file("detekt-baseline.xml"))
    }
}
