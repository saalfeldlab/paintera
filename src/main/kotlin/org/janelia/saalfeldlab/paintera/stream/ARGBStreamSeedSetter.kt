package org.janelia.saalfeldlab.paintera.stream

import javafx.event.EventHandler
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyEvent
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.function.Supplier

class ARGBStreamSeedSetter(private val stream: AbstractHighlightingARGBStream) {

    fun incrementHandler(keyBinding: Supplier<KeyCombination>) = EventHandler<KeyEvent> {
        if (keyBinding.get().match(it)) {
            it.consume()
            incrementStreamSeed()
        }
    }

    fun decrementHandler(keyBinding: Supplier<KeyCombination>) = EventHandler<KeyEvent> {
        if (keyBinding.get().match(it)) {
            it.consume()
            decrementStreamSeed()
        }
    }

    @JvmOverloads
    fun incrementStreamSeed(increment: Long = 1L) = changeStreamSeed { it + increment }

    @JvmOverloads
    fun decrementStreamSeed(decrement: Long = 1L) = incrementStreamSeed(-decrement)

    private fun changeStreamSeed(seedUpdate: (Long) -> Long): Boolean {
        val seed = stream.seed
        val currentSeed = seedUpdate(seed)
        if (currentSeed != seed) {
            LOG.debug("Updating seed from {} to {}", seed, currentSeed)
            stream.setSeed(currentSeed)
            stream.clearCache()
            return true
        }
        return false
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

}
