package bdv.bigcat.viewer.viewer3d;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import bdv.viewer.ViewerPanel;
import cleargl.GLTypeEnum;
import cleargl.GLVector;
import graphics.scenery.GenericTexture;
import graphics.scenery.Material;
import graphics.scenery.Node;
import graphics.scenery.PointLight;
import graphics.scenery.Scene;
import net.imglib2.Point;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;

public class OrthoSlice
{

	private static final String DIFFUSE = "diffuse";

	private final Scene scene;

	private final ViewerPanel viewer;

	private final ViewerTransformlistener viewerTransformListener = new ViewerTransformlistener();

	private final RenderTransformListener renderTransformListener = new RenderTransformListener();

	public OrthoSlice( final Scene scene, final ViewerPanel viewer )
	{
		super();
		this.scene = scene;
		this.viewer = viewer;
		this.viewer.addTransformListener( viewerTransformListener );
		this.viewer.addRenderTransformListener( renderTransformListener );
	}

	private final AffineTransform3D viewerTransform = new AffineTransform3D();

	private final List< Node > planes = new ArrayList<>();

	private void clearScene()
	{
		this.planes.forEach( this.scene::removeChild );
		this.planes.clear();
	}

	private void paint()
	{
		final AffineTransform3D viewerTransform = new AffineTransform3D();
		synchronized ( this.viewerTransform )
		{
			viewerTransform.set( this.viewerTransform );
		}
		final int w = viewer.getWidth();
		final int h = viewer.getHeight();
		if ( w <= 0 || h <= 0 )
			return;
		final BufferedImage buffer = new BufferedImage( w, h, BufferedImage.TYPE_INT_ARGB );
		// https://stackoverflow.com/questions/3857901/how-to-get-a-bufferedimage-from-a-component-in-java
		final int numChannels = Integer.BYTES;
		viewer.renderTarget().drawOverlays( buffer.getGraphics() );
		final int[] pixels = new int[ w * h ];
		for ( int k = 0, y = 0; y < h; ++y )
			for ( int x = 0; x < w; ++x, ++k )
				pixels[ k ] = buffer.getRGB( x, y );

		final OrthoSliceMesh orthoSlice = new OrthoSliceMesh( new Point( 0, 0 ), new Point( w, 0 ), new Point( w, h ), new Point( 0, h ), viewerTransform.inverse() );
		final Material m = new Material();
		m.setAmbient( new GLVector( 1.0f, 1.0f, 1.0f ) );
		m.setDiffuse( new GLVector( 0.0f, 0.0f, .0f ) );
		m.setSpecular( new GLVector( 0.0f, 0.0f, 0.0f ) );
		m.setDoubleSided( true );
		final byte[] data = new byte[ pixels.length * numChannels ];
		final ByteBuffer bb = ByteBuffer.wrap( data );
		for ( final int pixel : pixels )
		{
			final int color = pixel;
			final int r = Math.min( ( color & 0x00ff0000 ) >>> 16, 127 );
			final int g = Math.min( ( color & 0x0000ff00 ) >>> 8, 127 );
			final int b = Math.min( ( color & 0x000000ff ) >>> 0, 127 );
			final int alpha = 0xff;
			bb.putInt( r << 24 | g << 16 | b << 8 | alpha << 0 );
		}
		bb.flip();
		orthoSlice.setMaterial( m );
		final PointLight[] lights = {
				new PointLight(),
				new PointLight(),
				new PointLight(),
				new PointLight()
		};
		final FloatBuffer ver = orthoSlice.getVertices();

		final String textureName = "texture";
		final String textureType = DIFFUSE;
		final GenericTexture texture = new GenericTexture( textureName, new GLVector( w, h, 1.0f ), numChannels, GLTypeEnum.Byte, bb, true, true );
		m.getTransferTextures().put( textureName, texture );
		m.getTextures().put( textureType, "fromBuffer:" + textureName );
		m.getTextures().put( "ambient", "fromBuffer:" + textureName );
		m.setNeedsTextureReload( true );
		clearScene();
		for ( int i = 0; i < lights.length; ++i )
		{
			final PointLight light = lights[ i ];
			light.setEmissionColor( new GLVector( 1.0f, 1.0f, 1.0f ) );
			light.setIntensity( 10.2f * 5 );
			light.setLinear( 0.01f );
			light.setQuadratic( 0.0f );
			light.setPosition( new GLVector( ver.get(), ver.get(), ver.get() ) );
			this.planes.add( light );
			this.scene.addChild( light );
		}
		this.planes.add( orthoSlice );
		this.scene.addChild( orthoSlice );
		orthoSlice.setNeedsUpdate( true );
		orthoSlice.setDirty( true );
	}

	private final class ViewerTransformlistener implements TransformListener< AffineTransform3D >
	{

		@Override
		public void transformChanged( final AffineTransform3D transform )
		{
			synchronized ( viewerTransform )
			{
				viewerTransform.set( transform );
			}
		}
	}

	private final class RenderTransformListener implements TransformListener< AffineTransform3D >
	{

		@Override
		public void transformChanged( final AffineTransform3D transform )
		{
			paint();
		}

	}

}
