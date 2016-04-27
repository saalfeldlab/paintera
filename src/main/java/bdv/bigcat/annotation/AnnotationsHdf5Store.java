package bdv.bigcat.annotation;

import java.beans.MethodDescriptor;
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
	private String fileFormat;
	
	/**
	 * Create a new HDF5 store for the given file. Annotations will be read from and written to the group "/annotations".
	 * @param filename
	 */
	public AnnotationsHdf5Store(String filename) {

		this(filename, "/annotations");
	}
	
	/**
	 * Create a new HDF5 store for the given file. Annotations will be read from and written to the given group.
	 * @param filename
	 * @param groupname
	 */
	public AnnotationsHdf5Store(String filename, String groupname) {
		
		this.filename = filename;
		this.groupname = groupname;
		
		final IHDF5Reader reader = HDF5Factory.openForReading(filename);
		// TODO: following call is deprecated, but what to use instead?
		if (reader.hasAttribute("/", "file_format")) {
			fileFormat = reader.string().getAttr("/", "file_format");
		} else {
			fileFormat = "0.0";
		}
		reader.close();
		
		System.out.println("AnnotationsHdf5Store: detected file format " + fileFormat);
	}
	
	class AnnotationFactory {
		public Annotation create(long id, RealPoint pos, String comment, String type) throws Exception {
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
		
		Annotations annotations = new Annotations();
	
		final IHDF5Reader reader = HDF5Factory.openForReading(filename);
		final AnnotationFactory factory = new AnnotationFactory();
		
		if (fileFormat.equals("0.0")) {

			readAnnotations(annotations, reader, "synapse", factory);
			readAnnotations(annotations, reader, "presynaptic_site", factory);
			readAnnotations(annotations, reader, "postsynaptic_site", factory);
			readPrePostPartners(annotations, reader);

		} else if (fileFormat.equals("0.1")) {

			readAnnotations(annotations, reader, "all", factory);
			readPrePostPartners(annotations, reader);
			
		} else {
			
			throw new Exception("unsupported file format: " + fileFormat + ". Is your bigcat up-to-date?");
		}
		reader.close();
		
		return annotations;
	}

	private void readAnnotations(Annotations annotations, final IHDF5Reader reader, String type, AnnotationFactory factory) throws Exception {
		
		String locationsDataset;
		String idsDataset;
		String typesDataset;
		String commentsDataset;
		String commentTargetsDataset;
		
		if (fileFormat.equals("0.0")) {
			locationsDataset = type + "_locations";
			idsDataset = type + "_ids";
			typesDataset = null;
			commentsDataset = type + "_comments";
			commentTargetsDataset = null;
		} else if (fileFormat.equals("0.1")) {
			locationsDataset = "locations";
			idsDataset = "ids";
			typesDataset = "types";
			commentsDataset = "comments/comments";
			commentTargetsDataset = "comments/target_ids";
		} else {
			return;
		}
		
		final MDFloatArray locations;
		final MDLongArray ids;
		final String[] types;
		final HashMap<Long, String> comments = new HashMap<Long, String>();

		try {
			locations = reader.float32().readMDArray(groupname + "/" + locationsDataset);
			ids = reader.uint64().readMDArray(groupname + "/" + idsDataset);
			if (typesDataset != null)
				types = reader.string().readArray(groupname + "/" + typesDataset);
			else
				types = null;
		} catch (HDF5SymbolTableException e) {
			if (type.equals("all"))
				System.out.println("HDF5 file does not contain (valid) annotations");
			else
				System.out.println("HDF5 file does not contain (valid) annotations for " + type);
			return;
		}
			
		final int numAnnotations = locations.dimensions()[0];
		
		try {
			final String[] commentList = reader.string().readArray(groupname + "/" + commentsDataset);
			if (fileFormat.equals("0.0"))
				for (int i = 0; i < numAnnotations; i++)
					comments.put(ids.get(i), commentList[i]);
			else if (fileFormat.equals("0.1")) {
				final MDLongArray commentTargets = reader.uint64().readMDArray(groupname + "/" + commentTargetsDataset);
				for (int i = 0; i < commentList.length; i++)
					comments.put(commentTargets.get(i), commentList[i]);
			}
		} catch (HDF5SymbolTableException e) {
			System.out.println("HDF5 file does not contain comments for " + type);
		}
		
		for (int i = 0; i < numAnnotations; i++) {
			
			float p[] = {
				locations.get(i, 0),
				locations.get(i, 1),
				locations.get(i, 2),
			};
			RealPoint pos = new RealPoint(3);
			pos.setPosition(p);
			
			long id = ids.get(i);
			String comment = (comments.containsKey(id) ? comments.get(id) : "");
			IdService.invalidate(id);
			Annotation annotation = factory.create(id, pos, comment, (types == null ? type : types[i]));
			annotations.add(annotation);
		}
	}

	private void readPrePostPartners(Annotations annotations, final IHDF5Reader reader) {

		final String prePostDataset;
		final MDLongArray prePostPartners;
		
		if (fileFormat.equals("0.0"))
			prePostDataset = "pre_post_partners";
		else if (fileFormat.equals("0.1"))
			prePostDataset = "presynaptic_site/partners";
		else
			return;
		
		try {
			prePostPartners = reader.uint64().readMDArray(groupname + "/" + prePostDataset);
		} catch (HDF5SymbolTableException e) {
			return;
		}
		
		for (int i = 0; i < prePostPartners.dimensions()[0]; i++) {
			long pre = prePostPartners.get(i, 0);
			long post = prePostPartners.get(i, 1);
			PreSynapticSite preSite = (PreSynapticSite)annotations.getById(pre);
			PostSynapticSite postSite = (PostSynapticSite)annotations.getById(post);
			
			preSite.setPartner(postSite);
			postSite.setPartner(preSite);
		}
	}

	@Override
	public void write(Annotations annotations) {

		final IHDF5Writer writer = HDF5Factory.open(filename);
		
		final int numAnnotations = annotations.getAnnotations().size();
		
		class Counter extends AnnotationVisitor {
			
			public int numComments = 0;
			public int numPartners = 0;
			
			@Override
			public void visit(Annotation annotation) {
				if (annotation.getComment() != null && !annotation.getComment().isEmpty())
					numComments++;
			}
			
			@Override
			public void visit(Synapse synapse) {
			}

			@Override
			public void visit(PreSynapticSite preSynapticSite) {
				if (preSynapticSite.getPartner() != null)
					numPartners++;
			}

			@Override
			public void visit(PostSynapticSite postSynapticSite) {
			}
		}

		final Counter counter = new Counter();
		for (Annotation a : annotations.getAnnotations())
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
			
			private void fillPosition(float[] data, Annotation a) {
				
				for (int i = 0; i < 3; i++)
					data[i] = a.getPosition().getFloatPosition(i);
			}
			
			@Override
			public void visit(Annotation annotation) {
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
			public void visit(Synapse synapse) {
				types[typeIndex] = "synapse";
				typeIndex++;
			}

			@Override
			public void visit(PreSynapticSite preSynapticSite) {
				if (preSynapticSite.getPartner() != null) {
					partners[partnerIndex][0] = preSynapticSite.getId();
					partners[partnerIndex][1] = preSynapticSite.getPartner().getId();
					partnerIndex++;
				}
				types[typeIndex] = "presynaptic_site";
				typeIndex++;
			}

			@Override
			public void visit(PostSynapticSite postSynapticSite) {
				types[typeIndex] = "postsynaptic_site";
				typeIndex++;
			}
		}
		
		AnnotationsCrawler crawler = new AnnotationsCrawler();
		for (Annotation a : annotations.getAnnotations())
			a.accept(crawler);
		
		// TODO: following calls are deprecated, but what to use instead?
		try {
			writer.createGroup(groupname);
		} catch (HDF5SymbolTableException e) {
			// already existed
		}
		try {
			writer.createGroup(groupname + "/comments");
		} catch (HDF5SymbolTableException e) {
			// already existed
		}
		try {
			writer.createGroup(groupname + "/presynaptic_site");
		} catch (HDF5SymbolTableException e) {
			// already existed
		}
		
		writer.string().setAttr("/", "file_format", "0.1");
		
		writer.float32().writeMatrix(groupname + "/locations", crawler.locations);
		writer.uint64().writeArray(groupname + "/ids", crawler.ids);
		writer.string().writeArray(groupname + "/types", crawler.types);

		writer.string().writeArray(groupname + "/comments/comments", crawler.comments);
		writer.uint64().writeArray(groupname + "/comments/target_ids", crawler.commentTargets);

		writer.uint64().writeMatrix(groupname + "/presynaptic_site/partners", crawler.partners);
		
		// delete old datasets and groups
		if (fileFormat.equals("0.0")) {

			for (String type : new String[]{"synapse", "presynaptic_site", "postsynaptic_site" })
				for (String ds : new String[]{"locations", "ids", "comments" })
					deleteDataset(writer, type + "_" + ds);
			deleteDataset(writer, "pre_post_partners");
		}
		
		writer.close();
	}
	
	private void deleteDataset(IHDF5Writer writer, String name) {
		final String dsName = groupname + "/" + name;
		try {
			writer.delete(dsName);
		} catch (Exception e) {
			System.out.println("couldn't delete " + dsName);
		}
	}
}
