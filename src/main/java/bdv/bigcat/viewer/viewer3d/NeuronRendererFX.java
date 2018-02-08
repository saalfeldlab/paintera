package bdv.bigcat.viewer.viewer3d;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.bigcat.viewer.state.StateListener;
import bdv.bigcat.viewer.stream.ARGBStream;
import bdv.bigcat.viewer.stream.AbstractHighlightingARGBStream;
import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import bdv.bigcat.viewer.viewer3d.NeuronFX.ShapeKey;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableFloatArray;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.ObservableFaceArray;
import javafx.scene.shape.Shape3D;
import javafx.scene.shape.TriangleMesh;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;

/**
 *
 *
 * @author Vanessa Leite
 * @author Philipp Hanslovsky
 * @param <T>
 * @param <F>
 */
public class NeuronRendererFX< T, F extends FragmentSegmentAssignmentState< F > > implements StateListener< F >
{
	private final long selectedFragmentId;

	private long selectedSegmentId;

	private final F fragmentSegmentAssignment;

	private final ARGBStream stream;

	private final Localizable initialLocationInImageCoordinates;

	private final RandomAccessible< T > data;

	private final Interval interval;

	private final Function< T, Converter< T, BoolType > > createMaskConverterForType;

	private Converter< T, BoolType > maskConverter;

	private final Group root;

	private final ExecutorService es;

	private final AffineTransform3D toWorldCoordinates;

	private final int[] blockSize;

	private final int[] cubeSize;

	private boolean updateOnStateChange = true;

	private boolean allowRendering = true;

	private final ObjectProperty< Optional< NeuronFX > > neuron = new SimpleObjectProperty<>( Optional.empty() );

	private final MeshSaver meshSaver;

	private final ContextMenu rightClickMenu;

	private final EventHandler< MouseEvent > menuOpener;

	private final SelectedIds selectedIds;

	private final IdSelector idSelector = new IdSelector();

	private final MenuItem saverItem = new MenuItem( "Save neuron" );

	private final StateListener< AbstractHighlightingARGBStream > listener;

	private final SoftRefLoaderCache< ShapeKey, Shape3D > shapeCache = new SoftRefLoaderCache<>();

	/**
	 * Bounding box of the complete mesh/neuron (xmin, xmax, ymin, ymax, zmin,
	 * zmax)
	 */
	private final double[] completeBoundingBox = null;

	public NeuronRendererFX(
			final long selectedFragmentId,
			final F fragmentSegmentAssignment,
			final ARGBStream stream,
			final Localizable initialLocationInImageCoordinates,
			final RandomAccessible< T > data,
			final Interval interval,
			final Function< T, Converter< T, BoolType > > createMaskConverterForType,
			final Group root,
			final ExecutorService es,
			final AffineTransform3D toWorldCoordinates,
			final int[] blockSize,
			final int[] cubeSize,
			final SelectedIds selectedIds,
			final GlobalTransformManager transformManager )
	{
		super();
		this.selectedFragmentId = selectedFragmentId;
		this.selectedSegmentId = fragmentSegmentAssignment.getSegment( selectedFragmentId );
		this.fragmentSegmentAssignment = fragmentSegmentAssignment;
		this.stream = stream;
		this.initialLocationInImageCoordinates = initialLocationInImageCoordinates;
		this.data = data;
		this.interval = interval;
		this.createMaskConverterForType = createMaskConverterForType;
		this.root = root;
		this.es = es;
		this.toWorldCoordinates = toWorldCoordinates;
		this.blockSize = blockSize;
		this.cubeSize = cubeSize;

		updateForegroundCheck();
		this.fragmentSegmentAssignment.addListener( this );
		this.meshSaver = new MeshSaver();
		this.rightClickMenu = new ContextMenu();
		this.selectedIds = selectedIds;
		final MenuItem centerSlicesAt = new MenuItem();
		saverItem.setOnAction( meshSaver );
		saverItem.setDisable( true );
		// TODO are we ever going to work with data that requires RealPoint,
		// i.e. resolutions [a,b,c] with a,b,c < 0
		final Point clickedPoint = new Point( 3 );
		final AffineTransform3D globalTransform = new AffineTransform3D();
		transformManager.addListener( globalTransform::set );
		centerSlicesAt.setOnAction( event -> {
			final int N = 100;
			final AffineTransform3D source = globalTransform.copy();

			final AffineTransform3D target = source.copy();
			target.setTranslation( 0, 0, 0 );

			final AffineTransform3D translation = new AffineTransform3D();
			translation.setTranslation(
					-clickedPoint.getDoublePosition( 0 ),
					-clickedPoint.getDoublePosition( 1 ),
					-clickedPoint.getDoublePosition( 2 ) );
			target.concatenate( translation );

			final double[] start = source.getRowPackedCopy();
			final double[] stop = target.getRowPackedCopy();
			final double[] step = new double[ start.length ];
			for ( int d = 0; d < start.length; ++d )
				step[ d ] = ( stop[ d ] - start[ d ] ) / N;

			final Timeline tl = new Timeline( new KeyFrame( Duration.millis( 500.0 / N ), ev -> {
				for ( int d = 0; d < start.length; ++d )
					start[ d ] += step[ d ];
				target.set( start );
				transformManager.setTransform( target );
			} ) );
			tl.setCycleCount( N );
			tl.play();
		} );
		menuOpener = event -> {
			if ( event.isSecondaryButtonDown() && event.isShiftDown() && neuron.get().isPresent() )
			{
				clickedPoint.setPosition( new long[] { Math.round( event.getX() ), Math.round( event.getY() ), Math.round( event.getZ() ) } );
				centerSlicesAt.setText( "Center ortho slices at " + clickedPoint );
				this.rightClickMenu.show( neuron.get().get().meshes(), event.getScreenX(), event.getScreenY() );
			}
			else if ( this.rightClickMenu.isShowing() )
				this.rightClickMenu.hide();
		};

		this.rightClickMenu.getItems().add( saverItem );
		this.rightClickMenu.getItems().add( centerSlicesAt );
		this.neuron.addListener( ( obs, oldv, newv ) -> {
			newv.ifPresent( n -> n.isReadyProperty().addListener( ( bobs, boldv, bnewv ) -> saverItem.setDisable( !bnewv ) ) );
		} );

		listener = () -> {
			if ( this.neuron.get().isPresent() )
			{
				final NeuronFX neuron = this.neuron.get().get();
				neuron.setColor( desaturate( stream.argb( selectedFragmentId ), 0.25 ) );
			}
		};
	}

	public synchronized void cancelRendering()
	{
		neuron.get().ifPresent( NeuronFX::cancel );
	}

	public synchronized void removeSelfFromScene()
	{
		cancelRendering();
		neuron.get().ifPresent( n -> {
			n.meshes().removeEventHandler( MouseEvent.MOUSE_PRESSED, menuOpener );
			n.meshes().removeEventHandler( MouseEvent.MOUSE_PRESSED, idSelector );
			n.removeSelfUnchecked();
		} );
		if ( this.stream instanceof AbstractHighlightingARGBStream )
			( ( AbstractHighlightingARGBStream ) this.stream ).removeListener( listener );
	}

	public synchronized void updateOnStateChange( @SuppressWarnings( "hiding" ) final boolean updateOnStateChange )
	{
		this.updateOnStateChange = updateOnStateChange;
	}

	@Override
	public synchronized void stateChanged()
	{
		if ( updateOnStateChange )
		{
			this.selectedSegmentId = fragmentSegmentAssignment.getSegment( selectedFragmentId );
			updateForegroundCheck();
			render();
		}
	}

	public synchronized void render()
	{
		if ( allowRendering )
		{
			removeSelfFromScene();

			if ( this.stream instanceof AbstractHighlightingARGBStream )
				( ( AbstractHighlightingARGBStream ) this.stream ).addListener( listener );

			final NeuronFX neuron = new NeuronFX( interval, root, shapeCache );
			this.neuron.set( Optional.of( neuron ) );
			final float[] blub = new float[ 3 ];
			final int color = desaturate( stream.argb( selectedFragmentId ), 0.25 );
			initialLocationInImageCoordinates.localize( blub );
			toWorldCoordinates.apply( blub, blub );
//			camera.setTranslateX( blub[ 0 ] - 500 );
//			camera.setTranslateY( blub[ 1 ] - 500 );
//			camera.setTranslateZ( blub[ 2 ] - 600 );
//			final PointLight l = new PointLight( Color.RED );
//			l.setTranslateX( blub[ 0 ] );
//			l.setTranslateY( blub[ 1 ] - 500 );
//			l.setTranslateZ( blub[ 2 ] - 500 );
//			InvokeOnJavaFXApplicationThread.invoke( () -> root.getChildren().add( l ) );
			final RandomAccessible< BoolType > converted = Converters.convert( data, maskConverter, new BoolType() );
			neuron.render( initialLocationInImageCoordinates, converted, toWorldCoordinates, blockSize, cubeSize, color, es );
			neuron.meshes().addEventHandler( MouseEvent.MOUSE_PRESSED, menuOpener );
			neuron.meshes().addEventHandler( MouseEvent.MOUSE_PRESSED, idSelector );

		}
	}

	private static int desaturate( final int argb, final double amount )
	{
		final double normalize = 1.0 + amount;

		final int r = ( int ) Math.round( ( ( argb >> 16 & 0xff ) / 255.0 + amount ) / normalize * 255 );
		final int g = ( int ) Math.round( ( ( argb >> 8 & 0xff ) / 255.0 + amount ) / normalize * 255 );
		final int b = ( int ) Math.round( ( ( argb & 0xff ) / 255.0 + amount ) / normalize * 255 );

		return argb & 0xff000000 | r << 16 | g << 8 | b;
	}

	public long fragmentId()
	{
		return this.selectedFragmentId;
	}

	public synchronized long segmentId()
	{
		return this.selectedSegmentId;
	}

	private synchronized void updateForegroundCheck()
	{
		final RandomAccess< T > ra = data.randomAccess();
		ra.setPosition( initialLocationInImageCoordinates );
		this.maskConverter = createMaskConverterForType.apply( ra.get() );
	}

	@Override
	public synchronized String toString()
	{
		return String.format( "%s: %d %d", getClass().getSimpleName(), fragmentId(), segmentId() );
	}

	public synchronized void stopListening()
	{
		this.fragmentSegmentAssignment.removeListener( this );
	}

	public synchronized void allowRendering()
	{
		allowRendering( true );
	}

	public synchronized void disallowRendering()
	{
		allowRendering( false );
	}

	public synchronized void allowRendering( final boolean allow )
	{
		this.allowRendering = allow;
	}

	private class MeshSaver implements EventHandler< ActionEvent >
	{

		@Override
		public void handle( final ActionEvent event )
		{
			if ( !neuron.get().isPresent() )
				return;
			final Group meshes = neuron.get().get().meshes();
			final TFloatArrayList vertices = new TFloatArrayList();
			final TFloatArrayList normals = new TFloatArrayList();
			final TFloatArrayList texCoords = new TFloatArrayList( new float[] { 0.0f, 0.0f } );
			final TIntArrayList faces = new TIntArrayList();
			final FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle( "Save neuron with fragment " + fragmentId() + " " + segmentId() );
			final SimpleObjectProperty< Optional< File > > fileProperty = new SimpleObjectProperty<>( Optional.empty() );
			try
			{
				InvokeOnJavaFXApplicationThread.invokeAndWait( () -> {
					fileProperty.set( Optional.ofNullable( fileChooser.showSaveDialog( root.sceneProperty().get().getWindow() ) ) );
				} );
			}
			catch ( final InterruptedException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if ( fileProperty.get().isPresent() )
			{
				final File file = fileProperty.get().get();

				final StringBuilder sb = new StringBuilder()
						.append( "# fragment id: " ).append( fragmentId() ).append( "\n" )
						.append( "# segment id:  " ).append( segmentId() ).append( "\n" )
						.append( "# color:       " ).append( Integer.toHexString( stream.argb( selectedFragmentId ) ) );
				meshes
						.getChildren()
						.stream()
						.filter( c -> c instanceof MeshView ).map( mv -> ( ( MeshView ) mv ).getMesh() )
						.filter( m -> m instanceof TriangleMesh ).map( m -> ( TriangleMesh ) m )
						.forEach( mesh -> {
							final int previousEntries = vertices.size();
							append( mesh.getPoints(), vertices );
							append( mesh.getNormals(), normals );
							final int currentEntries = vertices.size();
							assert vertices.size() == normals.size();
							for ( int e = previousEntries, singleStep = previousEntries / 3; e < currentEntries; e += 3, ++singleStep )
							{
								faces.add( singleStep );
								faces.add( singleStep );
								faces.add( 0 );
							}
						} );
				final int numFacesEntries = faces.size();

				sb.append( "\n" );
				final int numVertices = vertices.size();
				for ( int k = 0; k < numVertices; k += 3 )
					sb.append( "\nv " ).append( vertices.get( k + 0 ) ).append( " " ).append( vertices.get( k + 1 ) ).append( " " ).append( vertices.get( k + 2 ) );

				sb.append( "\n" );
				final int numNormals = normals.size();
				for ( int k = 0; k < numNormals; k += 3 )
					sb.append( "\nvn " ).append( normals.get( k + 0 ) ).append( " " ).append( normals.get( k + 1 ) ).append( " " ).append( normals.get( k + 2 ) );

				sb.append( "\n" );
				final int numTexCoords = texCoords.size();
				for ( int k = 0; k < numTexCoords; k += 2 )
					sb.append( "\nvt " ).append( texCoords.get( k + 0 ) ).append( " " ).append( texCoords.get( k + 1 ) );

				sb.append( "\n" );
				for ( int k = 0; k < numFacesEntries; k += 9 )
				{
					final int v1 = faces.get( k );
					final int n1 = faces.get( k + 1 );
					final int t1 = faces.get( k + 2 );
					final int v2 = faces.get( k + 3 );
					final int n2 = faces.get( k + 4 );
					final int t2 = faces.get( k + 5 );
					final int v3 = faces.get( k + 6 );
					final int n3 = faces.get( k + 7 );
					final int t3 = faces.get( k + 8 );
					sb
							.append( "\nf " ).append( v1 + 1 ).append( "/" ).append( t1 + 1 ).append( "/" ).append( n1 + 1 )
							.append( " " ).append( v2 + 1 ).append( "/" ).append( t2 + 1 ).append( "/" ).append( n2 + 1 )
							.append( " " ).append( v3 + 1 ).append( "/" ).append( t3 + 1 ).append( "/" ).append( n3 + 1 );
				}

				try
				{
					Files.write( file.toPath(), sb.toString().getBytes() );
				}
				catch ( final IOException e )
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	private class IdSelector implements EventHandler< MouseEvent >
	{

		@Override
		public void handle( final MouseEvent event )
		{
			if ( event.isPrimaryButtonDown() && isNoModifierKeys( event ) )
			{
				if ( selectedIds.isOnlyActiveId( selectedFragmentId ) )
					selectedIds.deactivate( selectedFragmentId );
				else
					selectedIds.activate( selectedFragmentId );
			}
			else if ( event.isSecondaryButtonDown() && isNoModifierKeys( event ) )
				if ( selectedIds.isActive( selectedFragmentId ) )
					selectedIds.deactivate( selectedFragmentId );
				else
					selectedIds.activateAlso( selectedFragmentId );
		}

	}

	public static boolean isNoModifierKeys( final MouseEvent event )
	{
		return !( event.isControlDown() || event.isShiftDown() || event.isMetaDown() || event.isAltDown() );
	}

	public static void append( final ObservableFloatArray src, final TFloatArrayList target )
	{
		final int size = src.size();
		for ( int i = 0; i < size; ++i )
			target.add( src.get( i ) );
	}

	public static void append( final ObservableFaceArray src, final TIntArrayList target )
	{
		final int size = src.size();
		for ( int i = 0; i < size; ++i )
			target.add( src.get( i ) );
	}
}
