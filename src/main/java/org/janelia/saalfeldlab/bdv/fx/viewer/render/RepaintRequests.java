package org.janelia.saalfeldlab.bdv.fx.viewer.render;

import net.imglib2.Interval;
import net.imglib2.util.Intervals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

///
/// Completion of the repaints requested from a renderer.
///
final class RepaintRequests {

    private record Request(Interval interval, CompletableFuture<Void> completion) {
    }

    private Request nextRequest;

	private final List<Request> startedRequests = new ArrayList<>();

    /// track a requested `interval`, and return the [CompletableFuture] denoting its state. The same future is shared by all requests made
    /// before a paint starts on them.
    ///
    /// @return the future
	synchronized CompletableFuture<Void> request(final Interval interval) {

		nextRequest = nextRequest == null
				? new Request(interval, new CompletableFuture<>())
				: new Request(Intervals.union(nextRequest.interval, interval), nextRequest.completion);
		return nextRequest.completion;
	}

    /// transition the [nextRequest] to [startedRequests] if the paint `taken` covers it; otherwise it waits for one that does
	synchronized void paintStarted(final Interval taken) {

		if (nextRequest != null && Intervals.contains(taken, nextRequest.interval)) {
			startedRequests.add(nextRequest);
			nextRequest = null;
		}
	}

    /// mark paint as complete
    ///
    /// @param interval that was painted
    void painted(final Interval interval) {

		complete(take(interval, false), null);
	}

    /// If the paint failed, notify all futures that where in that interval with the failure.
    ///
    /// @param interval to fail
    /// @param failure that was encountered
	void failed(final Interval interval, final Throwable failure) {

		complete(interval == null ? takeAll() : take(interval, true), failure);
	}

	void cancel() {

		takeAll().forEach(request -> request.completion.cancel(false));
	}

	private synchronized List<Request> take(final Interval interval, final boolean includeIntersection) {

		final List<Request> taken = new ArrayList<>();
		startedRequests.removeIf(request -> {
			final boolean take = includeIntersection
					? !Intervals.isEmpty(Intervals.intersect(interval, request.interval))
					: Intervals.contains(interval, request.interval);
			if (take)
				taken.add(request);
			return take;
		});
		return taken;
	}

	private synchronized List<Request> takeAll() {

		final List<Request> taken = new ArrayList<>(startedRequests);
		startedRequests.clear();
		if (nextRequest != null)
			taken.add(nextRequest);
		nextRequest = null;
		return taken;
	}

	private static void complete(final List<Request> requests, final Throwable failure) {

		for (final Request request : requests) {
			if (failure == null)
				request.completion.complete(null);
			else
				request.completion.completeExceptionally(failure);
		}
	}
}
