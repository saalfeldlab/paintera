package bdv.bigcat.annotation;

import bdv.util.IdService;
import ch.systemsx.cisd.base.mdarray.MDFloatArray;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.hdf5.IHDF5Writer;
import ncsa.hdf.hdf5lib.exceptions.HDF5SymbolTableException;
import net.imglib2.RealPoint;

public class AnnotationsHdf5Store implements AnnotationsStore {
	
	private String filename;
	private String groupname;
	
	public AnnotationsHdf5Store(String filename, String groupname) {
		
		this.filename = filename;
		this.groupname = groupname;
	}

	@Override
	public Annotations read() {
		
		Annotations annotations = new Annotations();
	
		final IHDF5Reader reader = HDF5Factory.openForReading(filename);
		final MDFloatArray synapseLocations = reader.float32().readMDArray(groupname);
		
		final int[] dims = synapseLocations.dimensions();
		
		for (int i = 0; i < dims[1]; i++) {
			
			float p[] = {
				synapseLocations.get(i, 0),
				synapseLocations.get(i, 1),
				synapseLocations.get(i, 2),
			};
			RealPoint pos = new RealPoint(3);
			pos.setPosition(p);
			
			Synapse synapse = new Synapse(IdService.allocate(), pos, "");
			annotations.add(synapse);
		}
		
		return annotations;
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
			float[][] preSynapticSiteData = new float[counter.numPreSynapticSites][3];
			float[][] postSynapticSiteData = new float[counter.numPostSynapticSites][3];
			
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
				synapseIndex++;
			}

			@Override
			public void visit(PreSynapticSite preSynapticSite) {
				fillPosition(synapseData[preIndex], preSynapticSite);
				preIndex++;
			}

			@Override
			public void visit(PostSynapticSite postSynapticSite) {
				fillPosition(synapseData[postIndex], postSynapticSite);
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
		writer.float32().writeMatrix(groupname + "/synapses", crawler.synapseData);
		writer.float32().writeMatrix(groupname + "/presynaptic_sites", crawler.preSynapticSiteData);
		writer.float32().writeMatrix(groupname + "/postsynaptic_sites", crawler.postSynapticSiteData);
	}

}
