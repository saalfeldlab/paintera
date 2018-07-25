package org.janelia.saalfeldlab.paintera.ui;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.viewer.Source;
import javafx.event.EventHandler;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Spinner;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.ortho.OnEnterOnExit;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;

public class ARGBStreamSeedSetter
{

	private final HashMap<ViewerPanelFX, EventHandler<KeyEvent>> handlers = new HashMap<>();

	private final SourceInfo sourceInfo;

	private final KeyTracker keyTracker;

	public ARGBStreamSeedSetter(final SourceInfo sourceInfo, final KeyTracker keyTracker)
	{
		super();
		this.sourceInfo = sourceInfo;
		this.keyTracker = keyTracker;
	}

	public Consumer<ViewerPanelFX> onEnter()
	{
		return t -> {
			if (!this.handlers.containsKey(t))
			{
				final EventHandler<KeyEvent> handler = event -> {
					final Source<?> source = sourceInfo.currentSourceProperty().get();
					if (source != null && sourceInfo.getState(source).isVisibleProperty().get() && sourceInfo.getState(
							source) instanceof LabelSourceState<?, ?>)
					{
						final LabelSourceState<?, ?> state     = (LabelSourceState<?, ?>) sourceInfo.getState(source);
						final Converter<?, ARGBType> converter = state.getSourceAndConverter().getConverter();
						final Optional<AbstractHighlightingARGBStream> currentStream = Optional.ofNullable(
								converter instanceof HighlightingStreamConverter<?>
								? ((HighlightingStreamConverter<?>) converter).getStream()
								: null);

						if (keyTracker.areOnlyTheseKeysDown(KeyCode.C))
						{
							changeStream(currentStream, seed -> seed + 1, sourceInfo, source);
							event.consume();
						}
						else if (keyTracker.areOnlyTheseKeysDown(KeyCode.C, KeyCode.SHIFT))
						{
							changeStream(currentStream, seed -> seed - 1, sourceInfo, source);
							event.consume();
						}
						else if (keyTracker.areOnlyTheseKeysDown(KeyCode.C, KeyCode.SHIFT, KeyCode.CONTROL))
						{
							final LongUnaryOperator op = seed -> {
								final Spinner<Integer> spinner = new Spinner<>(
										Integer.MIN_VALUE,
										Integer.MAX_VALUE,
										(int) seed
								);
								final Dialog<Long>     dialog  = new Dialog<>();
								dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
								dialog.setHeaderText("Select seed for ARGB stream");
								dialog.getDialogPane().setContent(spinner);
								dialog.setResultConverter(param -> param.equals(ButtonType.OK)
								                                   ? (long) spinner.getValue()
								                                   : seed);
								final long newSeed = dialog.showAndWait().orElse(seed);
								return newSeed;
							};
							changeStream(currentStream, op, sourceInfo, source);
							event.consume();
						}
					}
				};
				this.handlers.put(t, handler);
			}
			t.addEventHandler(KeyEvent.KEY_PRESSED, this.handlers.get(t));

		};
	}

	public Consumer<ViewerPanelFX> onExit()
	{
		return t -> {
			t.removeEventHandler(KeyEvent.KEY_PRESSED, this.handlers.get(t));
		};
	}

	public OnEnterOnExit onEnterOnExit()
	{
		return new OnEnterOnExit(onEnter(), onExit());
	}

	private static boolean changeStream(final Optional<AbstractHighlightingARGBStream> currentStream, final
	LongUnaryOperator seedUpdate, final SourceInfo sourceInfo, final Source<?> source)
	{
		if (currentStream.isPresent())
		{
			final AbstractHighlightingARGBStream stream      = currentStream.get();
			final long                           seed        = stream.getSeed();
			final long                           currentSeed = seedUpdate.applyAsLong(seed);
			if (currentSeed != seed)
			{
				stream.setSeed(currentSeed);
				;
				stream.clearCache();
				return true;
			}
		}
		return false;
	}

}
