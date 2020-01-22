package org.janelia.saalfeldlab.paintera.meshes.managed

/**
 * @author Philipp Hanslovsky
 * @author Igor Pisarev
 */
class MeshManagerSimpleKotlin<N>//(
//    source: DataSource<*, *>,
//    getBlockListFor: GetBlockListFor<N>,
//    meshCache: Array<InterruptibleFunction<ShapeKey<T>?, Pair<FloatArray?, FloatArray?>?>?>?,
//    root: Group?,
//    viewFrustumProperty: ObjectProperty<ViewFrustum?>?,
//    eyeToWorldTransformProperty: ObjectProperty<AffineTransform3D?>?,
//    meshSettings: MeshSettings?,
//    managers: ExecutorService?,
//    workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority?>) : AbstractMeshManager<N, T>(
//    source,
//    blockListCache,
//    meshCache,
//    root,
//    viewFrustumProperty,
//    eyeToWorldTransformProperty,
//    meshSettings,
//    managers,
//    workers,
//    MeshViewUpdateQueue<T>()
//) {
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
