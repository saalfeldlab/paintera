package org.janelia.saalfeldlab.fx.event;

import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class DelegateEventHandlers {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static <E extends Event> EventHandler<E> fromSupplier(final Supplier<EventHandler<E>> handler) {
		return new SupplierDelegateEventHandler<>(handler);
	}

	public static <E extends Event> ListDelegateEventHandler<E> listHandler() {
		return new ListDelegateEventHandler<>();
	}

	public static AnyHandler handleAny() {
		return new AnyHandler();
	}

	public static class SupplierDelegateEventHandler<E extends Event> implements EventHandler<E> {

		private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


		private final Supplier<EventHandler<E>> currentEventHandler;

		public SupplierDelegateEventHandler(Supplier<EventHandler<E>> currentEventHandler) {
			this.currentEventHandler = currentEventHandler;
		}

		@Override
		public void handle(E event) {
			final EventHandler<E> handler = currentEventHandler.get();
			LOG.trace("Handling event {} with handler {}", event, handler);
			Optional.ofNullable(handler).ifPresent(h -> h.handle(event));
		}
	}

	public static class ListDelegateEventHandler<E extends Event> implements EventHandler<E> {

		private final List<EventHandler<E>> delegateHandlers = new ArrayList<>();

		@Override
		public void handle(E event) {
			for (final EventHandler<E> handler : delegateHandlers) {
				if (event.isConsumed())
					break;
				handler.handle(event);
			}
		}

		public boolean addHandler(final EventHandler<E> handler) {
			return this.delegateHandlers.add(handler);
		}

		public boolean removeHandler(final EventHandler<E> handler) {
			return this.delegateHandlers.remove(handler);
		}
	}

	public static class AnyHandler implements EventHandler<Event> {

		private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


		private final ListDelegateEventHandler<MouseEvent> mouseEventHandlers = new ListDelegateEventHandler<>();

		private final ListDelegateEventHandler<ScrollEvent> scrollEventHandlers = new ListDelegateEventHandler<>();

		private final ListDelegateEventHandler<KeyEvent> keyEventHandlers = new ListDelegateEventHandler<>();

		private final ListDelegateEventHandler<Event> otherHandlers = new ListDelegateEventHandler<>();


		@Override
		public void handle(Event event) {
			if (event instanceof MouseEvent) {
				LOG.debug("Handling mouse event {}", event);
				mouseEventHandlers.handle((MouseEvent) event);
			}
			else if (event instanceof KeyEvent) {
				LOG.debug("Handling key event {}", event);
				keyEventHandlers.handle((KeyEvent) event);
			} else if (event instanceof ScrollEvent) {
				LOG.debug("Handling scroll event {}", event);
				scrollEventHandlers.handle((ScrollEvent) event);
			}
			else {
				LOG.debug("Handling mouse event {}", event);
				otherHandlers.handle(event);
			}
		}

		public boolean addMouseHandler(final EventHandler<MouseEvent> handler) {
			return this.mouseEventHandlers.addHandler(handler);
		}

		public boolean addScrollHandler(final EventHandler<ScrollEvent> handler) {
			return this.scrollEventHandlers.addHandler(handler);
		}

		public boolean addKeyHandler(final EventHandler<KeyEvent> handler) {
			return this.keyEventHandlers.addHandler(handler);
		}

		public boolean addHandler(final EventHandler<Event> handler) {
			return this.otherHandlers.addHandler(handler);
		}

		public boolean remvoeMouseHandler(final EventHandler<MouseEvent> handler) {
			return this.mouseEventHandlers.removeHandler(handler);
		}

		public boolean remvoeScrollHandler(final EventHandler<ScrollEvent> handler) {
			return this.scrollEventHandlers.removeHandler(handler);
		}

		public boolean removeKeyHandler(final EventHandler<KeyEvent> handler) {
			return this.keyEventHandlers.removeHandler(handler);
		}

		public boolean removeHandler(final EventHandler<Event> handler) {
			return this.otherHandlers.removeHandler(handler);
		}
	}
}
