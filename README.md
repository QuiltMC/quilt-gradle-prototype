# Quilt Gradle

```groovy
plugins {
    id 'org.quiltmc.gradle' version '0.1.0'
}

group = 'org.example'
version = '0.1.0-SNAPSHOT'

dependencies {
    game minecraft.client('1.19.3')

    intermediate 'org.quiltmc:hashed:1.19.3'
    mappings 'org.quiltmc:quilt-mappings:1.19.3+build.9'
    via 'net.fabricmc:intermediary:1.19.3:v2'

    loader 'org.quiltmc:quilt-loader:0.17.8'

    modImplementation 'org.quiltmc:qsl:4.0.0-beta.3+1.19.3'
}

```
