package org.janelia.saalfeldlab.paintera.cache

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import bdv.cache.SharedQueue
import bdv.fx.viewer.render.BaseRenderUnit
import bdv.fx.viewer.render.RenderUnitState
import bdv.viewer.Interpolation
import bdv.viewer.TransformListener
import com.amazonaws.util.Base64
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.animation.AnimationTimer
import javafx.embed.swing.SwingFXUtils
import kotlinx.coroutines.*
import net.imglib2.cache.LoaderCache
import net.imglib2.cache.ref.SoftRefLoaderCache
import net.imglib2.parallel.TaskExecutors
import net.imglib2.realtransform.AffineTransform3D
import org.apache.commons.lang.builder.HashCodeBuilder
import org.apache.http.HttpException
import org.apache.http.client.HttpClient
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.bdv.fx.viewer.getDataSourceAndConverter
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.cache.SessionRenderUnitState.Companion.withSessionId
import org.janelia.saalfeldlab.paintera.composition.CompositeProjectorPreMultiply
import org.janelia.saalfeldlab.paintera.config.SegmentAnythingConfig
import org.janelia.saalfeldlab.paintera.control.tools.paint.SamPredictor
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.properties
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object SamEmbeddingLoaderCache : AsyncCacheWithLoader<RenderUnitState, OnnxTensor>() {

	override val cache: LoaderCache<RenderUnitState, Deferred<OnnxTensor>> = SoftRefLoaderCache()

	override tailrec suspend fun loader(key: RenderUnitState): OnnxTensor {
		runCatching { getImageEmbedding(key) }
			.onSuccess { return it }
			.onFailure {
				if (it !is SocketTimeoutException)
					throw it
			}
		return loader(key)
	}

	//TODO Caleb: May want to be smarter about this, server side health check maybe
	val canReachServer by LazyForeignValue({ paintera.properties.segmentAnythingConfig.serviceUrl }) {
		try {
			requestSessionId()
			true
		} catch (e: Exception) {
			LOG.debug(e) { "Exception occurred while attempting to reach server" }
			false
		}
	}

	private val navigationId by lazy { getSessionId() }

	private class NavigationBasedRequestTimer(val viewerAndTransforms: ViewerAndTransforms) : AnimationTimer() {

		companion object {
			private const val REQUEST_COUNTDOWN = 15 // Unit is pulses of the Animation timer; roughly targets 60 FPS.
		}

		private val viewer
			get() = viewerAndTransforms.viewer()
		private val globalToViewerTransform
			get() = viewerAndTransforms.globalToViewerTransform.transformCopy

		private val sessionId: String = navigationId

		private var previousJob: Job = Job()
			set(value) {
				field.cancel()
				field = value
			}
		private var requestCountDown = AtomicInteger(REQUEST_COUNTDOWN)
		val countdownResetListener = TransformListener<AffineTransform3D> {
			requestCountDown.set(REQUEST_COUNTDOWN)
		}

		override fun handle(now: Long) {
			/* currently using -1 to indicate no change to the transform */
			if (requestCountDown.get() == -1) return
			else if (requestCountDown.getAndDecrement() == 0) {
				previousJob = load(viewer, globalToViewerTransform, sessionId)
				requestCountDown.getAndSet(-1)
			}
		}

		override fun start() {
			viewerAndTransforms.globalToViewerTransform.addListener(countdownResetListener)
			super.start()
		}

		override fun stop() {
			super.stop()
			viewerAndTransforms.globalToViewerTransform.removeListener(countdownResetListener)
		}
	}


	private var navigationBasedRequestTimer: NavigationBasedRequestTimer? = null
		set(value) {
			if (value == null) field?.stop()
			field = value?.apply { start() }
		}

	fun stopNavigationBasedRequests() {
		navigationBasedRequestTimer = null
	}

	fun startNavigationBasedRequests(viewerAndTransforms: ViewerAndTransforms) {
		navigationBasedRequestTimer = NavigationBasedRequestTimer(viewerAndTransforms)
	}


	private val requestConfig: RequestConfig by LazyForeignValue({ properties.segmentAnythingConfig.responseTimeout }) { responseTimeout ->
		RequestConfig.custom()
			.setConnectionRequestTimeout(responseTimeout)
			.setSocketTimeout(10 * responseTimeout)
			.setConnectTimeout(responseTimeout)
			.build()
	}
	lateinit var ortEnv: OrtEnvironment

	private val currentSessions = HashSet<String>()

	@OptIn(ExperimentalCoroutinesApi::class)
	val createOrtSessionTask by LazyForeignValue({ properties.segmentAnythingConfig.modelLocation }) { modelLocation ->

		CoroutineScope(Dispatchers.IO).async {
			if (!SamEmbeddingLoaderCache::ortEnv.isInitialized)
				ortEnv = OrtEnvironment.getEnvironment()
			val modelArray = try {
				this::class.java.classLoader.getResourceAsStream(modelLocation)!!.readAllBytes()
			} catch (e: Exception) {
				Files.readAllBytes(Paths.get(modelLocation))
			}
			val session = ortEnv.createSession(modelArray)
			session
		}
	}.beforeValueChange { job ->
		job?.invokeOnCompletion {
			job.getCompleted().close()
		}
		job?.cancel(CancellationException("Ort Model Location Changed"))
	}

	internal fun RenderUnitState.calculateTargetSamScreenScaleFactor(): Double {
		val maxScreenScale = paintera.properties.screenScalesConfig.screenScalesProperty().get().scalesCopy.max()
		return calculateTargetSamScreenScaleFactor(width.toDouble(), height.toDouble(), maxScreenScale)
	}

	/**
	 * Calculates the target screen scale factor based on the highest screen scale and the viewer's dimensions.
	 * The resulting scale factor will always be the smallest of either:
	 *  1. the highest explicitly specified factor, or
	 *  2. [SamPredictor.MAX_DIM_TARGET] / `max(width, height)`
	 *
	 *  This means if the `scaleFactor * maxEdge` is less than [SamPredictor.MAX_DIM_TARGET] it will be used,
	 *  but if the `scaleFactor * maxEdge` is still larger than [SamPredictor.MAX_DIM_TARGET], then a more
	 *  aggressive scale factor will be returned. See [SamPredictor.MAX_DIM_TARGET] for more information.
	 *
	 * @return The calculated scale factor.
	 */
	internal fun calculateTargetSamScreenScaleFactor(width: Double, height: Double, highestScreenScale: Double): Double {
		val maxEdge = max(ceil(width * highestScreenScale), ceil(height * highestScreenScale))
		return min(highestScreenScale, SamPredictor.MAX_DIM_TARGET / maxEdge)
	}

	private fun saveImage(state: RenderUnitState): PipedInputStream {
		val threadGroup = ThreadGroup(this.toString())
		val sharedQueue = SharedQueue(PainteraBaseView.reasonableNumFetcherThreads(), 50)


		val imageRenderer = BaseRenderUnit(
			threadGroup,
			{ state },
			{ Interpolation.NLINEAR },
			CompositeProjectorPreMultiply.CompositeProjectorFactory(paintera.baseView.sourceInfo().composites()),
			sharedQueue,
			30 * 1000000L,
			TaskExecutors.singleThreaded(),
			skipOverlays = true,
			screenScales = doubleArrayOf(state.calculateTargetSamScreenScaleFactor()),
			dimensions = longArrayOf(state.width, state.height),
			useVolatileIfAvailable = false
		)

		val predictionImageInputStream = PipedInputStream()
		val predictionImageOutputStream = PipedOutputStream(predictionImageInputStream)

		imageRenderer.renderedImageProperty.subscribe { _, result ->
			result.image?.let { img ->
				/* jpg is not compatible with images with `alpha` */
				val rgbImage = BufferedImage(
					img.width.toInt(),
					img.height.toInt(),
					BufferedImage.TYPE_INT_RGB
				)
				val encodedImg = SwingFXUtils.fromFXImage(img, rgbImage)
				val written = ImageIO.write(encodedImg, "jpg", predictionImageOutputStream)
				if (!written)
					LOG.warn { "Failed to write prediction image to output stream" }

				predictionImageOutputStream.flush()
				predictionImageOutputStream.close()
				sharedQueue.shutdown()
				imageRenderer.stopRendering()
			}
		}
		imageRenderer.requestRepaint()
		return predictionImageInputStream
	}

	fun request(viewer: ViewerPanelFX, globalToViewerTransform: AffineTransform3D): Deferred<OnnxTensor> {
		return request(viewer.getSamRenderState(globalToViewerTransform))
	}

	fun load(viewer: ViewerPanelFX, globalToViewerTransform: AffineTransform3D, sessionId: String? = null): Job {
		val state = sessionId?.let {
			viewer.getSamRenderState(globalToViewerTransform).withSessionId(it)
		} ?: viewer.getSamRenderState(globalToViewerTransform)
		return load(state)
	}

	fun load(renderUnitState: RenderUnitState, id: String): Job {
		val state = (renderUnitState as? SessionRenderUnitState)?.state?.withSessionId(id) ?: renderUnitState

		val sessionState = state.withSessionId(id)
		return super.load(sessionState)
	}

	override fun load(key: RenderUnitState): Job {
		val sessionState = (key as? SessionRenderUnitState) ?: key.withSessionId(getSessionId())

		synchronized(currentSessions) {
			currentSessions += sessionState.sessionId
		}
		return super.load(sessionState)
	}

	val client: HttpClient = HttpClientBuilder.create()
		.useSystemProperties()
		.setDefaultRequestConfig(requestConfig)
		.setDefaultCookieStore(BasicCookieStore())
		.build()

	private fun requestSessionId(): String {
		val url =
			with(paintera.properties.segmentAnythingConfig) {
				with(SegmentAnythingConfig) {
					"$serviceUrl/$SESSION_ID_REQUEST_ENDPOINT"
				}
			}

		val getSessionId = HttpGet(url)
		val response = client.execute(getSessionId)
		if (response.statusLine.statusCode != HTTP_SUCCESS)
			throw HttpException("Received Error Code: ${response.statusLine.statusCode}")
		return EntityUtils.toString(response.entity!!, Charsets.UTF_8)
	}

	private fun getSessionId(): String {
		return try {
			requestSessionId()
		} catch (e: IOException) {
			e.message ?: "Cannot Get SAM Session ID"
		}
	}

	private fun getImageEmbedding(it: RenderUnitState): OnnxTensor {
		return getImageEmbedding(saveImage(it), (it as? SessionRenderUnitState)?.sessionId)
	}

	private fun cancelPendingRequests(id: String) {
		val url = with(paintera.properties.segmentAnythingConfig) {
			with(SegmentAnythingConfig) {
				"$serviceUrl/$EMBEDDING_REQUEST_ENDPOINT"
			}
		}


		val post = HttpPost(url)
		val client = HttpClientBuilder.create().useSystemProperties().setDefaultRequestConfig(requestConfig).build()
		val entityBuilder = MultipartEntityBuilder.create()
		entityBuilder.addTextBody("session_id", id)
		post.entity = entityBuilder.build()

		client.execute(post)
	}

	fun cancelPendingRequests(vararg ids: String = synchronized(currentSessions) { currentSessions.toTypedArray() }) {
		synchronized(currentSessions) {
			ids
				.filter { currentSessions.remove(it) }
				.forEach {
					runBlocking {
						launch(Dispatchers.IO) {
							cancelPendingRequests(it)
						}
					}
				}
		}
	}

	private fun getImageEmbedding(inputImage: PipedInputStream, sessionId: String? = null): OnnxTensor {
		val entityBuilder = MultipartEntityBuilder.create()
		entityBuilder.addBinaryBody("image", inputImage, ContentType.IMAGE_JPEG, "image.jpg")
		sessionId?.let { id ->
			entityBuilder.addTextBody("session_id", id);
			entityBuilder.addTextBody("cancel_pending", "true");
			synchronized(currentSessions) {
				currentSessions.remove(id)
			}
		}

		val url = with(paintera.properties.segmentAnythingConfig) {
			with(SegmentAnythingConfig) {
				val compress = if (compressEncoding) COMPRESS_ENCODING_PARAMETER else ""
				"$serviceUrl/$EMBEDDING_REQUEST_ENDPOINT?$compress"
			}
		}
		val post = HttpPost(url)
		post.entity = entityBuilder.build()

		val client = HttpClientBuilder.create().useSystemProperties().setDefaultRequestConfig(requestConfig).build()

		val response = client.execute(post)
		when (response.statusLine.statusCode) {
			HTTP_CANCELLED -> {
				throw CancellationException("Cancelled Embedding Request")
			}

			HTTP_SUCCESS -> {

			}

			else -> {
				response.entity?.let {
					throw HttpException(EntityUtils.toString(it))
				} ?: throw HttpException("Received Error Code: ${response.statusLine.statusCode}")
			}
		}
		EntityUtils.toByteArray(response.entity!!).let {
			val decodedEmbedding = Base64.decode(it)
			val directBuffer = ByteBuffer.allocateDirect(decodedEmbedding.size).order(ByteOrder.nativeOrder())
			directBuffer.put(decodedEmbedding, 0, decodedEmbedding.size)
			directBuffer.position(0)
			val floatBuffEmbedding = directBuffer.asFloatBuffer()
			floatBuffEmbedding.position(0)
			runBlocking {
				/* need the ortEnv to be initialized, which is done during session initialization; So block and wait here. */
				/* But we don't actually need the session here. */
				createOrtSessionTask.await()
			}
			return OnnxTensor.createTensor(ortEnv, floatBuffEmbedding, longArrayOf(1, 256, 64, 64))!!
		}
	}

	fun ViewerPanelFX.getSamRenderState(globalToViewerTransform: AffineTransform3D? = null, size: Pair<Long, Long>? = null): RenderUnitState {
		val activeSourceToSkip = paintera.currentSource?.sourceAndConverter?.spimSource
		val sacs = state.sources
			.filterNot { it.spimSource == activeSourceToSkip }
			.map { sac -> getDataSourceAndConverter<Any>(sac) } // to ensure non-volatile
			.toList()
		return RenderUnitState(
			globalToViewerTransform?.copy() ?: AffineTransform3D().also { state.getViewerTransform(it) },
			state.timepoint,
			sacs,
			size?.first ?: width.toLong(),
			size?.second ?: height.toLong()
		)
	}

	private val LOG = KotlinLogging.logger { }
	private const val HTTP_SUCCESS = 200
	private const val HTTP_CANCELLED = 499
}

private data class SessionRenderUnitState(val sessionId: String, val state: RenderUnitState) : RenderUnitState(state.transform, state.timepoint, state.sources, state.width, state.height) {

	override fun equals(other: Any?): Boolean {
		return state == other
	}

	override fun hashCode(): Int {
		return state.hashCode()
	}

	companion object {
		fun RenderUnitState.withSessionId(sessionId: String) = SessionRenderUnitState(sessionId, this)
	}
}

internal class HashableTransform(affineTransform3D: AffineTransform3D) : AffineTransform3D() {

	init {
		set(affineTransform3D)
	}

	override fun hashCode(): Int {
		return HashCodeBuilder()
			.append(floatArrayOf(a.m00.toFloat(), a.m01.toFloat(), a.m02.toFloat(), a.m03.toFloat()))
			.append(floatArrayOf(a.m10.toFloat(), a.m11.toFloat(), a.m12.toFloat(), a.m13.toFloat()))
			.append(floatArrayOf(a.m20.toFloat(), a.m21.toFloat(), a.m22.toFloat(), a.m23.toFloat()))
			.hashCode()
	}

	override fun equals(other: Any?): Boolean {
		return (other as? HashableTransform)?.hashCode() == hashCode()
	}

	internal fun toFloatString(): String {
		return "3d-affine: (${a.m00.toFloat()}, ${a.m01.toFloat()}, ${a.m02.toFloat()}, ${a.m03.toFloat()}, ${a.m10.toFloat()}, ${a.m11.toFloat()}, ${a.m12.toFloat()}, ${a.m13.toFloat()}, ${a.m20.toFloat()}, ${a.m21.toFloat()}, ${a.m22.toFloat()}, ${a.m23.toFloat()})"
	}


	companion object {
		internal fun AffineTransform3D.hashable() = (this as? HashableTransform) ?: HashableTransform(this)
	}
}