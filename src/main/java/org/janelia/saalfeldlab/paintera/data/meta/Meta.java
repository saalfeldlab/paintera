package org.janelia.saalfeldlab.paintera.data.meta;

import java.util.function.Function;

import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.meta.exception.SourceCreationFailed;
import org.janelia.saalfeldlab.paintera.state.SourceState;

import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import net.imglib2.RandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;

public interface Meta
{

	public < T extends NativeType< T >, V extends Volatile< T > & NativeType< V > > DataSource< T, V > asSource(
			final SharedQueue sharedQueue,
			final int priority,
			final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > dataInterpolation,
			final Function< Interpolation, InterpolatorFactory< V, RandomAccessible< V > > > viewerInterpolation,
			final AffineTransform3D transform,
			final String name,
			SourceState< ?, ? >... dependson ) throws SourceCreationFailed;

	public default Meta[] dependsOn()
	{
		return new Meta[] {};
	}

}
