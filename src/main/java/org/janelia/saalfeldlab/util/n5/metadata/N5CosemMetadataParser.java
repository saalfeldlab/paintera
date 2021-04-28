package org.janelia.saalfeldlab.util.n5.metadata;

import org.janelia.saalfeldlab.n5.DatasetAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class N5CosemMetadataParser extends AbstractN5DatasetMetadata.AbstractN5DatasetMetadataParser<N5CosemMetadata> {

  private static final HashMap<String, Class<?>> KEYS_TO_TYPES = new HashMap<>();

  {
	KEYS_TO_TYPES.put(N5CosemMetadata.CosemTransform.KEY, N5CosemMetadata.CosemTransform.class);
	AbstractN5DatasetMetadata.AbstractN5DatasetMetadataParser.addDatasetAttributeKeys(KEYS_TO_TYPES);
  }

  @Override
  public HashMap<String, Class<?>> keysToTypes() {

	return KEYS_TO_TYPES;
  }

  @Override
  public boolean check(final Map<String, Object> metaMap) {

	final Map<String, Class<?>> requiredKeys = AbstractN5DatasetMetadata.AbstractN5DatasetMetadataParser.datasetAtttributeKeys();
	for (final String k : requiredKeys.keySet()) {
	  if (!metaMap.containsKey(k))
		return false;
	  else if (metaMap.get(k) == null)
		return false;
	}

	// needs to contain one of pixelResolution key
	return metaMap.containsKey(N5CosemMetadata.CosemTransform.KEY);
  }

  @Override
  public Optional<N5CosemMetadata> parseMetadata(final Map<String, Object> metaMap) {

	if (!check(metaMap))
	  return Optional.ofNullable(null);

	final DatasetAttributes attributes = N5MetadataParser.parseAttributes(metaMap);
	if (attributes == null)
	  return Optional.ofNullable(null);

	final String dataset = (String)metaMap.get("dataset");
	final N5CosemMetadata.CosemTransform transform = (N5CosemMetadata.CosemTransform)metaMap.get(N5CosemMetadata.CosemTransform.KEY);

	if (transform == null)
	  return Optional.ofNullable(null);

	return Optional.of(new N5CosemMetadata(dataset, transform, attributes));
  }

}
