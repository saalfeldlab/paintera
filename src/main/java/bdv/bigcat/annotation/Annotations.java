package bdv.bigcat.annotation;

import java.util.LinkedList;
import java.util.List;

import net.imglib2.KDTree;
import net.imglib2.KDTreeNode;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.kdtree.ClipConvexPolytopeKDTree;
import net.imglib2.algorithm.kdtree.ConvexPolytope;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;

/**
 * Set of annotations and query functions.
 *  
 * @author Jan Funke <jfunke@iri.upc.edu>
 */
public class Annotations {
	
	public Annotations() {
		this.annotations = new LinkedList< Annotation >();
	}
	
	public void add(Annotation annotation) {
	
		annotations.add(annotation);
		kdTreeDirty = true;
	}	
	
	public void remove(Annotation annotation) {
		
		annotations.remove(annotation);
		kdTreeDirty = true;
	}
	
	public List< Annotation > getAnnotations() {
		
		return annotations;
	}
	
	public List< Annotation > getLocalAnnotations(ConvexPolytope polytope) {

		List< Annotation > localAnnotations = new LinkedList< Annotation >();
		if (annotations.size() == 0)
			return localAnnotations;
	
		if (kdTreeDirty)
			updateKdTree();
	
		final ClipConvexPolytopeKDTree< Annotation > clip = new ClipConvexPolytopeKDTree< Annotation >( kdTree );
		clip.clip( polytope );
		
		for (KDTreeNode< Annotation > node : clip.getInsideNodes())
			localAnnotations.add(node.get());
			
		return localAnnotations;
	}
	
	/**
	 * Find the k nearest annotations to a point.
	 * @param pos
	 * @param k
	 * @return List of k nearest annotations, sorted by distance.
	 */
	public List< Annotation > getKNearest(RealPoint pos, int k) {

		List< Annotation > nearest = new LinkedList< Annotation >();

		if (annotations.size() == 0)
			return nearest;
	
		if (kdTreeDirty)
			updateKdTree();

		KNearestNeighborSearchOnKDTree< Annotation > search = new KNearestNeighborSearchOnKDTree< Annotation >(kdTree, k);
		search.search(pos);
		
		for (int i = 0; i < k && search.getSampler(i) != null; i++)
			nearest.add(search.getSampler(i).get());
		
		return nearest;
	}
	
	public void markDirty() {
		
		kdTreeDirty = true;
	}
	
	private void updateKdTree() {
	
		List< RealPoint > positions = new LinkedList< RealPoint >();
		for (Annotation a : annotations)
			positions.add(a.getPosition());
		kdTree = new KDTree< Annotation >( annotations, positions );
		kdTreeDirty = false;
	}

	private List< Annotation > annotations;
	private KDTree< Annotation > kdTree;
	private boolean kdTreeDirty = true;
}
