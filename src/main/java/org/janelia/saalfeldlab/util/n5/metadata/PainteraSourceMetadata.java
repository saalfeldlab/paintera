package org.janelia.saalfeldlab.util.n5.metadata;

import org.janelia.saalfeldlab.n5.DataType;

public interface PainteraSourceMetadata extends N5DatasetMetadata, PainteraBaseMetadata {

  /**
   * @return the DataType as specified for the given dataset
   */
  @Override default DataType getDataType() {

	return getAttributes().getDataType();
  }
}
