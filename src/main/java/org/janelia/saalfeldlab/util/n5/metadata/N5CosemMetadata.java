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

import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ScaleAndTranslation;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Writer;

public class N5CosemMetadata extends AbstractN5DatasetMetadata implements PhysicalMetadata, N5MetadataWriter<N5CosemMetadata> {

  public static final String pixelResolutionKey = "pixelResolution";

  private boolean separateChannels = true;

  private final CosemTransform cosemTransformMeta;

  public N5CosemMetadata() {

	this("", null, null);
  }

  public N5CosemMetadata(final CosemTransform cosemTransformMeta) {

	this("", cosemTransformMeta, null);
  }

  public N5CosemMetadata(final String path, final CosemTransform cosemTransformMeta) {

	this(path, cosemTransformMeta, null);
  }

  public N5CosemMetadata(final String path, final CosemTransform cosemTransformMeta,
		  final DatasetAttributes attributes) {

	super(path, attributes);
	this.cosemTransformMeta = cosemTransformMeta;
  }

  public CosemTransform getTransform() {

	return cosemTransformMeta;
  }

  public void setSeparateChannels(final boolean separateChannels) {

	this.separateChannels = separateChannels;
  }

  @Override
  public void writeMetadata(final N5CosemMetadata t, final N5Writer n5, final String group) throws Exception {

	if (t.cosemTransformMeta != null)
	  n5.setAttribute(group, CosemTransform.KEY, t.cosemTransformMeta);
  }

  public static class CosemTransform {

	// COSEM scales and translations are in c-order
	public transient static final String KEY = "transform";
	public final String[] axes;
	public final double[] scale;
	public final double[] translate;
	public final String[] units;

	public CosemTransform(final String[] axes, final double[] scale, final double[] translate, final String[] units) {

	  this.axes = axes;
	  this.scale = scale;
	  this.translate = translate;
	  this.units = units;
	}

	public AffineGet getAffine() {

	  assert (scale.length == 3 && translate.length == 3);

	  // COSEM scales and translations are in c-order
	  double[] scaleRev = new double[scale.length];
	  double[] translateRev = new double[translate.length];

	  int j = scale.length - 1;
	  for (int i = 0; i < scale.length; i++) {
		scaleRev[i] = scale[j];
		translateRev[i] = translate[j];
		j--;
	  }

	  return new ScaleAndTranslation(scaleRev, translateRev);
	}

	public AffineTransform3D toAffineTransform3d() {

	  assert (scale.length == 3 && translate.length == 3);

	  // COSEM scales and translations are in c-order
	  final AffineTransform3D transform = new AffineTransform3D();
	  transform.set(scale[2], 0, 0, translate[2],
			  0, scale[1], 0, translate[1],
			  0, 0, scale[0], translate[0]);
	  return transform;
	}
  }

  @Override
  public AffineGet physicalTransform() {

	return getTransform().getAffine();
  }

  @Override
  public String[] units() {

	String[] rawUnits = getTransform().units;
	String[] out = new String[rawUnits.length];
	int j = rawUnits.length - 1;
	for (int i = 0; i < rawUnits.length; i++) {
	  out[i] = rawUnits[j];
	  j--;
	}
	return out;
  }

  @Override
  public AffineTransform3D physicalTransform3d() {

	return getTransform().toAffineTransform3d();
  }

}
