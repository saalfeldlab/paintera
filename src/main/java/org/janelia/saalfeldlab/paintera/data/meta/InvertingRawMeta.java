package org.janelia.saalfeldlab.paintera.data.meta;

import java.lang.invoke.MethodHandles;
import java.util.function.Function;

import org.janelia.saalfeldlab.paintera.composition.CompositeCopy;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.data.meta.exception.SourceCreationFailed;
import org.janelia.saalfeldlab.paintera.state.InvertingRawSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import net.imglib2.RandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;

public class InvertingRawMeta< T extends NativeType< T >, V extends Volatile< T > & NativeType< V > & RealType< V > > implements RawMeta< T, V >
{
	public static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static < T extends NumericType< T > > Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > defaultInterpolations()
	{
		return i -> i.equals( Interpolation.NLINEAR ) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>();
	}

	@Override
	public RawSourceState< T, V > asSource(
			SharedQueue sharedQueue,
			int priority,
			Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > dataInterpolation,
			Function< Interpolation, InterpolatorFactory< V, RandomAccessible< V > > > viewerInterpolation,
			AffineTransform3D transform,
			SourceState< ?, ? >... dependson ) throws SourceCreationFailed
	{
		RawSourceState< T, V > dependency = ( RawSourceState< T, V > ) dependson[ 0 ];
		return new InvertingRawSourceState<>(
				( ( RandomAccessibleIntervalDataSource< T, V > ) dependency.dataSource() ).copy(),
				new ARGBColorConverter.InvertingImp1<>( dependency.converter().getMax(), dependency.converter().getMin() ),
				new CompositeCopy<>(),
				"ohlala",
				this,
				dependson );
	}

}
