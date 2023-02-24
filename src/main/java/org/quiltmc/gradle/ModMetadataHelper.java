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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;

public class ModMetadataHelper {
	public static String getMappings(File jar) throws IOException, JsonParserException {
		FileSystem jarFs = FileSystems.newFileSystem(jar.toPath());

		if (Files.exists(jarFs.getPath("quilt.mod.json"))) {
			BufferedReader reader = Files.newBufferedReader(jarFs.getPath("quilt.mod.json"));
			JsonObject loader = JsonParser.object().from(reader).getObject("quilt_loader");

			if (!loader.containsKey("intermediate_mappings") || !loader.isString("intermediate_mappings")) {
				return "org.quiltmc:hashed";
			} else {
				return loader.getString("intermediate_mappings");
			}
		} else if (Files.exists(jarFs.getPath("fabric.mod.json"))) {
			return "net.fabricmc:intermediary";
		} else {
			return null;
		}
	}
}
