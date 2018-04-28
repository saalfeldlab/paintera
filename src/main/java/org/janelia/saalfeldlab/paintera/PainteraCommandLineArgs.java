package org.janelia.saalfeldlab.paintera;

import java.util.concurrent.Callable;

import picocli.CommandLine.Option;

public class PainteraCommandLineArgs implements Callable< Boolean >
{

	@Option( names = { "--width" }, required = false, description = "Initial width of viewer. Defaults to 800." )
	private int width = -1;

	@Option( names = { "--height" }, required = false, description = "Initial height of viewer. Defaults to 600." )
	private int height = -1;

	@Option( names = { "-h", "--help" }, usageHelp = true, description = "Display this help message." )
	private boolean helpRequested;

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

}
