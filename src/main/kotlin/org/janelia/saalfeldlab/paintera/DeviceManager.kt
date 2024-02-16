package org.janelia.saalfeldlab.paintera

import org.janelia.saalfeldlab.control.mcu.MCUControlPanel
import org.janelia.saalfeldlab.control.mcu.XTouchMiniMCUControlPanel
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import javax.sound.midi.MidiUnavailableException

object DeviceManager {

	private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

	private val activeMidiDevices = mutableListOf<MCUControlPanel>()

	var xTouchMini: MCUControlPanel? = loadXTouchMini()
		private set

	private fun loadXTouchMini() = loadMidiDevice { XTouchMiniMCUControlPanel.build().also { activeMidiDevices += it } }

	private fun loadMidiDevice(loadDevice: () -> MCUControlPanel?): MCUControlPanel? {
		return try {
			loadDevice()
		} catch (_: MidiUnavailableException) {
			null
		}
	}

	private fun closeMidiDevices() {
		activeMidiDevices.removeIf {
			try {
				it.close()
				true
			} catch (e: Exception) {
				LOG.error("Unable to close device $it")
				false
			}
		}
	}

	fun closeDevices(): Boolean =
		if (activeMidiDevices.isNotEmpty()) {
			closeMidiDevices()
			true
		} else false
}