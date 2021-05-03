package org.janelia.saalfeldlab.util.n5.metadata;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractN5DatasetMetadataParser<T extends N5DatasetMetadata> extends AbstractN5Metadata.AbstractN5MetadataParser<T> {

  protected final HashMap<String, Class<?>> keysToTypes = new HashMap<>(requiredDatasetAttributes());

  public static Map<String, Class<?>> datasetAtttributeKeys() {

	return new HashMap<>(requiredDatasetAttributes());
  }

  public static Map<String, Class<?>> requiredDatasetAttributes() {

	return Map.of(
			"dimensions", long[].class,
			"blockSize", int[].class,
			"dataType", String.class
	);
  }

  @Override public HashMap<String, Class<?>> keysToTypes() {

	return keysToTypes;
  }
}
