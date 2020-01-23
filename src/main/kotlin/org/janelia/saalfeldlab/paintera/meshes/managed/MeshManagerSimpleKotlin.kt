package org.janelia.saalfeldlab.paintera.meshes.managed
//
//import javafx.beans.value.ObservableValue
//import net.imglib2.realtransform.AffineTransform3D
//import org.janelia.saalfeldlab.paintera.data.DataSource
//import org.janelia.saalfeldlab.paintera.meshes.MeshSettings
//import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority
//import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum
//import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor
//import java.util.concurrent.ExecutorService
//
///**
// * @author Philipp Hanslovsky
// * @author Igor Pisarev
// */
//class MeshManagerSimpleKotlin<N>(
//    source: DataSource<*, *>,
//    getBlockListFor: PainteraMeshManager.GetBlockListFor<N>,
//    getMeshesFor: PainteraMeshManager.GetMeshFor<N>,
//    viewFrustumProperty: ObservableValue<ViewFrustum>,
//    eyeToWorldTransformProperty: ObservableValue<AffineTransform3D>,
//    meshSettings: MeshSettings,
//    managers: ExecutorService,
//    workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>) : PainteraMeshManager<N>
////    source,
////    blockListCache,
////    meshCache,
////    root,
////    viewFrustumProperty,
////    eyeToWorldTransformProperty,
////    meshSettings,
////    managers,
////    workers,
////    MeshViewUpdateQueue<T>()
//{
//
//    private val manager
//
//    @Synchronized
//    override fun addMesh(id: N) {
//        if (!areMeshesEnabledProperty.get()) return
//        if (neurons.containsKey(id)) return
//        val color = Bindings.createIntegerBinding(
//            Callable {
//                Colors.toARGBType(color.get()).get()
//            },
//            color
//        )
//        val meshGenerator = MeshGenerator(
//            source.numMipmapLevels,
//            idToMeshId.apply(id),
//            GetBlockListFor<T> { level: Int, t: T -> blockListCache[level].apply(t) },
//            GetMeshFor<T> { key: ShapeKey<T> ->
//                fromVerticesAndNormals(
//                    meshCache[key.scaleIndex()].apply(
//                        key
//                    )
//                )
//            },
//            meshViewUpdateQueue,
//            color,
//            IntFunction { level: Int -> unshiftedWorldTransforms[level] },
//            managers,
//            workers,
//            showBlockBoundariesProperty
//        )
//        meshGenerator.meshSettingsProperty().set(meshSettings)
//        neurons[id] = meshGenerator
//        root.children.add(meshGenerator.root)
//    }
//
//    @Synchronized
//    override fun refreshMeshes() {
//        update()
//    }
//
//    @Synchronized
//    override fun containedFragments(id: N): LongArray {
//        return getIds.apply(id)
//    }
//
//    override fun managedMeshSettings(): ManagedMeshSettings {
//        throw UnsupportedOperationException("not implemented yet")
//    }
//
//    companion object {
//        private val LOG =
//            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
//    }
//
//}
