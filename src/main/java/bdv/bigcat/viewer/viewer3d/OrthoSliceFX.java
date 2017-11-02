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

	public OrthoSliceFX( final Group scene, final ViewerPanelFX viewer )
	{
		super();
		this.scene = scene;
		this.viewer = viewer;
		this.viewer.getDisplay().addImageChangeListener( this.renderTransformListener );
		this.planes.add( mv );
		InvokeOnJavaFXApplicationThread.invoke( () -> this.scene.getChildren().add( mv ) );

		mv.setCullFace( CullFace.NONE );
	}

	private void paint( final Image image )
	{

		es.execute( () -> {
//			synchronized ( this.viewer )
			{
				final AffineTransform3D viewerTransform = new AffineTransform3D();
				this.viewer.getState().getViewerTransform( viewerTransform );
				final int w = ( int ) viewer.getWidth();
				final int h = ( int ) viewer.getHeight();
				if ( w <= 0 || h <= 0 )
					return;
				final PhongMaterial m = new PhongMaterial();
				m.setDiffuseColor( Color.BLACK );
				m.setSpecularColor( Color.BLACK );
				m.setSelfIlluminationMap( image );
				InvokeOnJavaFXApplicationThread.invoke( () -> {
					mesh.update( new Point( 0, 0 ), new Point( w, 0 ), new Point( w, h ), new Point( 0, h ), viewerTransform.inverse() );
					mv.setMaterial( m );
				} );

			}
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

}
