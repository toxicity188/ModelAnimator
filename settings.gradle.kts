plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "ModelAnimator"

include(
    "api",
    "dist",

    "scheduler:standard",
    "scheduler:folia",

    "nms:v1_20_R3",
)