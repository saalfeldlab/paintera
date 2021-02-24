package org.janelia.saalfeldlab.paintera.control.paint;

import java.util.stream.IntStream;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TLongArrayList;
import net.imglib2.RandomAccess;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.IntegerType;

public class FloodFillTransformedCylinder3D {

  private static final double[] D_X_LOCAL = {1.0, 0.0, 0.0};

  private static final double[] D_Y_LOCAL = {0.0, 1.0, 0.0};

  private static final double[] D_Z_LOCAL = {0.0, 0.0, 1.0};

  private final AffineTransform3D localToWorld;

  private final double radiusXSquared;

  private final double radiusYSquared;

  private final double radiusXSquaredRadiusYSquared;

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
		  final double radiusX,
		  final double radiusY,
		  final double zRange,
		  final RandomAccess<? extends IntegerType<?>> localAccess,
		  final RealLocalizable seedWorld,
		  final long fillLabel) {

	new FloodFillTransformedCylinder3D(localToWorld, radiusX, radiusY, zRange).fill(
			localAccess,
			seedWorld,
			fillLabel
	);
  }

  public static void fill(
		  final AffineTransform3D localToWorld,
		  final double radiusX,
		  final double radiusY,
		  final double zRangePos,
		  final double zRangeNeg,
		  final RandomAccess<? extends IntegerType<?>> localAccess,
		  final RealLocalizable seedWorld,
		  final long fillLabel) {

	new FloodFillTransformedCylinder3D(localToWorld, radiusX, radiusY, zRangePos, zRangeNeg).fill(
			localAccess,
			seedWorld,
			fillLabel
	);
  }

  // radius and range in world coordinates
  public FloodFillTransformedCylinder3D(
		  final AffineTransform3D localToWorld,
		  final double radiusX,
		  final double radiusY,
		  final double zRange) {

	this(localToWorld, radiusX, radiusY, +zRange, -zRange);
  }

  // radius and range in world coordinates
  public FloodFillTransformedCylinder3D(
		  final AffineTransform3D localToWorld,
		  final double radiusX,
		  final double radiusY,
		  final double zRangePos,
		  final double zRangeNeg) {

	super();
	this.localToWorld = localToWorld;
	this.radiusXSquared = radiusX * radiusX;
	this.radiusYSquared = radiusY * radiusY;
	this.radiusXSquaredRadiusYSquared = radiusXSquared * radiusYSquared;
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
		  final RandomAccess<? extends IntegerType<?>> localAccess,
		  final RealLocalizable seedWorld,
		  final long fillLabel) {

	final double[] pos = IntStream.range(0, 3).mapToDouble(seedWorld::getDoublePosition).toArray();

	final RealPoint seedLocal = new RealPoint(seedWorld);
	localToWorld.applyInverse(seedLocal, seedLocal);

	final TLongArrayList sourceCoordinates = new TLongArrayList();
	final TDoubleArrayList worldCoordinates = new TDoubleArrayList();

	final double cx = seedWorld.getDoublePosition(0);
	final double cy = seedWorld.getDoublePosition(1);
	final double zMinInclusive = seedWorld.getDoublePosition(2) + zRangeNeg;
	final double zMaxInclusive = seedWorld.getDoublePosition(2) + zRangePos;

	for (int d = 0; d < 3; ++d) {
	  sourceCoordinates.add(Math.round(seedLocal.getDoublePosition(d)));
	  worldCoordinates.add(pos[d]);
	}

	for (int offset = 0; offset < sourceCoordinates.size(); offset += 3) {
	  final int o0 = offset + 0;
	  final int o1 = offset + 1;
	  final int o2 = offset + 2;
	  final long lx = sourceCoordinates.get(o0);
	  final long ly = sourceCoordinates.get(o1);
	  final long lz = sourceCoordinates.get(o2);
	  localAccess.setPosition(lx, 0);
	  localAccess.setPosition(ly, 1);
	  localAccess.setPosition(lz, 2);

	  final IntegerType<?> val = localAccess.get();

	  if (val.getIntegerLong() == fillLabel) {
		continue;
	  }
	  val.setInteger(fillLabel);

	  final double x = worldCoordinates.get(o0);
	  final double y = worldCoordinates.get(o1);
	  final double z = worldCoordinates.get(o2);

	  final int[] moveDirection = {-1, 0, 1};
	  for (int xStep : moveDirection) {
		for (int yStep : moveDirection) {
		  for (int zStep : moveDirection) {
			if (xStep == yStep && xStep == zStep && xStep == 0) {
			  /* this is our current seed point, don't check it*/
			  continue;
			}
			final var labelX = lx + xStep;
			final var labelY = ly + yStep;
			final var labelZ = lz + zStep;

			final var worldX = x + xStep * dxx + yStep * dyx + zStep * dzx;
			final var worldY = y + xStep * dxy + yStep * dyy + zStep * dzy;
			final var worldZ = z + xStep * dxz + yStep * dyz + zStep * dzz;

			addIfInside(
					sourceCoordinates,
					worldCoordinates,
					labelX, labelY, labelZ,
					worldX, worldY, worldZ,
					cx, cy,
					zMinInclusive, zMaxInclusive
			);
		  }
		}
	  }
	}
  }

  private void addIfInside(
		  final TLongArrayList labelCoordinates,
		  final TDoubleArrayList worldCoordinates,
		  final long lx,
		  final long ly,
		  final long lz,
		  final double wx,
		  final double wy,
		  final double wz,
		  final double cx,
		  final double cy,
		  final double zMinInclusive,
		  final double zMaxInclusive) {

	if (wz >= zMinInclusive && wz <= zMaxInclusive) {
	  final double dx = wx - cx;
	  final double dy = wy - cy;
	  if (dx * dx * radiusYSquared + dy * dy * radiusXSquared <= radiusXSquaredRadiusYSquared) {
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
