package org.janelia.saalfeldlab.paintera.state;

import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.meta.RawMeta;

import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

public class InvertingRawSourceState< D, T extends RealType< T > > extends RawSourceState< D, T >
{

	public InvertingRawSourceState(
			final DataSource< D, T > dataSource,
			final ARGBColorConverter< T > converter,
			final Composite< ARGBType, ARGBType > composite,
			final String name,
			final RawMeta< D, T > info,
			final SourceState< ?, ? >... dependsOn )
	{
		super( dataSource, converter, composite, name, info, dependsOn );
		RawSourceState< ?, ? > otherRawSource = ( RawSourceState< ?, ? > ) dependsOn[ 0 ];
		otherRawSource.converter().minProperty().bindBidirectional( this.converter().maxProperty() );
		otherRawSource.converter().maxProperty().bindBidirectional( this.converter().minProperty() );
		otherRawSource.isVisibleProperty().addListener( ( obs, oldv, newv ) -> isVisibleProperty().set( !newv ) );
		isVisibleProperty().addListener( ( obs, oldv, newv ) -> otherRawSource.isVisibleProperty().set( !newv ) );
		isVisibleProperty().set( otherRawSource.isVisibleProperty().not().get() );
	}

}
