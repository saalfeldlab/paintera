package bdv.bigcat.viewer.atlas.mode;

import bdv.bigcat.viewer.state.SelectedIds;

public interface HandleMultipleIds
{

	public boolean handle( long[] ids, SelectedIds selectedIds, boolean[] isActive );

}
