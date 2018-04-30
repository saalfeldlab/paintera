package org.janelia.saalfeldlab.paintera;

import java.util.concurrent.Callable;

import picocli.CommandLine.Option;

public class PainteraCommandLineArgs implements Callable< Boolean >
{

	@Option( names = { "--width" }, paramLabel = "WIDTH", required = false, description = "Initial width of viewer. Defaults to 800." )
	private int width = -1;

	@Option( names = { "--height" }, paramLabel = "HEIGHT", required = false, description = "Initial height of viewer. Defaults to 600." )
	private int height = -1;

	@Option( names = { "-h", "--help" }, usageHelp = true, description = "Display this help message." )
	private boolean helpRequested;

	@Option( names = { "--raw-source" }, paramLabel = "RAW_SOURCE", required = false, description = "Open raw source at start-up. Has to be [file://]/path/to/<n5-or-hdf5>:path/to/dataset" )
	private String[] rawSources;

	@Option( names = { "--label-source" }, paramLabel = "LABEL_SOURCE", required = false, description = "Open label source at start-up. Has to be [file://]/path/to/<n5-or-hdf5>:path/to/dataset" )
	private String[] labelSources;

	@Override
	public Boolean call() throws Exception
	{
		width = width <= 0 ? 800 : width;
		height = height <= 0 ? 600 : height;
		return true;
	}

	public int width()
	{
		return width;
	}

	public int height()
	{
		return height;
	}

	public String[] rawSources()
	{
		return rawSources.clone();
	}

	public String[] labelSources()
	{
		return labelSources.clone();
	}

}
