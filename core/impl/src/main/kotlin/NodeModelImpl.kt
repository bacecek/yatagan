package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.base.BiObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.HasNodeModel
import com.yandex.daggerlite.core.InjectConstructorBindingModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.ConstructorLangModel
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.impl.Strings.Errors
import com.yandex.daggerlite.validation.impl.reportError
import javax.inject.Inject
import kotlin.LazyThreadSafetyMode.NONE

internal class NodeModelImpl private constructor(
    override val type: TypeLangModel,
    override val qualifier: AnnotationLangModel?,
) : NodeModel {

    init {
        require(!type.isVoid) {
            "Not reached: void can't be represented as NodeModel"
        }
    }

    override val implicitBinding: InjectConstructorBindingModel? by lazy(NONE) {
        if (qualifier == null) {
            type.declaration.constructors.find { it.isAnnotatedWith<Inject>() }?.let {
                InjectConstructorImpl(constructor = it)
            }
        } else null
    }

    private inner class InjectConstructorImpl(
        override val constructor: ConstructorLangModel,
    ) : InjectConstructorBindingModel, ConditionalHoldingModelImpl(constructor.constructee.conditionals) {
        init {
            require(constructor.isAnnotatedWith<Inject>())
        }

        override val inputs: Sequence<NodeDependency> = constructor.parameters.map { param ->
            NodeDependency(type = param.type, forQualifier = param)
        }.memoize()

        override val target: NodeModel
            get() = this@NodeModelImpl

        override val scope: AnnotationLangModel? by lazy {
            constructor.constructee.annotations.find(AnnotationLangModel::isScope)
        }

        override fun validate(validator: Validator) {
            super.validate(validator)
            for (input in inputs) {
                validator.child(input.node)
            }
        }

        override fun toString() = "@Inject $target"
    }

    override fun toString() = buildString {
        qualifier?.let {
            append(qualifier)
            append(' ')
        }
        append(type)
    }

    override fun multiBoundListNode(): NodeModel {
        return Factory(type = LangModelFactory.getListType(type), qualifier = qualifier)
    }

    override fun validate(validator: Validator) {
        if (isFrameworkType(type)) {
            validator.reportError(Errors.`framework type is manually managed`())
        }
    }

    override val superModel: HasNodeModel?
        get() {
            val declaration = type.declaration
            return when {
                ComponentFactoryModelImpl.canRepresent(declaration) -> ComponentFactoryModelImpl(declaration)
                ComponentModelImpl.canRepresent(declaration) -> ComponentFactoryModelImpl(declaration)
                else -> null
            }
        }

    override fun dropQualifier(): NodeModel {
        if (qualifier == null) return this
        return Factory(type = type, qualifier = null)
    }

    companion object Factory : BiObjectCache<TypeLangModel, AnnotationLangModel?, NodeModelImpl>() {
        class NoNode : NodeModel {
            override val type get() = LangModelFactory.errorType
            override val qualifier: Nothing? get() = null
            override val implicitBinding: Nothing? get() = null
            override val superModel: Nothing? get() = null
            override fun dropQualifier(): NodeModel = this
            override fun multiBoundListNode(): NodeModel = this
            override fun validate(validator: Validator) = Unit // No need to report an error here
            override fun toString() = "[invalid]"
        }

        operator fun invoke(
            type: TypeLangModel,
            forQualifier: AnnotatedLangModel?,
        ) = this(type, forQualifier?.annotations?.find(AnnotationLangModel::isQualifier))

        operator fun invoke(
            type: TypeLangModel,
            qualifier: AnnotationLangModel? = null,
        ): NodeModelImpl {
            val decayed = type.decay()
            return createCached(decayed, qualifier) {
                NodeModelImpl(
                    type = decayed,
                    qualifier = qualifier,
                )
            }
        }
    }
}