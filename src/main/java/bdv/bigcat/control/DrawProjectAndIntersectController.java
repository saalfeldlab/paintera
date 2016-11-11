package bdv.bigcat.control;

import static bdv.labels.labelset.Label.TRANSPARENT;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.util.Arrays;

import javax.swing.ActionMap;
import javax.swing.InputMap;

import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.InputTriggerAdder;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.ScrollBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.InputActionBindings;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.BigDataViewer;
import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.bigcat.ui.AbstractSaturatedARGBStream;
import bdv.labels.labelset.LabelMultisetType;
import bdv.util.IdService;
import bdv.viewer.ViewerPanel;
import net.imglib2.FinalInterval;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.fill.Filter;
import net.imglib2.algorithm.fill.FloodFill;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.ui.OverlayRenderer;
import net.imglib2.ui.TransformListener;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.IntervalView;
import net.imglib2.view.RandomAccessibleOnRealRandomAccessible;
import net.imglib2.view.RandomAccessiblePair;
import net.imglib2.view.Views;

/**
 * @autoher Philipp Hanslovsky &lt;hanslovskyp@janelia.hhmi.org&gt;
 */
public class DrawProjectAndIntersectController implements TransformListener< AffineTransform3D >
{
	private final ViewerPanel viewer;

	private final IdService idService;

	private final InputTriggerConfig config;

	private final InputActionBindings inputActionBindings;

	private final TriggerBehaviourBindings bindings;

	private final InputMap ksWithinModeInputMap = new InputMap();

	private final ActionMap ksWithinModeActionMap = new ActionMap();

	private final BehaviourMap withinModeBehaviourMap = new BehaviourMap();

	private final InputTriggerMap withinModeInputTriggerMap = new InputTriggerMap();

	private final AreaOverlay filledPixelsOverlay = new AreaOverlay();

	private final byte[] r = new byte[] { 0, ( byte ) 0x00 };

	private final byte[] g = new byte[] { 0, ( byte ) 0xff };

	private final byte[] b = new byte[] { 0, ( byte ) 0xff };

	private final byte[] a = new byte[] { 0, ( byte ) 0xff };

	public final BrushOverlay brushOverlay = new BrushOverlay();

	private final float overlayAlpha = 0.5f;

	final protected RealPoint labelLocation = new RealPoint( 3 );

	final protected AffineTransform3D viewerToGlobalCoordinatesTransform = new AffineTransform3D();

	final protected AffineTransform3D labelTransform;

	final protected FragmentSegmentAssignment assignment;

	final protected RandomAccessibleInterval< LabelMultisetType > labels;

	final protected RandomAccessibleInterval< LongType > paintedLabels;

	private final AbstractSaturatedARGBStream colorStream;

	private final SelectionController selectionController;

	public DrawProjectAndIntersectController(
			final BigDataViewer bdv,
			final IdService idService,
			final AffineTransform3D viewerToGlobalCoordinatesTransform,
			final InputTriggerConfig config,
			final RandomAccessibleInterval< LabelMultisetType > labels,
			final RandomAccessibleInterval< LongType > paintedLabels,
			final AffineTransform3D labelTransform,
			final FragmentSegmentAssignment assignment,
			final AbstractSaturatedARGBStream colorStream,
			final SelectionController selectionController,
			final InputActionBindings inputActionBindings,
			final TriggerBehaviourBindings bindings,
			final String... activateModeKeys )
	{
		this.viewer = bdv.getViewer();
		this.idService = idService;
		this.config = config;
		this.inputActionBindings = inputActionBindings;
		this.labels = labels;
		this.paintedLabels = paintedLabels;
		this.labelTransform = labelTransform;
		this.assignment = assignment;
		this.colorStream = colorStream;
		this.selectionController = selectionController;
		this.bindings = bindings;

		viewer.addTransformListener( this );

		final KeyStrokeAdder ksWithinModeInputAdder = config.keyStrokeAdder( ksWithinModeInputMap, "within dpi mode" );
		final InputTriggerAdder withinModeInputTriggerAdder = config.inputTriggerAdder( withinModeInputTriggerMap, "within dpi mdoe" );

		final Runnable action = new Runnable()
		{
			@Override
			public void run()
			{
				filledPixelsOverlay.setVisible( false );
				viewer.getDisplay().repaint();
			}
		};

		final ModeToggleController.ExecuteOnUnToggle noActionUnToggle = new ModeToggleController.ExecuteOnUnToggle( action, bindings, inputActionBindings, "abort dpi", "ESCAPE" );
		noActionUnToggle.register( ksWithinModeActionMap, ksWithinModeInputAdder );

		final IntersectAndLeave il = new IntersectAndLeave( action, bindings, inputActionBindings, "execute and leave dpi", "shift button1" );
		il.register( withinModeBehaviourMap, withinModeInputTriggerAdder );

		final NewActiveFragmentId nafi = new NewActiveFragmentId( "new active fragment id", "N" );
		nafi.register( ksWithinModeActionMap, ksWithinModeInputAdder );

		final ClearArea cc = new ClearArea( "clear canvas", "C" );
		cc.register( ksWithinModeActionMap, ksWithinModeInputAdder );

		final MoveBrush mb = new MoveBrush( "move brush", "SPACE" );
		mb.register( withinModeBehaviourMap, withinModeInputTriggerAdder );

		final ChangeBrushRadius cbr = new ChangeBrushRadius( "change brush radius", "SPACE scroll" );
		cbr.register( withinModeBehaviourMap, withinModeInputTriggerAdder );

		final Paint p = new Paint( "paint", "SPACE button1" );
		p.register( withinModeBehaviourMap, withinModeInputTriggerAdder );

		final Erase e = new Erase( "erase", "SPACE button2", "SPACE button3" );
		e.register( withinModeBehaviourMap, withinModeInputTriggerAdder );

		final OverlayVisibility ov = new OverlayVisibility( "visibility", "V" );
		ov.register( ksWithinModeActionMap, ksWithinModeInputAdder );

		final Fill f = new Fill( "fill", "M button1" );
		f.register( withinModeBehaviourMap, withinModeInputTriggerAdder );

		final Toggle toggle = new Toggle( bindings, inputActionBindings, "activate dpi mode", activateModeKeys );
		ModeToggleController.registerToggle( config, inputActionBindings, toggle, "dpi mode controller" );

		viewer.getDisplay().addOverlayRenderer( brushOverlay );
		viewer.getDisplay().addOverlayRenderer( filledPixelsOverlay );

	}

	private void updateCM()
	{
		final Color c = getColor();
		r[ 1 ] = ( byte ) ( c.getRed() & 0xFF );
		g[ 1 ] = ( byte ) ( c.getGreen() & 0xFF );
		b[ 1 ] = ( byte ) ( c.getBlue() & 0xFF );
		a[ 1 ] = ( byte ) 0xFF;
		final IndexColorModel cm = new IndexColorModel( 2, 2, r, g, b, a );
		filledPixelsOverlay.img = new BufferedImage( cm, filledPixelsOverlay.img.getRaster(), cm.isAlphaPremultiplied(), // what
																															// is
																															// this?
				null );

		filledPixelsOverlay.imgOld = filledPixelsOverlay.imgOld == null ? null : new BufferedImage( cm, filledPixelsOverlay.imgOld.getRaster(), cm.isAlphaPremultiplied(), // what
																																											// is
																																											// this?
				null );

		System.out.println( Arrays.toString( r ) + Arrays.toString( g ) + Arrays.toString( b ) );
	}

	@Override
	public void transformChanged( final AffineTransform3D t )
	{
		viewerToGlobalCoordinatesTransform.set( t );
	}

	private abstract class SelfRegisteringBehaviour implements Behaviour
	{
		private final String name;

		private final String[] defaultTriggers;

		protected String getName()
		{
			return name;
		}

		public SelfRegisteringBehaviour( final String name, final String... defaultTriggers )
		{
			this.name = name;
			this.defaultTriggers = defaultTriggers;
		}

		public void register( final BehaviourMap behaviourMap, final InputTriggerAdder inputAdder )
		{
			behaviourMap.put( name, this );
			inputAdder.put( name, defaultTriggers );
		}
	}

	private class Toggle extends ModeToggleController.AbstractToggle
	{

		private Toggle( final TriggerBehaviourBindings bindings, final InputActionBindings inputActionBindings, final String name, final String... defaultTriggers )
		{
			super( bindings, inputActionBindings, ksWithinModeInputMap, ksWithinModeActionMap, withinModeBehaviourMap, withinModeInputTriggerMap, name, defaultTriggers );
		}

		@Override
		public void actionImplementation()
		{
			filledPixelsOverlay.img = filledPixelsOverlay.create();
			filledPixelsOverlay.imgOld = null;
			filledPixelsOverlay.setVisible( true );
//            cmObsolete.updateRGB( getColor() );
			updateCM();
			System.out.println( "Action: " + getColor() );
		}
	}

	private class NewActiveFragmentId extends ModeToggleController.SelfRegisteringAction
	{
		public NewActiveFragmentId( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		public void setActiveFragmentId( final long id )
		{
			colorStream.setActive( id );
			selectionController.setActiveFragmentId( id );
			updateCM();
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			synchronized ( viewer )
			{
				setActiveFragmentId( idService.next() );
			}
			viewer.requestRepaint();
		}
	}

	private Color getColor()
	{
		final int c = colorStream.argb( selectionController.getActiveFragmentId() );
		return new Color( c );
	}

	private class ClearArea extends ModeToggleController.SelfRegisteringAction
	{

		public ClearArea( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed( final ActionEvent actionEvent )
		{
			filledPixelsOverlay.img = filledPixelsOverlay.create();
			filledPixelsOverlay.imgOld = null;
			viewer.getDisplay().repaint();
		}
	}

	private class BrushOverlay implements OverlayRenderer
	{

		final BasicStroke stroke = new BasicStroke( 1 );

		protected int x, y, radius = 5;

		protected boolean visible = false;

		public void setPosition( final int x, final int y )
		{
			this.x = x;
			this.y = y;
		}

		public void setRadius( final int radius )
		{
			this.radius = radius;
		}

		public void setVisible( final boolean visible )
		{
			this.visible = visible;
		}

		@Override
		public void drawOverlays( final Graphics g )
		{
			if ( visible )
			{
				final Graphics2D g2d = ( Graphics2D ) g;
				g2d.setColor( getColor() );
				g2d.setStroke( stroke );
				g2d.drawOval( x - radius, y - radius, 2 * radius + 1, 2 * radius + 1 );
			}
		}

		@Override
		public void setCanvasSize( final int width, final int height )
		{

		}

		public void setColor( final float r, final float g, final float b, final float a )
		{

		}
	}

	private class AreaOverlay implements OverlayRenderer
	{

		private BufferedImage img;

		private BufferedImage imgOld;

		private boolean visible = false;

		public void setVisible( final boolean visible )
		{
			this.visible = visible;
		}

		public boolean visible()
		{
			return visible;
		}

		public void updateImage()
		{
			imgOld = img;
		}

		public BufferedImage create()
		{
			return create( viewer.getWidth(), viewer.getHeight() );
		}

		public BufferedImage create( final int width, final int height )
		{
//            return new BufferedImage( width, height, BufferedImage.TYPE_INT_ARGB );
			return new BufferedImage( width, height, BufferedImage.TYPE_BYTE_INDEXED, new IndexColorModel( 2, 2, r, g, b, a ) );
		}

		@Override
		public void drawOverlays( final Graphics g )
		{
			if ( visible )
			{
				final Graphics2D g2d = ( Graphics2D ) g;

				final AlphaComposite comp = AlphaComposite.getInstance( AlphaComposite.SRC_OVER, overlayAlpha );
				g2d.setComposite( comp );

				g2d.drawImage( img, 0, 0, null );
			}
		}

		@Override
		public void setCanvasSize( final int width, final int height )
		{
			img = create( width, height );// new BufferedImage( width, height,
											// BufferedImage.TYPE_INT_ARGB );
			imgOld = imgOld == null ? create( width, height ) : imgOld; // new
																		// BufferedImage(
																		// width,
																		// height,
																		// BufferedImage.TYPE_INT_ARGB
																		// ) :
																		// imgOld;

			final Graphics2D g = ( Graphics2D ) img.getGraphics();
			g.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR );
			// define AffineTransform
			final double scale = ( double ) img.getWidth() / imgOld.getWidth();
			final AffineTransform tf = new AffineTransform( scale, 0, 0, scale, 0, ( img.getHeight() - scale * imgOld.getHeight() ) * 0.5 );
			g.drawImage( imgOld, tf, null );
		}
	}

	private class MoveBrush extends SelfRegisteringBehaviour implements DragBehaviour
	{
		public MoveBrush( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void init( final int x, final int y )
		{
			brushOverlay.setPosition( x, y );
			brushOverlay.setVisible( true );
			// TODO request only overlays to repaint
			viewer.setCursor( Cursor.getPredefinedCursor( Cursor.CROSSHAIR_CURSOR ) );
			viewer.getDisplay().repaint();
		}

		@Override
		public void drag( final int x, final int y )
		{
			brushOverlay.setPosition( x, y );
		}

		@Override
		public void end( final int x, final int y )
		{
			brushOverlay.setVisible( false );
			// TODO request only overlays to repaint
			viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
			viewer.getDisplay().repaint();

		}
	}

	private class ChangeBrushRadius extends SelfRegisteringBehaviour implements ScrollBehaviour
	{
		public ChangeBrushRadius( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void scroll( final double wheelRotation, final boolean isHorizontal, final int x, final int y )
		{
			if ( !isHorizontal )
			{
				if ( wheelRotation < 0 )
					brushOverlay.radius += 1;
				else if ( wheelRotation > 0 )
					brushOverlay.radius = Math.max( 2, brushOverlay.radius - 1 );
				// TODO request only overlays to repaint
				viewer.getDisplay().repaint();
			}
		}
	}

	private abstract class AbstractPaintBehavior extends SelfRegisteringBehaviour implements DragBehaviour
	{
		private int oX, oY;

		public AbstractPaintBehavior( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		protected void paint( final int x, final int y )
		{
			final Ellipse2D e = new Ellipse2D.Double( x - brushOverlay.radius, y - brushOverlay.radius, 2 * brushOverlay.radius + 1, 2 * brushOverlay.radius + 1 );
			action( filledPixelsOverlay.img, new Area( e ) );
			filledPixelsOverlay.updateImage();
			System.out.println( filledPixelsOverlay.img.getRGB( x, y ) );
		}

		protected void paint( final int x1, final int y1, final int x2, final int y2 )
		{
			final double[] p1 = { x1, y1 };
			final double[] p2 = { x2, y2 };
			LinAlgHelpers.subtract( p2, p1, p2 );

			final double l = LinAlgHelpers.length( p2 );
			LinAlgHelpers.normalize( p2 );

			System.out.println( x1 + " " + y1 + ", " + x2 + " " + y2 + ", " + l );
			long xOld = Math.round( p1[ 0 ] ), yOld = Math.round( p1[ 1 ] );
			for ( int i = 1; i < l; ++i )
			{

				LinAlgHelpers.add( p1, p2, p1 );
				final long x = Math.round( p1[ 0 ] ), y = Math.round( p1[ 1 ] );
				if ( x != xOld || y != yOld )
				{
					paint( ( int ) x, ( int ) y );
					xOld = x;
					yOld = y;
				}
			}
			paint( x2, y2 );
		}

		abstract protected void action( BufferedImage img, Area brush );

		@Override
		public void init( final int x, final int y )
		{

			filledPixelsOverlay.setVisible( true );

			synchronized ( this )
			{
				oX = x;
				oY = y;
			}

			paint( x, y );

			viewer.getDisplay().repaint();

//             System.out.println( getName() + " drag start (" + oX + ", " + oY + ")" );
		}

		@Override
		public void drag( final int x, final int y )
		{
			brushOverlay.setPosition( x, y );

			paint( oX, oY, x, y );

//            System.out.println( getName() + " drag by (" + (x -oX ) + ", " + (y-oY) + ")" );

			synchronized ( this )
			{
				oX = x;
				oY = y;
			}
			viewer.getDisplay().repaint();

		}

		@Override
		public void end( final int x, final int y )
		{

		}
	}

	private class Paint extends AbstractPaintBehavior
	{

		public Paint( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		protected void action( final BufferedImage img, final Area brush )
		{
			final Graphics2D g = ( Graphics2D ) img.getGraphics();
			g.fill( brush );
		}
	}

	private class Erase extends AbstractPaintBehavior
	{

		public Erase( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		protected void action( final BufferedImage img, final Area brush )
		{
			final Graphics2D g = ( Graphics2D ) img.getGraphics();
			g.setComposite( AlphaComposite.Src );
			g.setColor( new Color( 0, 0, 0, 0 ) );
			g.fill( brush );
			g.setComposite( AlphaComposite.SrcOver );
		}
	}

	private class OverlayVisibility extends ModeToggleController.SelfRegisteringAction
	{
		public OverlayVisibility( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed( final ActionEvent actionEvent )
		{
			filledPixelsOverlay.setVisible( !filledPixelsOverlay.visible() );
			viewer.getDisplay().repaint();
		}
	}

	public static ArrayImg< ByteType, ByteArray > wrapBufferedImage( final BufferedImage img )
	{
		final byte[] imgData = ( ( DataBufferByte ) img.getRaster().getDataBuffer() ).getData();
		ArrayImgs.bytes( imgData, img.getWidth(), img.getHeight() );
		return ArrayImgs.bytes( imgData, img.getWidth(), img.getHeight() );
	}

	private class Fill extends SelfRegisteringBehaviour implements ClickBehaviour
	{

		public Filter< Pair< ByteType, ByteType >, Pair< ByteType, ByteType > > filter = ( p1, p2 ) -> {
			return p1.getB().get() != p2.getB().get();
		};

		public Fill( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void click( final int x, final int y )
		{
			if ( filledPixelsOverlay.visible() )
			{
				synchronized ( viewer )
				{
					viewer.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );

					final Point p = new Point( x, y );

					final ArrayImg< ByteType, ByteArray > img = wrapBufferedImage( filledPixelsOverlay.img );

					final ByteType extension = new ByteType( ( byte ) 1 );

					final long t0 = System.currentTimeMillis();
					final ArrayRandomAccess< ByteType > ra = img.randomAccess();
					ra.setPosition( p );
					FloodFill.fill( Views.extendValue( img, extension ), Views.extendValue( img, extension ), p, extension.copy(), extension.copy(), new DiamondShape( 1 ), filter );
					final long t1 = System.currentTimeMillis();
					System.out.println( "Filling took " + ( t1 - t0 ) + " ms" );
					viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
					viewer.getDisplay().repaint();
				}
			}
		}
	}

	private void setCoordinates( final int x, final int y )
	{
		labelLocation.setPosition( x, 0 );
		labelLocation.setPosition( y, 1 );
		labelLocation.setPosition( 0, 2 );

		viewer.displayToGlobalCoordinates( labelLocation );

		labelTransform.applyInverse( labelLocation, labelLocation );
	}

	private class IntersectAndLeave extends ModeToggleController.AbstractUnToggleOnClick implements ClickBehaviour
	{

		private final Runnable action;

		public IntersectAndLeave( final Runnable action, final TriggerBehaviourBindings bindings, final InputActionBindings inputActionBindings, final String name, final String... defaultTriggers )
		{
			super( bindings, inputActionBindings, name, defaultTriggers );
			this.action = action;
		}

		@Override
		public void doOnUnToggle( final int x, final int y )
		{
			synchronized ( viewer )
			{
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
				setCoordinates( x, y );

				final Point p = new Point( Math.round( labelLocation.getDoublePosition( 0 ) ), Math.round( labelLocation.getDoublePosition( 1 ) ), Math.round( labelLocation.getDoublePosition( 2 ) ) );

				final ArrayImg< ByteType, ByteArray > img = wrapBufferedImage( filledPixelsOverlay.img );
				final ArrayRandomAccess< ByteType > imgAccess = img.randomAccess();
				imgAccess.setPosition( new int[] { x, y } );

				final byte overlayValueAtPoint = imgAccess.get().get();

				final ExtendedRandomAccessibleInterval< ByteType, IntervalView< ByteType > > borderExtended = Views.extendBorder( Views.interval( Views.addDimension( img ), new FinalInterval( img.dimension( 0 ), img.dimension( 1 ), overlayValueAtPoint ) ) );

				final RandomAccessibleOnRealRandomAccessible< ByteType > interpolatedAndTransformed = Views.raster( RealViews.transform( Views.interpolate( borderExtended, new NearestNeighborInterpolatorFactory<>() ), labelTransform.inverse().copy().concatenate( viewerToGlobalCoordinatesTransform.inverse() )// toLabelSpace
				) );

				final long seedFragmentLabel = LabelFillController.getBiggestLabel( labels, p );
				System.out.println( seedFragmentLabel + " " + overlayValueAtPoint + " " + getColor().getRGB() );
				final RandomAccess< LongType > paintedLabelAccess = paintedLabels.randomAccess();
				paintedLabelAccess.setPosition( p );
				final long paintedLabel = paintedLabelAccess.get().get();
				final long segmentLabel = assignment.getSegment( seedFragmentLabel );
				final long comparison = paintedLabel == TRANSPARENT ? segmentLabel : paintedLabel;
				final long[] fragmentsContainedInSegment = assignment.getFragments( segmentLabel );

				final Filter< Pair< Pair< LabelMultisetType, ByteType >, LongType >, Pair< Pair< LabelMultisetType, ByteType >, LongType > > filter = ( p1, p2 ) -> {

					final Pair< LabelMultisetType, ByteType > multiSetOverlayPairComp = p1.getA();
					final long currentPaint = p1.getB().get();

					if ( multiSetOverlayPairComp.getB().get() == overlayValueAtPoint && currentPaint != p2.getB().get() )
					{
						if ( currentPaint != TRANSPARENT )
							return currentPaint == comparison;
						else
						{
							final LabelMultisetType currentMultiSet = multiSetOverlayPairComp.getA();
							for ( final long fragment : fragmentsContainedInSegment )
								if ( currentMultiSet.contains( fragment ) )
									return true;
							return false;
						}
					}

					return false;
				};

				final long t0 = System.currentTimeMillis();

				FloodFill.fill( new RandomAccessiblePair<>( Views.extendValue( labels, new LabelMultisetType() ), interpolatedAndTransformed ), Views.extendValue( paintedLabels, new LongType( TRANSPARENT ) ), p, new ValuePair<>( new LabelMultisetType(), new ByteType( overlayValueAtPoint ) ), new LongType( selectionController.getActiveFragmentId() ), new DiamondShape( 1 ), filter );
				final long t1 = System.currentTimeMillis();
				System.out.println( "Filling took " + ( t1 - t0 ) + " ms" );
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
				viewer.requestRepaint();
			}
			action.run();
		}
	}

}
