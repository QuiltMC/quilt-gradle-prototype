/*
 * Copyright 2023 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.gradle.minecraft;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.quiltmc.gradle.Constants;
import org.quiltmc.gradle.QuiltGradlePlugin;
import org.quiltmc.gradle.api.QuiltGradleExtension;

import java.io.File;

public class MinecraftPlugin implements Plugin<Project> {
	@Override
	public void apply(Project project) {
		project.getPlugins().apply(QuiltGradlePlugin.class);
		project.getLogger().lifecycle("QuiltGradle: Setting up Minecraft plugin");

		QuiltGradleExtension quiltGradle = QuiltGradleExtension.get(project);


		// Setup repos
		File minecraftRepo = new File(quiltGradle.getGlobalCache().get().getAsFile(), MinecraftConstants.REPO);
		minecraftRepo.mkdirs();

		project.getRepositories().ivy(repo -> {
			repo.setName("QuiltGradle: Minecraft Cache");
			repo.setUrl(minecraftRepo);
			repo.patternLayout(layout -> layout.artifact("[revision]/[artifact].[ext]"));
			repo.metadataSources(IvyArtifactRepository.MetadataSources::artifact);
		});

		project.getRepositories().maven(repo -> {
			repo.setName("Mojang Libraries");
			repo.setUrl("https://libraries.minecraft.net");
		});


		// Setup extensions
		MinecraftProvider minecraftProvider = new MinecraftProvider(project, minecraftRepo);
		MinecraftExtension extension = project.getExtensions().create(MinecraftConstants.EXTENSION, MinecraftExtension.class);
		extension.setMinecraftProvider(minecraftProvider);


		// Setup functions to run in every source set
		quiltGradle.registerPerSourceSet(sourceSet -> project.afterEvaluate(unused -> {
			// Provide Minecraft libraries
			Configuration gameConf = quiltGradle.getConfigurationPerSourceSet(Constants.Configurations.GAME, sourceSet);
			Configuration gameLibrariesConf = quiltGradle.getConfigurationPerSourceSet(Constants.Configurations.GAME_LIBRARIES, sourceSet);

			try {
				minecraftProvider.provideLibraries(gameConf, gameLibrariesConf);
			} catch (Exception e) {
				throw new RuntimeException("Failed to provide game libraries", e);
			}
		}));
	}
}
