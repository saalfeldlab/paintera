package tmp.net.imglib2.cache;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.CacheRemover;
import net.imglib2.cache.IoSync;
import net.imglib2.cache.img.DiskCellCache;

/**
 * Basic {@link CacheRemover}/{@link CacheLoader} for writing/reading cells to a
 * disk cache. Currently blocks are simply written as flat files to a specified
 * directory. {@link #createTempDirectory(String, boolean)} can be used to
 * create a temporary directory that will be automatically removed when the JVM
 * shuts down.
 * <p>
 * Blocks which are not in the diskcache (yet) are obtained from a backing
 * {@link CacheLoader}.
 * </p>
 * <p>
 * <em> A {@link DiskCellCache} should be connected to a in-memory cache through
 * {@link IoSync} if the cache will be used concurrently by multiple threads!
 * </em>
 * </p>
 *
 * @param <A>
 *            access type
 *
 * @author Tobias Pietzsch
 * @author Philipp Hanslovsky
 */
public class GeneralDiskCache< K, A > implements CacheRemover< K, A >, CacheLoader< K, A >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final Path cacheLocation;

	private final Function< K, Path > filePathFromKey;

	private final FileIO< A > fileIO;

	private final CacheLoader< K, A > backingLoader;

	public GeneralDiskCache(
			final Path cacheLocation,
			final CacheLoader< K, A > backingLoader,
			final BiFunction< Path, K, Path > filePathFromKey,
			final FileIO< A > fileIO )
	{
		this.cacheLocation = cacheLocation;
		this.filePathFromKey = k -> filePathFromKey.apply( this.cacheLocation, k );
		this.fileIO = fileIO;
		this.backingLoader = backingLoader;
	}

	@Override
	public A get( final K key ) throws Exception
	{
		LOG.warn( "Getting key={}", key );
		final String filename = filePathFromKey.apply( key ).toString();
		LOG.warn( "Getting from filename={}", filename );

		if ( new File( filename ).exists() ) {
			return fileIO.fromFile( filename );
		}
		else
		{
			return backingLoader.get( key );
		}
	}

	// TODO Why can't this throw?
	@Override
	public void onRemoval( final K key, final A value )
	{
		LOG.warn( "Removing key={} value={}", key, value );
		final String filename = filePathFromKey.apply( key ).toString();
		LOG.warn( "Saving to file={}", filename );
		fileIO.toFile( filename, value );
	}

	// Adapted from http://stackoverflow.com/a/20280989
	static class DeleteTempFilesHook extends Thread
	{
		private final ArrayList< Path > tempPaths = new ArrayList<>();

		public void add( final Path path )
		{
			tempPaths.add( path );
		}

		@Override
		public void run()
		{
			for ( final Path path : tempPaths )
			{
				try
				{
					Files.walkFileTree( path, new SimpleFileVisitor< Path >()
					{
						@Override
						public FileVisitResult visitFile( final Path file, final BasicFileAttributes attrs ) throws IOException
						{
							Files.delete( file );
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult postVisitDirectory( final Path dir, final IOException e ) throws IOException
						{
							if ( e == null )
							{
								Files.delete( dir );
								return FileVisitResult.CONTINUE;
							}
							// directory iteration failed
							throw e;
						}
					} );
				}
				catch ( final IOException e )
				{
					throw new RuntimeException( e );
				}
			}
		}
	}

	static DeleteTempFilesHook deleteTempFilesHook = null;

	/**
	 * Register a path for deletion when the virtual machine shuts down. If the
	 * specified {@code path} is a directory, it is deleted recursively.
	 *
	 * @param path
	 *            path to delete on virtual machine shutdown.
	 */
	public static void addDeleteHook( final Path path )
	{
		if ( deleteTempFilesHook == null )
		{
			deleteTempFilesHook = new DeleteTempFilesHook();
			Runtime.getRuntime().addShutdownHook( deleteTempFilesHook );
		}
		deleteTempFilesHook.add( path );
	}

	/**
	 * Creates a new directory in the specified directory, using the given
	 * prefix to generate its name.
	 *
	 * @param dir
	 *            the path to directory in which to create the directory
	 * @param prefix
	 *            the prefix string to be used in generating the directory's
	 *            name; may be {@code null}
	 * @param deleteOnExit
	 *            whether the created directory should be automatically deleted
	 *            when the virtual machine shuts down.
	 */
	public static Path createTempDirectory( final Path dir, final String prefix, final boolean deleteOnExit ) throws IOException
	{
		final Path tmp = Files.createTempDirectory( dir, prefix );
		System.out.println( tmp );

		if ( deleteOnExit )
		{
			addDeleteHook( tmp );
		}

		return tmp;
	}

	/**
	 * Creates a new directory in the default temporary-file directory, using
	 * the given prefix to generate its name.
	 *
	 * @param prefix
	 *            the prefix string to be used in generating the directory's
	 *            name; may be {@code null}
	 * @param deleteOnExit
	 *            whether the created directory should be automatically deleted
	 *            when the virtual machine shuts down.
	 */
	public static Path createTempDirectory( final String prefix, final boolean deleteOnExit ) throws IOException
	{
		final Path tmp = Files.createTempDirectory( prefix );
		System.out.println( tmp );

		if ( deleteOnExit )
		{
			addDeleteHook( tmp );
		}

		return tmp;
	}
}