package org.janelia.saalfeldlab.util.n5.metadata;

import org.janelia.saalfeldlab.n5.DatasetAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DefaultDatasetMetadataParser extends AbstractN5DatasetMetadata.AbstractN5DatasetMetadataParser<DefaultDatasetMetadata> {

  private static final HashMap<String, Class<?>> KEYS_TO_TYPES = new HashMap<>();

  {
	AbstractN5DatasetMetadata.AbstractN5DatasetMetadataParser.addDatasetAttributeKeys(KEYS_TO_TYPES);
  }

  @Override
  public HashMap<String, Class<?>> keysToTypes() {

	return KEYS_TO_TYPES;
  }

  @Override
  public Optional<DefaultDatasetMetadata> parseMetadata(final Map<String, Object> metaMap) {

	if (!check(metaMap))
	  return Optional.empty();

	final String dataset = (String)metaMap.get("dataset");

	final DatasetAttributes attributes = N5MetadataParser.parseAttributes(metaMap);
	if (attributes == null)
	  return Optional.empty();

	return Optional.of(new DefaultDatasetMetadata(dataset, attributes));
  }
}
