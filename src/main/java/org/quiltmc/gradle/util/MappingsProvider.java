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

package org.quiltmc.gradle.util;

import net.fabricmc.lorenztiny.TinyMappingsReader;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.cadixdev.lorenz.MappingSet;
import org.gradle.api.artifacts.Configuration;

import java.io.*;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MappingsProvider {
	private static final byte[] ZIP_HEADER = new BigInteger("504B0304",16).toByteArray();
	private static final byte[] TINY_HEADER = "tiny\t2\t0".getBytes();

	private Configuration mappingsConf;
	private Configuration intermediatesConf;
	private Configuration viaConf;

	private MappingSet sourceMappings = null;
	private MappingSet intermediateMappings = null;
	private MappingSet mergedMappings = null;
	private final Map<String, MappingSet> viaMappings = new HashMap<>();

	public void setMappingsConf(Configuration conf) {
		mappingsConf = conf;
	}

	public void setIntermediatesConf(Configuration conf) {
		intermediatesConf = conf;
	}

	public void setViaConf(Configuration conf) {
		viaConf = conf;
	}

	private void loadSourceMappings() throws IOException {
		if (sourceMappings == null) {
			mappingsConf.resolve();
			sourceMappings = readMappings(mappingsConf.getSingleFile());
		}
	}

	private void loadIntermediateMappings() throws IOException {
		if (intermediateMappings == null) {
			intermediatesConf.resolve();
			intermediateMappings = readMappings(intermediatesConf.getSingleFile());
		}
	}

	private void loadViaMappings() throws IOException {
		for (var artifact : viaConf.getResolvedConfiguration().getResolvedArtifacts()) {
			String coordinate = removeVersion(artifact.getId().getComponentIdentifier().getDisplayName());
			if (!viaMappings.containsKey(coordinate)) {
				viaMappings.put(coordinate, readMappings(artifact.getFile()));
			}
		}
	}

	public String getMappingsName() {
		String fileName = mappingsConf.getSingleFile().getName();
		return mappingsConf.getSingleFile().getName().substring(0, fileName.lastIndexOf("."));
	}

	public MappingSet getMergedMappings() throws IOException {
		loadSourceMappings();
		loadIntermediateMappings();

		if (mergedMappings != null) {
			return mergedMappings;
		} else if (sourceMappings != null && intermediateMappings != null) { // Merge lazily
			mergedMappings = intermediateMappings.merge(sourceMappings);
			return mergedMappings;
		} else if (sourceMappings != null) {
			return sourceMappings;
		} else if (intermediateMappings != null) {
			return intermediateMappings;
		} else {
			return null;
		}
	}

	public MappingSet getViaMappings(String coordinate) throws IOException {
		loadViaMappings();

		return viaMappings.get(coordinate);
	}

	public MappingSet getViaMappings(String coordinate, MappingSet baseMappings, boolean reverse) throws IOException {
		loadViaMappings();

		if (reverse) return baseMappings.reverse().merge(getViaMappings(coordinate));
		else return baseMappings.merge(getViaMappings(coordinate));
	}

	public MappingSet getSourceMappingsVia(String coordinate) throws IOException {
		loadSourceMappings();
		loadViaMappings();

		return getViaMappings(coordinate).reverse().merge(getMergedMappings());
	}

	// TODO: When moving to QMT, we probably just need to reverse sourceMappings
	public MappingSet getTargetMappings() throws IOException {
		loadSourceMappings();
		loadIntermediateMappings();

		if (intermediateMappings == null) {
			return sourceMappings;
		} else {
			return intermediateMappings.reverse().merge(sourceMappings).reverse();
		}
	}

	private static MappingSet readMappings(File in) throws IOException {
		return readMappings(new FileInputStream(in));
	}

	private static MappingSet readMappings(InputStream in) throws IOException {
		PushbackInputStream stream = new PushbackInputStream(in, 32);

		if (startsWith(stream, ZIP_HEADER)) {
			ZipInputStream zip = new ZipInputStream(stream);

			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (entry.getName().equals("mappings/mappings.tiny")) {
					return readMappings(zip);
				}
			}
		} else if (startsWith(stream, TINY_HEADER)) {
			MemoryMappingTree tree = new MemoryMappingTree();
			MappingReader.read(new InputStreamReader(stream), tree);
			try (TinyMappingsReader reader = new TinyMappingsReader(tree, tree.getSrcNamespace(), tree.getDstNamespaces().get(tree.getMaxNamespaceId()-1))) {
				return reader.read();
			}
		}

		throw new RuntimeException("Failed to read mappings");
	}

	private static boolean startsWith(PushbackInputStream in, byte[] content) throws IOException {
		byte[] start = new byte[content.length];
		if (in.read(start) == -1) return false;
		in.unread(start);
		return Arrays.equals(start, content);
	}

	private static String removeVersion(String artifact) {
		return artifact.substring(0, artifact.lastIndexOf(":"));
	}
}
