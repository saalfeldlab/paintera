package org.janelia.saalfeldlab.fx.event;

import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
				LOG.trace("Handler {} handling event {}", handler, event);
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

		private final Map<EventHandler<? super Event>, EventType<? super Event>> handlerTypeMapping = new HashMap<>();


		@Override
		public void handle(Event event) {

			final HashMap<EventHandler<? super Event>, EventType<?>> handlerTypeMappingCopy = new HashMap<>(handlerTypeMapping);
			for (final Map.Entry<EventHandler<? super Event>, EventType<?>> entry : handlerTypeMappingCopy.entrySet()) {
				if (event.isConsumed())
					break;
				final EventType<?> handlerEventType = entry.getValue();
				if (handlerEventType == null)
					continue;

				EventType<? extends Event> eventType = event.getEventType();
				while (eventType != null) {
					if (eventType == handlerEventType) {
						LOG.trace("Handler for type {} handles type {}", handlerEventType, event.getEventType());
						entry.getKey().handle(event);
						break;
					}
					eventType = eventType.getSuperType();
				}
			}
		}

		public <E extends Event> EventType addEventHandler(final EventType<? super E> eventType, EventHandler<? super E> eventHandler) {
			return handlerTypeMapping.put((EventHandler) eventHandler, (EventType) eventType);
		}

		public <E extends Event> EventType<? super Event> removeEventHandler(EventHandler<? super E> eventHandler) {
			return handlerTypeMapping.remove(eventHandler);
		}

		public void addOnMousePressed(EventHandler<MouseEvent> handler) {
			addEventHandler(MouseEvent.MOUSE_PRESSED, handler);
		}

		public void addOnKeyPressed(EventHandler<KeyEvent> handler) {
			addEventHandler(KeyEvent.KEY_PRESSED, handler);
		}

		public void addOnKeyReleased(EventHandler<KeyEvent> handler) {
			addEventHandler(KeyEvent.KEY_RELEASED, handler);
		}

		public void addOnScroll(EventHandler<ScrollEvent> handler) {
			addEventHandler(ScrollEvent.SCROLL, handler);
		}
	}
}
