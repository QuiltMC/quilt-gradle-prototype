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

package org.quiltmc.gradle.base.impl;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.SourceSet;
import org.quiltmc.gradle.base.Constants;
import org.quiltmc.gradle.base.QuiltGradlePlugin;
import org.quiltmc.gradle.base.api.QuiltGradleExtension;

import java.io.File;
import java.util.function.Consumer;

public class QuiltGradleExtensionImpl implements QuiltGradleExtension {
	private final ObjectFactory factory;
	private final QuiltGradlePlugin plugin;

	private final DirectoryProperty projectCache;
	private final DirectoryProperty globalCache;

	public QuiltGradleExtensionImpl(Project project, QuiltGradlePlugin plugin) {
		this.factory = project.getObjects();
		this.plugin = plugin;

		this.projectCache = factory.directoryProperty().fileValue(new File(project.getProjectDir(), Constants.Locations.PROJECT_CACHE));
		this.globalCache = factory.directoryProperty().fileValue(new File(project.getGradle().getGradleUserHomeDir(), Constants.Locations.GLOBAL_CACHE));
	}


	@Override
	public DirectoryProperty getProjectCache() {
		return this.projectCache;
	}

	@Override
	public DirectoryProperty getGlobalCache() {
		return this.globalCache;
	}

	@Override
	public DirectoryProperty getProjectRepo() {
		return this.factory.directoryProperty().fileValue(new File(projectCache.get().getAsFile(), Constants.Locations.REPO));
	}

	@Override
	public DirectoryProperty getGlobalRepo() {
		return this.factory.directoryProperty().fileValue(new File(globalCache.get().getAsFile(), Constants.Locations.REPO));
	}

	@Override
	public void registerPerSourceSet(Consumer<SourceSet> action) {
		plugin.registerPerSourceSet(action);
	}

	@Override
	public Configuration getConfigurationPerSourceSet(String conf, SourceSet sourceSet) {
		return plugin.getConfigurationPerSourceSet(conf, sourceSet);
	}
}
