package bdv.bigcat.viewer.atlas.data.mask;

public class MaskInfo< D >
{
	public final int t;

	public final int level;

	public final D value;

	public MaskInfo( final int t, final int level, final D value )
	{
		super();
		this.t = t;
		this.level = level;
		this.value = value;
	}

	@Override
	public String toString()
	{
		return String.format( "{t=%d, level=%d, val=%s}", t, level, value );
	}
}
