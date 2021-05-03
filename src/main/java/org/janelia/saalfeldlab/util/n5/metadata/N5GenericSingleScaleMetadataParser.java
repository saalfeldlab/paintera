package org.janelia.saalfeldlab.util.n5.metadata;

import org.janelia.saalfeldlab.n5.DatasetAttributes;

import java.util.Map;
import java.util.Optional;

public class N5GenericSingleScaleMetadataParser extends AbstractN5DatasetMetadataParser<N5GenericSingleScaleMetadata> {

  public static final String MIN = "min";
  public static final String MAX = "max";
  public static final String RESOLUTION = "resolution";
  public static final String OFFSET = "offset";

  {
	keysToTypes.put(MIN, double.class);
	keysToTypes.put(MAX, double.class);
	keysToTypes.put(RESOLUTION, double[].class);
	keysToTypes.put(OFFSET, double[].class);
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

	return true;
  }

  @Override
  public Optional<N5GenericSingleScaleMetadata> parseMetadata(final Map<String, Object> metaMap) {

	final DatasetAttributes attributes = N5MetadataParser.parseAttributes(metaMap);
	if (attributes == null)
	  return Optional.empty();

	N5GenericSingleScaleMetadata metadata = new N5GenericSingleScaleMetadata(attributes);

	metadata.min = Optional.ofNullable(metaMap.get(MIN))
			.filter(Double.class::isInstance)
			.map(double.class::cast)
			.orElse(0.0);

	metadata.max = Optional.ofNullable(metaMap.get(MAX))
			.filter(Double.class::isInstance)
			.map(double.class::cast)
			.orElseGet(() -> PainteraBaseMetadata.maxForDataType(attributes.getDataType()));

	metadata.resolution = Optional.ofNullable(metaMap.get(RESOLUTION))
			.filter(double[].class::isInstance)
			.map(double[].class::cast)
			.orElse(new double[]{1.0, 1.0, 1.0});

	if (metadata.resolution.length != 3)
	  return Optional.empty();

	metadata.offset = Optional.ofNullable(metaMap.get(OFFSET))
			.filter(double[].class::isInstance)
			.map(double[].class::cast)
			.orElse(new double[]{1.0, 1.0, 1.0});

	if (metadata.offset.length != 3)
	  return Optional.empty();

	return Optional.of(metadata);
  }
}
