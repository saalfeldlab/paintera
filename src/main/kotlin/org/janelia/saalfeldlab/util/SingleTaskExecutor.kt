package org.janelia.saalfeldlab.util

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BooleanSupplier

class SingleTaskExecutor(factory: ThreadFactory = NamedThreadFactory("single-task-executor", true)) {

    interface Task<T>: Callable<T> {
        fun cancel()

        val isCanceled: Boolean
    }

    class TaskDelegate<T>(private val actualTask: (BooleanSupplier) -> T): Task<T> {

        constructor(actualTask: Callable<T>): this({ actualTask.call() })

        override var isCanceled: Boolean = false
            private set

        override fun cancel() {
            isCanceled = true
        }

        private val isCanceledSupplier: BooleanSupplier = BooleanSupplier { isCanceled }

        override fun call(): T = actualTask(isCanceledSupplier)
    }

    private val es = Executors.newSingleThreadExecutor(factory)

    private var taskRef = AtomicReference<Task<*>?>(null)

    var currentTask: Task<*>? = null

    fun submit(task: Task<*>): Future<Any?> {
        taskRef.set(task)
        return es.submit(Callable {
            synchronized(taskRef) {
                taskRef.getAndSet(null)?.also {
                    currentTask = it
                }
            }?.call()
        })
    }

    fun cancelCurrentTask() = currentTask?.cancel()

    companion object {
        fun <T> asTask(actualTask: (BooleanSupplier) -> T) = TaskDelegate(actualTask)
    }



}
