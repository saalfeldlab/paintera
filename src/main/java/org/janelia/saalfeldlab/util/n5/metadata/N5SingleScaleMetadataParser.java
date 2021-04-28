package org.janelia.saalfeldlab.util.n5.metadata;

import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.n5.DatasetAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class N5SingleScaleMetadataParser extends AbstractN5DatasetMetadata.AbstractN5DatasetMetadataParser<N5SingleScaleMetadata> {

  private final HashMap<String, Class<?>> KEYS_TO_TYPES = new HashMap<>();

  {
	KEYS_TO_TYPES.put(N5SingleScaleMetadata.DOWNSAMPLING_FACTORS_KEY, long[].class);
	KEYS_TO_TYPES.put(N5SingleScaleMetadata.PIXEL_RESOLUTION_KEY, FinalVoxelDimensions.class);
	KEYS_TO_TYPES.put(N5SingleScaleMetadata.AFFINE_TRANSFORM_KEY, AffineTransform3D.class);
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
	return metaMap.containsKey(N5SingleScaleMetadata.PIXEL_RESOLUTION_KEY);
  }

  @Override
  public Optional<N5SingleScaleMetadata> parseMetadata(final Map<String, Object> metaMap) {

	if (!check(metaMap))
	  return Optional.empty();

	final String dataset = (String)metaMap.get("dataset");

	final DatasetAttributes attributes = N5MetadataParser.parseAttributes(metaMap);
	if (attributes == null)
	  return Optional.empty();

	final long[] downsamplingFactors = (long[])metaMap.get(N5SingleScaleMetadata.DOWNSAMPLING_FACTORS_KEY);
	final FinalVoxelDimensions voxdim = (FinalVoxelDimensions)metaMap.get(N5SingleScaleMetadata.PIXEL_RESOLUTION_KEY);

	final double[] pixelResolution = new double[voxdim.numDimensions()];
	voxdim.dimensions(pixelResolution);

	final AffineTransform3D extraTransform = (AffineTransform3D)metaMap.get(N5SingleScaleMetadata.AFFINE_TRANSFORM_KEY);
	final AffineTransform3D transform = N5SingleScaleMetadata.buildTransform(downsamplingFactors, pixelResolution, extraTransform);
	return Optional.of(new N5SingleScaleMetadata(dataset, transform, voxdim.unit(), attributes));
  }
}
