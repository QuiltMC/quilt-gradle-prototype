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

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public class MinecraftProvider {
	private final Project project;
	private final File globalCache;

	public MinecraftProvider(Project project, File globalCache) {
		this.project = project;
		this.globalCache = globalCache;
	}

	 public Dependency downloadMinecraft(String version, String side) throws IOException, JsonParserException {
		File file = download(version, side, side + ".jar");

		if (file == null) {
			throw new RuntimeException("Failed to download Minecraft jar.");
		}

		File outputFile = globalCache.toPath().resolve(Constants.Locations.MINECRAFT_REPO).resolve(side + "-" + version + ".jar").toFile();

		if (!outputFile.exists()) {
			Files.copy(file.toPath(), outputFile.toPath());
		}

		return project.getDependencies().create("net.minecraft:" + side + ":" + version);
	}

	public Dependency downloadMojmap(String version) throws IOException, JsonParserException {
		File file = download(version, "client_mappings", "mojmap.txt");

		if (file == null) {
			throw new RuntimeException("Failed to download Mojmap.");
		}

		File outputFile = globalCache.toPath().resolve("repo").resolve("mojmap-" + version + ".txt").toFile();

		if (!outputFile.exists()) {
			Files.copy(file.toPath(), outputFile.toPath());
		}

		return project.getDependencies().create("com.mojang:mojmap:" + version + "@txt");

	}

	public void provideLibraries(File localJson) throws FileNotFoundException, JsonParserException {
		JsonObject versionJson = JsonParser.object().from(new FileReader(localJson));
		JsonArray libraries = versionJson.getArray("libraries");

		for (Object obj : libraries) {
			if (obj instanceof JsonObject jsonObj) {
				if (Boolean.parseBoolean(evaluateRules(jsonObj))) {
					project.getDependencies().add(Constants.Configurations.GAME_LIBRARIES, jsonObj.getString("name"));
				}
			}
		}
	}

	private File download(String version, String artifact, String output) throws IOException, JsonParserException {
		File dir = new File(globalCache, version);

		if (!dir.exists()) {
			dir.mkdirs();
		}

		File target = new File(dir, output);

		JsonObject manifest = JsonParser.object().from(new URL(Constants.Minecraft.VERSION_MANIFEST));
		JsonArray versions = manifest.getArray("versions");
		String versionUrl = null;

		for (Object obj : versions) {
			if (obj instanceof JsonObject jsonObj) {
				if (version.equals(jsonObj.getString("id"))) {
					versionUrl = jsonObj.getString("url");
				}
			}
		}

		if (versionUrl == null) {
			return null;
		}

		URL url = new URL(versionUrl);
		File localJson = new File(dir, "version.json");
		Files.copy(url.openStream(), localJson.toPath(), StandardCopyOption.REPLACE_EXISTING);

		JsonObject versionJson = JsonParser.object().from(new FileReader(localJson));
		JsonObject download = versionJson.getObject("downloads").getObject(artifact);

		if (download == null) {
			throw new IllegalStateException("Could not find download for artifact " + artifact);
		}

		if (!target.exists()) {
			URL downloadUrl = new URL(download.getString("url"));
			Files.copy(downloadUrl.openStream(), target.toPath());
		}

		provideLibraries(localJson);

		return target;
	}

	private static String evaluateRules(JsonObject jsonObj) {
		boolean allowed = true;

		if (jsonObj.containsKey("rules")) {
			for (Object rule : jsonObj.getArray("rules")) {
				if (rule instanceof JsonObject jsonRule) {
					boolean allow = "allow".equals(jsonRule.getString("action"));

					for (Map.Entry<String, Object> entry : jsonRule.entrySet()) {
						if (entry.getKey().equals("action")) {
							continue;
						}

						if (entry.getValue() instanceof JsonObject jsonValue) {
							for (Map.Entry<String, Object> subEntry : jsonValue.entrySet()) {
								if (subEntry.getValue().equals(System.getProperty(entry.getKey() + "." + subEntry.getKey()).toLowerCase())) {
									allowed = allow;
								} else {
									return Boolean.toString(!allowed);
								}
							}
						}
					}

					if (jsonRule.containsKey("value")) {
						if (jsonRule.isString("value")) {
							return jsonRule.getString("value");
						} else if (jsonRule.get("value") instanceof JsonArray valueArray) {
							return String.join(" ", valueArray.toArray(new String[0]));
						}
					} else {
						return Boolean.toString(allow);
					}
				}
			}
		}

		return Boolean.toString(true);
	}
}
