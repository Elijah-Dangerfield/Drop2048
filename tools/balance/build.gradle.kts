plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

// Build-time only. This module ships nothing: it plays `:libraries:cascade`
// headless so SPEC 5.3's spawn table is a number that was measured rather than
// guessed. It depends on the shipped engine rather than a copy of it, so the
// code that balances the game is the code that runs on device.
application {
    mainClass.set("com.dangerfield.drop2048.tools.balance.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
}

dependencies {
    implementation(projects.libraries.cascade)

    // A harness that quietly stopped measuring anything would look exactly like
    // a harness that found nothing, so the policies are pinned against each
    // other rather than left to be read off a printout.
    testImplementation(libs.kotlin.testJunit)
}
