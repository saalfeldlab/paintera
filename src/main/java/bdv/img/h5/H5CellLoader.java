package bdv.img.h5;

import java.util.function.Consumer;

import bdv.img.hdf5.Util;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.Cursor;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.GenericByteType;
import net.imglib2.type.numeric.integer.GenericIntType;
import net.imglib2.type.numeric.integer.GenericLongType;
import net.imglib2.type.numeric.integer.GenericShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;

public class H5CellLoader< T extends NativeType< T > > implements CellLoader< T >
{
	final private Consumer< SingleCellArrayImg< T, ? > > loader;

	public H5CellLoader( final IHDF5Reader reader, final String dataset )
	{
		final Class< ? > type = reader.getDataSetInformation( dataset ).getTypeInformation().tryGetJavaType();
		final boolean signed = reader.getDataSetInformation( dataset ).getTypeInformation().isSigned();
		System.out.println( type + " " + ( signed ? "signed" : "unsigned" ) );
		if ( type.isAssignableFrom( byte.class ) )
		{
			loader = signed ? ( img ) -> {
					final byte[] data = reader.int8().readMDArrayBlockWithOffset(
							dataset,
							Util.reorder( Intervals.dimensionsAsIntArray( img ) ),
							Util.reorder( Intervals.minAsLongArray( img ) ) ).getAsFlatArray();

					@SuppressWarnings( "unchecked" )
					final Cursor< ? extends GenericByteType< ? > > c = ( Cursor< ? extends GenericByteType< ? > > )img.cursor();
					for ( int i = 0; i < data.length; ++i )
						c.next().setByte( data[ i ] );
				} : ( img ) -> {
					final byte[] data = reader.uint8().readMDArrayBlockWithOffset(
							dataset,
							Util.reorder( Intervals.dimensionsAsIntArray( img ) ),
							Util.reorder( Intervals.minAsLongArray( img ) ) ).getAsFlatArray();

					@SuppressWarnings( "unchecked" )
					final Cursor< ? extends GenericByteType< ? > > c = ( Cursor< ? extends GenericByteType< ? > > )img.cursor();
					for ( int i = 0; i < data.length; ++i )
						c.next().setByte( data[ i ] );
				};
		}
		else if ( type.isAssignableFrom( short.class ) )
		{
			loader = signed ? ( img ) -> {
					final short[] data = reader.int16().readMDArrayBlockWithOffset(
							dataset,
							Util.reorder( Intervals.dimensionsAsIntArray( img ) ),
							Util.reorder( Intervals.minAsLongArray( img ) ) ).getAsFlatArray();

					@SuppressWarnings( "unchecked" )
					final Cursor< ? extends GenericShortType< ? > > c = ( Cursor< ? extends GenericShortType< ? > > )img.cursor();
					for ( int i = 0; i < data.length; ++i )
						c.next().setShort( data[ i ] );
				} : ( img ) -> {
					final short[] data = reader.uint16().readMDArrayBlockWithOffset(
							dataset,
							Util.reorder( Intervals.dimensionsAsIntArray( img ) ),
							Util.reorder( Intervals.minAsLongArray( img ) ) ).getAsFlatArray();

					@SuppressWarnings( "unchecked" )
					final Cursor< ? extends GenericShortType< ? > > c = ( Cursor< ? extends GenericShortType< ? > > )img.cursor();
					for ( int i = 0; i < data.length; ++i )
						c.next().setShort( data[ i ] );
				};
		}
		else if ( type.isAssignableFrom( int.class ) )
		{
			loader = signed ? ( img ) -> {
					final int[] data = reader.int32().readMDArrayBlockWithOffset(
							dataset,
							Util.reorder( Intervals.dimensionsAsIntArray( img ) ),
							Util.reorder( Intervals.minAsLongArray( img ) ) ).getAsFlatArray();

					@SuppressWarnings( "unchecked" )
					final Cursor< ? extends GenericIntType< ? > > c = ( Cursor< ? extends GenericIntType< ? > > )img.cursor();
					for ( int i = 0; i < data.length; ++i )
						c.next().setInt( data[ i ] );
				} : ( img ) -> {
					final int[] data = reader.uint32().readMDArrayBlockWithOffset(
							dataset,
							Util.reorder( Intervals.dimensionsAsIntArray( img ) ),
							Util.reorder( Intervals.minAsLongArray( img ) ) ).getAsFlatArray();

					@SuppressWarnings( "unchecked" )
					final Cursor< ? extends GenericIntType< ? > > c = ( Cursor< ? extends GenericIntType< ? > > )img.cursor();
					for ( int i = 0; i < data.length; ++i )
						c.next().setInt( data[ i ] );
				};

		}
		else if ( type.isAssignableFrom( long.class ) )
		{
			loader = signed ? ( img ) -> {
					final long[] data = reader.int64().readMDArrayBlockWithOffset(
							dataset,
							Util.reorder( Intervals.dimensionsAsIntArray( img ) ),
							Util.reorder( Intervals.minAsLongArray( img ) ) ).getAsFlatArray();

					@SuppressWarnings( "unchecked" )
					final Cursor< ? extends GenericLongType< ? > > c = ( Cursor< ? extends GenericLongType< ? > > )img.cursor();
					for ( int i = 0; i < data.length; ++i )
						c.next().setLong( data[ i ] );
				} : ( img ) -> {
					final long[] data = reader.uint64().readMDArrayBlockWithOffset(
							dataset,
							Util.reorder( Intervals.dimensionsAsIntArray( img ) ),
							Util.reorder( Intervals.minAsLongArray( img ) ) ).getAsFlatArray();

					@SuppressWarnings( "unchecked" )
					final Cursor< ? extends GenericLongType< ? > > c = ( Cursor< ? extends GenericLongType< ? > > )img.cursor();
					for ( int i = 0; i < data.length; ++i )
						c.next().setLong( data[ i ] );
				};
		}
		else if ( type.isAssignableFrom( float.class ) )
		{
			loader = ( img ) -> {
					final float[] data = reader.float32().readMDArrayBlockWithOffset(
							dataset,
							Util.reorder( Intervals.dimensionsAsIntArray( img ) ),
							Util.reorder( Intervals.minAsLongArray( img ) ) ).getAsFlatArray();

					@SuppressWarnings( "unchecked" )
					final Cursor< ? extends FloatType > c = ( Cursor< ? extends FloatType > )img.cursor();
					for ( int i = 0; i < data.length; ++i )
						c.next().set( data[ i ] );
				};
		}
		else if ( type.isAssignableFrom( double.class ) )
		{
			loader = ( img ) -> {
					final double[] data = reader.float64().readMDArrayBlockWithOffset(
							dataset,
							Util.reorder( Intervals.dimensionsAsIntArray( img ) ),
							Util.reorder( Intervals.minAsLongArray( img ) ) ).getAsFlatArray();

					@SuppressWarnings( "unchecked" )
					final Cursor< ? extends DoubleType > c = ( Cursor< ? extends DoubleType > )img.cursor();
					for ( int i = 0; i < data.length; ++i )
						c.next().set( data[ i ] );
				};
		}
		else
		{
			System.out.println( type );
			loader = null;
			System.out.println( type );
		}
	}

	@Override
	public void load( final SingleCellArrayImg< T, ? > cell )
	{
		loader.accept( cell );
	}
}
