package org.janelia.saalfeldlab.paintera.ui.opendialog.menu;

import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Pair;
import net.imglib2.loops.LoopBuilder;
import org.janelia.saalfeldlab.fx.MenuFromHandlers;
import org.janelia.saalfeldlab.fx.event.MouseClickFX;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.scijava.annotations.Index;
import org.scijava.annotations.IndexItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class OpenDialogMenu {

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
		synchronized(this.handlers) {
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
				OpenDialogMenu m = new OpenDialogMenu(exceptionHandler);
				ContextMenu cm = m.getContextMenu(menuText, viewer, projectDirectory);
				Bounds bounds = target.localToScreen(target.getBoundsInLocal());
				cm.show(target, x.getAsDouble() + bounds.getMinX(), y.getAsDouble() + bounds.getMinY());
			}
		};

	}

	private static ArrayList<Field> getDeclaredFields(Class<?> clazz) {

		final ArrayList<Field> fields = new ArrayList<>();
		fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
		for (clazz = clazz.getSuperclass(); clazz != null; clazz = clazz.getSuperclass())
			fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
		return fields;
	}

	@SuppressWarnings("unchecked")
	public static void update() {

		constructors.clear();

		final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		final Index<OpenDialogMenuEntry.OpenDialogMenuEntryPath> annotationIndex = Index.load(OpenDialogMenuEntry.OpenDialogMenuEntryPath.class, classLoader);
		for (final IndexItem<OpenDialogMenuEntry.OpenDialogMenuEntryPath> item : annotationIndex) {
			Class<? extends OpenDialogMenuEntry> clazz;
			try {
				clazz = (Class<? extends OpenDialogMenuEntry>)Class.forName(item.className());
				final String type = clazz.getAnnotation(OpenDialogMenuEntry.OpenDialogMenuEntryPath.class).path();

				Constructor<? extends OpenDialogMenuEntry> constructor = clazz.getDeclaredConstructor();
				constructors.put(type, constructor);

			} catch (final ClassNotFoundException | NoSuchMethodException | ClassCastException e) {
				e.printStackTrace();
			}
		}

	}

	public static List<Pair<String, BiConsumer<PainteraBaseView, String>>> getMenuEntries(Consumer<Exception> exceptionHandler)
	{
		try {
			return getMenuEntries();
		} catch (IllegalAccessException | InvocationTargetException | InstantiationException e)
		{
			exceptionHandler.accept(e);
			return new ArrayList<>();
		}
	}


	public static List<Pair<String, BiConsumer<PainteraBaseView, String>>> getMenuEntries() throws IllegalAccessException, InvocationTargetException, InstantiationException {

		List<Pair<String, ? extends OpenDialogMenuEntry>> entries = new ArrayList<>();
		synchronized(constructors)
		{
			if (constructors.size() == 0)
			{
				update();
			}
			for (Map.Entry<String, Constructor<? extends OpenDialogMenuEntry>> e : constructors.entrySet())
			{
				OpenDialogMenuEntry instance = e.getValue().newInstance();
				entries.add(new Pair<>(e.getKey(), instance));
			}
		}

		Collections.sort(entries, (e1, e2) -> {
			OpenDialogMenuEntry v1 = e1.getValue();
			OpenDialogMenuEntry v2 = e2.getValue();
			int intComp = Integer.compare(rank(v1), rank(v2));
			if (intComp == 0)
			{
				String p1 = path(v1);
				String p2 = path(v2);
				LOG.debug("Comparing paths {} {}", p1, p2);
				return p1.compareToIgnoreCase(p2);
			}
			return intComp;
		});
		return entries.stream().map(e -> new Pair<>(e.getKey(), e.getValue().onAction())).collect(Collectors.toList());
	}

	private static int rank(OpenDialogMenuEntry entry)
	{
		final OpenDialogMenuEntry.OpenDialogMenuEntryPath annotation = entry.getClass().getAnnotation(OpenDialogMenuEntry.OpenDialogMenuEntryPath.class);
		if (annotation != null)
			return annotation.rank();
		return Integer.MAX_VALUE;
	}

	private static String path(OpenDialogMenuEntry entry)
	{
		final OpenDialogMenuEntry.OpenDialogMenuEntryPath annotation = entry.getClass().getAnnotation(OpenDialogMenuEntry.OpenDialogMenuEntryPath.class);
		if (annotation != null)
			return annotation.path();
		return null;
	}
}
