package org.janelia.saalfeldlab.paintera.state.label.feature

import net.imglib2.Localizable
import net.imglib2.Point
import net.imglib2.RandomAccessibleInterval
import net.imglib2.RealLocalizable
import net.imglib2.RealPoint
import net.imglib2.algorithm.neighborhood.Shape
import net.imglib2.converter.Converters
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.BooleanType
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.view.Views
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.paintera.data.DataSource

data class PointWithinObject(
    override val id: Long,
    override val value: RealLocalizable) : ObjectFeature<RealLocalizable> {

    companion object {

        @JvmStatic
        fun findPointIn(
            id: Long,
            source: DataSource<IntegerType<*>, *>,
            labelBlockLookup: LabelBlockLookup): PointWithinObject? {

            val intervals = labelBlockLookup.read(LabelBlockLookupKey(0, id))
            val idT = source.dataType.also { it.setInteger(id) }

            for (interval in labelBlockLookup.read(LabelBlockLookupKey(0, id))) {
                val position = findPointIn(id, source.getDataSource(0, 0))
                if (position === null) continue
                return RealPoint(source.getDataSource(0, 0).numDimensions())
                    .also { p ->
                        AffineTransform3D()
                            .also { source.getSourceTransform(0, 0, it) }
                            .apply(position, p)
                    }
                    .let { PointWithinObject(id, it) }
            }

            return null

        }

        @JvmStatic
        fun findPointIn(
            id: Long,
            source: RandomAccessibleInterval<IntegerType<*>>): Localizable? {
            val cursor = Views.iterable(source).cursor()
            while (cursor.hasNext())
                if (cursor.next().getIntegerLong() == id)
                    return cursor
            return null
        }

        @JvmStatic
        fun findPointInIdAndMask(
            id: Long,
            source: DataSource<IntegerType<*>, *>,
            maskSource: DataSource<BooleanType<*>, *>,
            labelBlockLookup: LabelBlockLookup,
            maskOffsets: Shape): PointWithinObject? {

            return findPointInIdAndMaskImpl(
                id,
                source,
                maskSource,
                labelBlockLookup,
                maskOffsets)?.let { p ->
                val rp = RealPoint(p.numDimensions())
                AffineTransform3D()
                    .also { source.getSourceTransform(0, 0, it) }
                    .apply(p, rp)
                PointWithinObject(id, rp)
            }
        }

        private fun findPointInIdAndMaskImpl(
            id: Long,
            source: DataSource<IntegerType<*>, *>,
            maskSource: DataSource<BooleanType<*>, *>,
            labelBlockLookup: LabelBlockLookup,
            maskOffsets: Shape): Localizable? {

            val data = source.getDataSource(0, 0)
            val labelMask = Converters.convert(
                data,
                { s, t -> t.set(s.getIntegerLong() == id) },
                BoolType())
            val mask = maskSource.getDataSource(0, 0)
            val maskBoolType = Converters.convert(
                mask,
                { s, t -> t.set(s.get()) },
                BoolType())
            val offsetsAccessible = maskOffsets.neighborhoodsRandomAccessible(Views.extendZero(labelMask))
            val offsets = mutableListOf<Point>()
            val offsetCursor = offsetsAccessible.randomAccess().get().localizingCursor()
            while (offsetCursor.hasNext())
                offsets += Point(offsetCursor.also { it.next() })


            for (interval in labelBlockLookup.read(LabelBlockLookupKey(0, id))) {
                val labelMaskBlock = Views.interval(Views.extendZero(labelMask), interval)
                for (offset in offsets) {
                    val c1 = Views.flatIterable(labelMaskBlock).cursor()
                    val c2 = Views.flatIterable(Views.interval(Views.extendZero(maskBoolType), interval)).cursor()
                    while (c1.hasNext()) {
                        val v1 = c1.next().get()
                        val v2 = c2.next().get()
                        if (v1 && v2)
                            return Point(c1)
                    }
                }
            }

            return null
        }
    }


}

