package org.janelia.saalfeldlab.paintera.state;

import bdv.fx.viewer.ViewerPanelFX;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.scene.Cursor;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import kotlin.jvm.functions.Function1;
import net.imglib2.type.label.Label;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.saalfeldlab.fx.Tasks;
import org.janelia.saalfeldlab.fx.actions.ActionSet;
import org.janelia.saalfeldlab.fx.actions.NamedKeyCombination;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.control.IdSelector;
import org.janelia.saalfeldlab.paintera.control.actions.LabelActionType;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegments;
import org.janelia.saalfeldlab.paintera.control.modes.NavigationTool;
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

import static javafx.scene.input.KeyEvent.KEY_PRESSED;
import static org.janelia.saalfeldlab.fx.actions.PainteraActionSetKt.painteraActionSet;
import static org.janelia.saalfeldlab.fx.actions.PainteraActionSetKt.verifyPainteraNotDisabled;

public class LabelSourceStateIdSelectorHandler {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final LongPredicate FOREGROUND_CHECK = Label::isForeground;

	private static final ExecutorService selectorService = Executors.newSingleThreadExecutor(
			new ThreadFactoryBuilder().setNameFormat("id selector thread").build());

	private final DataSource<? extends IntegerType<?>, ?> source;

	private final IdService idService;

	private final SelectedIds selectedIds;

	private final FragmentSegmentAssignment assignment;

	private final LockedSegments lockedSegments;

	private final Runnable refreshMeshes;

	private Task<?> selectAllTask;

	private Future<?> selectAllFuture;

	/**
	 * @param source         that contains the labels to select
	 * @param idService      provides the next ID to select
	 * @param selectedIds    store the selected IDs
	 * @param assignment     used to lock fragments and segments
	 * @param lockedSegments track the locked segments
	 * @param refreshMeshes  after canceling a selection task
	 */
	public LabelSourceStateIdSelectorHandler(
			final DataSource<? extends IntegerType<?>, ?> source,
			final IdService idService,
			final SelectedIds selectedIds,
			final FragmentSegmentAssignment assignment,
			final LockedSegments lockedSegments,
			final Runnable refreshMeshes) {

		this.source = source;
		this.idService = idService;
		this.selectedIds = selectedIds;
		this.assignment = assignment;
		this.lockedSegments = lockedSegments;
		this.refreshMeshes = refreshMeshes;
	}

	public List<ActionSet> makeActionSets(KeyTracker keyTracker, Supplier<ViewerPanelFX> getActiveViewer) {

		final IdSelector selector = new IdSelector(source, selectedIds, getActiveViewer, FOREGROUND_CHECK);

		final var toggleLabelActions = painteraActionSet("toggle single id", LabelActionType.Toggle, actionSet -> {
			final var selectMaxCount = selector.selectFragmentWithMaximumCountAction();
			/* May need to revist this constraint; for now, ONLY allow selection of labels when the active tool is the NavigationTool */
			selectMaxCount.verify(activeToolIsNavigationTool());
			selectMaxCount.verify(event -> keyTracker.areOnlyTheseKeysDown(KeyCode.ALT) || keyTracker.noKeysActive());
			selectMaxCount.verify(mouseEvent -> !Paintera.getPaintera().getMouseTracker().isDragging());
			selectMaxCount.verifyButtonTrigger(MouseButton.PRIMARY);
			actionSet.addAction(selectMaxCount);
		});

		final var appendLabelActions = painteraActionSet("append id", LabelActionType.Append, actionSet -> {
			final var appendMaxCount = selector.appendFragmentWithMaximumCountAction();
			appendMaxCount.verify(activeToolIsNavigationTool());
			appendMaxCount.verify(mouseEvent -> !Paintera.getPaintera().getMouseTracker().isDragging());
			appendMaxCount.verify(mouseEvent -> {
				final var button = mouseEvent.getButton();
				final var leftClickTrigger = button == MouseButton.PRIMARY && keyTracker.areOnlyTheseKeysDown(KeyCode.CONTROL);
				final var rightClickTrigger = button == MouseButton.SECONDARY && keyTracker.noKeysActive();
				return leftClickTrigger || rightClickTrigger;
			});
			actionSet.addAction(appendMaxCount);
		});

		final var selectAllActions = painteraActionSet("Select All", LabelActionType.SelectAll, true, actionSet -> {
			actionSet.addKeyAction(KEY_PRESSED, keyAction -> {
				verifyPainteraNotDisabled(keyAction);
				keyAction.verify(activeToolIsNavigationTool());
				keyAction.keyMatchesBinding(LabelSourceStateKeys.SELECT_ALL);
				keyAction.verify(event -> selectAllTask == null);
				keyAction.onAction(keyEvent -> {
					final var selectTask = Tasks.createTask(task -> {
								Paintera.getPaintera().getBaseView().getNode().getScene().setCursor(Cursor.WAIT);
								selectAllTask = task;
								selector.selectAll();
							})
							.onEnd(task -> {
								selectAllTask = null;
								Paintera.getPaintera().getBaseView().getNode().getScene().setCursor(Cursor.DEFAULT);
							});
					selectAllFuture = selectorService.submit(selectTask);
				});
			});
			actionSet.addKeyAction(KEY_PRESSED, keyAction -> {
				verifyPainteraNotDisabled(keyAction);
				keyAction.verify(activeToolIsNavigationTool());
				keyAction.keyMatchesBinding(LabelSourceStateKeys.SELECT_ALL_IN_CURRENT_VIEW);
				keyAction.verify(event -> selectAllTask == null);
				keyAction.verify(event -> getActiveViewer.get() != null);
				keyAction.onAction(keyEvent -> {
					final var selectTask = Tasks.createTask(task -> {
								Paintera.getPaintera().getBaseView().getNode().getScene().setCursor(Cursor.WAIT);
								selectAllTask = task;
								selector.selectAllInCurrentView(getActiveViewer.get());
							})
							.onEnd(objectUtilityTask -> {
								selectAllTask = null;
								Paintera.getPaintera().getBaseView().getNode().getScene().setCursor(Cursor.DEFAULT);
							});
					selectAllFuture = selectorService.submit(selectTask);
				});
			});
			actionSet.addKeyAction(KEY_PRESSED, keyAction -> {
				keyAction.verify(activeToolIsNavigationTool());
				keyAction.setName("Cancel Select All");
				keyAction.keysDown(KeyCode.ESCAPE);
				keyAction.verify(keyEvent -> selectAllTask != null);
				keyAction.onAction(keyEvent -> {
					selectAllTask.cancel();
					selectAllFuture.cancel(true);
					refreshMeshes.run();
					selectedIds.deactivateAll();
				});
			});
		});
		final var lockSegmentActions = painteraActionSet("Toggle Segment Lock", LabelActionType.Lock, actionSet -> {
			actionSet.addKeyAction(KEY_PRESSED, keyAction -> {
				keyAction.keyMatchesBinding(LabelSourceStateKeys.LOCK_SEGMENT);
				keyAction.onAction(keyEvent -> selector.toggleLock(assignment, lockedSegments));
			});
		});

		return List.of(toggleLabelActions, appendLabelActions, selectAllActions, lockSegmentActions);
	}

	@NotNull private Function1<? super Event, Boolean> activeToolIsNavigationTool() {

		return event -> {
			final var activeMode = Paintera.getPaintera().getBaseView().getActiveModeProperty().getValue();
			return activeMode instanceof ToolMode && ((ToolMode)activeMode).getActiveTool() instanceof NavigationTool;
		};
	}

	public long nextId() {

		return nextId(true);
	}

	public long nextId(boolean activate) {

		final long next = idService.next();
		if (activate) {
			selectedIds.activate(next);
		}
		return next;
	}
}
