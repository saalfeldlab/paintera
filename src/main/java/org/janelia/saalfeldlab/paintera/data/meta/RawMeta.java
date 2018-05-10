package org.janelia.saalfeldlab.paintera.data.meta;

import java.util.function.Function;

import org.janelia.saalfeldlab.paintera.data.meta.exception.SourceCreationFailed;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;

import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import net.imglib2.RandomAccessible;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;

public interface RawMeta< D, T extends RealType< T > > extends Meta
{

	public RawSourceState< D, T > asSource(
			final SharedQueue sharedQueue,
			final int priority,
			final Function< Interpolation, InterpolatorFactory< D, RandomAccessible< D > > > dataInterpolation,
			final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > viewerInterpolation,
			final AffineTransform3D transform,
			SourceState< ?, ? >... dependson ) throws SourceCreationFailed;

}
