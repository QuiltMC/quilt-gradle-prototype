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

public final class Constants {
	public static final String TASK_GROUP = "quilt";

	public static final class Extensions {
		public static final String MINECRAFT = "minecraft";
	}

	public static final class Minecraft {
		public static final String VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
	}

	public static final class Configurations {
		public static final String GAME = "game";
		public static final String LOADER = "loader";
		public static final String MAPPINGS = "mappings";
		public static final String INTERMEDIATE = "intermediate";
		public static final String VIA = "via";

		public static final String MOD_PREFIX = "mod";
		public static final String REMAPPED_PREFIX = "remapped";

		public static final String GAME_LIBRARIES = "gameLibraries";
		public static final String LOADER_LIBRARIES = "loaderLibraries"; // Temporary until loader declares dependencies in it's POM
	}

	public static final class Tasks {
		public static final String DECOMPILE = "decompile";
		public static final String RUN_CLIENT = "runClient";
		public static final String RUN_SERVER = "runServer";

		public static final String REMAP_PREFIX = "remap";
	}

	public static final class Locations {
		/**
		 * Location of the project cache relative to the project directory
		 */
		public static final String PROJECT_CACHE = ".gradle/quilt-cache";
		/**
		 * Location of the global cache relative to the user's Gradle home directory
		 */
		public static final String GLOBAL_CACHE = "caches/quilt-gradle";

		public static final String REMAPPED_REPO = "remapped-repo";
		public static final String MINECRAFT_REPO = "minecraft-repo";
	}
}
