package org.janelia.saalfeldlab.paintera;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command( name = "Paintera" )
public class PainteraCommandLineArgs implements Callable< Boolean >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	@Option( names = { "--width" }, paramLabel = "WIDTH", required = false, description = "Initial width of viewer. Defaults to 800. Overrides width stored in project." )
	private int width = -1;

	@Option( names = { "--height" }, paramLabel = "HEIGHT", required = false, description = "Initial height of viewer. Defaults to 600. Overrides height stored in project." )
	private int height = -1;

	@Option( names = { "-h", "--help" }, usageHelp = true, description = "Display this help message." )
	private boolean helpRequested;

	@Option( names = { "--raw-source" }, paramLabel = "RAW_SOURCE", required = false, description = "Open raw source at start-up. Has to be [file://]/path/to/<n5-or-hdf5>:path/to/dataset" )
	private String[] rawSources;

	@Option( names = { "--label-source" }, paramLabel = "LABEL_SOURCE", required = false, description = "Open label source at start-up. Has to be [file://]/path/to/<n5-or-hdf5>:path/to/dataset" )
	private String[] labelSources;

	@Parameters( index = "0", paramLabel = "PROJECT", arity = "0..1", description = "Optional project N5 root (N5 or HDF5)." )
	private String project;

	@Override
	public Boolean call() throws Exception
	{
		width = width <= 0 ? -1 : width;
		height = height <= 0 ? -1 : height;
		rawSources = rawSources == null ? new String[] {} : rawSources;
		labelSources = labelSources == null ? new String[] {} : labelSources;
		return true;
	}

	public int width( final int defaultWidth )
	{
		return width <= 0 ? defaultWidth : width;
	}

	public int height( final int defaultHeight )
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
		final String returnedProject = this.project == null ? this.project : new File( project ).getAbsolutePath();
		LOG.debug( "Return project={}", returnedProject );
		return returnedProject;
	}

}
