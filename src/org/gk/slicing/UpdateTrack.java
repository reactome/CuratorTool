package org.gk.slicing;

import java.util.Date;
import org.gk.model.GKInstance;

public class UpdateTrack {
	// What was updated (e.g. catalyst replacement in RLE).
	private GKInstance updateSource;
	// When it was updated (default is current date).
	private Date updateDate;

	// Builder makes it less difficult to add additional parameters, but may not be warranted
	// with a small number of parameters. If that's the case, it may make sense to use the JavaBeans pattern.
	public static class Builder {
		// Required parameter.
		private GKInstance updateSource;

		// Optional parameter.
		private Date updateDate = new Date();

		public Builder(GKInstance updateSource) {
			this.updateSource = updateSource;
		}

		public Builder date(Date val) {
			updateDate = val;
			return this;
		}

		public UpdateTrack build() {
			return new UpdateTrack(this);
		}
	}

	private UpdateTrack(Builder builder) {
		updateSource = builder.updateSource;
		updateDate = builder.updateDate;
	}

	// Check that the instance, schema, and date are all valid.
	private boolean verifyTerms() {
		return false;
	}

	// Write update to gk_central.
	public void writeUpdate() {

	}
}
