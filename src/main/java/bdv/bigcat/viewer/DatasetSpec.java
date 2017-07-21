package bdv.bigcat.viewer;

import bdv.viewer.Source;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

public interface DatasetSpec< T, VT extends Volatile< T > >
{
	public Source< T > getSource();

	public Source< VT > getVolatileSource();

	public Converter< T, ARGBType > getConverter();

	public Converter< VT, ARGBType > getVolatileConverter();
}
