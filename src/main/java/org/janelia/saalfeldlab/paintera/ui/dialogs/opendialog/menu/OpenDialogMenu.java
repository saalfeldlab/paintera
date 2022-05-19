package org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.menu;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Pair;
import org.janelia.saalfeldlab.fx.MenuFromHandlers;
import org.janelia.saalfeldlab.fx.actions.ActionSet;
import org.janelia.saalfeldlab.fx.actions.PainteraActionSet;
import org.janelia.saalfeldlab.fx.event.MouseTracker;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.PainteraGateway;
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType;
import org.scijava.InstantiableException;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.PluginInfo;
import org.scijava.plugin.PluginService;
import org.scijava.service.AbstractService;
import org.scijava.service.SciJavaService;
import org.scijava.service.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Plugin(type = Service.class)
public class OpenDialogMenu extends AbstractService implements SciJavaService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final Map<String, Constructor<? extends OpenDialogMenuEntry>> constructors = new HashMap<>();

  @Parameter
  private PluginService pluginService;

  private List<Pair<String, BiConsumer<PainteraBaseView, Supplier<String>>>> handlers;

  public OpenDialogMenu() {

	super();
  }

  public Optional<Menu> getMenu(
		  String menuText,
		  PainteraBaseView viewer,
		  Supplier<String> projectDirectory,
		  Consumer<Exception> exceptionHandler) {

	try {
	  return Optional.of(getMenu(menuText, viewer, projectDirectory));
	} catch (InstantiableException e) {
	  exceptionHandler.accept(e);
	  return Optional.empty();
	}
  }

  public Menu getMenu(
		  final String menuText,
		  final PainteraBaseView viewer,
		  final Supplier<String> projectDirectory) throws InstantiableException {

	List<Pair<String, Consumer<ActionEvent>>> asConsumers = new ArrayList<>();
	final List<Pair<String, BiConsumer<PainteraBaseView, Supplier<String>>>> handlers = new ArrayList<>(getMenuEntries());
	for (Pair<String, BiConsumer<PainteraBaseView, Supplier<String>>> handler : handlers) {
	  Consumer<ActionEvent> consumer = event -> handler.getValue().accept(viewer, projectDirectory);
	  asConsumers.add(new Pair<>(handler.getKey(), consumer));
	}
	return new MenuFromHandlers(asConsumers).asMenu(menuText);
  }

  public Optional<ContextMenu> getContextMenu(
		  String menuText,
		  PainteraBaseView viewer,
		  Supplier<String> projectDirectory,
		  Consumer<Exception> exceptionHandler) {

	try {
	  return Optional.of(getContextMenu(menuText, viewer, projectDirectory));
	} catch (InstantiableException e) {
	  exceptionHandler.accept(e);
	  return Optional.empty();
	}
  }

  public ContextMenu getContextMenu(
		  String menuText,
		  PainteraBaseView viewer,
		  Supplier<String> projectDirectory) throws InstantiableException {

	List<Pair<String, Consumer<ActionEvent>>> asConsumers = new ArrayList<>();
	final List<Pair<String, BiConsumer<PainteraBaseView, Supplier<String>>>> handlers = new ArrayList<>(getMenuEntries());
	for (Pair<String, BiConsumer<PainteraBaseView, Supplier<String>>> handler : handlers) {
	  Consumer<ActionEvent> consumer = event -> handler.getValue().accept(viewer, projectDirectory);
	  asConsumers.add(new Pair<>(handler.getKey(), consumer));
	}
	return new MenuFromHandlers(asConsumers).asContextMenu(menuText);
  }

  public static ActionSet keyPressedAction(
		  final PainteraGateway gateway,
		  final Node target,
		  Consumer<Exception> exceptionHandler,
		  final PainteraBaseView viewer,
		  final Supplier<String> projectDirectory,
		  final MouseTracker mouseTracker,
		  final KeyCode... keys) {

	final var menuText = "Open dataset";
	return new PainteraActionSet(menuText, MenuActionType.AddSource, actionSet -> {
	  actionSet.addKeyAction(KeyEvent.KEY_PRESSED, keyAction -> {
		keyAction.keysDown(keys);
		keyAction.onAction(event -> {
		  var m = gateway.openDialogMenu();
		  var cm = m.getContextMenu(menuText, viewer, projectDirectory, exceptionHandler);
		  var bounds = target.localToScreen(target.getBoundsInLocal());
		  var mouseX = mouseTracker.getX();
		  var mouseY = mouseTracker.getX();
		  cm.ifPresent(menu -> menu.show(target, mouseX + bounds.getMinX(), mouseY + bounds.getMinY()));
		});
	  });
	});
  }

  private synchronized List<Pair<String, BiConsumer<PainteraBaseView, Supplier<String>>>> getMenuEntries()
		  throws InstantiableException {

	if (this.handlers == null) {
	  final List<PluginInfo<OpenDialogMenuEntry>> infos = pluginService.getPluginsOfType(OpenDialogMenuEntry.class);
	  infos.sort((i1, i2) -> {
		int rankComparison = -Double.compare(i1.getPriority(), i2.getPriority());
		LOG.trace("rank comparison for {} {} is {}", i1.getPriority(), i2.getPriority(), rankComparison);
		if (rankComparison == 0) {
		  return i1.getAnnotation().menuPath().compareToIgnoreCase(i2.getAnnotation().menuPath());
		}
		return rankComparison;
	  });

	  this.handlers = new ArrayList<>();
	  for (PluginInfo<OpenDialogMenuEntry> info : infos) {
		this.handlers.add(new Pair<>(info.getAnnotation().menuPath(), info.createInstance().onAction()));
	  }
	}
	return this.handlers;
  }

}
