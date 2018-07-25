package org.janelia.saalfeldlab.util;

public class DecorateRunnable implements Runnable
{

	private final Runnable r;

	private final Runnable before;

	private final Runnable after;

	public DecorateRunnable(final Runnable r, final Runnable before, final Runnable after)
	{
		super();
		this.before = before;
		this.r = r;
		this.after = after;
	}

	@Override
	public void run()
	{
		this.before.run();
		this.r.run();
		this.after.run();
	}

	public static Runnable before(final Runnable r, final Runnable before)
	{
		return new DecorateRunnable(r, before, () -> {
		});
	}

	public static Runnable after(final Runnable r, final Runnable after)
	{
		return new DecorateRunnable(r, () -> {
		}, after);
	}

	public static Runnable beforeAndAfter(final Runnable r, final Runnable before, final Runnable after)
	{
		return new DecorateRunnable(r, before, after);
	}

}
