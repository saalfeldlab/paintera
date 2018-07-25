package org.janelia.saalfeldlab.paintera.ui.opendialog;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import bdv.util.volatiles.SharedQueue;
import javafx.event.Event;
import javafx.event.EventHandler;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetArray;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.ui.opendialog.OpenSourceDialog.TYPE;
import org.janelia.saalfeldlab.paintera.ui.opendialog.meta.MetaPanel;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PainteraOpenDialogEventHandler implements EventHandler<Event>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final PainteraBaseView viewer;

	private final SharedQueue cellCache;

	private final Predicate<Event> check;

	private final boolean consume;

	private final Consumer<Exception> exceptionHandler;

	private final String projectDirectory;

	public PainteraOpenDialogEventHandler(
			final PainteraBaseView viewer,
			final SharedQueue cellCache,
			final Predicate<Event> check,
			final String projectDirectory)
	{
		this(viewer, cellCache, check, e -> {
		}, true, projectDirectory);
	}

	public PainteraOpenDialogEventHandler(
			final PainteraBaseView viewer,
			final SharedQueue cellCache,
			final Predicate<Event> check,
			final Consumer<Exception> exceptionHandler,
			final boolean consume,
			final String projectDirectory)
	{
		super();
		this.viewer = viewer;
		this.cellCache = cellCache;
		this.check = check;
		this.consume = consume;
		this.exceptionHandler = exceptionHandler;
		this.projectDirectory = projectDirectory;
	}

	private <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>> void
	addRaw(
			final String name,
			final BackendDialog dataset,
			final double min,
			final double max) throws Exception
	{
		final RawSourceState<T, V> raw = dataset.getRaw(name, cellCache, cellCache.getNumPriorities() - 1);
		LOG.debug("Got raw: {}", raw);
		InvokeOnJavaFXApplicationThread.invoke(() -> viewer.addRawSource(raw));
	}

	private <D extends NativeType<D> & IntegerType<D>, T extends Volatile<D> & NativeType<T>> void addLabel(
			final String name,
			final BackendDialog dataset) throws Exception
	{
		final LabelSourceState<D, T> rep = dataset.getLabels(
				name,
				cellCache,
				cellCache.getNumPriorities() - 1,
				viewer.viewer3D().meshesGroup(),
				viewer.getMeshManagerExecutorService(),
				viewer.getMeshWorkerExecutorService(),
				projectDirectory
		                                                    );
		final Object meta = dataset.metaData();
		LOG.debug("Adding label source with meta={}", meta);
		InvokeOnJavaFXApplicationThread.invoke(() -> viewer.addLabelSource(rep));
	}

	@Override
	public void handle(final Event event)
	{
		if (check.test(event))
		{
			if (consume)
			{
				event.consume();
			}

			final OpenSourceDialog        openDialog      = new OpenSourceDialog(viewer);
			final Optional<BackendDialog> datasetOptional = openDialog.showAndWait();
			LOG.debug("Data set choice: {} (is present? {})", datasetOptional, datasetOptional.isPresent());
			if (datasetOptional.isPresent())
			{
				final Thread t = new Thread(() -> {
					try
					{
						final BackendDialog dataset = datasetOptional.get();
						final MetaPanel     meta    = openDialog.getMeta();
						final TYPE          type    = openDialog.getType();
						LOG.debug("Type={}", type);
						switch (type)
						{
							case RAW:
								LOG.trace("Adding raw data");
								addRaw(openDialog.getName(), dataset, meta.min(), meta.max());
								break;
							case LABEL:
								addLabel(openDialog.getName(), dataset);
								break;
							default:
								break;
						}
					} catch (final Exception ex)
					{
						exceptionHandler.accept(ex);
					}
				});
				t.start();
			}
		}
	}

	public static <C extends Cell<VolatileLabelMultisetArray>, I extends RandomAccessible<C> & IterableInterval<C>>
	Function<Long, Interval[]>[] getBlockListCaches(
			final DataSource<LabelMultisetType, ?> source,
			final ExecutorService es)
	{
		final int numLevels = source.getNumMipmapLevels();
		if (IntStream.range(0, numLevels).mapToObj(lvl -> source.getDataSource(
				0,
				lvl
		                                                                      )).filter(src -> !(src instanceof
				AbstractCellImg<?, ?, ?, ?>)).count() > 0)
		{
			return null;
		}

		final int[][] blockSizes = IntStream
				.range(0, numLevels)
				.mapToObj(lvl -> (AbstractCellImg<?, ?, ?, ?>) source.getDataSource(0, lvl))
				.map(AbstractCellImg::getCellGrid)
				.map(PainteraOpenDialogEventHandler::blockSize)
				.toArray(int[][]::new);

		final double[][] scalingFactors = PainteraBaseView.scaleFactorsFromAffineTransforms(source);

		@SuppressWarnings("unchecked") final InterruptibleFunction<HashWrapper<long[]>, long[]>[] uniqueIdCaches = new
				InterruptibleFunction[numLevels];

		for (int level = 0; level < numLevels; ++level)
		{
			@SuppressWarnings("unchecked") final AbstractCellImg<LabelMultisetType, VolatileLabelMultisetArray, C, I>
					img =
					(AbstractCellImg<LabelMultisetType, VolatileLabelMultisetArray, C, I>) source.getDataSource(
							0,
							level
					                                                                                           );
			uniqueIdCaches[level] = uniqueLabelLoaders(img);
		}

		return CacheUtils.blocksForLabelCachesLongKeys(
				source,
				uniqueIdCaches,
				blockSizes,
				scalingFactors,
				CacheUtils::toCacheSoftRefLoaderCache
		                                              );

	}

	public static int[] blockSize(final CellGrid grid)
	{
		final int[] blockSize = new int[grid.numDimensions()];
		Arrays.setAll(blockSize, grid::cellDimension);
		return blockSize;
	}

	public static <C extends Cell<VolatileLabelMultisetArray>, I extends RandomAccessible<C> & IterableInterval<C>>
	InterruptibleFunction<HashWrapper<long[]>, long[]> uniqueLabelLoaders(
			final AbstractCellImg<LabelMultisetType, VolatileLabelMultisetArray, C, I> img)
	{
		final I cells = img.getCells();
		return InterruptibleFunction.fromFunction(location -> {
			final RandomAccess<C> access = cells.randomAccess();
			access.setPosition(location.getData());
			final long[] labels = new long[] {};
			LOG.debug("Position={}: labels={}", location.getData(), labels);
			return labels;
		});
	}

}
