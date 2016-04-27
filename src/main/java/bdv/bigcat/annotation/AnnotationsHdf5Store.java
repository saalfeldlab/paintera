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
		
		if (fileFormat == "0.0") {

			readAnnotations(annotations, reader, "synapse", factory);
			readAnnotations(annotations, reader, "presynaptic_site", factory);
			readAnnotations(annotations, reader, "postsynaptic_site", factory);
			readPrePostPartners(annotations, reader);

		} else if (fileFormat == "0.1") {

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
		
		if (fileFormat == "0.0") {
			locationsDataset = type + "_locations";
			idsDataset = type + "_ids";
			typesDataset = null;
			commentsDataset = type + "_comments";
			commentTargetsDataset = null;
		} else if (fileFormat == "0.1") {
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
			if (type == "all")
				System.out.println("HDF5 file does not contain (valid) annotations");
			else
				System.out.println("HDF5 file does not contain (valid) annotations for " + type);
			return;
		}
			
		final int numAnnotations = locations.dimensions()[0];
		
		try {
			final String[] commentList = reader.string().readArray(groupname + "/" + commentsDataset);
			if (fileFormat == "0.0")
				for (int i = 0; i < numAnnotations; i++)
					comments.put(ids.get(i), commentList[i]);
			else if (fileFormat == "0.1") {
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
		
		if (fileFormat == "0.0")
			prePostDataset = "pre_post_partners";
		else if (fileFormat == "0.1")
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
		
		class AnnotationsCounter extends AnnotationVisitor {
			
			public int numSynapses = 0;
			public int numPreSynapticSites = 0;
			public int numPostSynapticSites = 0;
			
			@Override
			public void visit(Synapse synapse) {
				numSynapses++;
			}

			@Override
			public void visit(PreSynapticSite preSynapticSite) {
				numPreSynapticSites++;
			}

			@Override
			public void visit(PostSynapticSite postSynapticSite) {
				numPostSynapticSites++;
			}
		}

		final AnnotationsCounter counter = new AnnotationsCounter();
		for (Annotation a : annotations.getAnnotations())
			a.accept(counter);
		
		class AnnotationsCrawler extends AnnotationVisitor {
			
			float[][] synapseData = new float[counter.numSynapses][3];
			long[] synapseIds = new long[counter.numSynapses];
			float[][] preSynapticSiteData = new float[counter.numPreSynapticSites][3];
			long[] preSynapticSiteIds = new long[counter.numPreSynapticSites];
			float[][] postSynapticSiteData = new float[counter.numPostSynapticSites][3];
			long[] postSynapticSiteIds = new long[counter.numPostSynapticSites];
			long[][] partners  = new long[counter.numPostSynapticSites][2];

			String[] synapseComments = new String[counter.numSynapses];
			String[] preSynapticSiteComments = new String[counter.numPreSynapticSites];
			String[] postSynapticSiteComments = new String[counter.numPostSynapticSites];
			
			private int synapseIndex = 0;
			private int preIndex = 0;
			private int postIndex = 0;
			
			private void fillPosition(float[] data, Annotation a) {
				
				for (int i = 0; i < 3; i++)
					data[i] = a.getPosition().getFloatPosition(i);
			}
			
			@Override
			public void visit(Synapse synapse) {
				fillPosition(synapseData[synapseIndex], synapse);
				synapseIds[synapseIndex] = synapse.getId();
				synapseComments[synapseIndex] = synapse.getComment();
				synapseIndex++;
			}

			@Override
			public void visit(PreSynapticSite preSynapticSite) {
				fillPosition(preSynapticSiteData[preIndex], preSynapticSite);
				preSynapticSiteIds[preIndex] = preSynapticSite.getId();
				preSynapticSiteComments[preIndex] = preSynapticSite.getComment();
				preIndex++;
			}

			@Override
			public void visit(PostSynapticSite postSynapticSite) {
				fillPosition(postSynapticSiteData[postIndex], postSynapticSite);
				postSynapticSiteIds[postIndex] = postSynapticSite.getId();
				postSynapticSiteComments[postIndex] = postSynapticSite.getComment();
				partners[postIndex][0] = postSynapticSite.getPartner().getId();
				partners[postIndex][1] = postSynapticSite.getId();
				postIndex++;
			}
		}
		
		AnnotationsCrawler crawler = new AnnotationsCrawler();
		for (Annotation a : annotations.getAnnotations())
			a.accept(crawler);
		
		try {
			writer.createGroup(groupname);
		} catch (HDF5SymbolTableException e) {
			// nada
		}
		writer.float32().writeMatrix(groupname + "/synapse_locations", crawler.synapseData);
		writer.uint64().writeArray(groupname + "/synapse_ids", crawler.synapseIds);
		writer.string().writeArray(groupname + "/synapse_comments", crawler.synapseComments);

		writer.float32().writeMatrix(groupname + "/presynaptic_site_locations", crawler.preSynapticSiteData);
		writer.uint64().writeArray(groupname + "/presynaptic_site_ids", crawler.preSynapticSiteIds);
		writer.string().writeArray(groupname + "/presynaptic_site_comments", crawler.preSynapticSiteComments);

		writer.float32().writeMatrix(groupname + "/postsynaptic_site_locations", crawler.postSynapticSiteData);
		writer.uint64().writeArray(groupname + "/postsynaptic_site_ids", crawler.postSynapticSiteIds);
		writer.string().writeArray(groupname + "/postsynaptic_site_comments", crawler.postSynapticSiteComments);

		writer.uint64().writeMatrix(groupname + "/pre_post_partners", crawler.partners);
		
		writer.close();
	}
}
