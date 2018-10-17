package org.janelia.saalfeldlab.paintera.data.axisorder;

import net.imglib2.realtransform.AffineTransform3D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public enum AxisOrder {

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

	// space, channel, and time (channel only before and after space)?
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
	ZXYTC, ZYXTC;

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public enum AxisType {
		SPATIAL,
		TIME,
		CHANNEL
	};

	public enum Axis
	{
		X, Y, Z, T(AxisType.TIME), C(AxisType.CHANNEL);

		private final AxisType type;

		Axis()
		{
			this(AxisType.SPATIAL);
		}

		Axis(final AxisType type) {
			this.type = type;
		}

		public AxisType getType()
		{
			return type;
		}

		public static Optional<Axis> fromName(final String name)
		{
			return Arrays.stream(values()).filter(ax -> ax.name().equalsIgnoreCase(name)).findFirst();
		}
	}

	private final int numDimensions;

	private final int numSpaceDimensions;

	private final boolean hasChannels;

	private final boolean hasTime;

	private final List<Axis> axes;

	AxisOrder()
	{
		final String upperCaseName = this.name().toUpperCase();
		this.numDimensions = upperCaseName.length();
		this.axes = new ArrayList<>();
		for (int i = 0; i < this.numDimensions; ++i)
			axes.add(Axis.fromName(upperCaseName.substring(i, i+1)).get());

		this.hasChannels = axes.contains(Axis.C);
		this.hasTime = axes.contains(Axis.T);
		this.numSpaceDimensions = (int)axes.stream().map(Axis::getType).filter(AxisType.SPATIAL::equals).count();

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
		LOG.debug("Axis order {} has channels? {}", this, this.hasChannels);
		return this.hasChannels;
	}

	public boolean hasTime()
	{
		return this.hasTime;
	}

	public static Optional<AxisOrder> defaultOrder(final int numDimensions)
	{
		switch (numDimensions)
		{
			case 3:
				return Optional.of(XYZ);
			case 4:
				return Optional.of(XYZC);
			case 5:
				return Optional.of(TCXYZ);
			default:
				return Optional.empty();
		}
	}

	public int index(Axis axis)
	{
		return axes.indexOf(axis);
	}

	public int[] indices(Axis... axes)
	{
		return Stream
				.of(axes)
				.mapToInt(this::index)
				.toArray();
	}

	public int[] spatialIndices()
	{
		return indices(Axis.X, Axis.Y, Axis.Z);
	}

	public int channelIndex()
	{
		return index(Axis.C);
	}

	public int timeIndex()
	{
		return index(Axis.T);
	}

	public Axis[] axes()
	{
		return axes.stream().toArray(Axis[]::new);
	}

	public static AxisOrder[] onlyThisSpatialOrder(AxisOrder order)
	{
		final AxisOrder spatialOnly = order.spatialOnly();
		return Stream
				.of(values())
				.filter(ao -> spatialOnly.equals(ao.spatialOnly()))
				.toArray(AxisOrder[]::new);
	}

	public AxisOrder spatialOnly()
	{
		// TODO make this more efficient
		final String[] spatialAxes = this
				.axes
				.stream()
				.filter(ax -> AxisType.SPATIAL.equals(ax.getType()))
				.map(Axis::name)
				.toArray(String[]::new);
		final String name = String.join("", spatialAxes);
		return Arrays.stream(AxisOrder.values()).filter(order -> name.equalsIgnoreCase(order.name())).findFirst().get();
	}

	public AffineTransform3D asAffineTransform()
	{
		final int[] spatialIndices = spatialOnly().spatialIndices();
		double[] values = new double[12];
		values[0 + spatialIndices[0]] = 1;
		values[4 + spatialIndices[1]] = 1;
		values[8 + spatialIndices[2]] = 1;

		final AffineTransform3D tf = new AffineTransform3D();
		tf.set(values);
		return tf;
	}

	public static AxisOrder[] valuesFor(int numDimensions)
	{
		return Stream.of(values()).filter(ax -> ax.numDimensions() == numDimensions).toArray(AxisOrder[]::new);
	}

	public static AxisOrder[] spatialValues()
	{
		return Stream
				.of(values())
				.filter(ao -> !ao.hasTime() && !ao.hasChannels())
				.toArray(AxisOrder[]::new);
	}

	private static int getIndexFor(final Axis identifier, final boolean hasTime, final int numSpaceDimensions)
	{
		switch (identifier)
		{
			case X:
				return 0;
			case Y:
				return 1;
			case Z:
				return 2;
			case T:
				return 3;
			case C:
				return numSpaceDimensions + (hasTime ? 1 : 0);
			default:
				return -1;
		}
	}

	public static void main(String[] args) {
		AxisOrder ax = AxisOrder.TCYZX;
		System.out.println(Arrays.toString(ax.spatialIndices()));
		System.out.println(ax.channelIndex());
		System.out.println(ax.timeIndex());
		System.out.println(ax.numDimensions());
		System.out.println(ax.numSpaceDimensions());
	}

}
