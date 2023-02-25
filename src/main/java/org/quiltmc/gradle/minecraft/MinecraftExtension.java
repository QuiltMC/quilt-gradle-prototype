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

package org.quiltmc.gradle.minecraft;

import com.grack.nanojson.JsonParserException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.provider.Provider;

import java.io.IOException;

public class MinecraftExtension {
    private Project project;
	private MinecraftProvider minecraftProvider;

	public Dependency merged(Provider<String> version) {
		return merged(version.get());
	}

    public Dependency merged(String version) {
        throw new RuntimeException("TODO: Not yet implemented");
    }

	public Dependency client(Provider<String> version) throws IOException, JsonParserException {
		return client(version.get());
	}

    public Dependency client(String version) throws IOException, JsonParserException {
        return minecraftProvider.downloadMinecraft(version, "client");
    }

	public Dependency server(Provider<String> version) throws IOException, JsonParserException {
		return server(version.get());
	}

    public Dependency server(String version) throws IOException, JsonParserException {
		return minecraftProvider.downloadMinecraft(version, "server");
    }

	public Dependency mojmap(Provider<String> version) throws IOException, JsonParserException {
		return mojmap(version.get());
	}

    public Dependency mojmap(String version) throws IOException, JsonParserException {
        return minecraftProvider.downloadMojmap(version);
    }

	public void setProject(Project project) {
        this.project = project;
    }

	public void setMinecraftProvider(MinecraftProvider minecraftProvider) {
		this.minecraftProvider = minecraftProvider;
	}
}
