package bdv.bigcat.viewer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import org.dockfx.DockPane;

import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.composite.CompositeProjector.CompositeProjectorFactory;
import bdv.bigcat.viewer.AtlasFocusHandler.OnEnterOnExit;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset.Entry;
import bdv.util.RandomAccessibleSource;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import bdv.viewer.ViewerPanel;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import net.imglib2.FinalInterval;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.converter.RealARGBConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InverseRealTransform;
import net.imglib2.realtransform.RealTransformRealRandomAccessible;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileFloatType;
import net.imglib2.util.ConstantUtils;

public class Atlas
{

	private final BaseView view;

	private final HashSet< Source< ? > > sources;

	private final Label status;

	private final AtlasFocusHandler focusHandler = new AtlasFocusHandler();

	private final AtlasValueDisplayListener valueDisplayListener;

	private final HashMap< DatasetSpec< ?, ? >, Source< ? > > specs = new HashMap<>();

	private final HashMap< Source< ? >, SelectedIds > selectedIds = new HashMap<>();

	private final AtlasIdSelector idSelector = new AtlasIdSelector( selectedIds );

	private final HashMap< Source< ? >, Composite< ARGBType, ARGBType > > composites = new HashMap<>();

	private final ViewerOptions viewerOptions;

	public Atlas()
	{
		this( ViewerOptions.options() );
	}

	public Atlas( final ViewerOptions viewerOptions )
	{
		super();
		this.viewerOptions = viewerOptions.accumulateProjectorFactory( new CompositeProjectorFactory<>( composites ) );
		this.view = new BaseView( focusHandler.onEnter(), focusHandler.onExit(), this.viewerOptions );
		this.sources = new HashSet<>();
		this.status = new Label();
		this.view.setBottom( status );
		valueDisplayListener = new AtlasValueDisplayListener( status );
//		final AtlasMouseCoordinatePrinter mcp = new AtlasMouseCoordinatePrinter( this.status );
//		addOnEnterOnExit( mcp.onEnter(), mcp.onExit(), true );
		addOnEnterOnExit( valueDisplayListener.onEnter(), valueDisplayListener.onExit(), true );
		addOnEnterOnExit( idSelector.onEnter(), idSelector.onExit(), false );
		final RandomAccessibleSource< FloatType > bg = new RandomAccessibleSource<>( ConstantUtils.constantRandomAccessible( new FloatType( 0.0f ), 3 ), new FinalInterval( 1000, 1000, 100 ), new FloatType(), "background" );
		final RandomAccessibleSource< VolatileFloatType > vbg = new RandomAccessibleSource<>( ConstantUtils.constantRandomAccessible( new VolatileFloatType( 0.0f ), 3 ), new FinalInterval( 1000, 1000, 100 ), new VolatileFloatType(), "background" );
		final RealARGBConverter< FloatType > conv = new RealARGBConverter<>( 0.0, 1.0 );
		final Converter< VolatileFloatType, ARGBType > vconv = ( input, output ) -> {
			conv.convert( input.get(), output );
		};

	}

	public void start( final Stage primaryStage ) throws Exception
	{

		final Scene scene = view.createScene( 800, 600 );

		primaryStage.setTitle( "ATLAS" );
		primaryStage.setScene( scene );
		primaryStage.sizeToScene();

		view.makeDefaultLayout();

		primaryStage.show();

		// test the look and feel with both Caspian and Modena
		Application.setUserAgentStylesheet( Application.STYLESHEET_CASPIAN );
//		Application.setUserAgentStylesheet( Application.STYLESHEET_MODENA );
		// initialize the default styles for the dock pane and undocked nodes
		// using the DockFX
		// library's internal Default.css stylesheet
		// unlike other custom control libraries this allows the user to
		// override them globally
		// using the style manager just as they can with internal JavaFX
		// controls
		// this must be called after the primary stage is shown
		// https://bugs.openjdk.java.net/browse/JDK-8132900
		DockPane.initializeDefaultUserAgentStylesheet();

	}

	public void addOnEnterOnExit( final Consumer< ViewerPanel > onEnter, final Consumer< ViewerPanel > onExit, final boolean onExitRemovable )
	{
		this.addOnEnterOnExit( new OnEnterOnExit( onEnter, onExit ), onExitRemovable );
	}

	public void addOnEnterOnExit( final OnEnterOnExit onEnterOnExit, final boolean onExitRemovable )
	{
		this.focusHandler.add( onEnterOnExit, onExitRemovable );
	}

	public < T, VT extends Volatile< T > > void addSource( final DatasetSpec< T, VT > spec )
	{
		final T t = spec.getSource().getType();
		final Composite< ARGBType, ARGBType > comp = t instanceof ARGBType || t instanceof LabelMultisetType ? new ARGBCompositeAlphaYCbCr() : new CompositeCopy<>();
		final Source< T > source = spec.getSource();
		final SourceAndConverter< VT > vsource = new SourceAndConverter<>( spec.getVolatileSource(), spec.getVolatileConverter() );
		final SourceAndConverter< ? > src = new SourceAndConverter<>( source, spec.getConverter(), vsource );
		view.addSource( src, comp );
		this.specs.put( spec, source );
		this.composites.put( source, comp );

		System.out.println( t.getClass().getName() + " " + ( t instanceof VolatileARGBType ) );
		final Function< T, String > valueToString;
		if ( t instanceof ARGBType )
			valueToString = ( Function< T, String > ) Object::toString;
		else if ( t instanceof IntegerType< ? > )
			valueToString = ( Function< T, String > ) rt -> String.format( "%d", ( ( IntegerType< ? > ) rt ).getIntegerLong() );
		else if ( t instanceof RealType< ? > )
			valueToString = ( Function< T, String > ) rt -> String.format( "%.3f", ( ( RealType< ? > ) rt ).getRealDouble() );
		else if ( t instanceof LabelMultisetType )
			valueToString = ( Function< T, String > ) rt -> {
				final StringBuilder sb = new StringBuilder( "{" );
				final Iterator< Entry< bdv.labels.labelset.Label > > it = ( ( LabelMultisetType ) rt ).entrySet().iterator();
				if ( it.hasNext() )
				{
					final Entry< bdv.labels.labelset.Label > entry = it.next();
					sb.append( entry.getElement().id() ).append( ":" ).append( entry.getCount() );
				}
				while ( it.hasNext() )
				{
					final Entry< bdv.labels.labelset.Label > entry = it.next();
					sb.append( " " ).append( entry.getElement().id() ).append( ":" ).append( entry.getCount() );
				}
				sb.append( "}" );
				return sb.toString();
			};
		else
			valueToString = rt -> "Do not understand type!";
		final AffineTransform3D affine = new AffineTransform3D();
		source.getSourceTransform( 0, 0, affine );
		final RealTransformRealRandomAccessible< T, InverseRealTransform > rra = RealViews.transformReal( source.getInterpolatedSource( 0, 0, Interpolation.NEARESTNEIGHBOR ), affine );
		this.valueDisplayListener.addSource( source, rra.realRandomAccess(), Optional.of( valueToString ) );

		if ( t instanceof ARGBType || t instanceof LabelMultisetType )
		{
			final ToLongFunction< T > toIdConverter;
			if ( t instanceof ARGBType )
				toIdConverter = argb -> ( ( ARGBType ) argb ).get();
			else
				toIdConverter = multiset -> {
					final Iterator< Entry< bdv.labels.labelset.Label > > it = ( ( LabelMultisetType ) multiset ).entrySet().iterator();
					return it.hasNext() ? it.next().getElement().id() : 0;
				};
			final SelectedIds selectedIds = spec instanceof HDF5LabelMultisetSourceSpec ? ( ( HDF5LabelMultisetSourceSpec ) spec ).getSelectedIds() : new SelectedIds();
			this.selectedIds.put( source, selectedIds );
			this.idSelector.addSource( source, rra.realRandomAccess(), toIdConverter );

			view.addActor( new ViewerActor()
			{

				@Override
				public Consumer< ViewerPanel > onRemove()
				{
					return vp -> {};
				}

				@Override
				public Consumer< ViewerPanel > onAdd()
				{
					return vp -> selectedIds.addListener( () -> vp.requestRepaint() );
				}
			} );

		}
	}

	public BaseView baseView()
	{
		return this.view;
	}

}
