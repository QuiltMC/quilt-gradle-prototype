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
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.quiltmc.gradle.impl.QuiltGradleApiImpl;
import org.quiltmc.gradle.task.*;
import org.quiltmc.gradle.util.MappingsProvider;
import org.quiltmc.gradle.util.QuiltLoaderHelper;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class QuiltGradlePlugin extends QuiltGradleApiImpl implements Plugin<Project> {
	public static QuiltGradlePlugin get(Project project) {
		return project.getPlugins().getPlugin(QuiltGradlePlugin.class);
	}

	@Override
    public void apply(Project project) {
		this.project = project;

		project.getLogger().lifecycle("QuiltGradle v${version}");


		// Apply default plugins
        project.getPlugins().apply(JavaLibraryPlugin.class);
        project.getPlugins().apply(IdeaPlugin.class);
        project.getPlugins().apply(EclipsePlugin.class);


		// Setup caches
		projectCache = new File(project.getProjectDir(), Constants.Locations.PROJECT_CACHE);
		globalCache = new File(project.getGradle().getGradleUserHomeDir(), Constants.Locations.GLOBAL_CACHE);
		projectRepo = new File(projectCache, Constants.Locations.REPO);
		globalRepo = new File(globalCache, Constants.Locations.REPO);

		projectRepo.mkdirs();
		globalRepo.mkdirs();


		// Setup repositories
		project.getRepositories().maven(repo -> {
			repo.setName("QuiltGradle: Project Cache");
			repo.setUrl(projectRepo);
			repo.metadataSources(MavenArtifactRepository.MetadataSources::artifact);
		});

		project.getRepositories().maven(repo -> {
			repo.setName("QuiltGradle: Global Cache");
			repo.setUrl(globalRepo);
			repo.metadataSources(MavenArtifactRepository.MetadataSources::artifact);
		});

		project.getRepositories().maven(repo -> {
			repo.setName("Quilt");
			repo.setUrl("https://maven.quiltmc.org/repository/release");
		});

		project.getRepositories().maven(repo -> {
			repo.setName("Fabric");
			repo.setUrl("https://maven.fabricmc.net");
		});

		project.getRepositories().mavenCentral();


		// Run source set configuration
		registerPerSourceSet(this::setupSourceSet);
	}

	private void setupSourceSet(SourceSet sourceSet) {
		// Setup configurations
		Configuration gameConf = createConfiguration(Constants.Configurations.GAME, sourceSet);
		Configuration remappedGameConf = createConfiguration(Constants.Configurations.REMAPPED_GAME, sourceSet);
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
			Configuration remappedConf = createConfiguration(Constants.Configurations.REMAPPED_MOD_PREFIX + capitalise(config.getValue()), sourceSet);

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


			// TODO: Temporary until loader includes these libraries in its POM
			QuiltLoaderHelper loaderHelper = new QuiltLoaderHelper(project);
			try {
				loaderHelper.provideLibraries(loaderConf, loaderLibrariesConf);
			} catch (Exception e) {
				throw new RuntimeException("Failed to provide loader libraries", e);
			}


			// Setup tasks
			registerTask(Constants.Tasks.DECOMPILE, DecompileJarTask.class, sourceSet, task -> {
				task.getConfiguration().set(gameConf);
			});

			registerTask(Constants.Tasks.RUN_CLIENT, RunGameTask.class, sourceSet, task -> {
				task.setClasspath(sourceSet.getRuntimeClasspath());
				task.getMainClass().set("org.quiltmc.loader.impl.launch.knot.KnotClient");
			});

			registerTask(Constants.Tasks.RUN_SERVER, RunGameTask.class, sourceSet, task -> {
				task.setClasspath(sourceSet.getRuntimeClasspath());
				task.getMainClass().set("org.quiltmc.loader.impl.launch.knot.KnotServer");
			});

			if (!supportsRemapping) {
				// This source set does not contain mappings, and thus cannot be remapped
				return;
			}

			if (project.getTasks().stream().anyMatch(task -> task.getName().equals(sourceSet.getJarTaskName()))) {
				TaskProvider<RemapJarTask> remapJarTask = registerRemapTask(sourceSet.getJarTaskName(), RemapJarTask.class, task -> {
					task.getJar().set(project.getTasks().named(sourceSet.getJarTaskName(), AbstractArchiveTask.class).get().getArchiveFile().get().getAsFile());
					task.getMappingsProvider().set(mappingsProvider);
					task.dependsOn(sourceSet.getJarTaskName());
				});

				if (sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
					project.getTasks().getByName(BasePlugin.ASSEMBLE_TASK_NAME).dependsOn(remapJarTask);
				}
			}

			if (project.getTasks().stream().anyMatch(task -> task.getName().equals(sourceSet.getSourcesJarTaskName()))) {
				TaskProvider<RemapSourcesJarTask> remapSourcesJarTask = registerRemapTask(sourceSet.getSourcesJarTaskName(), RemapSourcesJarTask.class, task -> {
					task.getJar().set(project.getTasks().named(sourceSet.getSourcesJarTaskName(), AbstractArchiveTask.class).get().getArchiveFile().get().getAsFile());
					task.getMappingsProvider().set(mappingsProvider);
					task.dependsOn(sourceSet.getSourcesJarTaskName());
				});

				if (sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
					project.getTasks().getByName(BasePlugin.ASSEMBLE_TASK_NAME).dependsOn(remapSourcesJarTask);
				}
			}

			// Dependency remapping tasks
			TaskProvider<RemapGameTask> remapGameTask = registerRemapTask(Constants.Configurations.GAME, RemapGameTask.class, sourceSet, task -> {
				task.getConfiguration().set(gameConf);
				task.getMappingsProvider().set(mappingsProvider);
				task.getDirectory().set(globalRepo);
				task.dependsOn(gameConf);
			});

			for (Dependency dependency : remapGameTask.get().getOutputDependencies().get()) {
				project.getDependencies().add(remappedGameConf.getName(), dependency);
			}

			for (Map.Entry<Configuration, Configuration> entry : modConfigurations.entrySet()) {
				TaskProvider<RemapDependencyTask> remapTask = registerRemapTask(entry.getKey().getName(), RemapDependencyTask.class, task -> {
					task.getConfiguration().set(entry.getKey());
					task.getMappingsProvider().set(mappingsProvider);
					task.getDirectory().set(globalRepo);
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
		return createConfiguration(getNamePerSourceSet(name, sourceSet));
	}

	private <T extends Task> TaskProvider<T> registerTask(String name, Class<T> clazz, Action<? super T> action) {
		return project.getTasks().register(name, clazz, action);
	}

	private <T extends Task> TaskProvider<T> registerTask(String name, Class<T> clazz, SourceSet sourceSet, Action<? super T> action) {
		return registerTask(getNamePerSourceSet(name, sourceSet), clazz, action);
	}

	private <T extends Task> TaskProvider<T> registerRemapTask(String name, Class<T> clazz, Action<? super T> action) {
		return registerTask(Constants.Tasks.REMAP_PREFIX + capitalise(name), clazz, action);
	}

	private <T extends Task> TaskProvider<T> registerRemapTask(String name, Class<T> clazz, SourceSet sourceSet, Action<? super T> action) {
		return registerRemapTask(getNamePerSourceSet(name, sourceSet), clazz, action);

	}

	public static String getNamePerSourceSet(String name, SourceSet sourceSet) {
		return sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME) ? name : sourceSet.getName() + capitalise(name);
	}

	private static String capitalise(String str) {
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
