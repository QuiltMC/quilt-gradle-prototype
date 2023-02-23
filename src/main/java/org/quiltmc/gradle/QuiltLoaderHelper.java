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

package org.quiltmc.gradle;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;

public class QuiltLoaderHelper {
	Project project;

	public QuiltLoaderHelper(Project project) {
		this.project = project;
	}

	public void provideLibraries(Configuration loaderConf, Configuration librariesConf) throws IOException, JsonParserException {
		if (loaderConf.isEmpty()) {
			return;
		}

		File loaderJar = loaderConf.getSingleFile();

		var installer = FileSystems.newFileSystem(loaderJar.toPath()).getPath("quilt_installer.json");
		var reader = Files.newBufferedReader(installer);

		var libraries = JsonParser.object().from(reader).getObject("libraries").getArray("common");

		for (var obj : libraries) {
			if (obj instanceof JsonObject jsonObj && jsonObj.containsKey("name")) {
				project.getDependencies().add(librariesConf.getName(), jsonObj.getString("name"));
			}
		}
	}
}
