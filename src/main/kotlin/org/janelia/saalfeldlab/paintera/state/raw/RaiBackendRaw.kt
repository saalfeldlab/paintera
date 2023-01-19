package org.janelia.saalfeldlab.paintera.state.raw

import net.imglib2.RandomAccessibleInterval
import net.imglib2.Volatile
import net.imglib2.type.NativeType
import net.imglib2.type.Type
import net.imglib2.type.numeric.RealType
import org.janelia.saalfeldlab.paintera.state.RandomAccessibleIntervalBackend

class RaiBackendRaw<D, T>(
	source: RandomAccessibleInterval<D>,
	resolution: DoubleArray,
	offset: DoubleArray,
	name: String
) : RandomAccessibleIntervalBackend<D, T>(
	name,
	source,
	resolution,
	offset
), ConnectomicsRawBackend<D, T> where D : RealType<D>, D : NativeType<D>, T : Volatile<D>, T : Type<T>