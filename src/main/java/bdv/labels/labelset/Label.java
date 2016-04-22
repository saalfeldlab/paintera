package bdv.labels.labelset;

public interface Label
{
	static public long TRANSPARENT = 0xffffffffffffffffL; // -1L or uint64.MAX_VALUE

	public long id();
}