package org.janelia.saalfeldlab.fx;

import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.Consumer;

public class MenuFromHandlers {

	private final List<Pair<String, Consumer<ActionEvent>>> entries = new ArrayList<>();

	private static final String MENU_SPLIT = ">";

	public MenuFromHandlers()
	{
		this(new ArrayList<>());
	}

	public MenuFromHandlers(Collection<Pair<String, Consumer<ActionEvent>>> entries)
	{
		this.entries.addAll(entries);
	}

	private Set<MenuPath> subMenus()
	{
		Set<MenuPath> subMenus = new HashSet<>();
		for (Pair<String, Consumer<ActionEvent>> entry : entries)
		{
			String[] elements = entry.getKey().split(MENU_SPLIT);
			if (elements.length > 0)
			{
				String[] parent = new String[elements.length - 1];
				System.arraycopy(elements, 0, parent, 0, parent.length);
				subMenus.add(new MenuPath(parent));
			}
		}
		return subMenus;
	}

	public ContextMenu asContextMenu(final String menuText)
	{
		final ContextMenu menu = new ContextMenu();
		if (menuText != null)
			menu.getItems().addAll(Menus.disabledItem(menuText), new SeparatorMenuItem());

		Set<MenuPath> parentPaths = subMenus();

		final Map<MenuPath, Menu> parentElements = new HashMap<>();

		for (Pair<String, Consumer<ActionEvent>> entry : entries)
		{
			final MenuPath elementPath = new MenuPath(entry.getKey().split(MENU_SPLIT));
			final MenuPath parentPath = elementPath.parent();
			MenuItem mi = new MenuItem(elementPath.elements[elementPath.elements.length - 1]);
			mi.setOnAction(entry.getValue()::accept);
			if (parentPath.elements.length == 0)
			{
				menu.getItems().add(mi);
			}
			else {
				final Stack<MenuPath> toCreate = new Stack<>();
				for (MenuPath p = elementPath.parent(); p.elements.length > 0; p = p.parent())
				{
					toCreate.add(p);
				}
				while (!toCreate.empty())
				{
					MenuPath p = toCreate.pop();
					Menu path = parentElements.get(p);
					if (path == null)
					{
						Menu m = new Menu(p.elements[p.elements.length - 1]);
						parentElements.put(p, m);
						if (p.elements.length == 1)
							menu.getItems().add(m);
						else
							parentElements.get(p.parent()).getItems().add(m);
					}
				}
				parentElements.get(parentPath).getItems().add(mi);
			}

		}

		return menu;
	}

	public static class MenuEntryConflict extends Exception
	{

		public MenuEntryConflict(final String message)
		{
			super(message);
		}

	}

	private static class MenuPath
	{
		private final String[] elements;


		private MenuPath(String[] elements) {
			this.elements = elements;
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof MenuPath && Arrays.equals(((MenuPath)o).elements, elements);
		}

		@Override
		public int hashCode()
		{
			return Arrays.hashCode(elements);
		}

		public MenuPath parent()
		{
			String[] parent = new String[elements.length - 1];
			System.arraycopy(elements, 0, parent, 0, parent.length);
			return new MenuPath(parent);
		}

	}

	public static void main(String[] args)
	{
		PlatformImpl.startup(() -> {});

		ContextMenu menu = new MenuFromHandlers(Arrays.asList(
				new Pair<>("bla>3>4", e -> System.out.println("bla 3 4")),
				new Pair<>("bla>3", e -> System.out.println("bla 3")),
				new Pair<>("tl", e -> System.out.println("top level"))
		)).asContextMenu("MENU");


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
