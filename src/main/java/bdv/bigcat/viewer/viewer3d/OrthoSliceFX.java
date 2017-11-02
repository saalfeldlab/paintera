package bdv.bigcat.viewer.viewer3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import bdv.util.InvokeOnJavaFXApplicationThread;
import bdv.viewer.ViewerPanelFX;
import graphics.scenery.PointLight;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import net.imglib2.Point;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;

public class OrthoSliceFX
{

	private static final String DIFFUSE = "diffuse";

	private final Group scene;

	private final ViewerPanelFX viewer;

	private final RenderTransformListener renderTransformListener = new RenderTransformListener();

	private final List< Node > planes = new ArrayList<>();

	private final OrthoSliceMeshFX mesh = new OrthoSliceMeshFX( new Point( 0, 0 ), new Point( 1, 0 ), new Point( 1, 1 ), new Point( 0, 1 ), new AffineTransform3D() );

	private final MeshView mv = new MeshView( mesh );

	final PointLight[] lights = {
			new PointLight(),
			new PointLight(),
			new PointLight(),
			new PointLight()
	};

	LatestTaskExecutor es = new LatestTaskExecutor();

	public OrthoSliceFX( final Group scene, final ViewerPanelFX viewer )
	{
		super();
		this.scene = scene;
		this.viewer = viewer;
		this.viewer.addRenderTransformListener( renderTransformListener );
		this.planes.add( mv );
		InvokeOnJavaFXApplicationThread.invoke( () -> this.scene.getChildren().add( mv ) );
		mv.setCullFace( CullFace.NONE );
	}

//	private void updateLights( final FloatBuffer vertices )
//	{
//		{
//			for ( int i = 0; i < lights.length; ++i )
//			{
//				final PointLight light = lights[ i ];
//				light.setEmissionColor( new GLVector( 1.0f, 1.0f, 1.0f ) );
//				light.setIntensity( 10.2f * 5 );
//				light.setLinear( 0.01f );
//				light.setQuadratic( 0.0f );
//				light.setPosition( new GLVector( vertices.get(), vertices.get(), vertices.get() ) );
//			}
//		}
//	}

	private void paint()
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
//				https://stackoverflow.com/questions/3857901/how-to-get-a-bufferedimage-from-a-component-in-java
				final Optional< ImageView > viewOptional = viewer.getDisplay().getChildren().stream().filter( node -> node instanceof ImageView ).map( node -> ( ImageView ) node ).findFirst();
				if ( !viewOptional.isPresent() )
					return;
				final ImageView view = viewOptional.get();
				final Image img = view.getImage();
				final PhongMaterial m = new PhongMaterial();
				m.setDiffuseColor( Color.BLACK );
				m.setSpecularColor( Color.BLACK );
				m.setSelfIlluminationMap( img );
				InvokeOnJavaFXApplicationThread.invoke( () -> {
					mesh.update( new Point( 0, 0 ), new Point( w, 0 ), new Point( w, h ), new Point( 0, h ), viewerTransform.inverse() );
					mv.setMaterial( m );
//				mv.setMesh( mesh );
				} );

			}
		} );
	}

//	private final class ViewerTransformlistener implements TransformListener< AffineTransform3D >
//	{
//
//		@Override
//		public void transformChanged( final AffineTransform3D transform )
//		{
//			synchronized ( viewerTransform )
//			{
//				viewerTransform.set( transform );
//				synchronized ( viewer )
//				{
//					final int w = viewer.getWidth();
//					final int h = viewer.getHeight();
//					if ( w > 0 && h > 0 )
//						synchronized ( mesh )
//						{
//							mesh.update( new Point( 0, 0 ), new Point( w, 0 ), new Point( w, h ), new Point( 0, h ), viewerTransform.inverse() );
//							updateLights( mesh.getVertices() );
//						}
//				}
//			}
//		}
//	}

	private final class RenderTransformListener implements TransformListener< AffineTransform3D >
	{

		@Override
		public void transformChanged( final AffineTransform3D transform )
		{
			paint();
		}

	}

}
