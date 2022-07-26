package com.yandex.daggerlite.graph

/**
 * A hierarchy trait, that allows accessing optional parent nodes.
 */
interface WithParents<P> where P : WithParents<P> {
    /**
     * Parent node. `null` if root is reached.
     */
    val parent: P?
}