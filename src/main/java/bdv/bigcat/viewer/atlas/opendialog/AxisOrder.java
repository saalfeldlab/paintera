package bdv.bigcat.viewer.atlas.opendialog;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum AxisOrder
{

	// space only
	XYZ, XZY,
	YZX, YXZ,
	ZXY, ZYX,

	// space and time only (time only before and after space)?
	TXYZ, TXZY,
	TYZX, TYXZ,
	TZXY, TZYX,

	XYZT, XZYT,
	YZXT, YXZT,
	ZXYT, ZYXT,

	// space and channel only (channel only before and after space)?
	CXYZ, CXZY,
	CYZX, CYXZ,
	CZXY, CZYX,

	XYZC, XZYC,
	YZXC, YXZC,
	ZXYC, ZYXC,

	// space and channel only (channel only before and after space)?
	CTXYZ, CTXZY,
	CTYZX, CTYXZ,
	CTZXY, CTZYX,

	TCXYZ, TCXZY,
	TCYZX, TCYXZ,
	TCZXY, TCZYX,

	XYZCT, XZYCT,
	YZXCT, YXZCT,
	ZXYCT, ZYXCT,

	XYZTC, XZYTC,
	YZXTC, YXZTC,
	ZXYTC, ZYXTC

	;

	private static final String TIME_IDENTIFIER = "T";

	private static final String DIMX_IDENTIFIER = "X";

	private static final String DIMY_IDENTIFIER = "Y";

	private static final String DIMZ_IDENTIFIER = "Z";

	private static final String CHAN_IDENTIFIER = "C";

	private final int numDimensions;

	private final int numSpaceDimensions;

	private final boolean hasChannels;

	private final boolean hasTime;

	private final int[] permutation;

	private final int[] inversePermutation;

	private AxisOrder()
	{
		final String upperCaseName = this.name().toUpperCase();
		this.numDimensions = upperCaseName.length();
		this.hasChannels = upperCaseName.contains( CHAN_IDENTIFIER );
		this.hasTime = upperCaseName.contains( TIME_IDENTIFIER );
		this.numSpaceDimensions = this.numDimensions - ( hasChannels ? 1 : 0 ) - ( hasTime ? 1 : 0 );
		this.permutation = new int[ this.numDimensions ];
		for ( int d = 0; d < this.numDimensions; ++d )
			this.permutation[ getIndexFor( upperCaseName.substring( d, d + 1 ), this.hasTime, this.numSpaceDimensions ) ] = d;
		this.inversePermutation = invertPermutation( this.permutation );
	}

	public int numDimensions()
	{
		return this.numDimensions;
	}

	public int numSpaceDimensions()
	{
		return this.numSpaceDimensions;
	}

	public boolean hasChannels()
	{
		return this.hasChannels;
	}

	public boolean hasTime()
	{
		return this.hasTime;
	}

	public int[] permutation()
	{
		return this.permutation.clone();
	}

	public int[] inversePermutation()
	{
		return this.inversePermutation.clone();
	}

	public AxisOrder spatialOnly()
	{
		final Pattern pattern = Pattern.compile( String.format( "([%s%s%s]+)+", DIMX_IDENTIFIER, DIMY_IDENTIFIER, DIMZ_IDENTIFIER ) );
		final Matcher matcher = pattern.matcher( name() );
		matcher.find();
		final String matched = matcher.group( 1 );
		return Arrays.stream( values() ).filter( order -> order.name().equals( matched ) ).findFirst().orElse( null );
	}

	public static Optional< AxisOrder > defaultOrder( final int numDimensions )
	{
		switch ( numDimensions )
		{
		case 3:
			return Optional.of( XYZ );
		case 4:
			return Optional.of( TXYZ );
		case 5:
			return Optional.of( TCXYZ );
		default:
			return Optional.empty();
		}
	}

	private static int getIndexFor( final String identifier, final boolean hasTime, final int numSpaceDimensions )
	{
		switch ( identifier )
		{
		case DIMX_IDENTIFIER:
			return 0;
		case DIMY_IDENTIFIER:
			return 1;
		case DIMZ_IDENTIFIER:
			return 2;
		case TIME_IDENTIFIER:
			return 3;
		case CHAN_IDENTIFIER:
			return numSpaceDimensions + ( hasTime ? 1 : 0 );
		default:
			return -1;
		}
	}

	private static int[] invertPermutation( final int[] permutation )
	{
		final int[] inverted = new int[ permutation.length ];
		for ( int i = 0; i < inverted.length; ++i )
			inverted[ permutation[ i ] ] = i;
		return inverted;
	}

//	public static void main( final String[] args )
//	{
//		final AxisOrder order = XZYTC;
//		System.out.println( order.spatialOnly() );
//	}

}
