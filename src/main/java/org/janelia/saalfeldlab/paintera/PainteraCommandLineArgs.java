package org.janelia.saalfeldlab.paintera;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "Paintera")
public class PainteraCommandLineArgs implements Callable<Boolean>
{

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

	@Option(names = {"--raw-source"}, paramLabel = "RAW_SOURCE", required = false, description = "Open raw source at " +
			"start-up. Has to be [file://]/path/to/<n5-or-hdf5>:path/to/dataset")
	private String[] rawSources;

	@Option(names = {"--label-source"}, paramLabel = "LABEL_SOURCE", required = false, description = "Open label " +
			"source at start-up. Has to be [file://]/path/to/<n5-or-hdf5>:path/to/dataset")
	private String[] labelSources;

	@Option(names = "--num-screen-scales", paramLabel = "NUM_SCREEN_SCALES", required = false, description = "Number " +
			"of screen scales, defaults to 3")
	private Integer numScreenScales;

	@Option(names = "--highest-screen-scale", paramLabel = "HIGHEST_SCREEN_SCALE", required = false, description =
			"Highest screen scale, restricted to the interval (0,1], defaults to 1")
	private Double highestScreenScale;

	@Option(names = "--screen-scale-factor", paramLabel = "SCREEN_SCALE_FACTOR", required = false, description =
			"Scalar value from the open interval (0,1) that defines how screen scales diminish in each dimension. " +
					"Defaults to 0.5")
	private Double screenScaleFactor;

	@Option(names = "--screen-scales", paramLabel = "SCREEN_SCALES", required = false, description = "Explicitly set " +
			"screen scales. Must be strictliy monotonically decreasing values in from the interval (0,1]. Overrides " +
			"all other screen scale options.", arity = "1..*", split = ",")
	private double[] screenScales;

	@Parameters(index = "0", paramLabel = "PROJECT", arity = "0..1", description = "Optional project N5 root (N5 or " +
			"HDF5).")
	private String project;

	@Option(names = "--print-error-codes", paramLabel = "PRINT_ERROR_CODES", required = false, description = "List all" +
			" error codes and exit.")
	private Boolean printErrorCodes;

	@Option(names = "--default-to-temp-directory", paramLabel = "DEFAULT_TO_TEMP_DIRECTORY", required = false,
			description = "Default to temporary directory instead of showing dialog when PROJECT is not specified.")
	private Boolean defaultToTempDirectory;

	@Option(names = "--version", paramLabel = "PRINT_VERSION_STRING", required = false, description = "Print version string and exit")
	private Boolean printVersionString;
	@Override
	public Boolean call() throws Exception
	{
		width = width <= 0 ? -1 : width;
		height = height <= 0 ? -1 : height;
		rawSources = rawSources == null ? new String[] {} : rawSources;
		labelSources = labelSources == null ? new String[] {} : labelSources;

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

	public String[] rawSources()
	{
		return rawSources.clone();
	}

	public String[] labelSources()
	{
		return labelSources.clone();
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

}
