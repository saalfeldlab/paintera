package bdv.bigcat.viewer.viewer3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import bdv.viewer.Source;
import bdv.viewer.state.SourceState;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;

public class OrthoSliceFX
{
	private final Group scene;

	private final ViewerPanelFX viewer;

	private final RenderTransformListener renderTransformListener = new RenderTransformListener();

	private final List< Node > planes = new ArrayList<>();

	private final OrthoSliceMeshFX mesh = new OrthoSliceMeshFX( new RealPoint( 0, 0 ), new RealPoint( 1, 0 ), new RealPoint( 1, 1 ), new RealPoint( 0, 1 ), new AffineTransform3D() );

	private final MeshView mv = new MeshView( mesh );

	private final PhongMaterial material;

	private boolean isVisible = false;

	private final SourceInfo sourceInfo;

	// 500ms delay
	LatestTaskExecutor es = new LatestTaskExecutor( 500 * 1000 * 1000 );

	public OrthoSliceFX( final Group scene, final ViewerPanelFX viewer, final SourceInfo sourceInfo )
	{
		super();
		this.scene = scene;
		this.viewer = viewer;
		this.sourceInfo = sourceInfo;
		this.viewer.getDisplay().addImageChangeListener( this.renderTransformListener );
		this.planes.add( mv );

		this.material = new PhongMaterial();

		mv.setCullFace( CullFace.NONE );
		mv.setMaterial( material );

		material.setDiffuseColor( Color.BLACK );
		material.setSpecularColor( Color.BLACK );

//		mv.addEventHandler( MouseEvent.MOUSE_CLICKED, e -> {
//			final Optional< Source< ? > > optionalSource = getSource();
//			if ( !optionalSource.isPresent() )
//				return;
//			final Source< ? > source = optionalSource.get();
//			Optional< ToIdConverter > toIdConverter = sourceInfo.toIdConverter( source );
//			selectedIds = sourceInfo.selectedIds( source, mode )
//			if ( toIdConverter.isPresent() && selectedIds.containsKey( source ) && dataSources.containsKey( source ) )
//			{
//				final Source< ? > dataSource = dataSources.get( source );
//				synchronized ( viewer )
//				{
//					final AffineTransform3D affine = new AffineTransform3D();
//					final ViewerState state = viewer.getState();
//					state.getViewerTransform( affine );
//					final int level = state.getBestMipMapLevel( affine, state.getSources().stream().map( src -> src.getSpimSource() ).collect( Collectors.toList() ).indexOf( source ) );
//					dataSource.getSourceTransform( 0, level, affine );
//					final RealRandomAccess< ? > access = RealViews.transformReal( dataSource.getInterpolatedSource( 0, level, Interpolation.NEARESTNEIGHBOR ), affine ).realRandomAccess();
//					access.setPosition( new double[] { e.getX(), e.getY(), e.getZ() } );
//					final Object val = access.get();
//					final long id = toIdConverters.get( source ).biggestFragment( val );
//					if ( Label.regular( id ) )
//						if ( selectedIds.get( source ).isOnlyActiveId( id ) )
//							selectedIds.get( source ).deactivate( id );
//						else
//							selectedIds.get( source ).activate( id );
//				}
//			}
//		} );
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
		InvokeOnJavaFXApplicationThread.invoke( () -> {
			mesh.update( new RealPoint( 0, 0 ), new RealPoint( w, 0 ), new RealPoint( w, h ), new RealPoint( 0, h ), viewerTransform.inverse() );
		} );
		es.execute( () -> {
			final double scale = 512.0 / Math.max( w, h );
			final int fitWidth = ( int ) Math.round( w * scale );
			final int fitHeight = ( int ) Math.round( h * scale );
			final ImageView imageView = new ImageView( image );
			imageView.setPreserveRatio( true );
			imageView.setFitWidth( fitWidth );
			imageView.setFitHeight( fitHeight );
			final SnapshotParameters snapshotParameters = new SnapshotParameters();
			snapshotParameters.setFill( Color.BLACK );
			InvokeOnJavaFXApplicationThread.invoke( () -> {
				imageView.snapshot( snapshotResult -> {
					InvokeOnJavaFXApplicationThread.invoke( () -> {

						material.setSelfIlluminationMap( snapshotResult.getImage() );
						mesh.update( new RealPoint( 0, 0 ), new RealPoint( w, 0 ), new RealPoint( w, h ), new RealPoint( 0, h ), viewerTransform.inverse() );
					} );
					return null;
				}, snapshotParameters, null );
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
}
