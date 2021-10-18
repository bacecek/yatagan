package com.yandex.daggerlite.core

/**
 * Represents [dagger.Module].
 */
interface ModuleModel : ClassBackedModel {
    /**
     * Bindings exposed by this module.
     */
    val bindings: Collection<Binding>

    /**
     * Subcomponents installed by this module.
     */
    val subcomponents: Collection<ComponentModel>
}