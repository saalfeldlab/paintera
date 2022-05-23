package org.janelia.saalfeldlab.util.grids;

import net.imglib2.Interval;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

@LabelBlockLookup.LookupType("NO_BLOCKS")
public class LabelBlockLookupNoBlocks implements LabelBlockLookup {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public String getType() {

	return "NO_BLOCKS";
  }

  @Override
  public Interval[] read(final LabelBlockLookupKey key) throws IOException {

	LOG.debug("Reading blocks not supported for non-paintera dataset -- returning empty array");
	return new Interval[0];
  }

  @Override
  public void write(final LabelBlockLookupKey key, Interval... intervals) throws IOException {

	LOG.debug("Saving blocks not supported for non-paintera dataset");
  }
}
