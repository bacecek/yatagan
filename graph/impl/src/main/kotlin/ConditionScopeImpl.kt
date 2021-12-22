package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.base.BiObjectCache
import com.yandex.daggerlite.core.lang.AnyConditionAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConditionAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConditionLangModel
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.MemberLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.isGetter
import com.yandex.daggerlite.graph.ConditionScope
import com.yandex.daggerlite.graph.ConditionScope.Literal
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.Validator.ChildValidationKind.Inline
import com.yandex.daggerlite.validation.impl.buildError
import kotlin.LazyThreadSafetyMode.NONE

internal val ConditionScope.Companion.Unscoped get() = ConditionScopeImpl.Unscoped
internal val ConditionScope.Companion.NeverScoped get() = ConditionScopeImpl.NeverScoped

internal fun ConditionScope(conditionModels: Sequence<ConditionLangModel>): ConditionScope {
    return ConditionScopeImpl(conditionModels.map { conditionModel ->
        when (conditionModel) {
            is AnyConditionAnnotationLangModel ->
                conditionModel.conditions.map { ConditionLiteralImpl(it) }.toSet()
            is ConditionAnnotationLangModel ->
                setOf(ConditionLiteralImpl(conditionModel))
        }
    }.toSet())
}

private class ConditionScopeImpl(
    override val expression: Set<Set<Literal>>,
) : ConditionScope {
    override fun contains(another: ConditionScope): Boolean {
        TODO("Required in validation step - implement later")
    }

    override fun and(rhs: ConditionScope): ConditionScope {
        if (this === Unscoped) return rhs
        if (rhs === Unscoped) return this
        return ConditionScopeImpl(expression + rhs.expression)
    }

    override fun or(rhs: ConditionScope): ConditionScope {
        if (this === NeverScoped) return rhs
        if (rhs === NeverScoped) return this
        return ConditionScopeImpl(buildSet {
            for (p in expression) for (q in rhs.expression) {
                add(p + q)
            }
        })
    }

    override fun not(): ConditionScope {
        fun impl(clause: Set<Literal>, rest: Set<Set<Literal>>): Set<Set<Literal>> {
            return clause.fold(emptySet()) { acc, literal ->
                acc + if (rest.isEmpty()) {
                    setOf(setOf(literal))
                } else {
                    impl(rest.first(), rest.drop(1).toSet()).map { it + !literal }
                }
            }
        }

        return when (this) {
            Unscoped -> NeverScoped
            NeverScoped -> Unscoped
            else -> ConditionScopeImpl(impl(expression.first(), expression.drop(1).toSet()))
        }
    }

    override val isAlways: Boolean
        get() = this === Unscoped || expression.isEmpty()

    override val isNever: Boolean
        get() = this === NeverScoped || expression.singleOrNull()?.isEmpty() == true

    override fun validate(validator: Validator) {
        for (literal: Literal in expression.asSequence().flatten().toSet()) {
            validator.child(literal)
        }
    }

    companion object {
        val Unscoped: ConditionScope = ConditionScopeImpl(emptySet())
        val NeverScoped: ConditionScope = ConditionScopeImpl(setOf(emptySet()))
    }
}

private class ConditionLiteralImpl private constructor(
    override val negated: Boolean,
    private val payload: LiteralPayload,
) : Literal {

    override fun not(): Literal = Factory(
        negated = !negated,
        payload = payload,
    )

    override val path get() = payload.path

    override val root get() = payload.root

    override fun validate(validator: Validator) {
        validator.child(payload, kind = Inline)
    }

    companion object Factory : BiObjectCache<Boolean, LiteralPayload, ConditionLiteralImpl>() {
        operator fun invoke(model: ConditionAnnotationLangModel): Literal {
            val condition = model.condition
            return ConditionRegex.matchEntire(condition)?.let { matched ->
                val (negate, names) = matched.destructured
                this(
                    negated = negate.isNotEmpty(),
                    payload = LiteralPayloadImpl(model.target.declaration, names),
                )
            } ?: this(
                negated = false,
                payload = object : LiteralPayload {
                    override val root: TypeDeclarationLangModel get() = LangModelFactory.errorType.declaration
                    override val path: List<MemberLangModel> get() = emptyList()
                    override fun validate(validator: Validator) {
                        // Always invalid
                        validator.report(buildError {
                            contents = "Invalid condition expression '$condition'"
                        })
                    }
                }
            )
        }

        private operator fun invoke(
            negated: Boolean,
            payload: LiteralPayload,
        ) = createCached(negated, payload) {
            ConditionLiteralImpl(negated, payload)
        }

        private val ConditionRegex = "^(!?)((?:[A-Za-z][A-Za-z0-9_]*\\.)*[A-Za-z][A-Za-z0-9_]*)\$".toRegex()
    }
}

private interface LiteralPayload : MayBeInvalid {
    val path: List<MemberLangModel>
    val root: TypeDeclarationLangModel
}

private class LiteralPayloadImpl private constructor(
    override val root: TypeDeclarationLangModel,
    private val pathSource: String,
) : LiteralPayload {
    private var pathParsingError: String? = null

    override fun validate(validator: Validator) {
        path  // Ensure path is parsed
        pathParsingError?.let { message ->
            validator.report(buildError {
                contents = message
            })
        }
    }

    override val path: List<MemberLangModel> by lazy(NONE) {
        buildList {
            var currentType = root.asType()
            var finished = false

            pathSource.split('.').forEach { rawName ->
                if (finished) {
                    pathParsingError = "Unable to reach boolean result with the given condition"
                    return@forEach
                }

                val member = findAccessor(currentType.declaration, rawName)
                if (member == null) {
                    pathParsingError = "Can't find accessible '$rawName' member in $currentType"
                    return@forEach
                }
                add(member)

                val type = when (member) {
                    is FunctionLangModel -> member.returnType
                    is FieldLangModel -> member.type
                }
                if (type.isBoolean) {
                    finished = true
                } else {
                    currentType = type
                }
            }
            if (!finished) {
                pathParsingError = "Unable to reach boolean result with the given condition"
            }
        }
    }

    companion object Factory : BiObjectCache<TypeDeclarationLangModel, String, LiteralPayload>() {
        operator fun invoke(root: TypeDeclarationLangModel, pathSource: String) : LiteralPayload {
            return createCached(root, pathSource) {
                LiteralPayloadImpl(root, pathSource)
            }
        }

        private fun findAccessor(type: TypeDeclarationLangModel, name: String): MemberLangModel? {
            val field = type.allPublicFields.find { it.name == name }
            if (field != null) {
                return field
            }

            val allMethods = type.allPublicFunctions
            val method = allMethods.find { function ->
                function.propertyAccessorInfo?.let {
                    // If this is a kotlin property getter, then look for property name
                    it.isGetter && it.propertyName == name
                } ?: (function.name == name)
            }
            if (method != null) {
                return method
            }
            return null
        }
    }
}