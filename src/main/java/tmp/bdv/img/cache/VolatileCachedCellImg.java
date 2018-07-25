package tmp.bdv.img.cache;

import java.util.function.Function;

import bdv.cache.CacheControl;
import net.imglib2.Volatile;
import net.imglib2.cache.iotiming.IoTimeBudget;
import net.imglib2.cache.queue.BlockingFetchQueues;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.NativeImg;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.LazyCellImg;
import net.imglib2.img.list.AbstractLongListImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.NativeTypeFactory;
import net.imglib2.util.Fraction;
import tmp.bdv.img.cache.VolatileCachedCellImg.VolatileCachedCells;

/**
 * A {@link LazyCellImg} for {@link Volatile} accesses. The only difference to {@link LazyCellImg} is that is has {@link
 * CacheHints}.
 *
 * @param <T>
 * 		the pixel type
 * @param <A>
 * 		the underlying native access type
 *
 * @author Tobias Pietzsch
 * @author Stephan Saalfeld
 * @author Philipp Hanslovsky
 */
public class VolatileCachedCellImg<T extends NativeType<T>, A>
		extends AbstractCellImg<T, A, Cell<A>, VolatileCachedCells<Cell<A>>>
{
	@FunctionalInterface
	public interface Get<T>
	{
		T get(long index, CacheHints cacheHints);
	}

	private final Runnable invalidateAll;

	public VolatileCachedCellImg(
			final CellGrid grid,
			final Fraction entitiesPerPixel,
			Function<NativeImg<T, ? extends A>, T> typeFactory,
			final CacheHints cacheHints,
			final Get<Cell<A>> get,
			final Runnable invalidateAll)
	{
		super(grid, new VolatileCachedCells<>(grid.getGridDimensions(), get, cacheHints), entitiesPerPixel);
		setLinkedType(typeFactory.apply(this));
		this.invalidateAll = invalidateAll;
	}

	public VolatileCachedCellImg(final CellGrid grid, final T type, final CacheHints cacheHints, final Get<Cell<A>>
			get, final Runnable invalidateAll)
	{
		super(grid, new VolatileCachedCells<>(grid.getGridDimensions(), get, cacheHints), type.getEntitiesPerPixel());

		@SuppressWarnings("unchecked") final NativeTypeFactory<T, ? super A> typeFactory = (NativeTypeFactory<T, ?
				super A>) type.getNativeTypeFactory();
		setLinkedType(typeFactory.createLinkedType(this));
		this.invalidateAll = invalidateAll;
	}

	/**
	 * Set {@link CacheHints hints} on how to handle cell requests for this cache. The hints comprise {@link
	 * LoadingStrategy}, queue priority, and queue order.
	 * <p>
	 * Whenever a cell is accessed its data may be invalid, meaning that the cell data has not been loaded yet. In this
	 * case, the {@link LoadingStrategy} determines when the data should be loaded:
	 * <ul>
	 * <li>{@link LoadingStrategy#VOLATILE}: Enqueue the cell for asynchronous
	 * loading by a fetcher thread, if it has not been enqueued in the current frame already.
	 * <li>{@link LoadingStrategy#BLOCKING}: Load the cell data immediately.
	 * <li>{@link LoadingStrategy#BUDGETED}: Load the cell data immediately if
	 * there is enough {@link IoTimeBudget} left for the current thread group. Otherwise enqueue for asynchronous
	 * loading, if it has not been enqueued in the current frame already.
	 * <li>{@link LoadingStrategy#DONTLOAD}: Do nothing.
	 * </ul>
	 * <p>
	 * If a cell is enqueued, it is enqueued in the queue with the specified {@link CacheHints#getQueuePriority() queue
	 * priority}. Priorities are consecutive integers <em>0 ... n-1</em>, where 0 is the highest priority. Requests
	 * with
	 * priority <em>i &lt; j</em> will be handled before requests with priority <em>j</em>.
	 * <p>
	 * Finally, the {@link CacheHints#isEnqueuToFront() queue order} determines whether the cell is enqueued to the
	 * front or to the back of the queue with the specified priority.
	 * <p>
	 * Note, that the queues are {@link BlockingFetchQueues#clearToPrefetch() cleared} whenever a {@link
	 * CacheControl#prepareNextFrame() new frame} is rendered.
	 *
	 * @param cacheHints
	 * 		describe handling of cell requests for this cache. May be {@code null}, in which case the default hints are
	 * 		restored.
	 */
	public void setCacheHints(final CacheHints cacheHints)
	{
		cells.cacheHints = (cacheHints != null) ? cacheHints : cells.defaultCacheHints;
	}

	public CacheHints getDefaultCacheHints()
	{
		return cells.defaultCacheHints;
	}

	@Override
	public ImgFactory<T> factory()
	{
		throw new UnsupportedOperationException("not implemented yet");
	}

	@Override
	public Img<T> copy()
	{
		throw new UnsupportedOperationException("not implemented yet");
	}

	public static final class VolatileCachedCells<T> extends AbstractLongListImg<T>
	{
		private final Get<T> get;

		final CacheHints defaultCacheHints;

		CacheHints cacheHints;

		protected VolatileCachedCells(final long[] dimensions, final Get<T> get, final CacheHints cacheHints)
		{
			super(dimensions);
			this.get = get;
			this.defaultCacheHints = cacheHints;
			this.cacheHints = cacheHints;
		}

		@Override
		protected T get(final long index)
		{
			return get.get(index, cacheHints);
		}

		@Override
		protected void set(final long index, final T value)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public ImgFactory<T> factory()
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public Img<T> copy()
		{
			throw new UnsupportedOperationException();
		}
	}

	public Runnable getInvalidateAll()
	{
		return this.invalidateAll;
	}
}
