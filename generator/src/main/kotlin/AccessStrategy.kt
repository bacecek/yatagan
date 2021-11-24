package com.yandex.daggerlite.generator

import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.graph.BindingGraph

internal interface AccessStrategy {
    fun generateInComponent(builder: TypeSpecBuilder) {
        // nothing by default
    }

    /**
     * Generates access expression [inside] the given component.
     */
    fun generateAccess(builder: ExpressionBuilder, kind: DependencyKind, inside: BindingGraph)
}