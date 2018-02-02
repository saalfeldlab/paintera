package bdv.bigcat.viewer.viewer3d;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.function.Predicate;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.bdvfx.MouseDragFX;
import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point3D;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.transform.Affine;
import javafx.stage.FileChooser;
import net.imglib2.Interval;

public class Scene3DHandler
{
	public static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final Viewer3DFX viewer;

	private double centerX = 0;

	private double centerY = 0;

	private final Affine initialTransform = new Affine();

	private final Affine affine = new Affine();

	final private static double step = 1.0;// Math.PI / 180;

	private double scrollFactor;

	private static final Point3D xNormal = new Point3D( 1, 0, 0 );

	private static final Point3D yNormal = new Point3D( 0, 1, 0 );

	private static final Point3D zNormal = new Point3D( 0, 0, 1 );

	public Scene3DHandler( final Viewer3DFX viewer )
	{
		this.viewer = viewer;
		this.viewer.meshesGroup().getTransforms().addAll( affine );

		affine.setToTransform( initialTransform );
		addCommands();

		final Rotate rotate = new Rotate( "rotate 3d", new SimpleDoubleProperty( 1.0 ), 1.0, MouseEvent::isPrimaryButtonDown );
		rotate.installInto( viewer );

		final TranslateXY translateXY = new TranslateXY( "translate", MouseEvent::isSecondaryButtonDown );
		translateXY.installInto( viewer );
	}


	public void setInitialTransformToInterval( final Interval interval )
	{
		initialTransform.setToIdentity();
		initialTransform.prependTranslation( -interval.dimension( 0 ) / 2, -interval.dimension( 1 ) / 2, -interval.dimension( 2 ) / 2 );
		final double sf = 1.0 / interval.dimension( 0 );
		initialTransform.prependScale( sf, sf, sf );
		InvokeOnJavaFXApplicationThread.invoke( () -> affine.setToTransform( initialTransform ) );
	}

	private void addCommands()
	{
		viewer.addEventHandler( ScrollEvent.SCROLL, event -> {

			final double scroll = event.getDeltaY();
			scrollFactor = scroll > 0 ? 1.05 : 1 / 1.05;

			if ( event.isShiftDown() )
			{
				if ( event.isControlDown() )
					scrollFactor = scroll > 0 ? 1.01 : 1 / 1.01;
				else
					scrollFactor = scroll > 0 ? 2.05 : 1 / 2.05;
			}

			if ( Math.abs( event.getDeltaY() ) > Math.abs( event.getDeltaX() ) )
			{
				InvokeOnJavaFXApplicationThread.invoke( () -> {
						affine.prependScale( scrollFactor, scrollFactor, scrollFactor );
				} );

				event.consume();
			}
		} );

		viewer.addEventHandler( KeyEvent.KEY_PRESSED, event -> {
			if ( event.getCode().equals( KeyCode.Z ) && event.isShiftDown() )
			{
				InvokeOnJavaFXApplicationThread.invoke( () -> affine.setToTransform( initialTransform ) );
				event.consume();
			}
		} );

		viewer.addEventHandler( KeyEvent.KEY_PRESSED, event -> {
			if ( event.getCode().equals( KeyCode.P ) && event.isControlDown() )
			{
				InvokeOnJavaFXApplicationThread.invoke( () -> {
					saveAsPng();
				} );
				event.consume();
			}
		} );
	}

	private class Rotate extends MouseDragFX
	{

		private final SimpleDoubleProperty speed = new SimpleDoubleProperty();

		private final SimpleDoubleProperty factor = new SimpleDoubleProperty();


		private final static double SLOW_FACTOR = 0.1;

		private final static double NORMAL_FACTOR = 1;

		private final static double FAST_FACTOR = 2;

		private final Affine affineDragStart = new Affine();

		@SafeVarargs
		public Rotate( final String name, final DoubleProperty speed, final double factor, final Predicate< MouseEvent >... eventFilter )
		{
			super( name, eventFilter, affine );
			LOG.trace( "rotation" );
			this.factor.set( factor );
			this.speed.set( speed.get() * this.factor.get() );

			speed.addListener( ( obs, old, newv ) -> this.speed.set( this.factor.get() * speed.get() ) );
			this.factor.addListener( ( obs, old, newv ) -> this.speed.set( speed.get() ) );
		}

		@Override
		public void initDrag( final javafx.scene.input.MouseEvent event )
		{
			factor.set( NORMAL_FACTOR );

			if ( event.isShiftDown() )
			{
				if ( event.isControlDown() )
					factor.set( SLOW_FACTOR );
				else
					factor.set( FAST_FACTOR );
			}

			this.speed.set( speed.get() * this.factor.get() );

			synchronized ( transformLock )
			{
				affineDragStart.setToTransform( affine );
			}
		}

		@Override
		public void drag( final javafx.scene.input.MouseEvent event )
		{
			synchronized ( transformLock )
			{
				LOG.trace( "drag - rotate" );
				final Affine target = new Affine( affineDragStart );
				final double dX = event.getX() - startX;
				final double dY = event.getY() - startY;
				final double v = step * this.speed.get();
				LOG.trace( "dx: {} dy: {}", dX, dY );

				target.prependRotation( v * dY, centerX, centerY, 0, xNormal );
				target.prependRotation( v * -dX, centerX, centerY, 0, yNormal );

				LOG.trace( "target: {}", target );
				InvokeOnJavaFXApplicationThread.invoke( () -> {
					affine.setToTransform( target );
				} );
			}
		}
	}

	private class TranslateXY extends MouseDragFX
	{
		@SafeVarargs
		public TranslateXY( final String name, final Predicate< MouseEvent >... eventFilter )
		{
			super( name, eventFilter, affine );
			LOG.trace( "translate" );
		}

		@Override
		public void initDrag( final MouseEvent event )
		{}

		@Override
		public void drag( final MouseEvent event )
		{
			synchronized ( transformLock )
			{
				LOG.trace( "drag - translate" );
				final double dX = event.getX() - startX;
				final double dY = event.getY() - startY;

				LOG.trace( "dx " + dX + " dy: " + dY );
				InvokeOnJavaFXApplicationThread.invoke( () -> {
					affine.prependTranslation( 2 * dX / viewer.getHeight(), 2 * dY / viewer.getHeight() );
				} );

				startX += dX;
				startY += dY;
			}
		}
	}

	private void saveAsPng()
	{
		final WritableImage image = viewer.scene().snapshot( new SnapshotParameters(), null );

		final FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle( "Save 3d snapshot " );
		final SimpleObjectProperty< Optional< File > > fileProperty = new SimpleObjectProperty<>( Optional.empty() );
		try
		{
			InvokeOnJavaFXApplicationThread.invokeAndWait( () -> {
				fileProperty.set( Optional.ofNullable( fileChooser.showSaveDialog( viewer.root().sceneProperty().get().getWindow() ) ) );
			} );
		}
		catch ( final InterruptedException e )
		{
			e.printStackTrace();
		}
		if ( fileProperty.get().isPresent() )
		{
			File file = fileProperty.get().get();
			if ( !file.getName().endsWith( ".png" ) )
			{
				// TODO: now, it is overwritten if there is a file with the same
				// name and extension
				file = new File( file.getAbsolutePath() + ".png" );
			}

			try
			{
				ImageIO.write( SwingFXUtils.fromFXImage( image, null ), "png", file );
			}
			catch ( final IOException e )
			{
				// TODO: handle exception here
			}
		}
	}

}
