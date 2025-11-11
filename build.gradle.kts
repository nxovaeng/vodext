import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.gradle.api.artifacts.Configuration
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.13.0")
        classpath("com.github.recloudstream:gradle:master-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/nxovaeng/vodext")
        authors = listOf("nxovaeng")
    }

    android {
        namespace = "nxovaeng"

        defaultConfig {
            minSdk = 21
            compileSdkVersion(33)
            targetSdk = 33
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }


        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8)
                freeCompilerArgs.addAll(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions"
                )
            }
        }
    }

    dependencies {
        configurations.named("implementation").configure {
            dependencies.add(project.dependencies.create(kotlin("stdlib")))
            dependencies.add(project.dependencies.create("com.github.Blatzar:NiceHttp:0.4.4"))
            dependencies.add(project.dependencies.create("org.jsoup:jsoup:1.15.3"))
            dependencies.add(project.dependencies.create("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.3"))
            dependencies.add(project.dependencies.create("com.fasterxml.jackson.core:jackson-databind:2.13.3"))
            dependencies.add(project.dependencies.create("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4"))
            dependencies.add(project.dependencies.create("org.mozilla:rhino:1.7.14"))
            dependencies.add(project.dependencies.create("me.xdrop:fuzzywuzzy:1.4.0"))
            dependencies.add(project.dependencies.create("com.google.code.gson:gson:2.9.0"))
            dependencies.add(project.dependencies.create("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1"))
            dependencies.add(project.dependencies.create("app.cash.quickjs:quickjs-android:0.9.2"))
            dependencies.add(project.dependencies.create("com.github.vidstige:jadb:v1.1.0"))
            dependencies.add(project.dependencies.create("org.bouncycastle:bcpkix-jdk15on:1.70"))
        }

        configurations.named("cloudstream").configure {
            dependencies.add(project.dependencies.create("com.lagradost:cloudstream3:pre-release"))
        }

        // Dependencies are now configured above
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory.get().asFile)
}
