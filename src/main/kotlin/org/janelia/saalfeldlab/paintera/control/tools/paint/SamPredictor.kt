package org.janelia.saalfeldlab.paintera.control.tools.paint

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.github.oshai.kotlinlogging.KotlinLogging
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.RealPoint
import net.imglib2.img.array.ArrayImgs
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.util.interval
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.max

private fun allocateDirectFloatBuffer(size: Int, order: ByteOrder = ByteOrder.nativeOrder()): FloatBuffer {
	return ByteBuffer.allocateDirect(size * Float.SIZE_BYTES).order(order).asFloatBuffer()
}

private val LOG = KotlinLogging.logger { }

class SamPredictor(
	private val environment: OrtEnvironment,
	private val session: OrtSession,
	var embedding: OnnxTensor,
	val originalImgSize: Pair<Int, Int>
) {
	companion object {

		/**
		 * SAM Embedding converts image to max dim length of 1024.
		 * Instead of letting the service do it, we just do it ahead of time.
		 * Helps ensure the image we send is as small as possible.
		 */
		internal const val MAX_DIM_TARGET = 1024

		const val LOW_RES_MASK_DIM = 256

		const val IMAGE_EMBEDDINGS = "image_embeddings"
		const val ORIG_IM_SIZE = "orig_im_size"
		const val POINT_COORDS = "point_coords"
		const val POINT_LABELS = "point_labels"
		const val MASK_INPUT = "mask_input"
		const val HAS_MASK_INPUT = "has_mask_input"

		/**
		 * Creates a prediction request with the given points.
		 * The points must be in the range [0, origImgSize) for each dimension,
		 * and have labels of either SparseLabel.OUT or SpareLabel.IN
		 *
		 * @param points The list of points.
		 * @return The created PredictionRequest object.
		 */
		fun points(points: List<SamPoint>): PredictionRequest {
			return SparsePrediction(points)
		}
	}

	/* TODO: Evaluate this is correct. I think we are supposed to introduce a half pixel offset somewhere...?
	*   */
	val imgEmbeddingScale = let {
		val (xDim, yDim) = originalImgSize
		if (xDim >= yDim) {
			MAX_DIM_TARGET.toDouble() / xDim
		} else {
			MAX_DIM_TARGET.toDouble() / yDim
		}
	}


	private val imgSizeBuffer = allocateDirectFloatBuffer(2).also {
		originalImgSize.let { (width, height) ->
			it.put(height.toFloat())
			it.put(width.toFloat())
		}
		it.position(0)
	}
	private val imgSizeTensor = OnnxTensor.createTensor(environment, imgSizeBuffer, longArrayOf(2))

	lateinit var lastPrediction: SamPrediction
	lateinit var result: RandomAccessibleInterval<NativeType<*>>

	fun predict(vararg requests: PredictionRequest): SamPrediction {
		/* Add the embedding and size */
		val params = mutableMapOf<String, OnnxTensorLike>(
			IMAGE_EMBEDDINGS to embedding,
			ORIG_IM_SIZE to imgSizeTensor
		)
		/* add the `no-mask` params. If a mask is present, they will be overwritten */
		params += MaskPrediction.noMaskParameters(environment)

		/* add the parameter maps */
		requests.map { it.mapParameters(this, environment) }.fold(params) { acc, map ->
			acc += map
			acc
		}
		/* run the prediction */
		synchronized(this) {
			val predictionResult = session.run(params)
			return SamPrediction(predictionResult, this).also {
				lastPrediction = it
			}
		}
	}


	/**
	 * Converts coordinates in the original image space to embedded image coordinates.
	 * The image sent to be embedded is always scaled such that the longest dimension is 1024,
	 * while maintaining the aspect ratio. The coordinates are scaled to match the scaled image.
	 *
	 *
	 * @param coord The coordinate to be converted, within the bounds of the [SamPredictor.originalImgSize].
	 * @return The converted coordinate within the bounds of the scaled image.
	 */
	private fun originalToEmbeddedImageCoord(coord: RealPoint): RealPoint {
		return coord.positionAsDoubleArray()
			.map { it * imgEmbeddingScale }
			.toDoubleArray().let {
				RealPoint.wrap(it)
			}
	}

	data class SamPrediction(
		val masks: OnnxTensor,
		val iouPredictions: OnnxTensor,
		val lowResMasks: OnnxTensor,
		val predictor: SamPredictor
	) {

		val image: RandomAccessibleInterval<FloatType> = ArrayImgs.floats(masks.floatBuffer.array(), predictor.originalImgSize.first.toLong(), predictor.originalImgSize.second.toLong())
		val lowResImage: RandomAccessibleInterval<FloatType> by lazy {
			ArrayImgs.floats(lowResMasks.floatBuffer.array(), LOW_RES_MASK_DIM.toLong(), LOW_RES_MASK_DIM.toLong()).interval(lowResIntervalWithoutPadding)
		}
		val lowToHighResScale: Double
		private val lowResIntervalWithoutPadding : Interval

		constructor(result: OrtSession.Result, predictor: SamPredictor) : this(
			result[MASKS].get() as OnnxTensor,
			result[IOU_PREDICTIONS].get() as OnnxTensor,
			result[LOW_RES_MASKS].get() as OnnxTensor,
			predictor
		)

		init {
			with(predictor) {
				val lowResWidth: Long
				val lowResHeight: Long
				val (imgWidth, imgHeight) = originalImgSize
				lowToHighResScale = max(imgWidth, imgHeight).toDouble() / LOW_RES_MASK_DIM
				if (imgWidth > imgHeight) {
					lowResWidth = LOW_RES_MASK_DIM.toLong()
					lowResHeight = ceil(imgHeight / lowToHighResScale).toLong()
				} else {
					lowResHeight = LOW_RES_MASK_DIM.toLong()
					lowResWidth = ceil(imgWidth / lowToHighResScale).toLong()
				}
				lowResIntervalWithoutPadding = Intervals.createMinSize(0, 0, lowResWidth, lowResHeight)
			}
		}

		companion object {
			const val MASKS = "masks"
			const val IOU_PREDICTIONS = "iou_predictions"
			const val LOW_RES_MASKS = "low_res_masks"
		}
	}


	enum class SparseLabel(val label: Float) {
		OUT(0f),
		IN(1f),
		TOP_LEFT_BOX(2f),
		BOTTOM_RIGHT_BOX(3f)
	}


	sealed interface PredictionRequest {

		fun mapParameters(predictor: SamPredictor, environment: OrtEnvironment): Map<String, OnnxTensorLike>
	}

	/**
	 * Represents a prediction request for sparse data.
	 * Points must be x,y integers relative to the top left of the image used to generate the embedding.
	 * Points must be within (0 - [SamPredictor.originalImgSize]) for all dimensions.
	 *
	 * @property points A list of points.
	 */
	class SparsePrediction(val points: List<SamPoint>) : PredictionRequest {

		override fun mapParameters(predictor: SamPredictor, environment: OrtEnvironment): Map<String, OnnxTensorLike> {

			val numPoints = if (points.isEmpty()) 1 else points.size
			val coordsBuffer = allocateDirectFloatBuffer(2 * numPoints)
			val labelsBuffer = allocateDirectFloatBuffer(numPoints)

			points.ifEmpty { listOf(SamPoint(0.0, 0.0, SparseLabel.OUT)) }.forEach {
				val (scaledX, scaledY) = it.centerScaledCoordinates(predictor.imgEmbeddingScale)
				coordsBuffer.put(scaledX.toFloat())
				coordsBuffer.put(scaledY.toFloat())
				labelsBuffer.put(it.label.ordinal.toFloat())
			}

			coordsBuffer.position(0)
			labelsBuffer.position(0)

			val onnxCoords = OnnxTensor.createTensor(environment, coordsBuffer, longArrayOf(1, numPoints.toLong(), 2))
			val onnxLabels = OnnxTensor.createTensor(environment, labelsBuffer, longArrayOf(1, numPoints.toLong()))
			return mapOf(
				POINT_COORDS to onnxCoords,
				POINT_LABELS to onnxLabels
			)
		}
	}

	/**
	 * Represents a prediction request with a mask input.
	 * Mask Interval should be 256x256
	 *
	 * @property mask A lowres mask dictating parts of the image that should be inside or outside the segmentation
	 */
	class MaskPrediction(val mask: RandomAccessibleInterval<in NativeType<*>>) : PredictionRequest {

		companion object {
			const val MASK_DIM = LOW_RES_MASK_DIM.toLong()

			/* 4D low-res mask, expected to be 256x256 */
			private val noMaskBuffer by lazy {
				val maskDim = MASK_DIM.toInt()
				allocateDirectFloatBuffer(1 * 1 * maskDim * maskDim)
			}
			private val maskShape = longArrayOf(1, 1, MASK_DIM, MASK_DIM)

			private val hasNoMaskInput by lazy { allocateDirectFloatBuffer(1) }
			private val hasMaskFlagShape = longArrayOf(1)

			fun noMaskParameters(environment: OrtEnvironment): Map<String, OnnxTensor> {
				return mapOf(
					MASK_INPUT to OnnxTensor.createTensor(environment, noMaskBuffer, maskShape),
					HAS_MASK_INPUT to OnnxTensor.createTensor(environment, hasNoMaskInput, hasMaskFlagShape),
				)
			}
		}

		init {
			assert(mask.dimension(0) == MASK_DIM)
			assert(mask.dimension(1) == MASK_DIM)
		}

		override fun mapParameters(predictor: SamPredictor, environment: OrtEnvironment): Map<String, OnnxTensorLike> {
			TODO("Not yet implemented")
		}
	}

	data class SamPoint(val x: Double, val y: Double, val label: SparseLabel) {

		fun centerScaledCoordinates(scale: Double): Pair<Double, Double> {
			return (x + .5) * scale to (y + .5) * scale
		}
	}

}