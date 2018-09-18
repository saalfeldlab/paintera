package org.janelia.saalfeldlab.paintera.control.paint;

import java.lang.invoke.MethodHandles;
import java.util.stream.IntStream;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TLongArrayList;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.BooleanType;
import net.imglib2.type.numeric.IntegerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FloodFillTransformedPlane
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final double[] D_X_LOCAL = {1.0, 0.0, 0.0};

	private static final double[] D_Y_LOCAL = {0.0, 1.0, 0.0};

	private static final double[] D_Z_LOCAL = {0.0, 0.0, 1.0};

	private final AffineTransform3D localToWorld;

	private final double minX;

	private final double minY;

	private final double maxX;

	private final double maxY;

	private final double zRangePos;

	private final double zRangeNeg;

	private final double dxx;

	private final double dxy;

	private final double dxz;

	private final double dyx;

	private final double dyy;

	private final double dyz;

	private final double dzx;

	private final double dzy;

	private final double dzz;

	public static void fill(
			final AffineTransform3D localToWorld,
			final double zRange,
			final RandomAccess<? extends BooleanType<?>> relevantBackgroundAccess,
			final RandomAccess<? extends IntegerType<?>> localAccess,
			final RealLocalizable seedWorld,
			final long fillLabel)
	{
		new FloodFillTransformedPlane(localToWorld, zRange).fill(
				relevantBackgroundAccess,
				localAccess,
				seedWorld,
				fillLabel
		                                                        );
	}

	public static void fill(
			final AffineTransform3D localToWorld,
			final double[] min,
			final double[] max,
			final double zRangePos,
			final double zRangeNeg,
			final RandomAccess<? extends BooleanType<?>> relevantBackgroundAccess,
			final RandomAccess<? extends IntegerType<?>> localAccess,
			final RealLocalizable seedWorld,
			final long fillLabel)
	{
		new FloodFillTransformedPlane(localToWorld, min[0], min[1], max[0], max[1], zRangePos, zRangeNeg).fill(
				relevantBackgroundAccess,
				localAccess,
				seedWorld,
				fillLabel
		                                                                                                      );
	}

	// maxX, maxY, zRange in world coordinates
	public FloodFillTransformedPlane(
			final AffineTransform3D localToWorld,
			final double zRange)
	{
		this(
				localToWorld,
				Double.NEGATIVE_INFINITY,
				Double.NEGATIVE_INFINITY,
				Double.POSITIVE_INFINITY,
				Double.POSITIVE_INFINITY,
				+zRange,
				-zRange
		    );
	}

	// maxX, maxY, zRange in world coordinates
	public FloodFillTransformedPlane(
			final AffineTransform3D localToWorld,
			final double minX,
			final double minY,
			final double maxX,
			final double maxY,
			final double zRange)
	{
		this(localToWorld, minX, minY, maxX, maxY, +zRange, -zRange);
	}

	// maxX, maxY, zRange in world coordinates
	public FloodFillTransformedPlane(
			final AffineTransform3D localToWorld,
			final double minX,
			final double minY,
			final double maxX,
			final double maxY,
			final double zRangePos,
			final double zRangeNeg)
	{
		super();
		this.minX = minX;
		this.minY = minY;
		this.maxX = maxX;
		this.maxY = maxY;
		this.localToWorld = localToWorld;
		this.zRangePos = zRangePos;
		this.zRangeNeg = zRangeNeg;

		final double[] dx = D_X_LOCAL.clone();
		final double[] dy = D_Y_LOCAL.clone();
		final double[] dz = D_Z_LOCAL.clone();

		final AffineTransform3D transformNoTranslation = this.localToWorld.copy();
		transformNoTranslation.setTranslation(0.0, 0.0, 0.0);
		transformNoTranslation.apply(dx, dx);
		transformNoTranslation.apply(dy, dy);
		transformNoTranslation.apply(dz, dz);

		this.dxx = dx[0];
		this.dxy = dx[1];
		this.dxz = dx[2];

		this.dyx = dy[0];
		this.dyy = dy[1];
		this.dyz = dy[2];

		this.dzx = dz[0];
		this.dzy = dz[1];
		this.dzz = dz[2];

	}

	public void fill(
			final RandomAccess<? extends BooleanType<?>> relevantBackgroundAccess,
			final RandomAccess<? extends IntegerType<?>> maskAccess,
			final RealLocalizable seedWorld,
			final long fillLabel)
	{
		final double[] pos = IntStream.range(0, 3).mapToDouble(seedWorld::getDoublePosition).toArray();

		final RealPoint seedLocal = new RealPoint(seedWorld);
		localToWorld.applyInverse(seedLocal, seedLocal);

		final TLongArrayList   sourceCoordinates = new TLongArrayList();
		final TDoubleArrayList worldCoordinates  = new TDoubleArrayList();

		final double zMinInclusive = seedWorld.getDoublePosition(2) + zRangeNeg;
		final double zMaxInclusive = seedWorld.getDoublePosition(2) + zRangePos;

		for (int d = 0; d < 3; ++d)
		{
			sourceCoordinates.add(Math.round(seedLocal.getDoublePosition(d)));
			worldCoordinates.add(pos[d]);
		}

		relevantBackgroundAccess.setPosition(sourceCoordinates.get(0), 0);
		relevantBackgroundAccess.setPosition(sourceCoordinates.get(1), 1);
		relevantBackgroundAccess.setPosition(sourceCoordinates.get(2), 2);

		if (!relevantBackgroundAccess.get().get())
		{
			LOG.info(
					"Started at invalid position {}: {} -- not doing anything",
					new Point(relevantBackgroundAccess),
					relevantBackgroundAccess.get()
			        );
			return;
		}

		for (int offset = 0; offset < sourceCoordinates.size(); offset += 3)
		{
			final int  o0 = offset + 0;
			final int  o1 = offset + 1;
			final int  o2 = offset + 2;
			final long lx = sourceCoordinates.get(o0);
			final long ly = sourceCoordinates.get(o1);
			final long lz = sourceCoordinates.get(o2);
			relevantBackgroundAccess.setPosition(lx, 0);
			relevantBackgroundAccess.setPosition(ly, 1);
			relevantBackgroundAccess.setPosition(lz, 2);
			maskAccess.setPosition(lx, 0);
			maskAccess.setPosition(ly, 1);
			maskAccess.setPosition(lz, 2);

			final IntegerType<?> val = maskAccess.get();

			if (val.getIntegerLong() == fillLabel)
			{
				continue;
			}
			val.setInteger(fillLabel);

			final double x = worldCoordinates.get(o0);
			final double y = worldCoordinates.get(o1);
			final double z = worldCoordinates.get(o2);

			maskAccess.fwd(0);
			relevantBackgroundAccess.fwd(0);
			addIfInside(
					relevantBackgroundAccess.get().get(),
					fillLabel,
					maskAccess.get().getIntegerLong(),
					sourceCoordinates,
					worldCoordinates,
					lx + 1, ly, lz,
					x + dxx, y + dxy, z + dxz,
					zMinInclusive, zMaxInclusive
			           );

			maskAccess.move(-2l, 0);
			relevantBackgroundAccess.move(-2l, 0);
			addIfInside(
					relevantBackgroundAccess.get().get(),
					fillLabel,
					maskAccess.get().getIntegerLong(),
					sourceCoordinates,
					worldCoordinates,
					lx - 1, ly, lz,
					x - dxx, y - dxy, z - dxz,
					zMinInclusive, zMaxInclusive
			           );
			maskAccess.fwd(0);
			relevantBackgroundAccess.fwd(0);

			maskAccess.fwd(1);
			relevantBackgroundAccess.fwd(1);
			addIfInside(
					relevantBackgroundAccess.get().get(),
					fillLabel,
					maskAccess.get().getIntegerLong(),
					sourceCoordinates,
					worldCoordinates,
					lx, ly + 1, lz,
					x + dyx, y + dyy, z + dyz,
					zMinInclusive, zMaxInclusive
			           );

			maskAccess.move(-2l, 1);
			relevantBackgroundAccess.move(-2l, 1);
			addIfInside(
					relevantBackgroundAccess.get().get(),
					fillLabel,
					maskAccess.get().getIntegerLong(),
					sourceCoordinates,
					worldCoordinates,
					lx, ly - 1, lz,
					x - dyx, y - dyy, z - dyz,
					zMinInclusive, zMaxInclusive
			           );
			maskAccess.fwd(1);
			relevantBackgroundAccess.fwd(1);

			maskAccess.fwd(2);
			relevantBackgroundAccess.fwd(2);
			addIfInside(
					relevantBackgroundAccess.get().get(),
					fillLabel,
					maskAccess.get().getIntegerLong(),
					sourceCoordinates,
					worldCoordinates,
					lx, ly, lz + 1,
					x + dzx, y + dzy, z + dzz,
					zMinInclusive, zMaxInclusive
			           );

			maskAccess.move(-2l, 2);
			relevantBackgroundAccess.move(-2l, 2);
			addIfInside(
					relevantBackgroundAccess.get().get(),
					fillLabel,
					maskAccess.get().getIntegerLong(),
					sourceCoordinates,
					worldCoordinates,
					lx, ly, lz - 1,
					x - dzx, y - dzy, z - dzz,
					zMinInclusive, zMaxInclusive
			           );
			maskAccess.fwd(2);
			relevantBackgroundAccess.fwd(2);

		}
	}

	private void addIfInside(
			final boolean isRelevant,
			final long fillLabel,
			final long pixelLabel,
			final TLongArrayList labelCoordinates,
			final TDoubleArrayList worldCoordinates,
			final long lx,
			final long ly,
			final long lz,
			final double wx,
			final double wy,
			final double wz,
			final double zMinInclusive,
			final double zMaxInclusive)
	{
		if (isRelevant && fillLabel != pixelLabel && wz >= zMinInclusive && wz <= zMaxInclusive)
		{
			if (wx > minX && wx < maxX && wy > minY && wy < maxY)
			{
				labelCoordinates.add(lx);
				labelCoordinates.add(ly);
				labelCoordinates.add(lz);

				worldCoordinates.add(wx);
				worldCoordinates.add(wy);
				worldCoordinates.add(wz);
			}
		}
	}

}
