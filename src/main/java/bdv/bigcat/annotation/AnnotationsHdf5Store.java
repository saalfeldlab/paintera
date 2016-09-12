package bdv.bigcat.annotation;

import java.util.HashMap;

import bdv.util.IdService;
import ch.systemsx.cisd.base.mdarray.MDFloatArray;
import ch.systemsx.cisd.base.mdarray.MDLongArray;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.hdf5.IHDF5Writer;
import ncsa.hdf.hdf5lib.exceptions.HDF5SymbolTableException;
import net.imglib2.RealPoint;

public class AnnotationsHdf5Store implements AnnotationsStore {

	private String filename;
	private String groupname;
	private double fileFormat;

	final private IdService idService;

	// annotations offset in (x,y,z)
	private float[] offset = {0.0f, 0.0f, 0.0f};

	/**
	 * Create a new HDF5 store for the given file. Annotations will be read from and written to the group "/annotations".
	 * @param filename
	 */
	public AnnotationsHdf5Store(final String filename, final IdService idService) {

		this(filename, "/annotations", idService);
	}

	/**
	 * Create a new HDF5 store for the given file. Annotations will be read from and written to the given group.
	 * @param filename
	 * @param groupname
	 */
	public AnnotationsHdf5Store(final String filename, final String groupname, final IdService idService) {

		this.filename = filename;
		this.groupname = groupname;
		this.idService = idService;

		final IHDF5Reader reader = HDF5Factory.openForReading(filename);
		// TODO: following call is deprecated, but what to use instead?
		if (reader.object().hasAttribute("/", "file_format")) {
			fileFormat = Double.parseDouble(reader.string().getAttr("/", "file_format"));
		} else {
			fileFormat = 0.0;
		}

		if (reader.exists(groupname) && reader.hasAttribute(groupname, "offset")) {
			final float[] data = reader.getFloatArrayAttribute(groupname, "offset");
			offset[0] = data[2];
			offset[1] = data[1];
			offset[2] = data[0];
		}

		reader.close();

		System.out.println("AnnotationsHdf5Store: detected file format " + fileFormat);
	}

	class AnnotationFactory {
		public Annotation create(final long id, final RealPoint pos, final String comment, final String type) throws Exception {
			switch (type) {
			case "synapse":
				return new Synapse(id, pos, comment);
			case "presynaptic_site":
				return new PreSynapticSite(id, pos, comment);
			case "postsynaptic_site":
				return new PostSynapticSite(id, pos, comment);
			default:
				throw new Exception("annotation type " + type + " is unknown");
			}
		}
	}

	@Override
	public Annotations read() throws Exception {

		final Annotations annotations = new Annotations();

		final IHDF5Reader reader = HDF5Factory.openForReading(filename);
		final AnnotationFactory factory = new AnnotationFactory();

		if (fileFormat == 0.0) {

			readAnnotations(annotations, reader, "synapse", factory);
			readAnnotations(annotations, reader, "presynaptic_site", factory);
			readAnnotations(annotations, reader, "postsynaptic_site", factory);
			readPrePostPartners(annotations, reader);

		} else if (fileFormat >= 0.1 && fileFormat <= 0.2) {

			readAnnotations(annotations, reader, "all", factory);
			readPrePostPartners(annotations, reader);

		} else {

			throw new Exception("unsupported file format: " + fileFormat + ". Is your bigcat up-to-date?");
		}
		reader.close();

		return annotations;
	}

	private void readAnnotations(final Annotations annotations, final IHDF5Reader reader, final String type, final AnnotationFactory factory) throws Exception {

		// prior to 0.1 there was no groupname prefix
		final String groupname = (fileFormat == 0.0 ? "" : this.groupname);

		String locationsDataset;
		String idsDataset;
		String typesDataset;
		String commentsDataset;
		String commentTargetsDataset;

		if (fileFormat == 0.0) {
			locationsDataset = type + "_locations";
			idsDataset = type + "_ids";
			typesDataset = null;
			commentsDataset = type + "_comments";
			commentTargetsDataset = null;
		} else {
			locationsDataset = "locations";
			idsDataset = "ids";
			typesDataset = "types";
			commentsDataset = "comments/comments";
			commentTargetsDataset = "comments/target_ids";
		}

		final MDFloatArray locations;
		final MDLongArray ids;
		final String[] types;
		final HashMap<Long, String> comments = new HashMap<Long, String>();

		try {
			locations = reader.float32().readMDArray(groupname + "/" + locationsDataset);
			ids = reader.uint64().readMDArray(groupname + "/" + idsDataset);
			if (typesDataset != null && locations.size() > 0 )
				types = reader.string().readArray(groupname + "/" + typesDataset);
			else
				types = null;
		} catch (final HDF5SymbolTableException e) {
			if (type.equals("all"))
				System.out.println("HDF5 file does not contain (valid) annotations");
			else
				System.out.println("HDF5 file does not contain (valid) annotations for " + type);
			return;
		}

		final int numAnnotations = locations.dimensions()[0];

		try {
			final String[] commentList = reader.string().readArray(groupname + "/" + commentsDataset);
			if (fileFormat == 0.0)
				for (int i = 0; i < numAnnotations; i++)
					comments.put(ids.get(i), commentList[i]);
			else if (fileFormat >= 0.1) {
				final MDLongArray commentTargets = reader.uint64().readMDArray(groupname + "/" + commentTargetsDataset);
				for (int i = 0; i < commentList.length; i++)
					comments.put(commentTargets.get(i), commentList[i]);
			}
		} catch (final HDF5SymbolTableException e) {
			System.out.println("HDF5 file does not contain comments for " + type);
		} catch (final NullPointerException e) {
			System.out.println("HDF5 file does not contain comments for " + type);
		}

		for (int i = 0; i < numAnnotations; i++) {

			// coordinates are stored as (z,y,x), but internally we use (x,y,z)
			final float p[] = {
				locations.get(i, 2) + offset[0],
				locations.get(i, 1) + offset[1],
				locations.get(i, 0) + offset[2],
			};
			final RealPoint pos = new RealPoint(3);
			pos.setPosition(p);

			final long id = ids.get(i);
			final String comment = (comments.containsKey(id) ? comments.get(id) : "");

			// TODO synchronize this if executed in parallel to other id activity
			idService.invalidate( id );
			final Annotation annotation = factory.create(id, pos, comment, (types == null ? type : types[i]));
			annotations.add(annotation);
		}
	}

	private void readPrePostPartners(final Annotations annotations, final IHDF5Reader reader) {

		// prior to 0.1 there was no groupname prefix
		final String groupname = (fileFormat == 0.0 ? "" : this.groupname);

		final String prePostDataset;
		final MDLongArray prePostPartners;

		if (fileFormat == 0.0)
			prePostDataset = "pre_post_partners";
		else
			prePostDataset = "presynaptic_site/partners";

		try {
			prePostPartners = reader.uint64().readMDArray(groupname + "/" + prePostDataset);
		} catch (final HDF5SymbolTableException e) {
			return;
		}

		for (int i = 0; i < prePostPartners.dimensions()[0]; i++) {
			final long pre = prePostPartners.get(i, 0);
			final long post = prePostPartners.get(i, 1);
			final PreSynapticSite preSite = (PreSynapticSite)annotations.getById(pre);
			final PostSynapticSite postSite = (PostSynapticSite)annotations.getById(post);

			preSite.setPartner(postSite);
			postSite.setPartner(preSite);
		}
	}

	@Override
	public void write(final Annotations annotations) {

		final IHDF5Writer writer = HDF5Factory.open(filename);

		final int numAnnotations = annotations.getAnnotations().size();

		class Counter extends AnnotationVisitor {

			public int numComments = 0;
			public int numPartners = 0;

			@Override
			public void visit(final Annotation annotation) {
				if (annotation.getComment() != null && !annotation.getComment().isEmpty())
					numComments++;
			}

			@Override
			public void visit(final Synapse synapse) {
			}

			@Override
			public void visit(final PreSynapticSite preSynapticSite) {
				if (preSynapticSite.getPartner() != null)
					numPartners++;
			}

			@Override
			public void visit(final PostSynapticSite postSynapticSite) {
			}
		}

		final Counter counter = new Counter();
		for (final Annotation a : annotations.getAnnotations())
			a.accept(counter);

		class AnnotationsCrawler extends AnnotationVisitor {

			float[][] locations = new float[numAnnotations][3];
			long[] ids = new long[numAnnotations];
			String[] types = new String[numAnnotations];
			String[] comments = new String[counter.numComments];
			long[] commentTargets = new long[counter.numComments];
			long[][] partners  = new long[counter.numPartners][2];

			private int annotationIndex = 0;
			private int typeIndex = 0;
			private int commentIndex = 0;
			private int partnerIndex = 0;

			private void fillPosition(final float[] data, final Annotation a) {

				// We store locations as (z,y,x). The internal coordinates
				// are (x,y,z) and need to be inverted.
				for (int i = 0; i < 3; i++)
					data[i] = a.getPosition().getFloatPosition(2 - i) - offset[2 - i];
			}

			@Override
			public void visit(final Annotation annotation) {
				fillPosition(locations[annotationIndex], annotation);
				ids[annotationIndex] = annotation.getId();
				if (annotation.getComment() != null && !annotation.getComment().isEmpty()) {
					comments[commentIndex] = annotation.getComment();
					commentTargets[commentIndex] = annotation.getId();
					commentIndex++;
				}
				annotationIndex++;
			}

			@Override
			public void visit(final Synapse synapse) {
				types[typeIndex] = "synapse";
				typeIndex++;
			}

			@Override
			public void visit(final PreSynapticSite preSynapticSite) {
				if (preSynapticSite.getPartner() != null) {
					partners[partnerIndex][0] = preSynapticSite.getId();
					partners[partnerIndex][1] = preSynapticSite.getPartner().getId();
					partnerIndex++;
				}
				types[typeIndex] = "presynaptic_site";
				typeIndex++;
			}

			@Override
			public void visit(final PostSynapticSite postSynapticSite) {
				types[typeIndex] = "postsynaptic_site";
				typeIndex++;
			}
		}

		final AnnotationsCrawler crawler = new AnnotationsCrawler();
		for (final Annotation a : annotations.getAnnotations())
			a.accept(crawler);

		// TODO: following calls are deprecated, but what to use instead?
		try {
			writer.createGroup(groupname);
		} catch (final HDF5SymbolTableException e) {
			// already existed
		}
		try {
			writer.createGroup(groupname + "/comments");
		} catch (final HDF5SymbolTableException e) {
			// already existed
		}
		try {
			writer.createGroup(groupname + "/presynaptic_site");
		} catch (final HDF5SymbolTableException e) {
			// already existed
		}

		writer.string().setAttr("/", "file_format", "0.2");

		if (writer.hasAttribute(groupname, "offset")) {
			final float[] data = { offset[2], offset[1], offset[0] };
			writer.setFloatArrayAttribute(groupname, "offset", data);
		}

		writer.float32().writeMatrix(groupname + "/locations", crawler.locations);
		writer.uint64().writeArray(groupname + "/ids", crawler.ids);
		writer.string().writeArray(groupname + "/types", crawler.types);

		writer.string().writeArray(groupname + "/comments/comments", crawler.comments);
		writer.uint64().writeArray(groupname + "/comments/target_ids", crawler.commentTargets);

		writer.uint64().writeMatrix(groupname + "/presynaptic_site/partners", crawler.partners);

		// delete old datasets and groups
		if (fileFormat == 0.0) {

			for (final String type : new String[]{"synapse", "presynaptic_site", "postsynaptic_site" })
				for (final String ds : new String[]{"locations", "ids", "comments" })
					deleteDataset(writer, type + "_" + ds);
			deleteDataset(writer, "pre_post_partners");
		}

		writer.close();
	}

	private void deleteDataset(final IHDF5Writer writer, final String name) {

		// prior to 0.1 there was no groupname prefix
		final String groupname = (fileFormat == 0.0 ? "" : this.groupname);

		final String dsName = groupname + "/" + name;
		try {
			writer.delete(dsName);
		} catch (final Exception e) {
			System.out.println("couldn't delete " + dsName);
		}
	}
}
