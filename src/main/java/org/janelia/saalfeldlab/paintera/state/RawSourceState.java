package org.janelia.saalfeldlab.paintera.state;

import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.data.DataSource;

import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

public class RawSourceState< D, T extends RealType< T > > extends MinimalSourceState< D, T, DataSource< D, T >, ARGBColorConverter< T > >
{

	public RawSourceState(
			final DataSource< D, T > dataSource,
			final ARGBColorConverter< T > converter,
			final Composite< ARGBType, ARGBType > composite,
			final String name )
	{
		super( dataSource, converter, composite, name );
	}

}
