package bdv.bigcat.viewer.source;

import java.util.HashMap;

import bdv.AbstractViewerSetupImgLoader;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.ui.ARGBConvertedLabelsSource;
import bdv.bigcat.ui.ARGBStream;
import bdv.bigcat.ui.highlighting.ModalGoldenAngleSaturatedHighlightingARGBStream;
import bdv.bigcat.viewer.Listener;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.viewer.SourceAndConverter;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.display.ScaledARGBConverter;
import net.imglib2.display.ScaledARGBConverter.ARGB;
import net.imglib2.display.ScaledARGBConverter.VolatileARGB;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;

public class LabelLayer implements SourceLayer, Listener< TLongHashSet >
{

	private final LabelSource source;

	private final ARGBStream stream;

	private final TLongHashSet activeIds = new TLongHashSet();

	private boolean active = true;

	private final HashMap< bdv.viewer.Source< ? >, Composite< ARGBType, ARGBType > > sourceCompositesMap;

	private final AbstractViewerSetupImgLoader< LabelMultisetType, VolatileLabelMultisetType > loader;

	private final ARGBConvertedLabelsSource convertedSource;

	private final bdv.viewer.Source< ? > nonVolatile;

	public LabelLayer( final LabelSource source, final HashMap< bdv.viewer.Source< ? >, Composite< ARGBType, ARGBType > > sourceCompositesMap ) throws Exception
	{
		super();
		this.source = source;
		this.loader = source.loader();
		this.stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( activeIds );
		this.convertedSource = new ARGBConvertedLabelsSource( 0, loader, this.stream );
		this.nonVolatile = convertedSource.nonVolatile();

		this.sourceCompositesMap = sourceCompositesMap;

	}

	@Override
	public SourceAndConverter< ? > getSourceAndConverter()
	{

		final ARGB converter = new ScaledARGBConverter.ARGB( 0, 255 );
		final VolatileARGB vconverter = new ScaledARGBConverter.VolatileARGB( 0, 255 );
		final SourceAndConverter< VolatileARGBType > vsoc = new SourceAndConverter<>( convertedSource, vconverter );
		final SourceAndConverter< ARGBType > soc = new SourceAndConverter<>( convertedSource.nonVolatile(), converter, vsoc );

		final ARGBCompositeAlphaYCbCr composite = new ARGBCompositeAlphaYCbCr();
		sourceCompositesMap.put( nonVolatile, composite );

		return soc;
	}

	@Override
	public void update( final TLongHashSet activeIds )
	{
		this.activeIds.clear();
		this.activeIds.addAll( activeIds );
	}

	@Override
	public boolean isActive()
	{
		return active;
	}

	@Override
	public void setActive( final boolean active )
	{
		this.active = active;
	}

	public void setComposite( final Composite< ARGBType, ARGBType > composite )
	{
		sourceCompositesMap.put( nonVolatile, composite );
	}

	@Override
	public String name()
	{
		return source.name();
	}

//	@Override
//	public int hashCode()
//	{
//		return name().hashCode();
//	}

	@Override
	public boolean equals( final Object o )
	{
		return o instanceof LabelLayer && ( ( LabelLayer ) o ).name().equals( name() );
	}

	@Override
	public Source< ?, ? > source()
	{
		return source;
	}

}
