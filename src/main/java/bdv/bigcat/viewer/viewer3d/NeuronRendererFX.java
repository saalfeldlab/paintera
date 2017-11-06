package bdv.bigcat.viewer.viewer3d;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import bdv.bigcat.ui.ARGBStream;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.StateListener;
import bdv.bigcat.viewer.viewer3d.marchingCubes.ForegroundCheck;
import bdv.util.InvokeOnJavaFXApplicationThread;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableFloatArray;
import javafx.event.EventHandler;
import javafx.scene.Camera;
import javafx.scene.Group;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.ObservableFaceArray;
import javafx.scene.shape.TriangleMesh;
import javafx.stage.FileChooser;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;

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

	private final List< NeuronRendererListener > listeners = new ArrayList<>();

	private long selectedSegmentId;

	private final F fragmentSegmentAssignment;

	private final ARGBStream stream;

	private final Localizable initialLocationInImageCoordinates;

	private final RandomAccessible< T > data;

	private final Interval interval;

	private final Function< T, ForegroundCheck< T > > getForegroundCheck;

	private ForegroundCheck< T > foregroundCheck;

	private final Group root;

	private final Camera camera;

	private final ExecutorService es;

	private final AffineTransform3D toWorldCoordinates;

	private final int[] blockSize;

	private final int[] cubeSize;

	private boolean updateOnStateChange = true;

	private boolean allowRendering = true;

	private final ObjectProperty< Optional< NeuronFX< T > > > neuron = new SimpleObjectProperty<>( Optional.empty() );

	private final MeshSaver meshSaver;

	/**
	 * Bounding box of the complete mesh/neuron (xmin, xmax, ymin, ymax, zmin,
	 * zmax)
	 */
	private float[] completeBoundingBox = null;

	public NeuronRendererFX(
			final long selectedFragmentId,
			final F fragmentSegmentAssignment,
			final ARGBStream stream,
			final Localizable initialLocationInImageCoordinates,
			final RandomAccessible< T > data,
			final Interval interval,
			final Function< T, ForegroundCheck< T > > getForegroundCheck,
			final Group root,
			final Camera camera,
			final ExecutorService es,
			final AffineTransform3D toWorldCoordinates,
			final int[] blockSize,
			final int[] cubeSize )
	{
		super();
		this.selectedFragmentId = selectedFragmentId;
		this.selectedSegmentId = fragmentSegmentAssignment.getSegment( selectedFragmentId );
		this.fragmentSegmentAssignment = fragmentSegmentAssignment;
		this.stream = stream;
		this.initialLocationInImageCoordinates = initialLocationInImageCoordinates;
		this.data = data;
		this.interval = interval;
		this.getForegroundCheck = getForegroundCheck;
		this.root = root;
		this.camera = camera;
		this.es = es;
		this.toWorldCoordinates = toWorldCoordinates;
		this.blockSize = blockSize;
		this.cubeSize = cubeSize;

		updateForegroundCheck();
		this.fragmentSegmentAssignment.addListener( this );
		this.meshSaver = new MeshSaver();
	}

	public synchronized void cancelRendering()
	{
		neuron.get().ifPresent( NeuronFX::cancel );
	}

	public synchronized void removeSelfFromScene()
	{
		cancelRendering();
		neuron.get().ifPresent( NeuronFX::removeSelfUnchecked );
		neuron.get().ifPresent( n -> n.meshView().removeEventHandler( MouseEvent.MOUSE_PRESSED, meshSaver ) );
	}

	public synchronized void updateOnStateChange( final boolean updateOnStateChange )
	{
		this.updateOnStateChange = updateOnStateChange;
	}

	public void addListener( final NeuronRendererListener cameraCallback )
	{
		listeners.add( cameraCallback );
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
			final NeuronFX< T > neuron = new NeuronFX<>( interval, root );
			this.neuron.set( Optional.of( neuron ) );
			final float[] blub = new float[ 3 ];
			final int color = stream.argb( selectedFragmentId );
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
			neuron.render( initialLocationInImageCoordinates, data, foregroundCheck, toWorldCoordinates, blockSize, cubeSize, color, es );
			neuron.meshView().addEventHandler( MouseEvent.MOUSE_PRESSED, meshSaver );

		}
	}

//	private void hasNeighboringData( Localizable location, boolean[] neighboring )
//	{
//		// for each dimension, verifies first in the +, then in the - direction
//		// if the voxels in the boundary contain the foregroundvalue
//		final Interval chunkInterval = partitioner.getChunk( location ).getA().interval();
//		for ( int i = 0; i < chunkInterval.numDimensions(); i++ )
//		{
//			// initialize each direction with false
//			neighboring[ i * 2 ] = false;
//			neighboring[ i * 2 + 1 ] = false;
//
//			checkData( i, chunkInterval, neighboring, "+" );
//			checkData( i, chunkInterval, neighboring, "-" );
//		}
//	}
//
//	private void checkData( int i, Interval chunkInterval, boolean[] neighboring, String direction )
//	{
//		final long[] begin = new long[ chunkInterval.numDimensions() ];
//		final long[] end = new long[ chunkInterval.numDimensions() ];
//
//		begin[ i ] = ( direction.compareTo( "+" ) == 0 ) ? chunkInterval.max( i ) : chunkInterval.min( i );
//		end[ i ] = begin[ i ];
//
//		for ( int j = 0; j < chunkInterval.numDimensions(); j++ )
//		{
//			if ( i == j )
//				continue;
//
//			begin[ j ] = chunkInterval.min( j );
//			end[ j ] = chunkInterval.max( j );
//		}
//
//		RandomAccessibleInterval< T > slice = Views.interval( volumeLabels, new FinalInterval( begin, end ) );
//		Cursor< T > cursor = Views.flatIterable( slice ).cursor();
//
//		System.out.println( "Checking dataset from: " + begin[ 0 ] + " " + begin[ 1 ] + " " + begin[ 2 ] + " to: " + end[ 0 ] + " " + end[ 1 ] + " " + end[ 2 ] );
//
//		while ( cursor.hasNext() )
//		{
//			cursor.next();
//			if ( foregroundCheck.test( cursor.get() ) == 1 )
//			{
//				int index = ( direction.compareTo( "+" ) == 0 ) ? i * 2 : i * 2 + 1;
//				neighboring[ index ] = true;
//				break;
//			}
//		}
//		int index = ( direction.compareTo( "+" ) == 0 ) ? i * 2 : i * 2 + 1;
//		System.out.println( "this dataset is: {}" + neighboring[ index ] );
//	}

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
		this.foregroundCheck = getForegroundCheck.apply( ra.get() );
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

	public synchronized void updateCompleteBoundingBox( final float[] boundingBox )
	{
		assert completeBoundingBox.length == boundingBox.length;

		completeBoundingBox = maxBoundingBox( completeBoundingBox, boundingBox );

		for ( final NeuronRendererListener listener : listeners )
			listener.updateCamera( completeBoundingBox );
	}

	private synchronized float[] maxBoundingBox( final float[] completeBoundingBox, final float[] boundingBox )
	{
		if ( completeBoundingBox == null )
			return boundingBox;

		for ( int d = 0; d < completeBoundingBox.length; d++ )
			if ( d % 2 == 0 && completeBoundingBox[ d ] > boundingBox[ d ] )
				completeBoundingBox[ d ] = boundingBox[ d ];
			else if ( d % 2 != 0 && completeBoundingBox[ d ] < boundingBox[ d ] )
				completeBoundingBox[ d ] = boundingBox[ d ];

		return completeBoundingBox;
	}

	private class MeshSaver implements EventHandler< MouseEvent >
	{

		@Override
		public void handle( final MouseEvent event )
		{
			if ( !neuron.get().isPresent() )
				return;
			final TriangleMesh mesh = ( TriangleMesh ) neuron.get().get().meshView().getMesh();
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
				final ObservableFloatArray vertices = mesh.getPoints();
				final ObservableFloatArray normals = mesh.getNormals();
				final ObservableFloatArray texCoords = mesh.getTexCoords();
				final ObservableFaceArray faces = mesh.getFaces();
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
				for ( int k = 0; k < numTexCoords; k += 3 )
					sb.append( "\nvn " ).append( texCoords.get( k + 0 ) ).append( " " ).append( texCoords.get( k + 1 ) );

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
}
