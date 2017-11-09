package bdv.bigcat.viewer.viewer3d;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Predicate;

import javax.imageio.ImageIO;

import bdv.bigcat.viewer.bdvfx.MouseDragFX;
import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
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
	private final Viewer3DFX viewer;

	private double centerX = 0;

	private double centerY = 0;

	private final Affine initialTransform = new Affine();

	private final Affine affine = new Affine();

	final private static double step = 1.0;// Math.PI / 180;

	private double scrollFactor;

	public Scene3DHandler( Viewer3DFX viewer, final Interval interval )
	{
		this.viewer = viewer;

		viewer.meshesGroup().getTransforms().addAll( affine );
		initialTransform.prependTranslation( -interval.dimension( 0 ) / 2, -interval.dimension( 1 ) / 2, -interval.dimension( 2 ) / 2 );

		final double sf = 1.0 / interval.dimension( 0 );
		initialTransform.prependScale( sf, sf, sf );

		affine.setToTransform( initialTransform );
		addCommands();

		final Rotate rotate = new Rotate( "rotate 3d", affine, new SimpleDoubleProperty( 1.0 ), new SimpleDoubleProperty( 1.0 ), MouseEvent::isPrimaryButtonDown );
		rotate.installInto( viewer );

		final TranslateXY translateXY = new TranslateXY( "translate", affine, MouseEvent::isSecondaryButtonDown );
		translateXY.installInto( viewer );
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
		private SimpleDoubleProperty speed = new SimpleDoubleProperty();

		private final static double SLOW = 0.5;

		private final static double NORMAL = 1;

		private final static double FAST = 2;

		private SimpleDoubleProperty factor;

		private final Affine affine;

		private final Affine affineDragStart = new Affine();

		public Rotate( final String name, final Affine affine, final SimpleDoubleProperty speed, final SimpleDoubleProperty factor, final Predicate< MouseEvent >... eventFilter )
		{
			super( name, eventFilter );
			this.speed.set( speed.get() );
			this.factor = factor;
			this.affine = affine;
			this.factor.addListener( ( obs, old, newv ) -> this.speed.set( speed.get() ) );
		}

		@Override
		public void initDrag( final javafx.scene.input.MouseEvent event )
		{
			factor.set( NORMAL );

			if ( event.isShiftDown() )
			{
				if ( event.isControlDown() )
					factor.set( SLOW );
				else
					factor.set( FAST );
			}

			this.speed.set( speed.get() * this.factor.get() );
			synchronized ( affine )
			{
				affineDragStart.setToTransform( affine );
			}
		}

		@Override
		public void drag( final javafx.scene.input.MouseEvent event )
		{
			synchronized ( affineDragStart )
			{
				final Affine target = new Affine( affineDragStart );
				final double dX = event.getX() - startX;
				final double dY = event.getY() - startY;
				final double v = step * this.speed.get();

				target.prependRotation( v * dY, centerX, centerY, 0, new Point3D( 1, 0, 0 ) );
				target.prependRotation( v * -dX, centerX, centerY, 0, new Point3D( 0, 1, 0 ) );

				InvokeOnJavaFXApplicationThread.invoke( () -> {
					this.affine.setToTransform( target );
				} );
			}
		}
	}

	private class TranslateXY extends MouseDragFX
	{

		private final Affine affine;

		private final Affine affineDragStart = new Affine();

		public TranslateXY( final String name, final Affine affine, final Predicate< MouseEvent >... eventFilter )
		{
			super( name, eventFilter );
			this.affine = affine;
		}

		@Override
		public void initDrag( final MouseEvent event )
		{
			synchronized ( affine )
			{
				affineDragStart.setToTransform( affine );
			}
		}

		@Override
		public void drag( final MouseEvent event )
		{
			final double dX = event.getX() - startX;
			final double dY = event.getY() - startY;

			final Affine target = new Affine( affineDragStart );
			target.prependTranslation( 2 * dX / viewer.getHeight(), 2 * dY / viewer.getHeight() );

			centerX = 2 * dX / viewer.getHeight();
			centerY = 2 * dY / viewer.getHeight();

			InvokeOnJavaFXApplicationThread.invoke( () -> {
				affine.setToTransform( target );
			} );

		}
	}

	private void saveAsPng()
	{
		WritableImage image = viewer.scene().snapshot( new SnapshotParameters(), null );

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
			catch ( IOException e )
			{
				// TODO: handle exception here
			}
		}
	}

}
