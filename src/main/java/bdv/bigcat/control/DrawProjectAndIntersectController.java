package bdv.bigcat.control;

import bdv.BigDataViewer;
import bdv.bigcat.control.ModeToggleController.NoActionUnToggle;
import bdv.util.AbstractNamedAction.NamedActionAdder;
import bdv.viewer.InputActionBindings;
import bdv.viewer.TriggerBehaviourBindings;
import bdv.viewer.ViewerPanel;
import net.imglib2.Point;
import net.imglib2.algorithm.fill.Filter;
import net.imglib2.algorithm.fill.FloodFill;
import net.imglib2.algorithm.fill.Writer;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.ui.OverlayRenderer;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import org.scijava.ui.behaviour.*;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.util.Random;

/**
 * @autoher Philipp Hanslovsky &lt;hanslovskyp@janelia.hhmi.org&gt;
 */
public class DrawProjectAndIntersectController {

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


    private ArrayImg< BitType, LongArray > localCanvas = null;
    public final BrushOverlay brushOverlay = new BrushOverlay();
    private float overlayAlpha = 0.5f;
    private int width = 0;
    private int height = 0;

    public DrawProjectAndIntersectController(
            final BigDataViewer bdv,
            final InputTriggerConfig config,
            final InputActionBindings inputActionBindings,
            final TriggerBehaviourBindings bindings,
            final String... activateModeKeys ) {
        this.bdv = bdv;
        this.viewer = bdv.getViewer();
        this.config = config;
        this.inputActionBindings = inputActionBindings;
        this.bindings = bindings;

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

//        RandomPixelOnCanvas rpc = new RandomPixelOnCanvas( "draw on image", "D" );
//        rpc.register( ksWithinModeActionAdder, ksWithinModeInputAdder );

//        PutCanvasToScreen pcs = new PutCanvasToScreen("put canvas to screen", "P");
//        pcs.register( ksWithinModeActionAdder, ksWithinModeInputAdder );

        ClearArea cc = new ClearArea("clear canvas", "C");
        cc.register( ksWithinModeActionAdder, ksWithinModeInputAdder );

//        FillCanvas fc = new FillCanvas("fill canvas", "M");
//        fc.register( withinModeBehaviourMap, withinModeInputTriggerAdder );

        MoveBrush mb = new MoveBrush("move brush", "SPACE");
        mb.register( withinModeBehaviourMap, withinModeInputTriggerAdder );

        ChangeBrushRadius cbr = new ChangeBrushRadius("change brush radius", "SPACE scroll");
        cbr.register( withinModeBehaviourMap, withinModeInputTriggerAdder );

        Paint p = new Paint("paint", "SPACE button1");
        p.register( withinModeBehaviourMap, withinModeInputTriggerAdder );

        Erase e = new Erase("erase", "SPACE button2", "SPACE button3");
        e.register( withinModeBehaviourMap, withinModeInputTriggerAdder );

        OverlayVisibility ov = new OverlayVisibility("visibility", "V");
        ov.register( ksWithinModeActionAdder, ksWithinModeInputAdder );

        Fill f = new Fill( "fill", "M button1" );
        f.register( withinModeBehaviourMap, withinModeInputTriggerAdder );


        Toggle toggle = new Toggle(bindings, inputActionBindings, "activate dpi mode", activateModeKeys );
        ModeToggleController.registerToggle( config, inputActionBindings, toggle, "dpi mode controller" );

        viewer.getDisplay().addOverlayRenderer( brushOverlay );
        viewer.getDisplay().addOverlayRenderer( filledPixelsOverlay );

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


//    private class RandomPixelOnCanvas extends ModeToggleController.SelfRegisteringAction
//    {
//
//        Random rng = new Random( 100 );
//
//        public RandomPixelOnCanvas(String name, String... defaultTriggers) {
//            super(name, defaultTriggers);
//        }
//
//        @Override
//        public void actionPerformed(ActionEvent actionEvent) {
//            long w = localCanvas.dimension(0);
//            long h = localCanvas.dimension(1);
//            int x = rng.nextInt((int) w);
//            int y = rng.nextInt((int) h);
//            ArrayRandomAccess<BitType> access = localCanvas.randomAccess();
//            access.setPosition( new int[] { x, y } );
//            access.get().set( true );
//            System.out.println( x + " " + y );
//        }
//    }


//    private class PutCanvasToScreen extends ModeToggleController.SelfRegisteringAction
//    {
//
//        public PutCanvasToScreen(String name, String... defaultTriggers) {
//            super(name, defaultTriggers);
//        }
//
//        @Override
//        public void actionPerformed(ActionEvent actionEvent) {
//            Graphics g = viewer.getGraphics();
//            for(ArrayCursor<BitType> c = localCanvas.cursor(); c.hasNext(); )
//            {
//                c.fwd();
//                if ( c.get().get() ) {
//                    int x = c.getIntPosition( 0 );
//                    int y = c.getIntPosition( 1 );
//                    g.drawLine( x, y, x, y );
//                }
//            }
//        }
//    }


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
            DrawProjectAndIntersectController.this.width = width;
            DrawProjectAndIntersectController.this.height = height;
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
            DrawProjectAndIntersectController.this.width = width;
            DrawProjectAndIntersectController.this.height = height;
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


    private abstract class AbstractPaintBehavior extends SelfRegisteringBehaviour implements DragBehaviour
    {
        private int oX, oY;

        public AbstractPaintBehavior( final String name, final String... defaultTriggers )
        {
            super( name, defaultTriggers );
        }


        protected void paint( final int x, final int y )
        {
            Ellipse2D e = new Ellipse2D.Double( x - brushOverlay.radius, y - brushOverlay.radius, 2*brushOverlay.radius+1, 2*brushOverlay.radius+1 );
            action( filledPixelsOverlay.img, new Area( e ) );
            filledPixelsOverlay.updateImage();
//            System.out.println( filledPixels );
        }

        protected void paint( final int x1, final int y1, final int x2, final int y2 )
        {
            final double[] p1 = { x1, y1 };
            final double[] p2 = { x2, y2 };
            LinAlgHelpers.subtract( p2, p1, p2 );

            final double l = LinAlgHelpers.length( p2 );
            LinAlgHelpers.normalize( p2 );

            System.out.println( x1 + " " + y1 + ", " + x2 + " " + y2 + ", " + l );
            long xOld = Math.round( p1[0] ), yOld = Math.round( p1[1] );
            for ( int i = 1; i < l; ++i )
            {

                LinAlgHelpers.add( p1, p2, p1 );
                long x = Math.round( p1[0] ), y = Math.round( p1[1] );
                if ( x != xOld || y != yOld )
                {
                    paint( (int) x, (int) y );
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

//            viewer.requestRepaint();
            viewer.getDisplay().repaint();

        }

        @Override
        public void end( final int x, final int y )
        {
//            Graphics2D g = (Graphics2D) (viewer.getGraphics());
//            g.setColor( Color.BLACK );
//            g.fill( filledPixels );
        }
    }


    private class Paint extends AbstractPaintBehavior
    {

        public Paint(String name, String... defaultTriggers) {
            super(name, defaultTriggers);
        }

        @Override
        protected void action( BufferedImage img, Area brush) {
//            filledPixels.add( brush );
            Graphics2D g = (Graphics2D) img.getGraphics();
            g.fill( brush );
        }
    }


    private class Erase extends AbstractPaintBehavior
    {

        public Erase(String name, String... defaultTriggers) {
            super(name, defaultTriggers);
        }

        @Override
        protected void action( BufferedImage img, Area brush) {
//            filledPixels.add( brush );
            Graphics2D g = (Graphics2D) img.getGraphics();
            g.fill( brush );
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


    private class Fill extends SelfRegisteringBehaviour implements ClickBehaviour
    {

        public Filter< Pair< IntType, IntType >, Pair< IntType, IntType > > filter = (p1, p2) -> p1.getB().get() != p2.getB().get();
//        public Filter< Pair< IntType, IntType >, Pair< IntType, IntType > > filter = new Filter<Pair<IntType, IntType>, Pair<IntType, IntType>>() {
//            @Override
//            public boolean accept(Pair<IntType, IntType> p1, Pair<IntType, IntType> p2) {
//                return p1.getB().getIntegerLong() != p2.getB().getIntegerLong();
//            }
//        };

        public Fill(String name, String... defaultTriggers) {
            super(name, defaultTriggers);
        }

        @Override
        public void click( int x, int y ) {
            synchronized( viewer )
            {
                viewer.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );

                final Point p = new Point( x, y );

                int[] imgData = ((DataBufferInt)filledPixelsOverlay.img.getRaster().getDataBuffer()).getData();
                ArrayImg<IntType, IntArray> img = ArrayImgs.ints(imgData, filledPixelsOverlay.img.getWidth(), filledPixelsOverlay.img.getHeight());

//                IntType extension = new IntType( 0 |
//                        Color.WHITE.getAlpha() << 24 |
//                        Color.WHITE.getRed() << 16 |
//                        Color.WHITE.getGreen() << 8|
//                        Color.WHITE.getBlue() << 0
//                );

                IntType extension = new IntType(Color.WHITE.getRGB());



                final long t0 = System.currentTimeMillis();
                ArrayRandomAccess<IntType> ra = img.randomAccess();
                ra.setPosition( p );
//                ra.get().set( 255 | 255 << 8 );
                FloodFill.fill(
                        Views.extendValue( img, extension ),
                        Views.extendValue( img, extension ),
                        p,
                        extension.copy(),
                        extension.copy(),
                        new DiamondShape( 1 ),
                        filter );
                final long t1 = System.currentTimeMillis();
                System.out.println( "Filling took " + ( t1 - t0 ) + " ms" );
                viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
                filledPixelsOverlay.setVisible( true );
                viewer.getDisplay().repaint();
//                filledPixelsOverlay.
//                viewer.requestRepaint();
            }
        }
    }


}
