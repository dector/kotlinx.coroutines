/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.flow.internal

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.internal.*
import kotlinx.coroutines.intrinsics.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
//import kotlinx.coroutines.flow.unsafeFlow as flow

/**
 * Creates a [CoroutineScope] and calls the specified suspend block with this scope.
 * This builder is similar to [coroutineScope] with the only exception that it *ties* lifecycle of children
 * and itself regarding the cancellation, thus being cancelled when one of the children becomes cancelled.
 *
 * For example:
 * ```
 * flowScope {
 *     launch {
 *         throw CancellationException()
 *     }
 * } // <- CE will be rethrown here
 * ```
 */
internal suspend fun <R> flowScope(@BuilderInference block: suspend CoroutineScope.() -> R): R =
    suspendCoroutineUninterceptedOrReturn { uCont ->
        val coroutine = FlowCoroutine<R>(uCont.context, uCont)
        coroutine.startUndispatchedOrReturn(coroutine, block)
    }

/**
 * Creates a flow that also provides a [CoroutineScope] for each collector
 * Shorthand for:
 * ```
 * flow {
 *     flowScope {
 *         ...
 *     }
 * }
 * ```
 * with additional constraint on cancellation.
 * To cancel child without cancelling itself, `cancel(ChildCancelledException())` should be used.
 */
internal fun <R> scopedFlow(@BuilderInference block: suspend CoroutineScope.(FlowCollector<R>) -> Unit): Flow<R> =
    flow<R> {
        val collector = this
        flowScope<Unit> { block(collector) }
    }

internal class FlowCoroutine<T>(context: CoroutineContext, uCont: Continuation<T>) :
    ScopeCoroutine<T>(context, uCont) {

    public override fun childCancelled(cause: Throwable): Boolean {
        if (cause is ChildCancelledException) return true
        return cancelImpl(cause)
    }
}
