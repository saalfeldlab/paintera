package org.janelia.saalfeldlab.fx.ortho;

import bdv.TransformEventHandlerFactory;
import bdv.ui.UIUtils;
import bdv.ui.appearance.AppearanceManager;
import bdv.ui.keymap.KeymapManager;
import bdv.viewer.ViewerOptions;
import bdv.viewer.animate.MessageOverlayAnimator;
import bdv.viewer.render.AccumulateProjectorFactory;
import net.imglib2.type.numeric.ARGBType;
import org.scijava.ui.behaviour.KeyPressedManager;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

public class OrthoViewerOptions extends ViewerOptions {

	{
		setOrthoDefaults(values);
	}

	public OrthoViewerOptions() {

		super();
	}

	@Override public OrthoViewerOptions width(int w) {

		super.width(w);
		return this;
	}

	@Override public OrthoViewerOptions height(int h) {

		super.height(h);
		return this;
	}

	@Override public OrthoViewerOptions screenScales(double[] s) {

		super.screenScales(s);
		return this;
	}

	@Override public OrthoViewerOptions targetRenderNanos(long t) {

		super.targetRenderNanos(t);
		return this;
	}

	@Override public OrthoViewerOptions numRenderingThreads(int n) {

		super.numRenderingThreads(n);
		return this;
	}

	@Override public OrthoViewerOptions numSourceGroups(int n) {

		super.numSourceGroups(n);
		return this;
	}

	@Override public OrthoViewerOptions useVolatileIfAvailable(boolean v) {

		super.useVolatileIfAvailable(v);
		return this;
	}

	@Override public OrthoViewerOptions msgOverlay(MessageOverlayAnimator o) {

		super.msgOverlay(o);
		return this;
	}

	@Override public OrthoViewerOptions transformEventHandlerFactory(TransformEventHandlerFactory f) {

		super.transformEventHandlerFactory(f);
		return this;
	}

	@Override public OrthoViewerOptions is2D(boolean is2D) {

		super.is2D(is2D);
		return this;
	}

	@Override public OrthoViewerOptions accumulateProjectorFactory(AccumulateProjectorFactory<ARGBType> f) {

		super.accumulateProjectorFactory(f);
		return this;
	}

	@Override public OrthoViewerOptions inputTriggerConfig(InputTriggerConfig c) {

		super.inputTriggerConfig(c);
		return this;
	}

	@Override public OrthoViewerOptions shareKeyPressedEvents(KeyPressedManager manager) {

		super.shareKeyPressedEvents(manager);
		return this;
	}

	@Override public OrthoViewerOptions keymapManager(KeymapManager keymapManager) {

		super.keymapManager(keymapManager);
		return this;
	}

	@Override public OrthoViewerOptions appearanceManager(AppearanceManager appearanceManager) {

		super.appearanceManager(appearanceManager);
		return this;
	}

	public static OrthoViewerOptions options() {
		return new OrthoViewerOptions();
	}

	private void setOrthoDefaults(final Values values) {
		/* the main difference so far is that with 3 orthoslice you expect the default to be smaller and square. */
		defaultOthoViewSize();
	}

	private void defaultOthoViewSize() {

		defaultOrthoViewWidth();
		defaultOrthoViewHeight();
	}

	private void defaultOrthoViewHeight() {

		int height = (int)Math.round(400 * UIUtils.getUIScaleFactor(this));
		height(height);
	}

	private void defaultOrthoViewWidth() {

		int width = (int)Math.round(400 * UIUtils.getUIScaleFactor(this));
		width(width);
	}
}
