package com.yandex.daggerlite.graph

import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel

/**
 * Represents a way to provide a [NodeModel].
 * Each [NodeModel] must have a single [BaseBinding] for a [BindingGraph] to be valid.
 */
sealed interface BaseBinding {
    /**
     * A node that this binding provides.
     */
    val target: NodeModel

    /**
     * A graph which hosts the binding.
     */
    val owner: BindingGraph

    /**
     * If binding came from a [ModuleModel] then this is it.
     * If it's intrinsic - `null` is returned.
     */
    val originModule: ModuleModel?
}