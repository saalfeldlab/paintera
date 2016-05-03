package bdv.bigcat.control;

import static bdv.labels.labelset.Label.TRANSPARENT;

import java.awt.*;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import javax.swing.*;

import net.imglib2.*;
import net.imglib2.Point;
import net.imglib2.algorithm.fill.Filter;
import net.imglib2.algorithm.fill.FloodFill;
import net.imglib2.algorithm.fill.Writer;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.outofbounds.OutOfBounds;
import net.imglib2.realtransform.*;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.ui.OverlayRenderer;
import net.imglib2.ui.TransformListener;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.*;

import org.scijava.ui.behaviour.*;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.BigDataViewer;
import bdv.bigcat.FragmentSegmentAssignment;
import bdv.labels.labelset.LabelMultisetType;
import bdv.util.AbstractNamedAction.NamedActionAdder;
import bdv.util.IdService;
import bdv.viewer.InputActionBindings;
import bdv.viewer.TriggerBehaviourBindings;
import bdv.viewer.ViewerPanel;

/**
 * @autoher Philipp Hanslovsky &lt;hanslovskyp@janelia.hhmi.org&gt;
 */
public class CreateSeparatingManifoldController implements TransformListener< AffineTransform3D > {

    private final BigDataViewer bdv;
    private final ViewerPanel viewer;
    private final InputTriggerConfig config;
    private final InputActionBindings inputActionBindings;
    private final TriggerBehaviourBindings bindings;

    private final InputMap ksWithinModeInputMap = new InputMap();
    private final ActionMap ksWithinModeActionMap = new ActionMap();

    private final BehaviourMap withinModeBehaviourMap = new BehaviourMap();
    private final InputTriggerMap withinModeInputTriggerMap = new InputTriggerMap();

    private final Shape shape = new DiamondShape( 1 );

    private final Area filledPixels = new Area();
    private final AreaOverlay filledPixelsOverlay = new AreaOverlay();

    private Rectangle rectangle = null;


    private ArrayImg< BitType, LongArray > localCanvas = null;
    public final BrushOverlay brushOverlay = new BrushOverlay();
    private float overlayAlpha = 0.5f;
    private int width = 0;
    private int height = 0;
    final protected RealPoint labelLocation = new RealPoint( 3 );
    final protected AffineTransform3D viewerToGlobalCoordinatesTransform = new AffineTransform3D();
    final protected AffineTransform3D labelTransform;

    final protected FragmentSegmentAssignment assignment;
    final protected RandomAccessibleInterval<LabelMultisetType> labels;
    final protected RandomAccessibleInterval<LongType> paintedLabels;

    public CreateSeparatingManifoldController(
            final BigDataViewer bdv,
            final AffineTransform3D viewerToGlobalCoordinatesTransform,
            final InputTriggerConfig config,
            final RandomAccessibleInterval<LabelMultisetType > labels,
            final RandomAccessibleInterval< LongType > paintedLabels,
            final AffineTransform3D labelTransform,
            final FragmentSegmentAssignment assignment,
            final InputActionBindings inputActionBindings,
            final TriggerBehaviourBindings bindings,
            final String... activateModeKeys ) {
        this.bdv = bdv;
        this.viewer = bdv.getViewer();
        this.config = config;
        this.inputActionBindings = inputActionBindings;
        this.labels = labels;
        this.paintedLabels = paintedLabels;
        this.labelTransform = labelTransform;
        this.assignment = assignment;
        this.bindings = bindings;

        viewer.addTransformListener( this );

        NamedActionAdder ksWithinModeActionAdder = new NamedActionAdder(ksWithinModeActionMap);
        KeyStrokeAdder ksWithinModeInputAdder = config.keyStrokeAdder(ksWithinModeInputMap, "within dpi mode");
        InputTriggerAdder withinModeInputTriggerAdder = config.inputTriggerAdder(withinModeInputTriggerMap, "within dpi mdoe");

        Runnable action = new Runnable() {
            @Override
            public void run() {
                filledPixelsOverlay.setVisible(false);
                viewer.getDisplay().repaint();
            }
        };

        ModeToggleController.ExecuteOnUnToggle noActionUnToggle =
                new ModeToggleController.ExecuteOnUnToggle(action, bindings, inputActionBindings, "abort dpi", "T");
        noActionUnToggle.register( ksWithinModeActionAdder, ksWithinModeInputAdder );

        RectangleCreator rc = new RectangleCreator("rectangle creator", "SPACE button1");
        rc.register( withinModeBehaviourMap, withinModeInputTriggerAdder );



        Toggle toggle = new Toggle(bindings, inputActionBindings, "activate dpi mode", activateModeKeys );
        ModeToggleController.registerToggle( config, inputActionBindings, toggle, "dpi mode controller" );

        viewer.getDisplay().addOverlayRenderer( brushOverlay );
        viewer.getDisplay().addOverlayRenderer( filledPixelsOverlay );

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

        public void register( BehaviourMap behaviourMap, InputTriggerAdder inputAdder )
        {
            behaviourMap.put( name, this );
            inputAdder.put( name, defaultTriggers );
        }
    }



    private class Toggle extends ModeToggleController.AbstractToggle
    {

        private Toggle(
                TriggerBehaviourBindings bindings,
                InputActionBindings inputActionBindings,
                String name,
                String... defaultTriggers) {
            super( bindings, inputActionBindings, ksWithinModeInputMap, ksWithinModeActionMap,
                    withinModeBehaviourMap, withinModeInputTriggerMap, name, defaultTriggers );
        }

        @Override
        public void actionImplementation() {
            localCanvas = ArrayImgs.bits( viewer.getWidth(), viewer.getHeight() );
            filledPixelsOverlay.img = new BufferedImage( viewer.getWidth(), viewer.getHeight(), BufferedImage.TYPE_INT_ARGB );
            filledPixelsOverlay.imgOld = null;
            filledPixelsOverlay.setVisible( true );
            System.out.println( "Action: "  + localCanvas );
        }
    }


    private class ClearArea extends ModeToggleController.SelfRegisteringAction
    {

        public ClearArea(String name, String... defaultTriggers) {
            super(name, defaultTriggers);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            filledPixelsOverlay.img = new BufferedImage( viewer.getWidth(), viewer.getHeight(), BufferedImage.TYPE_INT_ARGB );
            filledPixelsOverlay.imgOld = null;
            viewer.getDisplay().repaint();
        }
    }


    private class FillCanvas extends SelfRegisteringBehaviour implements ClickBehaviour
    {

        private final Point seed = new Point( 2 );
        private final Filter< Pair< BitType, BitType >, Pair< BitType, BitType > > filter
                = (Filter) (p1, p2) -> !((Pair<BitType,BitType>)p1).getA().get();
        private final Writer< BitType > writer = (source, target) -> target.set( true );


        public FillCanvas(String name, String... defaultTriggers) {
            super(name, defaultTriggers);
        }

        @Override
        public void click(int x, int y) {
            seed.setPosition( x , 0 );
            seed.setPosition( y , 1 );
            FloodFill.fill(
                    Views.extendValue( localCanvas, new BitType( true ) ),
                    Views.extendValue( localCanvas, new BitType( true ) ),
                    seed,
                    new BitType( false ),
                    new BitType( true ),
                    shape,
                    filter,
                    writer
            );
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
        public void drawOverlays( Graphics g ) {
            if ( visible ) {
                final Graphics2D g2d = (Graphics2D) g;
                g2d.setColor( Color.WHITE );
                g2d.setStroke(stroke);
                g2d.drawOval(x - radius, y - radius, 2*radius + 1, 2*radius + 1);
            }
        }

        @Override
        public void setCanvasSize( final int width, final int height )
        {
            CreateSeparatingManifoldController.this.width = width;
            CreateSeparatingManifoldController.this.height = height;
        }

        public void setColor( float r, float g, float b, float a )
        {

        }
    }


    private class AreaOverlay implements OverlayRenderer
    {

        private BufferedImage img;
        private BufferedImage imgOld;

        private boolean visible = false;

        public void setVisible( boolean visible )
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

        @Override
        public void drawOverlays(Graphics g) {
            if ( visible ) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setColor( Color.WHITE );
                AlphaComposite comp = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, overlayAlpha);
                g2d.setComposite( comp );
                g2d.drawImage( img, 0, 0, null );
            }
        }

        @Override
        public void setCanvasSize( final int width, final int height )
        {
            CreateSeparatingManifoldController.this.width = width;
            CreateSeparatingManifoldController.this.height = height;
//            imgOld = img;
            img = new BufferedImage( width, height, BufferedImage.TYPE_INT_ARGB );
            imgOld = imgOld == null ? new BufferedImage( width, height, BufferedImage.TYPE_INT_ARGB ) : imgOld;
            Graphics2D g = (Graphics2D) img.getGraphics();
            g.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR );
            // define AffineTransform
            double scale = (double)img.getWidth()/imgOld.getWidth();
            AffineTransform tf = new AffineTransform(
                    scale, 0,
                    0, scale,
                    0, (img.getHeight() - scale*imgOld.getHeight())*0.5
                    );
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


    private class RectangleCreator extends SelfRegisteringBehaviour implements DragBehaviour
    {

        boolean visible = false;
        Rectangle r = new Rectangle();
        Area a = new Area( r );

        private OverlayRenderer showRectangleWhileDrawing = new OverlayRenderer() {

            @Override
            public void drawOverlays(Graphics g) {
                if ( visible )
                {
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setColor( Color.WHITE );
                    g2d.fill( r );
                }
            }

            @Override
            public void setCanvasSize(int width, int height) {

            }
        };

        private int startX, startY;

        public RectangleCreator(String name, String... defaultTriggers) {
            super(name, defaultTriggers);
        }

        private void setRectangle( int x, int y )
        {
            int xLow = Math.min(x, startX);
            int yLow = Math.min(y, startY);

            int xSize = Math.abs(x - startX);
            int ySize = Math.abs(y - startY);

            r.setLocation( xLow, yLow );
            r.setSize( xSize, ySize );

        }

        @Override
        public void init(int x, int y) {
            startX = x;
            startY = y;
            r.setLocation( x, y );
            setRectangle( x, y );
            visible = true;
            viewer.getDisplay().addOverlayRenderer( showRectangleWhileDrawing );
            viewer.getDisplay().repaint();
        }

        @Override
        public void drag(int x, int y) {
            setRectangle( x, y );
            viewer.getDisplay().repaint();
        }

        @Override
        public void end(int x, int y) {
            setRectangle( x, y );
            visible = false;
            viewer.getDisplay().removeOverlayRenderer( showRectangleWhileDrawing );
            viewer.getDisplay().repaint();
        }
    }





    private class OverlayVisibility extends ModeToggleController.SelfRegisteringAction
    {
        public OverlayVisibility(String name, String... defaultTriggers) {
            super(name, defaultTriggers);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            filledPixelsOverlay.setVisible(!filledPixelsOverlay.visible());
            viewer.getDisplay().repaint();
        }
    }


    public static ArrayImg< IntType, IntArray > wrapBufferedImage( BufferedImage img )
    {
        int[] imgData = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();
        return ArrayImgs.ints(imgData, img.getWidth(), img.getHeight());
    }


    private class Fill extends SelfRegisteringBehaviour implements ClickBehaviour
    {

        public Filter< Pair< IntType, IntType >, Pair< IntType, IntType > > filter = (p1, p2) -> p1.getB().get() != p2.getB().get();

        public Fill(String name, String... defaultTriggers) {
            super(name, defaultTriggers);
        }

        @Override
        public void click( int x, int y ) {
            if ( filledPixelsOverlay.visible() ) {
                synchronized (viewer) {
                    viewer.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                    final Point p = new Point(x, y);

//                    int[] imgData = ((DataBufferInt) filledPixelsOverlay.img.getRaster().getDataBuffer()).getData();
//                    ArrayImg<IntType, IntArray> img = ArrayImgs.ints(imgData, filledPixelsOverlay.img.getWidth(), filledPixelsOverlay.img.getHeight());
                    ArrayImg<IntType, IntArray> img = wrapBufferedImage(filledPixelsOverlay.img);

//                IntType extension = new IntType( 0 |
//                        Color.WHITE.getAlpha() << 24 |
//                        Color.WHITE.getRed() << 16 |
//                        Color.WHITE.getGreen() << 8|
//                        Color.WHITE.getBlue() << 0
//                );

                    IntType extension = new IntType(Color.WHITE.getRGB());


                    final long t0 = System.currentTimeMillis();
                    ArrayRandomAccess<IntType> ra = img.randomAccess();
                    ra.setPosition(p);
//                ra.get().set( 255 | 255 << 8 );
                    FloodFill.fill(
                            Views.extendValue(img, extension),
                            Views.extendValue(img, extension),
                            p,
                            extension.copy(),
                            extension.copy(),
                            new DiamondShape(1),
                            filter);
                    final long t1 = System.currentTimeMillis();
                    System.out.println("Filling took " + (t1 - t0) + " ms");
                    viewer.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
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

        public IntersectAndLeave(
                Runnable action,
                TriggerBehaviourBindings bindings,
                InputActionBindings inputActionBindings,
                String name,
                String... defaultTriggers) {
            super( bindings, inputActionBindings, name, defaultTriggers);
            this.action = action;
        }

        @Override
        public void doOnUnToggle( int x, int y ) {
            synchronized ( viewer )
            {
                viewer.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
                setCoordinates( x, y );
//                System.out.println( "Filling " + labelLocation + " with " + selectionController.getActiveFragmentId() );


                final Point p = new Point(
                        Math.round( labelLocation.getDoublePosition( 0 ) ),
                        Math.round( labelLocation.getDoublePosition( 1 ) ),
                        Math.round( labelLocation.getDoublePosition( 2 ) ) );

                ArrayImg<IntType, IntArray> img = wrapBufferedImage(filledPixelsOverlay.img);

                int overlayValueAtPoint = filledPixelsOverlay.img.getRGB( x, y );

                ExtendedRandomAccessibleInterval<IntType, IntervalView<IntType>> borderExtended
                        = Views.extendBorder(Views.interval(Views.addDimension(img), new FinalInterval(img.dimension(0), img.dimension(1), 1)));

                AffineTransform3D offset = new AffineTransform3D();
                offset.setTranslation( new double[] { -0.5*filledPixelsOverlay.img.getWidth(), -0.5*filledPixelsOverlay.img.getHeight(), 0 } );

                AffineTransform3D imgToGlobalCoordinates = viewerToGlobalCoordinatesTransform.inverse().copy().concatenate(offset);

                RealPoint dummy = new RealPoint( 3 );
                dummy.setPosition( x, 0 );
                dummy.setPosition( y, 1 );
                dummy.setPosition( 0, 2 );
                offset.apply( dummy, dummy );
                viewer.displayToGlobalCoordinates( dummy );

                RealTransformRandomAccessible<IntType, InverseRealTransform> iat =
                        RealViews.transform(Views.interpolate(borderExtended, new NearestNeighborInterpolatorFactory<>()), imgToGlobalCoordinates);
                RealTransformRealRandomAccessible<IntType, InverseRealTransform>.RealTransformRealRandomAccess dummyAccess = iat.realRandomAccess();
                dummyAccess.setPosition( dummy );
                System.out.println( "Dummy: " + dummyAccess.get().get() );

                AffineTransform3D toLabelSpace = labelTransform.inverse().copy().concatenate(imgToGlobalCoordinates);

                RandomAccessibleOnRealRandomAccessible<IntType> interpolatedAndTransformed = Views.raster(
                        RealViews.transform(
                                Views.interpolate(borderExtended, new NearestNeighborInterpolatorFactory<>()),
                                labelTransform.inverse().copy().concatenate( viewerToGlobalCoordinatesTransform.inverse() )// toLabelSpace
                ) );

                RealPoint dummyP = new RealPoint(3);
                System.out.println( p );
                toLabelSpace.applyInverse( dummyP , p );
                imgToGlobalCoordinates.applyInverse( dummy, dummy );
                System.out.println( x + " " + y + dummy + " " + " " + p + " " + dummyP );

                OutOfBounds<IntType> access1 = borderExtended.randomAccess();
                access1.setPosition( x, 0 );
                access1.setPosition( y, 1 );

                RandomAccess<IntType> access2 = interpolatedAndTransformed.randomAccess();
                access2.setPosition( p );

                RandomAccess<IntType> access3 = Views.interval(Views.addDimension(img), new FinalInterval(img.dimension(0), img.dimension(1), 1)).randomAccess();
                access3.setPosition( x, 0 );
                access3.setPosition( y, 1 );
                access3.setPosition( 0, 2 );

                System.out.println( "access: " + access1.get().get() + " " + access2.get().get() + " " + access3.get().get() );

                final long seedFragmentLabel = LabelFillController.getBiggestLabel( labels, p );
                System.out.println( seedFragmentLabel + " " + overlayValueAtPoint + " " + Color.WHITE.getRGB() );
                final RandomAccess<LongType> paintedLabelAccess = paintedLabels.randomAccess();
                paintedLabelAccess.setPosition( p );
                final long paintedLabel = paintedLabelAccess.get().get();
                long segmentLabel = assignment.getSegment(seedFragmentLabel);
                final long comparison = paintedLabel == TRANSPARENT ? segmentLabel : paintedLabel;
                final long[] fragmentsContainedInSegment = assignment.getFragments( segmentLabel );

                Filter< Pair<
                        Pair< LabelMultisetType, IntType >, LongType >,
                        Pair< Pair< LabelMultisetType, IntType >, LongType >  > filter  =
                        (p1, p2) -> {

                            Pair<LabelMultisetType, IntType> multiSetOverlayPairComp = p1.getA();
                            long currentPaint = p1.getB().get();

                            if ( multiSetOverlayPairComp.getB().get() == overlayValueAtPoint &&
                                    currentPaint != p2.getB().get() )
                            {
                                if ( currentPaint != TRANSPARENT )
                                    return currentPaint == comparison;
                                else {
                                    LabelMultisetType currentMultiSet = multiSetOverlayPairComp.getA();
                                    for ( final long fragment : fragmentsContainedInSegment )
                                        if ( currentMultiSet.contains( fragment ) )
                                            return true;
                                    return false;
                                }
                            }

                            return false;
                        };


                final long t0 = System.currentTimeMillis();

                RandomAccessiblePair<LabelMultisetType, IntType> def = new RandomAccessiblePair<>(Views.extendValue(labels, new LabelMultisetType()), interpolatedAndTransformed);
                FloodFill.fill(
                        new RandomAccessiblePair<LabelMultisetType, IntType>( Views.extendValue( labels, new LabelMultisetType() ), interpolatedAndTransformed ),
                        Views.extendValue( paintedLabels, new LongType( TRANSPARENT ) ),
                        p,
                        new ValuePair<>( new LabelMultisetType(), new IntType( overlayValueAtPoint )),
                        new LongType( IdService.allocate() ),
                        new DiamondShape( 1 ),
                        filter );
                final long t1 = System.currentTimeMillis();
                System.out.println( "Filling took " + ( t1 - t0 ) + " ms" );
                viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
                viewer.requestRepaint();
            }
            action.run();
        }
    }


}
