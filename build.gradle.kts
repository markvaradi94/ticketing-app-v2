// Each service has its own build file; the root only resolves the plugin versions once for both.
plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}
