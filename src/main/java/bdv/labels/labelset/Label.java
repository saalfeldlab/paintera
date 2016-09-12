package bdv.labels.labelset;

import bdv.util.IdService;

public interface Label
{
	static public long BACKGROUND = 0L;

	static public long TRANSPARENT = 0xffffffffffffffffL; // -1L or uint64.MAX_VALUE
	static public long INVALID = 0xfffffffffffffffeL; // -2L or uint64.MAX_VALUE - 1
	static public long OUTSIDE = 0xfffffffffffffffdL; // -3L or uint64.MAX_VALUE - 2
	static public long MAX_ID = 0xfffffffffffffffcL; // -4L or uint64.MAX_VALUE - 3

	public long id();

	/**
	 * True if that passed long is less than or equal Label.MAX_ID, i.e. the
	 * corresponding uint64 is a regular ID and has no 'special' meaning.
	 *
	 * @return
	 */
	static public boolean regular( final long id )
	{
		return IdService.max( id, Label.MAX_ID ) == Label.MAX_ID;
	}
}