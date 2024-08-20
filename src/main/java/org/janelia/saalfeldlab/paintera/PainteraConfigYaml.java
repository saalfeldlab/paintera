package org.janelia.saalfeldlab.paintera;

import com.pivovarit.function.ThrowingSupplier;
import org.janelia.saalfeldlab.paintera.config.PainteraDirectoriesConfig;
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
import java.util.function.Function;
import java.util.function.Supplier;

public class PainteraConfigYaml {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final String PAINTERA_YAML = "paintera.yml";

	public static <T> T getConfig(final Supplier<T> fallBack, final String... segments) {


		Map<?, ?> currentConfig = getConfig();

		final Function<Object, T> getOrFallback = (config) -> {
			try {
				return (T) config;
			} catch (ClassCastException e) {
				return fallBack.get();
			}
		};

		for (int i = 0; i < segments.length; i++) {
			final String segment = segments[i];
			if (currentConfig.containsKey(segment)) {
				final Object config = currentConfig.get(segment);
				if (i == segments.length - 1)
					return getOrFallback.apply(config);
				if (config instanceof Map<?, ?>)
					currentConfig = (Map<?, ?>) config;
				else
					return fallBack.get();
			}
		}
		return fallBack.get();
	}

	public static Map<?, ?> getConfig() {

		String appConfigDir;
		try {
			appConfigDir = Paintera.getPaintera().getProperties().getPainteraDirectoriesConfig().getAppConfigDir();
		} catch (Exception e) {
			appConfigDir = PainteraDirectoriesConfig.APPLICATION_DIRECTORIES.configDir;
		}
		return readConfigUnchecked(Paths.get(appConfigDir, PAINTERA_YAML));
	}

	private static Map<?, ?> readConfigUnchecked(Path painteraYaml) {
		return ThrowingSupplier.unchecked(() -> readConfig(painteraYaml)).get();
	};

	private static Map<?, ?> readConfig(Path painteraYaml) throws IOException {

		final Yaml yaml = new Yaml();
		try (final InputStream fis = new FileInputStream(painteraYaml.toFile())) {
			final Map<?, ?> data = yaml.load(fis);
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
