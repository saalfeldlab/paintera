package org.janelia.saalfeldlab.paintera.ui.opendialog.googlecloud;

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class StorageAndBucket
{

	public final ObjectProperty<Storage> storage = new SimpleObjectProperty<>();

	public final ObjectProperty<Bucket> bucket = new SimpleObjectProperty<>();

}
