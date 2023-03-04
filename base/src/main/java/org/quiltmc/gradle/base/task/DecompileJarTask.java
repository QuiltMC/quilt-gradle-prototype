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

package org.quiltmc.gradle.base.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.quiltmc.gradle.base.Constants;

import java.io.File;

public abstract class DecompileJarTask extends DefaultTask {
	public DecompileJarTask() {
		setGroup(Constants.TASK_GROUP);
	}

	@InputFiles
	public abstract Property<Configuration> getConfiguration();

	@TaskAction
	public void execute() {
		File jar = getConfiguration().get().getSingleFile();
		File sources = new File(jar.getParentFile(), jar.getName().replaceFirst(".jar$", "-sources.jar"));
		ConsoleDecompiler.main(new String[]{"-log=ERROR",  "-ind=\t", jar.getAbsolutePath(), sources.getAbsolutePath()});
	}
}
