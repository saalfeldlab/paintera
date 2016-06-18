package bdv.labels.labelset;

public interface Label
{
	static public long TRANSPARENT = 0xffffffffffffffffL; // -1L or uint64.MAX_VALUE
	static public long INVALID = 0xfffffffffffffffeL; // -2L or uint64.MAX_VALUE - 1
	static public long OUTSIDE = 0xfffffffffffffffdL; // -3L or uint64.MAX_VALUE - 2

	public long id();
}