package bdv.bigcat.composite;

import bdv.labels.labelset.LabelMultisetType;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.type.BooleanType;

public class LabelMultisetTypeToBoolean
{

	public static < B extends BooleanType< B > > RandomAccessibleInterval< B > convert( final RandomAccessibleInterval< LabelMultisetType > source, final long selectedId, final B b )
	{
		final Converter< LabelMultisetType, B > converter = ( src, target ) -> {
			target.set( src.entrySet().stream().filter( entry -> entry.getElement().id() == selectedId ).count() > 0 );
		};
		return Converters.convert( source, converter, b );
	}

}
