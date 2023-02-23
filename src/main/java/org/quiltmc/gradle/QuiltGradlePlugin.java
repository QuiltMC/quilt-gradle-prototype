/*
 * Copyright 2022-2023 QuiltMC
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

package org.quiltmc.gradle;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.quiltmc.gradle.task.*;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class QuiltGradlePlugin implements Plugin<Project> {
	private Project project;
	private File projectCache;
	private File globalCache;
	private File remappedRepo;
	private File globalRepo;

	public Map<SourceSet, MappingsProvider> mappingsProviders = new HashMap<>();

	@Override
    public void apply(Project project) {
		this.project = project;

		project.getLogger().lifecycle("Quilt Gradle v${version}");


		// Apply default plugins
        project.getPlugins().apply(JavaLibraryPlugin.class);
        project.getPlugins().apply(IdeaPlugin.class);
        project.getPlugins().apply(EclipsePlugin.class);


		// Setup caches
		projectCache = new File(project.getProjectDir(), Constants.Locations.PROJECT_CACHE);
		globalCache = new File(project.getGradle().getGradleUserHomeDir(), Constants.Locations.GLOBAL_CACHE);
		remappedRepo = new File(projectCache, Constants.Locations.REMAPPED_REPO);
		globalRepo = new File(globalCache, Constants.Locations.MINECRAFT_REPO);

		if (!remappedRepo.exists()) {
			remappedRepo.mkdirs();
		}

		if (!globalRepo.exists()) {
			globalRepo.mkdirs();
		}


		// Setup repositories
		project.getRepositories().flatDir(repo -> {
			repo.setName("Quilt Gradle: Remapped Dependencies");
			repo.setDirs(Collections.singleton(remappedRepo));
		});

		project.getRepositories().flatDir(repo -> {
			repo.setName("Quilt Gradle: Global Cache");
			repo.setDirs(Collections.singleton(globalRepo));
		});

		project.getRepositories().maven(repo -> {
			repo.setName("Quilt");
			repo.setUrl("https://maven.quiltmc.org/repository/release");
		});

		// Required for first-party intermediary support
		project.getRepositories().maven(repo -> {
			repo.setName("Fabric");
			repo.setUrl("https://maven.fabricmc.net");
		});

		project.getRepositories().maven(repo -> {
			repo.setName("Mojang Libraries");
			repo.setUrl("https://libraries.minecraft.net");
		});

		project.getRepositories().mavenCentral();


		// Setup extensions
		MinecraftProvider minecraftProvider = new MinecraftProvider(project, globalCache);
        MinecraftExtension extension = project.getExtensions().create(Constants.Extensions.MINECRAFT, MinecraftExtension.class);
        extension.setProject(project);
		extension.setMinecraftProvider(minecraftProvider);


		// Setup per source set
		SourceSetContainer sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
		sourceSets.forEach(this::setupSourceSet);
		sourceSets.whenObjectAdded(this::setupSourceSet);
	}

	private void setupSourceSet(SourceSet sourceSet) {
		// Setup configurations
		Configuration gameConf = createConfiguration(Constants.Configurations.GAME, sourceSet);
		Configuration remappedGameConf = createRemappedConfiguration(Constants.Configurations.GAME, sourceSet);
		Configuration loaderConf = createConfiguration(Constants.Configurations.LOADER, sourceSet);
		Configuration mappingsConf = createConfiguration(Constants.Configurations.MAPPINGS, sourceSet);
		Configuration intermediateConf = createConfiguration(Constants.Configurations.INTERMEDIATE, sourceSet);
		Configuration viaConf = createConfiguration(Constants.Configurations.VIA, sourceSet).extendsFrom(intermediateConf);

		Configuration gameLibrariesConf = createConfiguration(Constants.Configurations.GAME_LIBRARIES, sourceSet);
		Configuration loaderLibrariesConf = createConfiguration(Constants.Configurations.LOADER_LIBRARIES, sourceSet);

		// Map of source set config name -> base config name, for consistent naming purposes
		Map<String, String> modConfigs = Map.of(
				sourceSet.getApiConfigurationName(), JavaPlugin.API_CONFIGURATION_NAME,
				sourceSet.getImplementationConfigurationName(), JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
				sourceSet.getCompileOnlyConfigurationName(), JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME,
				sourceSet.getCompileOnlyApiConfigurationName(), JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME,
				sourceSet.getRuntimeOnlyConfigurationName(), JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME
		);

		Map<Configuration, Configuration> modConfigurations = new HashMap<>();

		for (Map.Entry<String, String> config : modConfigs.entrySet()) {
			if (project.getConfigurations().stream().noneMatch(conf -> conf.getName().equals(config.getKey()))) {
				// Skip any configurations that don't exist inside this source set
				continue;
			}

			Configuration conf = createConfiguration(Constants.Configurations.MOD_PREFIX + capitalise(config.getValue()), sourceSet);
			Configuration remappedConf = createRemappedConfiguration(Constants.Configurations.MOD_PREFIX + capitalise(config.getValue()), sourceSet);

			if (config.getValue().equals(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)) {
				conf.extendsFrom(loaderConf);
			}

			project.getConfigurations().getByName(config.getKey()).extendsFrom(remappedConf);

			modConfigurations.put(conf, remappedConf);
		}

		project.getConfigurations().getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, conf ->
				conf.extendsFrom(remappedGameConf, gameLibrariesConf, loaderLibrariesConf)
		);

		// Setup after evaluation
		project.afterEvaluate(action -> {
			boolean supportsRemapping = false;

			MappingsProvider mappingsProvider = new MappingsProvider();
			mappingsProviders.put(sourceSet, mappingsProvider);

			// Setup dependencies
			if (gameConf.getDependencies().size() > 1) throw new IllegalStateException("Multiple game dependencies specified for source set "+sourceSet.getName()+".");
			if (loaderConf.getDependencies().size() > 1) throw new IllegalStateException("Multiple loader dependencies specified for source set "+sourceSet.getName()+".");

			DependencySet mappingsDeps = mappingsConf.getDependencies();
			if (mappingsDeps.size() > 1)  {
				throw new IllegalStateException("Multiple mappings specified for source set "+sourceSet.getName()+".");
			} else if (!mappingsDeps.isEmpty()) {
				mappingsProvider.setMappingsConf(mappingsConf);
				supportsRemapping = true;
			}

			DependencySet intermediateDeps = intermediateConf.getDependencies();
			if (intermediateDeps.size() > 1) {
				throw new IllegalStateException("Multiple intermediate mappings specified for source set "+sourceSet.getName()+".");
			} else if (!intermediateDeps.isEmpty()) {
				mappingsProvider.setIntermediatesConf(intermediateConf);
				supportsRemapping = true;
			}

			mappingsProvider.setViaConf(viaConf);


			// Provide extra libraries
			// TODO: This should become a proper game-agnostic API
			MinecraftProvider minecraftProvider = new MinecraftProvider(project, globalCache);
			try {
				minecraftProvider.provideLibraries(gameConf, gameLibrariesConf);
			} catch (Exception e) {
				throw new RuntimeException("Failed to provide game libraries", e);
			}

			// TODO: Temporary until loader includes these libraries in its POM
			QuiltLoaderHelper loaderHelper = new QuiltLoaderHelper(project);
			try {
				loaderHelper.provideLibraries(loaderConf, loaderLibrariesConf);
			} catch (Exception e) {
				throw new RuntimeException("Failed to provide loader libraries", e);
			}


			// Setup tasks
			registerTask(Constants.Tasks.DECOMPILE, DecompileJarTask.class, sourceSet, task -> {
				task.setGroup(Constants.TASK_GROUP);
				task.setConfiguration(gameConf);
			});

			registerTask(Constants.Tasks.RUN_CLIENT, RunGameTask.class, sourceSet, task -> {
				task.setGroup(Constants.TASK_GROUP);
				task.setClasspath(sourceSet.getRuntimeClasspath());
				task.setWorkingDir(new File(project.getProjectDir(), "run"));
				task.getMainClass().set("org.quiltmc.loader.impl.launch.knot.KnotClient");
				task.systemProperty("loader.development", "true");
			});

			registerTask(Constants.Tasks.RUN_SERVER, RunGameTask.class, sourceSet, task -> {
				task.setGroup(Constants.TASK_GROUP);
				task.setClasspath(sourceSet.getRuntimeClasspath());
				task.setWorkingDir(new File(project.getProjectDir(), "run"));
				task.getMainClass().set("org.quiltmc.loader.impl.launch.knot.KnotServer");
				task.systemProperty("loader.development", "true");
			});

			if (!supportsRemapping) {
				// This source set does not contain mappings, and thus cannot be remapped
				return;
			}

			if (project.getTasks().stream().anyMatch(task -> task.getName().equals(sourceSet.getJarTaskName()))) {
				TaskProvider<RemapJarTask> remapJarTask = registerRemapTask(sourceSet.getJarTaskName(), RemapJarTask.class, task -> {
					task.setGroup(Constants.TASK_GROUP);
					task.setJar(project.getTasks().named(sourceSet.getJarTaskName(), AbstractArchiveTask.class).get().getArchiveFile().get().getAsFile());
					task.setMappingsProvider(mappingsProvider);
					task.dependsOn(sourceSet.getJarTaskName());
				});

				if (sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
					project.getTasks().getByName(BasePlugin.ASSEMBLE_TASK_NAME).dependsOn(remapJarTask);
				}
			}

			if (project.getTasks().stream().anyMatch(task -> task.getName().equals(sourceSet.getSourcesJarTaskName()))) {
				TaskProvider<RemapSourcesJarTask> remapSourcesJarTask = registerRemapTask(sourceSet.getSourcesJarTaskName(), RemapSourcesJarTask.class, task -> {
					task.setGroup(Constants.TASK_GROUP);
					task.setJar(project.getTasks().named(sourceSet.getSourcesJarTaskName(), AbstractArchiveTask.class).get().getArchiveFile().get().getAsFile());
					task.setMappingsProvider(mappingsProvider);
					task.dependsOn(sourceSet.getSourcesJarTaskName());
				});

				if (sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
					project.getTasks().getByName(BasePlugin.ASSEMBLE_TASK_NAME).dependsOn(remapSourcesJarTask);
				}
			}

			// Dependency remapping tasks
			TaskProvider<RemapGameTask> remapGameTask = registerRemapTask(Constants.Configurations.GAME, RemapGameTask.class, sourceSet, task -> {
				task.setGroup(Constants.TASK_GROUP);
				task.setConfiguration(gameConf);
				task.setMappingsProvider(mappingsProvider);
				task.setDirectory(remappedRepo);
				task.dependsOn(gameConf);
			});

			for (Dependency dependency : remapGameTask.get().getOutputDependencies().get()) {
				project.getDependencies().add(remappedGameConf.getName(), dependency);
			}

			for (Map.Entry<Configuration, Configuration> entry : modConfigurations.entrySet()) {
				TaskProvider<RemapDependencyTask> remapTask = registerRemapTask(entry.getKey().getName(), RemapDependencyTask.class, task -> {
					task.setGroup(Constants.TASK_GROUP);
					task.setConfiguration(entry.getKey());
					task.setMappingsProvider(mappingsProvider);
					task.setDirectory(remappedRepo);
					task.dependsOn(entry.getKey());
				});

				for (Dependency dependency : remapTask.get().getOutputDependencies().get()) {
					project.getDependencies().add(entry.getValue().getName(), dependency);
				}
			}
		});
	}

	private Configuration createConfiguration(String name) {
		return project.getConfigurations().create(name);
	}

	private Configuration createConfiguration(String name, SourceSet sourceSet) {
		if (sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
			return createConfiguration(name);
		} else {
			return createConfiguration(sourceSet.getName() + capitalise(name));
		}
	}

	private Configuration createRemappedConfiguration(String name) {
		return createConfiguration(Constants.Configurations.REMAPPED_PREFIX + capitalise(name));
	}

	private Configuration createRemappedConfiguration(String name, SourceSet sourceSet) {
		if (sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
			return createRemappedConfiguration(name);
		} else {
			return createRemappedConfiguration(sourceSet.getName() + capitalise(name));
		}
	}

	private <T extends Task> TaskProvider<T> registerTask(String name, Class<T> clazz, Action<? super T> action) {
		return project.getTasks().register(name, clazz, action);
	}

	private <T extends Task> TaskProvider<T> registerTask(String name, Class<T> clazz, SourceSet sourceSet, Action<? super T> action) {
		if (sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
			return registerTask(name, clazz, action);
		} else {
			return registerTask(sourceSet.getName() + capitalise(name), clazz, action);
		}
	}

	private <T extends Task> TaskProvider<T> registerRemapTask(String name, Class<T> clazz, Action<? super T> action) {
		return registerTask(Constants.Tasks.REMAP_PREFIX + capitalise(name), clazz, action);
	}

	private <T extends Task> TaskProvider<T> registerRemapTask(String name, Class<T> clazz, SourceSet sourceSet, Action<? super T> action) {
		if (sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
			return registerRemapTask(name, clazz, action);
		} else {
			return registerRemapTask(sourceSet.getName() + capitalise(name), clazz, action);
		}
	}

	private static String capitalise(String str) {
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
