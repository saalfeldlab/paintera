package bdv.bigcat.annotation;

import java.util.Collection;
import java.util.HashMap;
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
 * @author Jan Funke <jfunke@iri.upc.edu>
 */
public class Annotations {

	public Annotations() {
		this.annotations = new HashMap< Long, Annotation >();
		this.listeners = new LinkedList<Annotations.AnnotationsListener>();
	}

	public void add(Annotation annotation) {

		annotations.put(annotation.getId(), annotation);
		kdTreeDirty = true;
		for (AnnotationsListener l : listeners)
			l.onAnnotationAdded(annotation);
	}

	public void remove(Annotation annotation) {

		annotations.remove(annotation.getId());
		kdTreeDirty = true;
		for (AnnotationsListener l : listeners)
			l.onAnnotationRemoved(annotation);
	}

	public Collection< Annotation > getAnnotations() {

		return annotations.values();
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

	public Annotation getById(long id) {

		return annotations.get(id);
	}

	public void markDirty() {

		kdTreeDirty = true;
	}

	private void updateKdTree() {

		List< RealPoint > positions = new LinkedList< RealPoint >();
		List< Annotation > annotations = new LinkedList< Annotation >();
		for (Annotation a : this.annotations.values()) {
			positions.add(a.getPosition());
			annotations.add(a);
		}
		kdTree = new KDTree< Annotation >( annotations, positions );
		kdTreeDirty = false;
	}

	public interface AnnotationsListener {

		public void onAnnotationAdded(Annotation a);
		public void onAnnotationRemoved(Annotation a);
	}

	public void addAnnotationsListener(AnnotationsListener listener) {

		listeners.add(listener);
	}

	private HashMap< Long, Annotation > annotations;
	private KDTree< Annotation > kdTree;
	private boolean kdTreeDirty = true;

	private List<AnnotationsListener> listeners;
}
