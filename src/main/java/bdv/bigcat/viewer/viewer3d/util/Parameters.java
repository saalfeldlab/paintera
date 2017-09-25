package bdv.bigcat.viewer.viewer3d.util;

import com.beust.jcommander.Parameter;

/**
 * Parameters used to select the file, dataset and neuron id.
 * 
 * @author vleite
 *
 */
public class Parameters
{
	@Parameter( names = { "--file", "-f" }, description = "input file path (hdf5)" )
//	public String filePath = "resources/sample_B_20160708_frags_46_50.hdf";
	public String filePath = "data/sample_B.augmented.0.hdf";

	@Parameter( names = { "--label", "-l" }, description = "label dataset name" )
	public String labelDatasetPath = "/volumes/labels/neuron_ids";

	@Parameter( names = { "--neuronId", "-id" }, description = "foreground value" )
//	public Integer foregroundValue = 7;
//	public Integer foregroundValue = 364328; // separated parts
	public Integer foregroundValue = 289449;
//	public Integer foregroundValue = 302633; // bird :P
//	public Integer foregroundValue = 289449;
//	public Integer foregroundValue = 462291;
//	public Integer foregroundValue = 370928;
//	public Integer foregroundValue = 416321;

	@Parameter( names = "--help", help = true )
	private boolean help;
}
