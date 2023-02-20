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

import org.cadixdev.atlas.Atlas;
import org.cadixdev.atlas.FixedAtlas;
import org.cadixdev.bombe.jar.asm.JarEntryRemappingTransformer;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.asm.LorenzRemapper;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public class Remapper {
	private final FixedAtlas atlas = new FixedAtlas();;

    public void remap(File inFile, File outFile, MappingSet mappings, boolean overwrite) {
        if (overwrite || !outFile.exists()) {
            try {
                atlas.install(ctx -> new JarEntryRemappingTransformer(new LorenzRemapper(mappings, ctx.inheritanceProvider())));
                outFile.getParentFile().mkdirs();
                atlas.run(inFile.toPath(), outFile.toPath());
				atlas.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
