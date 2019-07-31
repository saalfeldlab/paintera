package org.janelia.saalfeldlab.paintera;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.pivovarit.function.ThrowingConsumer;
import com.pivovarit.function.ThrowingFunction;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "Paintera")
public class PainteraCommandLineArgs implements Callable<Boolean>
{

	private static final class AddDatasetArgument {

		private static final class Options {
			@Option(names = {"-c", "--container"}, paramLabel = "CONTAINER", required = false, description = "" +
					"Container of dataset(s) to be added. " +
					"If none is provided, default to Paintera project (if any). " +
					"Currently N5 file system and HDF5 containers are supported.")
			File container = null;

			@Option(names = {"-d", "--dataset"}, paramLabel = "DATASET", arity = "1..*", required = true, description = "" +
					"Dataset(s) within CONTAINER to be added. " +
					"TODO: If no datasets are specified, all datasets will be added (or use a separate option for this).")
			String[] datasets = null;

			@Option(names = {"-r", "--resolution"}, paramLabel = "RESOLUTION", required = false, description = "" +
					"Spatial resolution for all dataset(s) specified by DATASET. " +
					"Takes meta-data over resolution specified in meta data of DATASET")
			double[] resolution = null;

			@Option(names = {"-o", "--offset"}, paramLabel = "OFFSET", required = false, description = "" +
					"Spatial offset for all dataset(s) specified by DATASET. " +
					"Takes meta-data over resolution specified in meta data of DATASET")
			double[] offset = null;

			@Option(names = {"-R", "--revert-array-attributes"}, paramLabel = "REVERT", description = "" +
					"Revert array attributes found in meta data of attributes of DATASET. " +
					"Does not affect any array attributes set explicitly through the RESOLUTION or OFFSET options.")
			Boolean revertArrayAttributes = false;

			@Option(names = { "--min"}, paramLabel = "MIN", description = "" +
					"Minimum value of contrast range for raw and channel data.")
			private Double min = null;

			@Option(names = { "--max"}, paramLabel = "MAX", description = "" +
					"Maximum value of contrast range for raw and channel data.")
			private Double max = null;

			@Option(names = {"--channel-dimension"}, defaultValue = "3", paramLabel = "CHANNEL_DIMENSION", description = "" +
					"Defines the dimension of a 4D dataset to be interpreted as channel axis. " +
					"0 <= CHANNEL_DIMENSION <= 3")
			private Integer channelDimension = 3;

			@Option(names =  {"--channels"}, paramLabel = "CHANNELS", description = "" +
					"Use only this subset of channels for channel (4D) data. " +
					"Multiple subsets can be specified. " +
					"If no channels are specified, use all channels.")
			private long[][] channels = null;

			@Option(names = {"--name"}, paramLabel = "NAME", description = "" +
					"Specify name for dataset(s). " +
					"The names are assigned to datasets in the same order as specified. " +
					"If more datasets than names are specified, the remaining dataset names " +
					"will default to the last segment of the dataset path.")
			String[] name = null;
		}

		@Option(names = "--add-dataset", required = true)
		private Boolean addN5Dataset = false;

		@CommandLine.ArgGroup(multiplicity = "1", exclusive = false)
		private Options options = null;

		private void addToViewer(
				final PainteraBaseView viewer,
				final String projectDirectory) throws IOException {
			if (options == null || addN5Dataset == null || !addN5Dataset)
				return;

			if (options.datasets == null) {
				LOG.warn("" +
						"No datasets will be added: " +
						"--add-dataset was specified but no dataset was provided through the -d, --dataset option. " +
						"TODO: detect all datasets in container and add all datasets when no dataset is specified.");
				return;
			}

			final File container = options.container == null ? new File(projectDirectory) : options.container;
			final String containerPath = container.getAbsolutePath();
			for (int index = 0; index < options.datasets.length; ++index) {
				final String dataset = options.datasets[index];
				N5Helpers.addToViewer(
						viewer,
						projectDirectory,
						containerPath,
						dataset,
						options.revertArrayAttributes,
						options.resolution,
						options.offset,
						options.min,
						options.max,
						options.channelDimension,
						options.channels,
						options.name == null ? null : getIfInRange(options.name, index));
			}
		}
	}

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final int DEFAULT_NUM_SCREEN_SCALES = 5;

	private static final double DEFAULT_HIGHEST_SCREEN_SCALE = 1.0;

	private static final double DEFAULT_SCREEN_SCALE_FACTOR = 0.5;

	@Option(names = {"--width"}, paramLabel = "WIDTH", required = false, description = "Initial width of viewer. " +
			"Defaults to 800. Overrides width stored in project.")
	private int width = -1;

	@Option(names = {"--height"}, paramLabel = "HEIGHT", required = false, description = "Initial height of viewer. " +
			"Defaults to 600. Overrides height stored in project.")
	private int height = -1;

	@Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help message.")
	private boolean helpRequested;

	@Option(names = "--num-screen-scales", paramLabel = "NUM_SCREEN_SCALES", required = false, description = "Number " +
			"of screen scales, defaults to 3. If no scale option is specified, scales default to [1.0, 0.5, 0.25, 0.125, 0.0625].")
	private Integer numScreenScales;

	@Option(names = "--highest-screen-scale", paramLabel = "HIGHEST_SCREEN_SCALE", required = false, description =
			"Highest screen scale, restricted to the interval (0,1], defaults to 1. If no scale option is specified, scales default to [1.0, 0.5, 0.25, 0.125, 0.0625].")
	private Double highestScreenScale;

	@Option(names = "--screen-scale-factor", paramLabel = "SCREEN_SCALE_FACTOR", required = false, description =
			"Scalar value from the open interval (0,1) that defines how screen scales diminish in each dimension. " +
					"Defaults to 0.5. If no scale option is specified, scales default to [1.0, 0.5, 0.25, 0.125, 0.0625].")
	private Double screenScaleFactor;

	@Option(names = "--screen-scales", paramLabel = "SCREEN_SCALES", required = false, description = "Explicitly set " +
			"screen scales. Must be strictly monotonically decreasing values in from the interval (0,1]. Overrides " +
			"all other screen scale options. If no scale option is specified, scales default to [1.0, 0.5, 0.25, 0.125, 0.0625].", arity = "1..*", split = ",")
	private double[] screenScales;

	@Parameters(index = "0", paramLabel = "PROJECT", arity = "0..1", description = "Optional project N5 root (N5 or " +
			"FileSystem).")
	private String project;

	@Option(names = "--print-error-codes", paramLabel = "PRINT_ERROR_CODES", required = false, description = "List all" +
			" error codes and exit.")
	private Boolean printErrorCodes;

	@Option(names = "--default-to-temp-directory", paramLabel = "DEFAULT_TO_TEMP_DIRECTORY", required = false,
			description = "Default to temporary directory instead of showing dialog when PROJECT is not specified.")
	private Boolean defaultToTempDirectory;

	@Option(names = "--version", paramLabel = "PRINT_VERSION_STRING", required = false, description = "Print version string and exit")
	private Boolean printVersionString;

	@CommandLine.ArgGroup(exclusive = false, multiplicity = "1..*")
	private AddDatasetArgument[] n5datasets = null;

	private boolean screenScalesProvided = false;

	@Override
	public Boolean call() throws Exception
	{
		width = width <= 0 ? -1 : width;
		height = height <= 0 ? -1 : height;

		screenScalesProvided = screenScales != null || numScreenScales != null || highestScreenScale != null || screenScaleFactor != null;

		numScreenScales = Optional.ofNullable(this.numScreenScales).filter(n -> n > 0).orElse(DEFAULT_NUM_SCREEN_SCALES);
		highestScreenScale = Optional.ofNullable(highestScreenScale).filter(s -> s > 0 && s <= 1).orElse(DEFAULT_HIGHEST_SCREEN_SCALE);
		screenScaleFactor = Optional.ofNullable(screenScaleFactor).filter(f -> f > 0 && f < 1).orElse(DEFAULT_SCREEN_SCALE_FACTOR);
		screenScales = screenScales == null
		               ? createScreenScales(numScreenScales, highestScreenScale, screenScaleFactor)
		               : screenScales;

		if (screenScales != null)
		{
			checkScreenScales(screenScales);
		}

		printErrorCodes = printErrorCodes == null ? false : printErrorCodes;
		if (printErrorCodes)
		{
			LOG.info("Error codes:");
			for (final Paintera.Error error : Paintera.Error.values())
			{
				LOG.info("{} -- {}", error.code, error.description);
			}
			return false;
		}

		printVersionString = printVersionString == null ? false : printVersionString;
		if (printVersionString)
		{
			LOG.info("Paintera version: {}", Version.VERSION_STRING);
			return false;
		}

		defaultToTempDirectory = defaultToTempDirectory == null ? false : defaultToTempDirectory;

		return true;
	}

	public int width(final int defaultWidth)
	{
		return width <= 0 ? defaultWidth : width;
	}

	public int height(final int defaultHeight)
	{
		return height <= 0 ? defaultHeight : height;
	}

	public String project()
	{
		final String returnedProject = this.project == null ? this.project : new File(project).getAbsolutePath();
		LOG.debug("Return project={}", returnedProject);
		return returnedProject;
	}

	public double[] screenScales()
	{
		return this.screenScales.clone();
	}

	public boolean defaultToTempDirectory()
	{
		return this.defaultToTempDirectory;
	}

	public boolean wereScreenScalesProvided()
	{
		return this.screenScalesProvided;
	}

	public void addToViewer(final PainteraBaseView viewer, final String projectDirectory) {
		if (this.n5datasets == null)
			return;
		Stream.of(this.n5datasets).forEach(ThrowingConsumer.unchecked(ds -> ds.addToViewer(viewer, projectDirectory)));
	}

	private static double[] createScreenScales(final int numScreenScales, final double highestScreenScale, final
	double screenScaleFactor)
	throws ZeroLengthScreenScales
	{
		if (numScreenScales <= 1) { throw new ZeroLengthScreenScales(); }

		final double[] screenScales = new double[numScreenScales];
		screenScales[0] = highestScreenScale;
		for (int i = 1; i < screenScales.length; ++i)
		{
			screenScales[i] = screenScaleFactor * screenScales[i - 1];
		}
		LOG.debug("Returning screen scales {}", screenScales);
		return screenScales;
	}

	private static void checkScreenScales(final double[] screenScales)
	throws ZeroLengthScreenScales, InvalidScreenScaleValue, ScreenScaleNotDecreasing
	{
		if (screenScales.length == 0) { throw new ZeroLengthScreenScales(); }

		if (screenScales[0] <= 0 || screenScales[0] > 1) { throw new InvalidScreenScaleValue(screenScales[0]); }

		for (int i = 1; i < screenScales.length; ++i)
		{
			final double prev = screenScales[i - 1];
			final double curr = screenScales[i];
			// no check for > 1 necessary because already checked for
			// monotonicity
			if (curr <= 0) { throw new InvalidScreenScaleValue(curr); }
			if (prev <= curr) { throw new ScreenScaleNotDecreasing(prev, curr); }
		}

	}

	public static class ZeroLengthScreenScales extends Exception
	{

	}

	public static class InvalidScreenScaleValue extends Exception
	{
		InvalidScreenScaleValue(final double scale)
		{
			super("Screen scale " + scale + " not in legal interval (0,1]");
		}
	}

	public static class ScreenScaleNotDecreasing extends Exception
	{
		public ScreenScaleNotDecreasing(final double first, final double second)
		{
			super("Second screen scale " + second + " larger than or equal to first " + first);
		}
	}

	private static <T> T getIfInRange(T[] array, final int index) {
		return index < array.length ? array[index] : null;
	}

}
