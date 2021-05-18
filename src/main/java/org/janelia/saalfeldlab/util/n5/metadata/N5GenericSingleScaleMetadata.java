/**
 * Copyright (c) 2018--2020, Saalfeld lab
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * <p>
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.janelia.saalfeldlab.util.n5.metadata;

import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Writer;

public class N5GenericSingleScaleMetadata extends AbstractN5DatasetMetadata implements PainteraSourceMetadata, N5MetadataWriter<N5GenericSingleScaleMetadata> {

  final public double[] resolution;
  final public double[] offset;
  final protected double min;
  final protected double max;
  final protected boolean isLabelMultiset;
  final double[] downsamplingFactors;

  public N5GenericSingleScaleMetadata(
		  String path,
		  DatasetAttributes attributes,
		  Double min,
		  Double max,
		  double[] resolution,
		  double[] offset,
		  double[] downsamplingFactors,
		  Boolean isLabelMultiset
  ) {

	super(path, attributes);
	this.resolution = resolution;
	this.min = min;
	this.max = max;
	this.offset = offset;
	this.isLabelMultiset = isLabelMultiset;
	this.downsamplingFactors = downsamplingFactors;
  }

  @Override
  public void writeMetadata(final N5GenericSingleScaleMetadata t, final N5Writer n5, final String group) throws Exception {
	//TODO actually do something here
	return;
  }

  @Override public double min() {

	return min;
  }

  @Override public double max() {

	return max;
  }

  @Override public double[] getDownsamplingFactors() {

	return downsamplingFactors;
  }

  @Override public double[] getResolution() {

	return resolution;
  }

  @Override public double[] getOffset() {

	return offset;
  }

  @Override public boolean isLabelMultiset() {

	return isLabelMultiset;
  }
}
