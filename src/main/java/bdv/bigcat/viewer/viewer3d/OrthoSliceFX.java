package bdv.bigcat.viewer.viewer3d;

import java.util.ArrayList;
import java.util.List;

import bdv.util.InvokeOnJavaFXApplicationThread;
import bdv.viewer.ViewerPanelFX;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import net.imglib2.Point;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;

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
			material.setSelfIlluminationMap( image );
			InvokeOnJavaFXApplicationThread.invoke( () -> {
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
		if ( isVisible )
			InvokeOnJavaFXApplicationThread.invoke( () -> this.scene.getChildren().remove( mv ) );
		else
			InvokeOnJavaFXApplicationThread.invoke( () -> this.scene.getChildren().add( mv ) );
		isVisible = !isVisible;
	}

}
