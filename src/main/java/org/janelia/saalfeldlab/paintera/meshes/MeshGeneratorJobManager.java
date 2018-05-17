package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.ObservableMap;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import net.imglib2.Interval;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;

public class MeshGeneratorJobManager
{
	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final ObservableMap< ShapeKey< Long >, MeshView > meshes;

	private final ExecutorService manager;

	private final ExecutorService workers;

	public MeshGeneratorJobManager(
			final ObservableMap< ShapeKey< Long >, MeshView > meshes,
			final ExecutorService manager,
			final ExecutorService workers )
	{
		super();
		this.meshes = meshes;
		this.manager = manager;
		this.workers = workers;
	}

	public Future< Void > submit(
			final long id,
			final int scaleIndex,
			final int simplificationIterations,
			final double smoothingLambda,
			final int smoothingIterations,
			final InterruptibleFunction< Long, Interval[] > getBlockList,
			final InterruptibleFunction< ShapeKey< Long >, Pair< float[], float[] > > getMesh,
			final IntConsumer setNumberOfTasks,
			final IntConsumer setNumberOfCompletedTasks,
			final Runnable onFinish )
	{
		return manager.submit(
				new ManagementTask(
						id,
						scaleIndex,
						simplificationIterations,
						smoothingLambda,
						smoothingIterations,
						getBlockList,
						getMesh,
						setNumberOfTasks,
						setNumberOfCompletedTasks,
						onFinish ) );
	}

	public class ManagementTask implements Callable< Void >
	{
		private final long id;

		private final int scaleIndex;

		private final int simplificationIterations;

		private final double smoothingLambda;

		private final int smoothingIterations;

		private final InterruptibleFunction< Long, Interval[] > getBlockList;

		private final InterruptibleFunction< ShapeKey< Long >, Pair< float[], float[] > > getMesh;

		private boolean isInterrupted = false;

		private final IntConsumer setNumberOfTasks;

		private final IntConsumer setNumberOfCompletedTasks;

		private final Runnable onFinish;

		public ManagementTask(
				final long id,
				final int scaleIndex,
				final int simplificationIterations,
				final double smoothingLambda,
				final int smoothingIterations,
				final InterruptibleFunction< Long, Interval[] > getBlockList,
				final InterruptibleFunction< ShapeKey< Long >, Pair< float[], float[] > > getMesh,
				final IntConsumer setNumberOfTasks,
				final IntConsumer setNumberOfCompletedTasks,
				final Runnable onFinish )
		{
			super();
			this.id = id;
			this.scaleIndex = scaleIndex;
			this.simplificationIterations = simplificationIterations;
			this.smoothingLambda = smoothingLambda;
			this.smoothingIterations = smoothingIterations;
			this.getBlockList = getBlockList;
			this.getMesh = getMesh;
			this.setNumberOfTasks = setNumberOfTasks;
			this.setNumberOfCompletedTasks = setNumberOfCompletedTasks;
			this.onFinish = onFinish;
		}

		@Override
		public Void call()
		{
			try
			{
				synchronized ( meshes )
				{
					meshes.clear();
				}

				final List< Interval > blockList = new ArrayList<>();

				final CountDownLatch countDownOnBlockList = new CountDownLatch( 1 );

				synchronized ( setNumberOfTasks )
				{
					setNumberOfTasks.accept( 0 );
					setNumberOfCompletedTasks.accept( 0 );
				}

				workers.submit( () -> {
					try
					{
						blockList.addAll( Arrays.asList( getBlockList.apply( id ) ) );
					}
					finally
					{
						countDownOnBlockList.countDown();
					}
				} );
				try
				{
					countDownOnBlockList.await();
				}
				catch ( final InterruptedException e )
				{
					LOG.debug( "Interrupted while waiting for block lists for label {}", id );
					getBlockList.interruptFor( id );
					this.isInterrupted = true;
				}

				synchronized ( setNumberOfTasks )
				{
					setNumberOfTasks.accept( blockList.size() );
					setNumberOfCompletedTasks.accept( 0 );
				}

				LOG.debug( "Found {} blocks", blockList.size() );

				if ( this.isInterrupted )
				{
					LOG.debug( "Got interrupted before building meshes -- returning" );
					return null;
				}

				LOG.debug( "Generating mesh with {} blocks for fragment {}.", blockList.size(), this.id );

				final List< ShapeKey< Long > > keys = new ArrayList<>();
				for ( final Interval block : blockList )
				{
					keys.add(
							new ShapeKey<>(
									id,
									scaleIndex,
									simplificationIterations,
									smoothingLambda,
									smoothingIterations,
									Intervals.minAsLongArray( block ),
									Intervals.maxAsLongArray( block ) ) );
				}

				final int numTasks = keys.size();
				final CountDownLatch countDownOnMeshes = new CountDownLatch( numTasks );

				final ArrayList< Callable< Void > > tasks = new ArrayList<>();
				for ( final ShapeKey< Long > key : keys )
				{
					tasks.add( () -> {
						try
						{
							final String initialName = Thread.currentThread().getName();
							try
							{
								Thread.currentThread().setName( initialName + " -- generating mesh: " + key );
								LOG.trace( "Set name of current thread to {} ( was {})", Thread.currentThread().getName(), initialName );
								final Pair< float[], float[] > verticesAndNormals = getMesh.apply( key );
								final MeshView mv = makeMeshView( verticesAndNormals );
								if ( !Thread.interrupted() )
								{
									synchronized ( meshes )
									{
										meshes.put( key, mv );
									}
								}
							}
							catch ( final RuntimeException e )
							{
								LOG.debug( "Was not able to retrieve mesh for {}: {}", key, e.getMessage() );
							}
							finally
							{
								Thread.currentThread().setName( initialName );
							}
							return null;
						}
						finally
						{
							synchronized ( setNumberOfTasks )
							{
								countDownOnMeshes.countDown();
								setNumberOfCompletedTasks.accept( numTasks - ( int ) countDownOnMeshes.getCount() );
							}
							LOG.debug( "Counted down latch. {} remaining", countDownOnMeshes.getCount() );
						}

					} );
				}

				try
				{
					workers.invokeAll( tasks );
				}
				catch ( final InterruptedException e )
				{
					this.isInterrupted = true;
					keys.forEach( getMesh::interruptFor );
				}

				try
				{
					if ( this.isInterrupted )
					{
						keys.forEach( getMesh::interruptFor );
					}
					else
					{
						countDownOnMeshes.await();
					}
				}
				catch ( final InterruptedException e )
				{
					LOG.debug( "Current thread was interrupted while waiting for mesh count down latch ({} remaining)", countDownOnMeshes.getCount() );
					synchronized ( getMesh )
					{
						this.isInterrupted = true;
						keys.forEach( getMesh::interruptFor );
					}
				}

				return null;
			}
			finally
			{
				{
					if ( this.isInterrupted )
					{
						LOG.debug( "Was interrupted, removing all meshes" );
						synchronized ( meshes )
						{
							meshes.clear();
						}
					}
				}
				this.onFinish.run();
			}

		}

	}

	private static MeshView makeMeshView( final Pair< float[], float[] > verticesAndNormals )
	{
		final float[] vertices = verticesAndNormals.getA();
		final float[] normals = verticesAndNormals.getB();
		final TriangleMesh mesh = new TriangleMesh();
		mesh.getPoints().addAll( vertices );
		mesh.getNormals().addAll( normals );
		mesh.getTexCoords().addAll( 0, 0 );
		mesh.setVertexFormat( VertexFormat.POINT_NORMAL_TEXCOORD );
		final int[] faceIndices = new int[ vertices.length ];
		for ( int i = 0, k = 0; i < faceIndices.length; i += 3, ++k )
		{
			faceIndices[ i + 0 ] = k;
			faceIndices[ i + 1 ] = k;
			faceIndices[ i + 2 ] = 0;
		}
		mesh.getFaces().addAll( faceIndices );
		final PhongMaterial material = new PhongMaterial();
		material.setSpecularColor( new Color( 1, 1, 1, 1.0 ) );
		material.setSpecularPower( 50 );
//						material.diffuseColorProperty().bind( color );
		final MeshView mv = new MeshView( mesh );
		mv.setOpacity( 1.0 );
//						synchronized ( this.isVisible )
//						{
//							mv.visibleProperty().bind( this.isVisible );
//						}
		mv.setCullFace( CullFace.NONE );
		mv.setMaterial( material );
		mv.setDrawMode( DrawMode.FILL );
		return mv;
	}

}
