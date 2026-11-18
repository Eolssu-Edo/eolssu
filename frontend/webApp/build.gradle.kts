import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenExec

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))

            implementation(libs.compose.ui)
            implementation(libs.androidx.navigation.compose)
        }
    }
}

tasks.named<BinaryenExec>("compileProductionExecutableKotlinWasmJsOptimize") {
    // Coalesce locals once after the default optimization passes.
    binaryenArguments.addAll("--skip-pass=coalesce-locals", "--coalesce-locals-learning")
}
