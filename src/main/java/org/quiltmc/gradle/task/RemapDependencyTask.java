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

package org.quiltmc.gradle.task;

import com.grack.nanojson.JsonParserException;
import org.cadixdev.lorenz.MappingSet;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.*;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFiles;
import org.quiltmc.gradle.Constants;
import org.quiltmc.gradle.util.MappingsProvider;
import org.quiltmc.gradle.util.ModMetadataHelper;
import org.quiltmc.gradle.util.Remapper;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public abstract class RemapDependencyTask extends DefaultTask {
	public RemapDependencyTask() {
		setGroup(Constants.TASK_GROUP);
	}

	@InputFiles
	public abstract Configuration getConfiguration();
	public abstract void setConfiguration(Configuration configuration);

	@Input
	public abstract MappingsProvider getMappingsProvider();
	public abstract void setMappingsProvider(MappingsProvider mappingsProvider);

	@OutputDirectory
	public abstract File getDirectory();
	public abstract void setDirectory(File directory);

	@OutputFiles
	public Provider<Set<Dependency>> getOutputDependencies() {
		return this.getProject().provider(() -> {
			ResolvedConfiguration conf = getConfiguration().getResolvedConfiguration();
			Set<Dependency> outputs = new HashSet<>();
			Remapper remapper = new Remapper();

			for (ResolvedArtifact artifact : conf.getResolvedArtifacts()) {
				ModuleVersionIdentifier dependency = artifact.getModuleVersion().getId();

				String group = dependency.getGroup().replace(".", "/");
				String name = dependency.getName() + "-" + dependency.getVersion();
				String version = getMappingsProvider().getMappingsName().replace(":", "_").replace("-", "_");

				File inputFile = artifact.getFile();
				File outputFile = getDirectory().toPath().resolve(group).resolve(name).resolve(version).resolve(name + "-" + version + ".jar").toFile();
				outputFile.getParentFile().mkdirs();

				if (!outputFile.exists()) {
					getLogger().lifecycle("Remapping dependency " + inputFile.getName());
					remapper.remap(inputFile, outputFile, getMappings(inputFile), false);
				}

				String notation = dependency.getGroup() + ":" + name + ":" + version;
				outputs.add(getProject().getDependencies().create(notation));
			}

			return outputs;
		});
	}

	MappingSet getMappings(File jar) throws IOException, JsonParserException {
		return getMappingsProvider().getSourceMappingsVia(ModMetadataHelper.getMappings(jar));
	}
}
