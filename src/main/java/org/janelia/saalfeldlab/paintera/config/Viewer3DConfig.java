package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class Viewer3DConfig {

  public static final int RENDERER_BLOCK_SIZE_MIN_VALUE = 8;
  public static final int RENDERER_BLOCK_SIZE_MAX_VALUE = 64;
  public static final int RENDERER_BLOCK_SIZE_DEFAULT_VALUE = 16;

  public static final int NUM_ELEMENTS_PER_FRAME_MIN_VALUE = 1000;
  public static final int NUM_ELEMENTS_PER_FRAME_MAX_VALUE = 50000;
  public static final int NUM_ELEMENTS_PER_FRAME_DEFAULT_VALUE = 10000;

  public static final long FRAME_DELAY_MSEC_MIN_VALUE = 0;
  public static final long FRAME_DELAY_MSEC_MAX_VALUE = 100;
  public static final long FRAME_DELAY_MSEC_DEFAULT_VALUE = 20;

  public static final long SCENE_UPDATE_DELAY_MSEC_MIN_VALUE = 100;
  public static final long SCENE_UPDATE_DELAY_MSEC_MAX_VALUE = 1000;
  public static final long SCENE_UPDATE_DELAY_MSEC_DEFAULT_VALUE = 250;

  // TODO the Viewer3DFX and handler should probably hold an instance of this

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final SimpleBooleanProperty areMeshesEnabled = new SimpleBooleanProperty(true);

  private final SimpleBooleanProperty showBlockBoundaries = new SimpleBooleanProperty(false);

  private final SimpleIntegerProperty rendererBlockSize = new SimpleIntegerProperty(RENDERER_BLOCK_SIZE_DEFAULT_VALUE);

  private final SimpleIntegerProperty numElementsPerFrame = new SimpleIntegerProperty(NUM_ELEMENTS_PER_FRAME_DEFAULT_VALUE);

  private final SimpleLongProperty frameDelayMsec = new SimpleLongProperty(FRAME_DELAY_MSEC_DEFAULT_VALUE);

  private final SimpleLongProperty sceneUpdateDelayMsec = new SimpleLongProperty(SCENE_UPDATE_DELAY_MSEC_DEFAULT_VALUE);

  private final SimpleObjectProperty<Color> backgroundColor = new SimpleObjectProperty<>(Color.BLACK);

  private final Affine affine = new Affine();

  // TODO this is only necessary while projects without serialized transform exist
  private boolean wasAffineSet = false;

  public BooleanProperty areMeshesEnabledProperty() {

	return this.areMeshesEnabled;
  }

  public BooleanProperty showBlockBoundariesProperty() {

	return this.showBlockBoundaries;
  }

  public IntegerProperty rendererBlockSizeProperty() {

	return this.rendererBlockSize;
  }

  public IntegerProperty numElementsPerFrameProperty() {

	return this.numElementsPerFrame;
  }

  public LongProperty frameDelayMsecProperty() {

	return this.frameDelayMsec;
  }

  public LongProperty sceneUpdateDelayMsecProperty() {

	return this.sceneUpdateDelayMsec;
  }

  public SimpleObjectProperty<Color> backgroundColorProperty() {

	return backgroundColor;
  }

  public void bindViewerToConfig(final Viewer3DFX viewer) {

	viewer.getMeshesEnabled().bind(this.areMeshesEnabled);
	viewer.getShowBlockBoundaries().bind(this.showBlockBoundaries);
	viewer.getRendererBlockSize().bind(this.rendererBlockSize);
	viewer.getNumElementsPerFrame().bind(this.numElementsPerFrame);
	viewer.getFrameDelayMsec().bind(this.frameDelayMsec);
	viewer.getSceneUpdateDelayMsec().bind(this.sceneUpdateDelayMsec);

	final Affine affineCopy = this.affine.clone();
	final boolean wasAffineSet = this.wasAffineSet;
	viewer.addAffineListener(this::setAffine);
	viewer.backgroundFillProperty().bindBidirectional(this.backgroundColor);
	if (wasAffineSet) {
	  LOG.debug("Setting viewer affine to {}", affineCopy);
	  viewer.setAffine(affineCopy);
	}
  }

  public boolean isWasAffineSet() {

	return wasAffineSet;
  }

  public void set(final Viewer3DConfig that) {

	this.areMeshesEnabled.set(that.areMeshesEnabled.get());
	this.showBlockBoundaries.set(that.showBlockBoundaries.get());
	this.rendererBlockSize.set(that.rendererBlockSize.get());
	this.numElementsPerFrame.set(that.numElementsPerFrame.get());
	this.frameDelayMsec.set(that.frameDelayMsec.get());
	this.sceneUpdateDelayMsec.set(that.sceneUpdateDelayMsec.get());
	this.backgroundColor.set(that.backgroundColor.get());
	if (that.wasAffineSet)
	  setAffine(that.affine);
  }

  public void setAffine(final Affine affine) {

	LOG.trace("Set affine {} to {}", this.affine, affine);
	this.affine.setToTransform(affine);
	this.wasAffineSet = true;
  }

  public Affine getAffineCopy() {

	return this.affine.clone();
  }

}
