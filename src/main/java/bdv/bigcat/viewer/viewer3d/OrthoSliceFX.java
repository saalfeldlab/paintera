package bdv.bigcat.viewer.viewer3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.labels.labelset.Label;
import bdv.util.InvokeOnJavaFXApplicationThread;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanelFX;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import net.imglib2.Point;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;

public class OrthoSliceFX
{

	private final Group scene;

	private final ViewerPanelFX viewer;

	private final RenderTransformListener renderTransformListener = new RenderTransformListener();

	private final List< Node > planes = new ArrayList<>();

	private final OrthoSliceMeshFX mesh = new OrthoSliceMeshFX( new Point( 0, 0 ), new Point( 1, 0 ), new Point( 1, 1 ), new Point( 0, 1 ), new AffineTransform3D() );

	private final MeshView mv = new MeshView( mesh );

	LatestTaskExecutor es = new LatestTaskExecutor();

	private final PhongMaterial material;

	private boolean isVisible = false;

	private final HashMap< Source< ? >, Source< ? > > dataSources = new HashMap<>();

	private final HashMap< Source< ? >, ToIdConverter > toIdConverters = new HashMap<>();

	private final HashMap< Source< ? >, SelectedIds > selectedIds = new HashMap<>();

	public OrthoSliceFX( final Group scene, final ViewerPanelFX viewer )
	{
		super();
		this.scene = scene;
		this.viewer = viewer;
		this.viewer.getDisplay().addImageChangeListener( this.renderTransformListener );
		this.planes.add( mv );

		this.material = new PhongMaterial();

		mv.setCullFace( CullFace.NONE );
		mv.setMaterial( material );

		material.setDiffuseColor( Color.BLACK );
		material.setSpecularColor( Color.BLACK );

		mv.addEventHandler( MouseEvent.MOUSE_CLICKED, e -> {
			final Optional< Source< ? > > optionalSource = getSource();
			if ( !optionalSource.isPresent() )
				return;
			final Source< ? > source = optionalSource.get();
			if ( toIdConverters.containsKey( source ) && selectedIds.containsKey( source ) && dataSources.containsKey( source ) )
			{
				final Source< ? > dataSource = dataSources.get( source );
				synchronized ( viewer )
				{
					final AffineTransform3D affine = new AffineTransform3D();
					final ViewerState state = viewer.getState();
					state.getViewerTransform( affine );
					final int level = state.getBestMipMapLevel( affine, state.getSources().stream().map( src -> src.getSpimSource() ).collect( Collectors.toList() ).indexOf( source ) );
					dataSource.getSourceTransform( 0, level, affine );
					final RealRandomAccess< ? > access = RealViews.transformReal( dataSource.getInterpolatedSource( 0, level, Interpolation.NEARESTNEIGHBOR ), affine ).realRandomAccess();
					access.setPosition( new double[] { e.getX(), e.getY(), e.getZ() } );
					final Object val = access.get();
					final long id = toIdConverters.get( source ).biggestFragment( val );
					if ( Label.regular( id ) )
						if ( selectedIds.get( source ).isOnlyActiveId( id ) )
							selectedIds.get( source ).deactivate( id );
						else
							selectedIds.get( source ).activate( id );
				}
			}
		} );
	}

	private void paint( final Image image )
	{
		final AffineTransform3D viewerTransform = new AffineTransform3D();
		final double w;
		final double h;
		synchronized ( viewer )
		{
			w = viewer.getWidth();
			h = viewer.getHeight();
			this.viewer.getState().getViewerTransform( viewerTransform );
		}
		if ( w <= 0 || h <= 0 )
			return;
//		viewerTransform.set( viewerTransform.get( 0, 3 ) - w / 2, 0, 3 );
//		viewerTransform.set( viewerTransform.get( 1, 3 ) - h / 2, 1, 3 );
		es.execute( () -> {
			InvokeOnJavaFXApplicationThread.invoke( () -> {
				material.setSelfIlluminationMap( image );
				mesh.update( new RealPoint( 0, 0 ), new RealPoint( w, 0 ), new RealPoint( w, h ), new RealPoint( 0, h ), viewerTransform.inverse() );
//				mv.setMaterial( m );
			} );
		} );
	}

	private final class RenderTransformListener implements ChangeListener< Image >
	{

		@Override
		public void changed( final ObservableValue< ? extends Image > observable, final Image oldValue, final Image newValue )
		{

			if ( newValue != null )
				paint( newValue );
		}

	}

	public void toggleVisibility()
	{
		synchronized ( this )
		{
			if ( isVisible )
				InvokeOnJavaFXApplicationThread.invoke( () -> this.scene.getChildren().remove( mv ) );
			else
				InvokeOnJavaFXApplicationThread.invoke( () -> this.scene.getChildren().add( mv ) );
			isVisible = !isVisible;
		}
	}

	private Optional< Source< ? > > getSource()
	{
		final int currentSource = viewer.getState().getCurrentSource();
		final List< SourceState< ? > > sources = viewer.getState().getSources();
		if ( sources.size() <= currentSource || currentSource < 0 )
			return Optional.empty();
		final Source< ? > activeSource = sources.get( currentSource ).getSpimSource();
		return Optional.of( activeSource );
	}

	public < T, U > void addSource( final Source< T > source, final Source< U > dataSource, final ToIdConverter conv, final SelectedIds sel )
	{
		this.dataSources.put( source, dataSource );
		this.toIdConverters.put( source, conv );
		this.selectedIds.put( source, sel );
	}

	public < T, U > void addSource( final Source< T > source )
	{
		this.dataSources.remove( source );
		this.toIdConverters.remove( source );
		this.selectedIds.remove( source );
	}

}
