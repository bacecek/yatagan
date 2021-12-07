package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.FunctionLangModel

/**
 * Component dependency.
 * Every "getter" is exposed as a graph-level binding.
 *
 * @see com.yandex.daggerlite.Component.dependencies
 */
interface ComponentDependencyModel : ClassBackedModel {
    val exposedDependencies: Map<NodeModel, FunctionLangModel>

    fun asNode(): NodeModel
}