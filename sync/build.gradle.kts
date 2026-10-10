plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    explicitApi()
}

dependencies {
    api(project(":contracts"))
    api(libs.kotlinx.coroutines.core)

    // The OpLog contract suite, shared with :data's instrumented tests.
    testFixturesApi(libs.junit)
    testFixturesApi(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    // PLAN.md §3: convergence of the calendar itself, not just the op set. Tests only; :sync never depends on :schedule.
    testImplementation(project(":schedule"))
    testImplementation(libs.kotlinx.coroutines.test)
}
