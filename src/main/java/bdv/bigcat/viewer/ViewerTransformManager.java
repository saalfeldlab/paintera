package bdv.bigcat.viewer;

import java.util.Arrays;

import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.ScrollBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformEventHandler;
import net.imglib2.ui.TransformListener;

public class ViewerTransformManager implements TransformListener< AffineTransform3D >, TransformEventHandler< AffineTransform3D >
{

	/**
	 * One step of rotation (radian).
	 */
	final private static double step = Math.PI / 180;

	private static final String DRAG_TRANSLATE = "drag translate";

	private static final String ZOOM_NORMAL = "scroll zoom";

	private static final String SELECT_AXIS_X = "axis x";

	private static final String SELECT_AXIS_Y = "axis y";

	private static final String SELECT_AXIS_Z = "axis z";

	private static final double[] speed = { 1.0, 10.0, 0.1 };

	private static final String[] SPEED_NAME = { "", " fast", " slow" };

	private static final String[] speedMod = { "", "shift ", "ctrl " };

	private static final String DRAG_ROTATE = "drag rotate";

	private static final String SCROLL_Z = "scroll browse z";

	private static final String ROTATE_LEFT = "rotate left";

	private static final String ROTATE_RIGHT = "rotate right";

	private static final String KEY_ZOOM_IN = "zoom in";

	private static final String KEY_ZOOM_OUT = "zoom out";

	private static final String KEY_FORWARD_Z = "forward z";

	private static final String KEY_BACKWARD_Z = "backward z";

	private double speedFactor = 1;

	public void speedFactor( final double speedFactor )
	{
		this.speedFactor = speedFactor;
	}

	public ViewerTransformManager(
			final GlobalTransformManager manager,
			final AffineTransform3D globalToViewer,
			final TransformListener< AffineTransform3D > listener )
	{
		super();
		this.manager = manager;
		this.globalToViewer = globalToViewer;
		this.listener = listener;
		this.manager.addListener( this );
		this.canvasH = 1;
		this.canvasW = 1;
		this.centerX = this.canvasW / 2;
		this.centerY = this.canvasH / 2;

		behaviours = new Behaviours( config, "bdv" );

		behaviours.behaviour( new TranslateXY(), "drag translate", "button2", "button3" );
		behaviours.behaviour( new Zoom( speed[ 0 ] ), ZOOM_NORMAL, "meta scroll", "ctrl shift scroll" );
		for ( int s = 0; s < 3; ++s )
		{
			behaviours.behaviour( new Rotate( speed[ s ] ), DRAG_ROTATE + SPEED_NAME[ s ], speedMod[ s ] + "button1" );
			behaviours.behaviour( new TranslateZ( speed[ s ] ), SCROLL_Z + SPEED_NAME[ s ], speedMod[ s ] + "scroll" );
		}
	}

	public void setGlobalToViewer( final AffineTransform3D affine )
	{
		this.globalToViewer.set( affine );
		update();
	}

	private final GlobalTransformManager manager;

	private final InputTriggerConfig config = new InputTriggerConfig();

	private final AffineTransform3D global = new AffineTransform3D();

	private final AffineTransform3D concatenated = new AffineTransform3D();

	private final AffineTransform3D displayTransform = new AffineTransform3D();

	private final AffineTransform3D globalToViewer;

	private TransformListener< AffineTransform3D > listener;

	private final Behaviours behaviours;

	private int canvasW = 1, canvasH = 1;

	private int centerX = 0, centerY = 0;

	private void notifyListener()
	{
		final AffineTransform3D copy = concatenated.copy();
//			copy.preConcatenate( globalToViewer );
//			System.out.println( copy );
		listener.transformChanged( copy );
	}

	private synchronized void update()
	{
		concatenated.set( global );
		concatenated.preConcatenate( globalToViewer );
//			System.out.println( "UPDATE " + displayTransform );
		concatenated.preConcatenate( displayTransform );
		notifyListener();
	}

	@Override
	public synchronized void setTransform( final AffineTransform3D transform )
	{
		global.set( transform );
		update();
	}

	@Override
	public synchronized void transformChanged( final AffineTransform3D transform )
	{
		setTransform( transform );
	}

	@Override
	public void setCanvasSize( final int width, final int height, final boolean updateTransform )
	{
		if ( width == 0 || height == 0 )
			return;
//			System.out.println( "setCanvasSize " + width + " " + height + " " + displayTransform + " " + canvasW + " " + canvasH );
		if ( updateTransform ) // && false )
			synchronized ( this )
			{
				displayTransform.set( displayTransform.get( 0, 3 ) - canvasW / 2, 0, 3 );
				displayTransform.set( displayTransform.get( 1, 3 ) - canvasH / 2, 1, 3 );
				displayTransform.scale( ( double ) width / canvasW );
				displayTransform.set( displayTransform.get( 0, 3 ) + width / 2, 0, 3 );
				displayTransform.set( displayTransform.get( 1, 3 ) + height / 2, 1, 3 );
				update();
				notifyListener();
			}
		canvasW = width;
		canvasH = height;
		centerX = width / 2;
		centerY = height / 2;
	}

	@Override
	public synchronized AffineTransform3D getTransform()
	{
		return concatenated.copy();
	}

	@Override
	public void setTransformListener( final TransformListener< AffineTransform3D > transformListener )
	{
		this.listener = transformListener;
	}

	@Override
	public String getHelpString()
	{
		return "TODO";
	}

	public void install( final TriggerBehaviourBindings bindings )
	{
		behaviours.install( bindings, "transform" );
	}

	private class GetOuter
	{
		public ViewerTransformManager getOuter()
		{
			return ViewerTransformManager.this;
		}
	}

	private class TranslateXY extends GetOuter implements DragBehaviour
	{

		private int oX, oY;

		private final double[] delta = new double[ 3 ];

		private final AffineTransform3D affineDrag = new AffineTransform3D();

		@Override
		public synchronized void init( final int x, final int y )
		{
			synchronized ( global )
			{
				this.oX = x;
				this.oY = y;
				affineDrag.set( global );
			}
		}

		@Override
		public synchronized void drag( final int x, final int y )
		{
			synchronized ( global )
			{
				final double dX = ( x - oX ) / displayTransform.get( 0, 0 );
				final double dY = ( y - oY ) / displayTransform.get( 0, 0 );
				global.set( affineDrag );
				delta[ 0 ] = dX;
				delta[ 1 ] = dY;
				delta[ 2 ] = 0.0;

				globalToViewer.applyInverse( delta, delta );
				for ( int d = 0; d < delta.length; ++d )
					global.set( global.get( d, 3 ) + delta[ d ], d, 3 );
				manager.setTransform( global );
			}

		}

		@Override
		public void end( final int x, final int y )
		{}
	}

	private class TranslateZ extends GetOuter implements ScrollBehaviour
	{
		private final double speed;

		private final double[] delta = new double[ 3 ];

		public TranslateZ( final double speed )
		{
			this.speed = speed;
		}

		@Override
		public void scroll( final double wheelRotation, final boolean isHorizontal, final int x, final int y )
		{
			synchronized ( global )
			{
				delta[ 0 ] = 0;
				delta[ 1 ] = 0;
				delta[ 2 ] = speedFactor * speed * -wheelRotation;
				globalToViewer.applyInverse( delta, delta );
				final AffineTransform3D shift = new AffineTransform3D();
				shift.translate( delta );
				manager.concatenate( shift );
			}
		}
	}

	private class Zoom extends GetOuter implements ScrollBehaviour
	{
		private final double speed;

		public Zoom( final double speed )
		{
			this.speed = speed;
		}

		@Override
		public void scroll( final double wheelRotation, final boolean isHorizontal, final int x, final int y )
		{
			final AffineTransform3D global;
			synchronized ( getOuter().global )
			{
				global = getOuter().global.copy();
			}
			final double[] location = new double[] { x, y, 0 };
			concatenated.applyInverse( location, location );
			global.apply( location, location );

			final double s = speed * wheelRotation;
			final double dScale = 1.0 + 0.05;
			final double scale = s > 0 ? 1.0 / dScale : dScale;

			for ( int d = 0; d < location.length; ++d )
				global.set( global.get( d, 3 ) - location[ d ], d, 3 );
			global.scale( scale );
			for ( int d = 0; d < location.length; ++d )
				global.set( global.get( d, 3 ) + location[ d ], d, 3 );

			manager.setTransform( global );
		}
	}

	private class Rotate extends GetOuter implements DragBehaviour
	{
		private final double speed;

		public Rotate( final double speed )
		{
			this.speed = speed;
		}

		private int oX;

		private int oY;

		private final AffineTransform3D affineDragStart = new AffineTransform3D();

		@Override
		public void init( final int x, final int y )
		{
			synchronized ( global )
			{
				oX = x;
				oY = y;
				affineDragStart.set( global );
			}
		}

		@Override
		public void drag( final int x, final int y )
		{
			final AffineTransform3D affine = new AffineTransform3D();
			synchronized ( global )
			{
				final double v = step * speed;
				affine.set( affineDragStart );
				final double[] point = new double[] { x, y, 0 };
				final double[] origin = new double[] { oX, oY, 0 };

				displayTransform.applyInverse( point, point );
				displayTransform.applyInverse( origin, origin );

				System.out.println( Arrays.toString( point ) + " " + Arrays.toString( origin ) );
				final double[] delta = new double[] { point[ 0 ] - origin[ 0 ], point[ 1 ] - origin[ 1 ], 0 };
				// TODO do scaling separately. need to swap .get( 0, 0 ) and
				// .get( 1, 1 ) ?
				final double[] rotation = new double[] { delta[ 1 ] * v * displayTransform.get( 0, 0 ), -delta[ 0 ] * v * displayTransform.get( 1, 1 ), 0 };

				globalToViewer.applyInverse( origin, origin );
				globalToViewer.applyInverse( rotation, rotation );

				// center shift
				for ( int d = 0; d < origin.length; ++d )
					affine.set( affine.get( d, 3 ) - origin[ d ], d, 3 );

				for ( int d = 0; d < rotation.length; ++d )
					affine.rotate( d, rotation[ d ] );

				// center un-shift
				for ( int d = 0; d < origin.length; ++d )
					affine.set( affine.get( d, 3 ) + origin[ d ], d, 3 );

				manager.setTransform( affine );
			}
		}

		@Override
		public void end( final int x, final int y )
		{}
	}

}