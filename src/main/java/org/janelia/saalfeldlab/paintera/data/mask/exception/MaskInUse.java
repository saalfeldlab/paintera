package org.janelia.saalfeldlab.paintera.data.mask.exception;

public class MaskInUse extends MaskedSourceException {

	/**
	 *
	 */
	private static final long serialVersionUID = 8042080288640420968L;

	private Boolean offerReset = false;

	public MaskInUse(final String message) {

		super(message);
	}

	public MaskInUse(final String message, Boolean offerReset) {

		super(message);
		this.offerReset = offerReset;
	}

	@Override public Boolean offerReset() {

		return this.offerReset;
	}
}

