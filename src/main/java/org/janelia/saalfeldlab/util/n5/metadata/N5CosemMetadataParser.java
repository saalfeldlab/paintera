package org.janelia.saalfeldlab.util.n5.metadata;

import org.janelia.saalfeldlab.n5.DatasetAttributes;

import java.util.Map;
import java.util.Optional;

public class N5CosemMetadataParser extends AbstractN5DatasetMetadataParser<N5CosemMetadata> {

  {
	keysToTypes.put(N5CosemMetadata.CosemTransform.KEY, N5CosemMetadata.CosemTransform.class);
  }

  @Override
  public boolean check(final Map<String, Object> metaMap) {

	final Map<String, Class<?>> requiredKeys = AbstractN5DatasetMetadataParser.datasetAtttributeKeys();
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
	  return Optional.empty();

	final DatasetAttributes attributes = N5MetadataParser.parseAttributes(metaMap);
	if (attributes == null)
	  return Optional.empty();

	final String dataset = (String)metaMap.get("dataset");
	final N5CosemMetadata.CosemTransform transform = (N5CosemMetadata.CosemTransform)metaMap.get(N5CosemMetadata.CosemTransform.KEY);

	if (transform == null)
	  return Optional.empty();

	return Optional.of(new N5CosemMetadata(dataset, transform, attributes));
  }

}
