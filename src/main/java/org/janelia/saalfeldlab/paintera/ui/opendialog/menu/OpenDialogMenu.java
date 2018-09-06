package org.janelia.saalfeldlab.paintera.ui.opendialog.menu;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.KeyEvent;
import javafx.util.Pair;
import org.janelia.saalfeldlab.fx.MenuFromHandlers;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.scijava.Context;
import org.scijava.InstantiableException;
import org.scijava.log.LogService;
import org.scijava.plugin.PluginInfo;
import org.scijava.plugin.PluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenDialogMenu
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final Map<String, Constructor<? extends OpenDialogMenuEntry>> constructors = new HashMap<>();

	private final List<Pair<String, BiConsumer<PainteraBaseView, String>>> handlers;

	public OpenDialogMenu(Consumer<Exception> exceptionHandler)
	{
		this.handlers = getMenuEntries(exceptionHandler);
	}

	public ContextMenu getContextMenu(
			String menuText,
			PainteraBaseView viewer,
			String projectDirectory)
	{
		List<Pair<String, Consumer<ActionEvent>>> asConsumers = new ArrayList<>();
		synchronized (this.handlers)
		{
			for (Pair<String, BiConsumer<PainteraBaseView, String>> handler : handlers)
			{
				Consumer<ActionEvent> consumer = event -> handler.getValue().accept(viewer, projectDirectory);
				asConsumers.add(new Pair<>(handler.getKey(), consumer));
			}
		}
		return new MenuFromHandlers(asConsumers).asContextMenu(menuText);
	}

	public static EventHandler<KeyEvent> keyPressedHandler(
			final Node target,
			Consumer<Exception> exceptionHandler,
			Predicate<KeyEvent> check,
			final String menuText,
			final PainteraBaseView viewer,
			final String projectDirectory,
			final DoubleSupplier x,
			final DoubleSupplier y)
	{

		return event -> {
			if (check.test(event))
			{
				event.consume();
				OpenDialogMenu m      = new OpenDialogMenu(exceptionHandler);
				ContextMenu    cm     = m.getContextMenu(menuText, viewer, projectDirectory);
				Bounds         bounds = target.localToScreen(target.getBoundsInLocal());
				cm.show(target, x.getAsDouble() + bounds.getMinX(), y.getAsDouble() + bounds.getMinY());
			}
		};

	}

	public static List<Pair<String, BiConsumer<PainteraBaseView, String>>> getMenuEntries(Consumer<Exception> exceptionHandler)
	{
		try
		{
			return getMenuEntries();
		} catch (InstantiableException e)
		{
			exceptionHandler.accept(e);
			return new ArrayList<>();
		}
	}


	public static List<Pair<String, BiConsumer<PainteraBaseView, String>>> getMenuEntries()
	throws InstantiableException
	{

		final Context context = new Context(PluginService.class);

		final PluginService                         pluginService = context.getService(PluginService.class);
		final List<PluginInfo<OpenDialogMenuEntry>> infos         =
				pluginService.getPluginsOfType(OpenDialogMenuEntry.class);
		Collections.sort(infos, (i1, i2) -> {
			int rankComparison = -Double.compare(i1.getPriority(), i2.getPriority());
			LOG.trace("rank comparison for {} {} is {}", i1.getPriority(), i2.getPriority(), rankComparison);
			if (rankComparison == 0)
			{
				return i1.getAnnotation().menuPath().compareToIgnoreCase(i2.getAnnotation().menuPath());
			}
			return rankComparison;
		});

		List<Pair<String, BiConsumer<PainteraBaseView, String>>> menuEntries = new ArrayList<>();
		for (PluginInfo<OpenDialogMenuEntry> info : infos)
			menuEntries.add(new Pair<>(info.getAnnotation().menuPath(), info.createInstance().onAction()));
		return menuEntries;
	}

}
