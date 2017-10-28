package bdv.bigcat.viewer;

import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.DoubleConverter;

/**
 * Parameters used to select the file, dataset and neuron id.
 * 
 * @author vleite
 *
 */
public class Parameters
{
	@Parameter( names = { "--file", "-f" }, description = "input file path (hdf5)" )
	public String filePath = "";

	@Parameter( names = { "--label", "-l" }, description = "label dataset name" )
	public String labelDatasetPath = "";

	@Parameter( names = { "--raw", "-r" }, description = "raw dataset name" )
	public String rawDatasetPath = "";

	@Parameter( names = { "--resolution", "-rs" }, description = "resolution", converter = DoubleConverter.class )
	public List< Double > resolution = new ArrayList<>();;

	@Parameter( names = { "--rawCellSize", "-rcs" }, description = "raw cell size" )
	public List< Integer > rawCellSize = new ArrayList<>();;

	@Parameter( names = { "--labelCellSize", "-lcs" }, description = "label cell size" )
	public List< Integer > labelCellSize = new ArrayList<>();;

	@Parameter( names = "--help", help = true )
	private boolean help;
}
