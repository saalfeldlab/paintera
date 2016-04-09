package bdv.bigcat.annotation;

import java.util.LinkedList;
import java.util.List;

import net.imglib2.KDTree;
import net.imglib2.KDTreeNode;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.kdtree.ClipConvexPolytopeKDTree;
import net.imglib2.algorithm.kdtree.ConvexPolytope;

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
	
	public List< Annotation > getLocalAnnotations(ConvexPolytope polytope) {
	
		if (kdTreeDirty)
			updateKdTree();
	
		final ClipConvexPolytopeKDTree< Annotation > clip = new ClipConvexPolytopeKDTree< Annotation >( kdTree );
		clip.clip( polytope );
		
		List< Annotation > localAnnotations = new LinkedList< Annotation >();
		for (KDTreeNode< Annotation > node : clip.getInsideNodes())
			localAnnotations.add(node.get());
			
		return localAnnotations;
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
