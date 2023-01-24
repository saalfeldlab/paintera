package org.janelia.saalfeldlab.paintera;

import com.pivovarit.function.ThrowingSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class PainteraConfigYaml {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final String USER_HOME = System.getProperty("user.home");

	private static final Path PAINTERA_YAML = Paths.get(USER_HOME, ".config", "paintera.yml");

	public static Object getConfig(final Supplier<Object> fallBack, final String... segments) {

		Object currentConfig = getConfig();
		for (final String segment : segments) {
			if (!(currentConfig instanceof Map<?, ?>))
				return fallBack.get();
			final Map<?, ?> map = (Map<?, ?>)currentConfig;
			if (!map.containsKey(segment))
				return fallBack.get();
			currentConfig = map.get(segment);
		}
		return currentConfig;
	}

	// TODO should this return copy?
	public static Map<?, ?> getConfig() {

		return CONFIG;
	}

	private static final Map<?, ?> CONFIG = readConfigUnchecked();

	private static Map<?, ?> readConfigUnchecked() {

		return ThrowingSupplier.unchecked(PainteraConfigYaml::readConfig).get();
	}

	private static Map<?, ?> readConfig() throws IOException {

		final Yaml yaml = new Yaml();
		try (final InputStream fis = new FileInputStream(PAINTERA_YAML.toFile())) {
			// TODO is this cast always safe?
			// TODO make this type safe, maybe create config class
			final Map<?, ?> data = (Map<?, ?>)yaml.load(fis);
			LOG.debug("Loaded paintera info: {}", data);
			return data;
		} catch (final FileNotFoundException e) {
			LOG.debug("Paintera config file not found: {}", e.getMessage());
		}
		return new HashMap<>();
	}

	public static void main(String[] args) throws IOException {

		LOG.info("Got config: {}", getConfig());
	}

}
