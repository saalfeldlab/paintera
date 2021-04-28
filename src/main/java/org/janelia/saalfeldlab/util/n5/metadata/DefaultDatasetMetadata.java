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

import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.Scale;
import net.imglib2.realtransform.Scale2D;
import net.imglib2.realtransform.Scale3D;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Writer;

import java.util.stream.DoubleStream;
import java.util.stream.Stream;

public class DefaultDatasetMetadata extends AbstractN5DatasetMetadata<DefaultDatasetMetadata> implements PhysicalMetadata {

  private final FinalVoxelDimensions voxDims;

  public static final String dimensionsKey = "dimensions";

  public DefaultDatasetMetadata(final int nd) {

	this("", nd);
  }

  public DefaultDatasetMetadata(final String path, final DatasetAttributes attributes) {

	super(path, attributes);
	final int nd = attributes.getNumDimensions();
	if (nd > 0) {
	  voxDims = new FinalVoxelDimensions("pixel",
			  DoubleStream.iterate(1, x -> x).limit(nd).toArray());
	} else
	  voxDims = null;

  }

  public DefaultDatasetMetadata(final String path, final int nd) {

	super(path, null);
	if (nd > 0) {
	  voxDims = new FinalVoxelDimensions("pixel",
			  DoubleStream.iterate(1, x -> x).limit(nd).toArray());
	} else
	  voxDims = null;
  }

  @Override
  public void writeMetadata(final DefaultDatasetMetadata t, final N5Writer n5, final String group) throws Exception {
	// does nothing
  }

  @Override
  public AffineGet physicalTransform() {

	final int nd = voxDims != null ? voxDims.numDimensions() : 0;
	if (nd == 0)
	  return null;
	else if (nd == 2)
	  return new Scale2D(1, 1);
	else if (nd == 3)
	  return new Scale3D(1, 1, 1);
	else
	  return new Scale(DoubleStream.generate(() -> 1.0).limit(nd).toArray());
  }

  @Override public String[] units() {

	final int nd = voxDims != null ? voxDims.numDimensions() : 0;
	return Stream.generate(() -> "pixel").limit(nd).toArray(String[]::new);
  }

}
