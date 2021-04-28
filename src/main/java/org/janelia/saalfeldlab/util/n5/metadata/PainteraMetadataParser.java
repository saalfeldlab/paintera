package org.janelia.saalfeldlab.util.n5.metadata;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PainteraMetadataParser extends AbstractN5DatasetMetadata.AbstractN5DatasetMetadataParser<PainteraMetadata> {

  private static final HashMap<String, Class<?>> KEYS_TO_TYPES = new HashMap<>();

  {
	AbstractN5DatasetMetadata.AbstractN5DatasetMetadataParser.addDatasetAttributeKeys(KEYS_TO_TYPES);
  }

  private final HashMap<String, Class<?>> keysToTypes = new HashMap<>();

  @Override
  public HashMap<String, Class<?>> keysToTypes() {

	return keysToTypes;
  }

  @Override public boolean check(Map<String, Object> map) {

	for (final String k : keysToTypes().keySet()) {
	  if (!map.containsKey(k))
		return false;
	  else if (map.get(k) == null)
		return false;
	}
	return true;
  }

  @Override public Optional<PainteraMetadata> parseMetadata(Map<String, Object> map) {
	//FIXME what do we actually need here?
	return Optional.empty();
  }
}
