package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.config.CrosshairConfig;
import org.janelia.saalfeldlab.paintera.config.NavigationConfig;
import org.janelia.saalfeldlab.paintera.config.OrthoSliceConfigBase;
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig;
import org.janelia.saalfeldlab.paintera.config.Viewer3DConfig;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.janelia.saalfeldlab.util.MakeUnchecked.CheckedConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Properties implements TransformListener<AffineTransform3D>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final String SOURCES_KEY = "sourceInfo";

	private static final String GLOBAL_TRANSFORM_KEY = "globalTransform";

	private static final String WINDOW_PROPERTIES_KEY = "windowProperties";

	private static final String GRID_CONSTRAINTS_KEY = "gridConstraints";

	private static final String CROSSHAIR_CONFIG_KEY = "crosshairConfig";

	private static final String ORTHO_SLICE_CONFIG_KEY = "orthoSliceConfig";

	private static final String NAVIGATION_CONFIG_KEY = "navigationConfig";

	private static final String VIEWER_3D_CONFIG_KEY = "viewer3DConfig";

	private static final String SCREEN_SCALES_CONFIG_KEY = "screenScalesConfig";

	@Expose
	public final SourceInfo sourceInfo;

	@Expose
	public final AffineTransform3D globalTransform = new AffineTransform3D();

	@Expose
	public final WindowProperties windowProperties = new WindowProperties();

	@Expose
	public final GridConstraintsManager gridConstraints;

	@Expose
	public final CrosshairConfig crosshairConfig = new CrosshairConfig();

	@Expose
	public final OrthoSliceConfigBase orthoSliceConfig = new OrthoSliceConfigBase();

	@Expose
	public final NavigationConfig navigationConfig = new NavigationConfig();

	@Expose
	public final Viewer3DConfig viewer3DConfig = new Viewer3DConfig();

	@Expose
	public final ScreenScalesConfig screenScalesConfig = new ScreenScalesConfig();

	private transient final BooleanProperty transformDirty = new SimpleBooleanProperty(false);

	public transient final ObservableBooleanValue isDirty;

	public Properties(
			final PainteraBaseView viewer,
			final GridConstraintsManager gridConstraints)
	{
		this(viewer.sourceInfo(), gridConstraints);
		viewer.manager().addListener(this);
	}

	public Properties(
			final SourceInfo sources,
			final GridConstraintsManager gridConstraints)
	{
		super();
		this.sourceInfo = sources;
		this.gridConstraints = gridConstraints;
		this.isDirty = transformDirty.or(windowProperties.hasChanged).or(sources.isDirtyProperty());
	}

	@Override
	public void transformChanged(final AffineTransform3D transform)
	{
		globalTransform.set(transform);
		transformDirty.set(true);
	}

	public AffineTransform3D globalTransformCopy()
	{
		return this.globalTransform.copy();
	}

	public void setGlobalTransformClean()
	{
		this.transformDirty.set(true);
	}

	public boolean isDirty()
	{
		return isDirty.get();
	}

	public void clean()
	{
		sourceInfo.clean();
		setGlobalTransformClean();
		windowProperties.clean();
	}

	public static Properties fromSerializedProperties(
			final JsonObject serializedProperties,
			final PainteraBaseView viewer,
			final boolean removeExistingSources,
			final Supplier<String> projectDirectory,
			final Map<Integer, SourceState<?, ?>> indexToState,
			final GridConstraintsManager manager)
	{
		final Arguments arguments = new StatefulSerializer.Arguments(viewer);
		return fromSerializedProperties(
				serializedProperties,
				viewer,
				removeExistingSources,
				indexToState,
				manager,
				GsonHelpers.builderWithAllRequiredDeserializers(arguments, projectDirectory, indexToState::get)
						.create()
		                               );
	}

	public static Properties fromSerializedProperties(
			final JsonObject serializedProperties,
			final PainteraBaseView viewer,
			final boolean removeExistingSources,
			final Map<Integer, SourceState<?, ?>> indexToState,
			final GridConstraintsManager gridConstraints,
			final Gson gson)
	{

		LOG.debug("Populating with {}", serializedProperties);

		final Properties properties = new Properties(
				viewer,
				gridConstraints
		);
		final GridConstraintsManager deserializedGridConstraints = Optional
				.ofNullable(serializedProperties.get(GRID_CONSTRAINTS_KEY))
				.map(json -> gson.fromJson(json, GridConstraintsManager.class))
				.orElse(gridConstraints);

		Optional
				.ofNullable(serializedProperties.get(CROSSHAIR_CONFIG_KEY))
				.map(json -> gson.fromJson(json, CrosshairConfig.class))
				.ifPresent(conf -> {
					properties.crosshairConfig.setOnFocusColor(conf.getOnFocusColor());
					properties.crosshairConfig.setOutOfFocusColor(conf.getOutOfFocusColor());
					properties.crosshairConfig.setShowCrosshairs(conf.getShowCrosshairs());
				});

		Optional
				.ofNullable(serializedProperties.get(ORTHO_SLICE_CONFIG_KEY))
				.map(json -> gson.fromJson(json, OrthoSliceConfigBase.class))
				.ifPresent(conf -> {
					properties.orthoSliceConfig.isEnabledProperty().set(conf.isEnabledProperty().get());
					properties.orthoSliceConfig.showTopLeftProperty().set(conf.showTopLeftProperty().get());
					properties.orthoSliceConfig.showTopRightProperty().set(conf.showTopRightProperty().get());
					properties.orthoSliceConfig.showBottomLeftProperty().set(conf.showBottomLeftProperty().get());
					properties.orthoSliceConfig.delayInNanoSeconds().set(conf.delayInNanoSeconds().get());
				});
		Optional
				.ofNullable(serializedProperties.get(NAVIGATION_CONFIG_KEY))
				.map(json -> gson.fromJson(json, NavigationConfig.class))
				.ifPresent(properties.navigationConfig::set);
		Optional
				.ofNullable(serializedProperties.get(VIEWER_3D_CONFIG_KEY))
				.map(json -> gson.fromJson(json, Viewer3DConfig.class))
				.ifPresent(properties.viewer3DConfig::set);

		Optional
				.ofNullable(serializedProperties.get(SCREEN_SCALES_CONFIG_KEY))
				.map(json -> gson.fromJson(json, ScreenScalesConfig.class))
				.ifPresent(properties.screenScalesConfig::set);

		gridConstraints.set(deserializedGridConstraints);

		if (removeExistingSources)
		{
			properties.sourceInfo.removeAllSources();
		}

		Optional
				.ofNullable(serializedProperties.get(SOURCES_KEY))
				.ifPresent(MakeUnchecked.unchecked((CheckedConsumer<JsonElement>) element -> SourceInfoSerializer
						.populate(
						viewer::addState,
						properties.sourceInfo.currentSourceIndexProperty()::set,
						element.getAsJsonObject(),
						indexToState::put,
						gson
				                                                                                                          )));

		LOG.debug("De-serializing global transform {}", serializedProperties.get(GLOBAL_TRANSFORM_KEY));
		Optional
				.ofNullable(serializedProperties.get(GLOBAL_TRANSFORM_KEY))
				.map(element -> gson.fromJson(element, AffineTransform3D.class))
				.ifPresent(viewer.manager()::setTransform);

		properties.clean();

		return properties;
	}

}
