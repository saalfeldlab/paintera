package org.janelia.saalfeldlab.util.n5.metadata;

import org.janelia.saalfeldlab.n5.DatasetAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class N5PainteraLabelMetadataParser extends AbstractN5DatasetMetadata.AbstractN5DatasetMetadataParser<N5PainteraLabelMetadata> {

  private static final HashMap<String, Class<?>> KEYS_TO_TYPES = new HashMap<>();

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

	return true;
  }

  @Override
  public Optional<N5PainteraLabelMetadata> parseMetadata(final Map<String, Object> metaMap) {

	final DatasetAttributes attributes = N5MetadataParser.parseAttributes(metaMap);
	if (attributes == null)
	  return Optional.ofNullable(null);

	final var painteraData = (Map<String, Object>)metaMap.get("painteraData");
	if (painteraData == null)
	  return Optional.ofNullable(null);
	final var type = (String)painteraData.get("type");
	final String maxId = (String)metaMap.get("maxId");

	if (type == null)
	  return Optional.ofNullable(null);

	if (maxId == null)
	  return Optional.ofNullable(null);

	return Optional.of(new N5PainteraLabelMetadata(attributes));
  }
}
