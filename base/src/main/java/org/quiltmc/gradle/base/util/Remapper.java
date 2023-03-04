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

package org.quiltmc.gradle.base.util;

import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyRemapperConfiguration;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.*;

import java.io.File;
import java.util.Map;
import java.util.regex.Pattern;

public class Remapper {
	private static final Map<String, String> JAVAX_TO_JETBRAINS = Map.of(
			"javax/annotation/Nullable", "org/jetbrains/annotations/Nullable",
			"javax/annotation/Nonnull", "org/jetbrains/annotations/NotNull",
			"javax/annotation/concurrent/Immutable", "org/jetbrains/annotations/Unmodifiable"
	);

    public void remap(File inFile, File outFile, MappingSet mappings, boolean overwrite) {
        if (overwrite || !outFile.exists()) {
			TinyRemapper remapper = TinyRemapper.newRemapper()
					.withMappings(createProvider(mappings))
					.withMappings(out -> JAVAX_TO_JETBRAINS.forEach(out::acceptClass))
					.configuration(new TinyRemapperConfiguration(
							false,
							true,
							false,
							false,
							false,
							true,
							false,
							true,
							Pattern.compile("\\$\\$\\d+|c_[a-z]{8}"),
							true))
					.build();

			try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(outFile.toPath()).build()) {
				outputConsumer.addNonClassFiles(inFile.toPath());
				remapper.readInputs(inFile.toPath());
				remapper.apply(outputConsumer);
			} catch (Exception e) {
				throw new RuntimeException("Failed to remap jar", e);
			} finally {
				remapper.finish();
			}
		}
    }

	private static IMappingProvider createProvider(MappingSet mappings) {
		return acceptor -> {
			for (TopLevelClassMapping classDef : mappings.getTopLevelClassMappings()) {
				createProviderPerClass(classDef, acceptor);
			}
		};
	}

	private static <T extends ClassMapping<?, ?>> void createProviderPerClass(T classDef, IMappingProvider.MappingAcceptor acceptor) {
		String className = classDef.getFullObfuscatedName();
		String dstName = classDef.getFullDeobfuscatedName();

		acceptor.acceptClass(className, dstName);

		for (InnerClassMapping innerClass : classDef.getInnerClassMappings()) {
			createProviderPerClass(innerClass, acceptor);
		}

		for (FieldMapping field : classDef.getFieldMappings()) {
			acceptor.acceptField(new IMappingProvider.Member(className, field.getObfuscatedName(), field.getType().orElseThrow().toString()), field.getDeobfuscatedName());
		}

		for (MethodMapping method : classDef.getMethodMappings()) {
			IMappingProvider.Member methodIdentifier = new IMappingProvider.Member(className, method.getObfuscatedName(), method.getObfuscatedDescriptor());
			acceptor.acceptMethod(methodIdentifier, method.getDeobfuscatedName());

			for (MethodParameterMapping parameter : method.getParameterMappings()) {
				String name = parameter.getDeobfuscatedName();
				acceptor.acceptMethodArg(methodIdentifier, parameter.getIndex(), name);
			}
		}
	}
}
