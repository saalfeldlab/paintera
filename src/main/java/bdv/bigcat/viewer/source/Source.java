package bdv.bigcat.viewer.source;

import bdv.ViewerSetupImgLoader;
import net.imglib2.Volatile;

public interface Source< T, VT extends Volatile< T > >
{

//	public Optional< double[] > resolution();
//
//	public Optional< double[] > offset();
//
//	public int[] cellSize();

	public ViewerSetupImgLoader< T, VT > loader() throws Exception;

	public String name();

}
