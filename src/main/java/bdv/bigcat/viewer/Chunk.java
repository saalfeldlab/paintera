package bdv.bigcat.viewer;

import bdv.labels.labelset.LabelMultisetType;
import net.imglib2.RandomAccessibleInterval;

public class Chunk
{
	private RandomAccessibleInterval< LabelMultisetType > volume;

	private int[] offset;

	public Chunk()
	{
		volume = null;
		offset = null;
	}

	public RandomAccessibleInterval< LabelMultisetType > getVolume()
	{
		return volume;
	}

	public void setVolume( RandomAccessibleInterval< LabelMultisetType > volume )
	{
		this.volume = volume;
	}

	public int[] getOffset()
	{
		return offset;
	}

	public void setOffset( int[] offset )
	{
		this.offset = offset;
	}

}
