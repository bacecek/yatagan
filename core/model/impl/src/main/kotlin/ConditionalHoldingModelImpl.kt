package com.yandex.daggerlite.core.model.impl

import com.yandex.daggerlite.core.model.ConditionalHoldingModel
import com.yandex.daggerlite.core.model.ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel
import com.yandex.daggerlite.core.model.ConditionalHoldingModel.FeatureModel
import com.yandex.daggerlite.core.model.Variant.FlavorModel
import com.yandex.daggerlite.lang.BuiltinAnnotation
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.appendChildContextReference
import com.yandex.daggerlite.validation.format.modelRepresentation

internal open class ConditionalHoldingModelImpl(
    sources: List<BuiltinAnnotation.Conditional>,
) : ConditionalHoldingModel {
    final override val conditionals: List<ConditionalWithFlavorConstraintsModel> = sources.map { annotation ->
        ConditionalWithFlavorConstraintsModelImpl(annotation)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "its conditions declaration",
        representation = {
            append("{ ")
            when (childContext) {
                is FlavorModel -> {
                    append("component-flavor-constraints (onlyIn): [.., ")
                    appendChildContextReference(reference = childContext.type)
                    append(", ..]")
                }
                is FeatureModel -> {
                    append("runtime-conditions: [.., ")
                    appendChildContextReference(reference = childContext.type)
                    append(", ..]")
                }
                else -> append("...")
            }
            append(" }")
        },
    )

    private class ConditionalWithFlavorConstraintsModelImpl(
        annotation: BuiltinAnnotation.Conditional,
    ) : ConditionalWithFlavorConstraintsModel {
        override val onlyIn: List<FlavorModel> =
            annotation.onlyIn.map { FlavorImpl(it) }
        override val featureTypes: List<FeatureModel> =
            annotation.featureTypes.map { FeatureModelImpl(it.declaration) }

        override fun validate(validator: Validator) {
            onlyIn.forEach(validator::child)
            featureTypes.forEach(validator::child)
        }

        override fun toString(childContext: MayBeInvalid?) = throw AssertionError("not reached")
    }

    override fun validate(validator: Validator) {
        conditionals.forEach(validator::inline)
    }

}