# Quilt Gradle

```groovy
plugins {
    id 'org.quiltmc.gradle.minecraft' version '0.1.0'
}

group = 'org.example'
version = '0.1.0'

dependencies {
    game minecraft.merged('1.19.3')

    intermediate 'org.quiltmc:hashed:1.19.3'
    mappings 'org.quiltmc:quilt-mappings:1.19.3+build.26'
    via 'net.fabricmc:intermediary:1.19.3:v2'

    loader 'org.quiltmc:quilt-loader:0.18.3'

    modImplementation 'org.quiltmc:qsl:4.0.0-beta.12+1.19.3'
}

```
