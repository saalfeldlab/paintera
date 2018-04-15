package bdv.bigcat.viewer.ortho;

import java.util.function.Function;

import bdv.bigcat.composite.ARGBCompositeAlphaAdd;
import bdv.bigcat.composite.ClearingCompositeProjector;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.viewer.ARGBColorConverter;
import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.source.AtlasSourceState;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import bdv.bigcat.viewer.viewer3d.Viewer3DFX;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.layout.Pane;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

public class PainteraBaseView
{
	private final SourceInfo sourceInfo = new SourceInfo();

	private final GlobalTransformManager manager = new GlobalTransformManager();

	private final SharedQueue cacheControl;

	private final ViewerOptions viewerOptions;

	private final Viewer3DFX viewer3D = new Viewer3DFX( 1, 1 );

	private final OrthogonalViews< Viewer3DFX > views;

	private final ObservableList< SourceAndConverter< ? > > visibleSourcesAndConverters = sourceInfo.trackVisibleSourcesAndConverters();

	private final ListChangeListener< SourceAndConverter< ? > > vsacUpdate;

	public PainteraBaseView( final int numFetcherThreads, final Function< Source< ? >, Interpolation > interpolation )
	{
		this( numFetcherThreads, ViewerOptions.options(), interpolation );
	}

	public PainteraBaseView(
			final int numFetcherThreads,
			final ViewerOptions viewerOptions,
			final Function< Source< ? >, Interpolation > interpolation )
	{
		super();
		this.cacheControl = new SharedQueue( numFetcherThreads );
		this.viewerOptions = viewerOptions
				.accumulateProjectorFactory( new ClearingCompositeProjector.ClearingCompositeProjectorFactory<>( sourceInfo.composites(), new ARGBType() ) )
				.numRenderingThreads( Math.min( 3, Math.max( 1, Runtime.getRuntime().availableProcessors() / 3 ) ) );
		this.views = new OrthogonalViews<>( manager, cacheControl, this.viewerOptions, viewer3D, interpolation );
		this.vsacUpdate = change -> views.setAllSources( visibleSourcesAndConverters );
		visibleSourcesAndConverters.addListener( vsacUpdate );
	}

	public OrthogonalViews< Viewer3DFX > orthogonalViews()
	{
		return this.views;
	}

	public Viewer3DFX viewer3D()
	{
		return this.viewer3D;
	}

	public SourceInfo sourceInfo()
	{
		return this.sourceInfo;
	}

	public Pane pane()
	{
		return orthogonalViews().pane();
	}

	public GlobalTransformManager manager()
	{
		return this.manager;
	}

	public < T extends RealType< T >, U extends RealType< U > > void addRawSource(
			final DataSource< T, U > spec,
			final double min,
			final double max,
			final ARGBType color )
	{
		final Composite< ARGBType, ARGBType > comp = new ARGBCompositeAlphaAdd();
		final AtlasSourceState< U, T > state = sourceInfo.addRawSource( spec, min, max, color, comp );
		final Function< T, String > valueToString = T::toString;
		final Converter< U, ARGBType > conv = state.converterProperty().get();
		if ( conv instanceof ARGBColorConverter< ? > )
		{
			final ARGBColorConverter< U > colorConv = ( ARGBColorConverter< U > ) conv;
			colorConv.colorProperty().addListener( ( obs, oldv, newv ) -> orthogonalViews().requestRepaint() );
			colorConv.minProperty().addListener( ( obs, oldv, newv ) -> orthogonalViews().requestRepaint() );
			colorConv.maxProperty().addListener( ( obs, oldv, newv ) -> orthogonalViews().requestRepaint() );
			colorConv.alphaProperty().addListener( ( obs, oldv, newv ) -> orthogonalViews().requestRepaint() );
		}
	}

}
