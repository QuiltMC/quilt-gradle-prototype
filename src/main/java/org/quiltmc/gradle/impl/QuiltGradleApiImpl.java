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

package org.quiltmc.gradle.impl;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.quiltmc.gradle.QuiltGradlePlugin;
import org.quiltmc.gradle.api.QuiltGradleApi;

import java.io.File;
import java.util.function.Consumer;

public abstract class QuiltGradleApiImpl implements QuiltGradleApi {
	protected Project project;
	protected File projectCache;
	protected File globalCache;
	protected File projectRepo;
	protected File globalRepo;

	@Override
	public File getProjectCache() {
		return projectCache;
	}

	@Override
	public File getGlobalCache() {
		return globalCache;
	}

	@Override
	public File getProjectRepo() {
		return projectRepo;
	}

	@Override
	public File getGlobalRepo() {
		return globalRepo;
	}

	@Override
	public void registerPerSourceSet(Consumer<SourceSet> action) {
		// Run consumers per source set
		SourceSetContainer sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
		sourceSets.forEach(action);
		sourceSets.whenObjectAdded(action::accept);
	}

	@Override
	public Configuration getConfigurationPerSourceSet(String conf, SourceSet sourceSet) {
		return project.getConfigurations().getByName(QuiltGradlePlugin.getNamePerSourceSet(conf, sourceSet));
	}
}
