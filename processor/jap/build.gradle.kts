plugins {
    id("yatagan.implementation-artifact")
}

dependencies {
    implementation(project(":api:public"))
    implementation(project(":processor:common"))
    implementation(project(":lang:jap"))
    implementation(project(":base:impl"))
}