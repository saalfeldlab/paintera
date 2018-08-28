package org.janelia.saalfeldlab.paintera.ui.opendialog.menu;

import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
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
import java.util.function.Predicate;

public class OpenDialogMenu {

	private final List<Pair<String, BiConsumer<PainteraBaseView, String>>> handlers;

	private static final HashMap<String, Constructor<? extends OpenDialogMenuEntry>> constructors = new HashMap<>();

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
			Consumer<Exception> exceptionHandler,
			Predicate<KeyEvent> check,
			final String menuText,
			final PainteraBaseView viewer,
			final String projectDirectory)
	{

		return event -> {
			if (check.test(event))
			{
				event.consume();
				OpenDialogMenu m = new OpenDialogMenu(exceptionHandler);
				ContextMenu cm = m.getContextMenu(menuText, viewer, projectDirectory);
				cm.show(viewer.pane().getScene().getWindow());
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
		synchronized(constructors)
		{
			if (constructors.size() == 0)
			{
				update();
			}
		}
		List<Pair<String, BiConsumer<PainteraBaseView, String>>> handlers = new ArrayList<>();
		for (Map.Entry<String, Constructor<? extends OpenDialogMenuEntry>> e : constructors.entrySet())
		{
			OpenDialogMenuEntry instance = e.getValue().newInstance();
			handlers.add(new Pair<>(e.getKey(), instance.onAction()));
		}
		return handlers;
	}

	@OpenDialogMenuEntry.OpenDialogMenuEntryPath(path="sort>of>ok")
	public static class DummyOpenDialogMenuEntry implements OpenDialogMenuEntry
	{

		@Override
		public BiConsumer<PainteraBaseView, String> onAction() {
			return (pbv, pd) -> System.out.println("LOL DUMMY!");
		}
	}



	public static void main(String[] args)
	{
		PlatformImpl.startup(() -> {});

		OpenDialogMenu odm = new OpenDialogMenu(Exception::printStackTrace);
		ContextMenu menu = odm.getContextMenu("SOME MENU!", null, null);


		Platform.runLater(() -> {
			Stage stage = new Stage();
			StackPane root = new StackPane();
			root.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> menu.show(root, e.getScreenX(), e.getScreenY()));
			Scene scene = new Scene(root, 800, 600);
			stage.setScene(scene);
			stage.show();
		});


	}
}
