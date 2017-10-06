package bdv.bigcat.viewer.atlas.data;

import bdv.bigcat.viewer.viewer3d.marchingCubes.ForegroundCheck;

public interface RenderableSpec< T, VT > extends DatasetSpec< T, VT >
{

	public ForegroundCheck< T > foregroundCheck( T selection );

}
