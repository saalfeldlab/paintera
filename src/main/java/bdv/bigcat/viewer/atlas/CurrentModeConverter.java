package bdv.bigcat.viewer.atlas;

import bdv.bigcat.viewer.atlas.data.HDF5LabelMultisetDataSource.HighlightingStreamConverter;
import bdv.labels.labelset.VolatileLabelMultisetType;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;

public class CurrentModeConverter implements Converter< VolatileLabelMultisetType, VolatileARGBType >
{

	private Converter< VolatileLabelMultisetType, ARGBType > streamConverter;

	public CurrentModeConverter()
	{
		super();
		this.streamConverter = ( s, t ) -> {};
	}

	@Override
	public void convert( final VolatileLabelMultisetType s, final VolatileARGBType t )
	{
		// TODO Auto-generated method stub
		final boolean isValid = s.isValid();
		t.setValid( isValid );
		if ( isValid )
			streamConverter.convert( s, t.get() );
	}

	public void setConverter( final HighlightingStreamConverter converter )
	{
		this.streamConverter = converter;
	}

}
