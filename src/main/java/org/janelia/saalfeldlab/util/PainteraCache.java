package org.janelia.saalfeldlab.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PainteraCache {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static String USER_HOME = System.getProperty("user.home");

	public static Path getCacheFile(Class<?> clazz, final String filename) {
		return Paths.get(USER_HOME, ".cache", "paintera", clazz.getName(), filename);
	}

	public static List<String> readLines(Class<?> clazz, final String filename) {
		final Path p = getCacheFile(clazz, filename);
		try {
			LOG.debug("Reading lines from {}", p);
			return Files.readAllLines(p);
		} catch (final IOException e) {
			LOG.debug("Caught exception when trying to read lines from file at {}", p, e);
			return Collections.emptyList();
		}
	}

	public static void appendLine(Class<?> clazz, String filename, String toAppend, int maxNumLines) {
		final List<String> lines = new ArrayList<>(readLines(clazz, filename));
		lines.remove(toAppend);
		lines.add(toAppend);
		final Path p = getCacheFile(clazz, filename);
		try {
			LOG.debug("Writing lines to {}: {}", p, lines);
			p.getParent().toFile().mkdirs();
			Files.write(p, lines.subList(Math.max(lines.size() - maxNumLines, 0), lines.size()));
		} catch (IOException e) {
			LOG.debug("Caught exception when trying to write lines to file at {}: {}", p, lines, e);
			e.printStackTrace();
		}
	}

}
