package com.yandex.daggerlite.graph

import com.yandex.daggerlite.core.AssistedInjectFactoryModel
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ConditionModel
import com.yandex.daggerlite.core.ConditionScope
import com.yandex.daggerlite.core.HasNodeModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.graph.BindingGraph.LiteralUsage.Eager
import com.yandex.daggerlite.graph.BindingGraph.LiteralUsage.Lazy
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * A Fully built dagger-lite graph of [Binding]s.
 *
 * Each [BindingGraph] is built around [ComponentModel]. For each given [ComponentModel] multiple different
 * [BindingGraph]s may exist if [ComponentModel.isRoot] is `false`, because the model may have different parents.
 */
interface BindingGraph : MayBeInvalid, Extensible, WithParents<BindingGraph>, WithChildren<BindingGraph> {
    /**
     * A model behind this graph.
     */
    val model: HasNodeModel

    /**
     * Requested bindings that are **hosted** in this component.
     * Consists of bindings directly requested by entryPoints plus bindings requested by sub-graphs.
     *
     * Thus, every binding that is present in this collection has it [owner][Binding.owner] set to `this` graph.
     *
     * The associated info is [BindingUsage].
     */
    val localBindings: Map<Binding, BindingUsage>

    /**
     * All [condition literals][ConditionScope.Literal]s that are **hosted** in this component.
     * Consists of literals directly used by bindings in this and children graphs.
     *
     * All literals are [normalized][ConditionScope.Literal.normalized].
     *
     * The associated info is [LiteralUsage].
     */
    val localConditionLiterals: Map<ConditionModel, LiteralUsage>

    /**
     * [AssistedInjectFactoryModel]s that are hosted in this graph.
     */
    val localAssistedInjectFactories: Collection<AssistedInjectFactoryModel>

    /**
     * A collection of parent (not necessarily direct) [BindingGraph]s, from which bindings and/or conditions are used
     * to satisfy dependencies from `this` graph.
     */
    val usedParents: Set<BindingGraph>

    /**
     * See [ComponentModel.isRoot].
     */
    val isRoot: Boolean

    /**
     * Graph variant (full - merged with parents)
     *
     * @see ComponentModel.variant
     */
    val variant: Variant

    /**
     * Modules of the underlying model.
     *
     * @see ComponentModel.modules
     */
    val modules: Collection<ModuleModel>

    /**
     * Component dependencies declared in the underlying model.
     *
     * @see ComponentModel.dependencies
     */
    val dependencies: Collection<ComponentDependencyModel>

    /**
     * Graph scopes.
     *
     * @see ComponentModel.scopes
     */
    val scopes: Set<AnnotationLangModel>

    /**
     * Component creator model declared in the underlying model.
     *
     * @see [ComponentModel.factory]
     */
    val creator: ComponentFactoryModel?

    /**
     * All explicit graph entry-points - getter functions declared in the underlying model.
     *
     * @see [ComponentModel.entryPoints]
     */
    val entryPoints: Collection<GraphEntryPoint>

    /**
     * All injector functions (a kind of entry-points) declared in the underlying model.
     *
     * @see [ComponentModel.memberInjectors]
     */
    val memberInjectors: Collection<GraphMemberInjector>

    /**
     * A condition for this graph.
     *
     * Equals [Always][com.yandex.daggerlite.core.ConditionExpression.Unscoped] for [root][BindingGraph.isRoot] components.
     * Arbitrary for non-root components.
     */
    val conditionScope: ConditionScope

    /**
     * Multi-thread access status declared in the underlying model.
     *
     * @see ComponentModel.requiresSynchronizedAccess
     */
    val requiresSynchronizedAccess: Boolean

    /**
     * Resolves binding for the given node. Resulting binding may belong to this graph or any parent one.
     *
     * @return resolved binding (it may be [EmptyBinding] due to
     * [NeverScoped][com.yandex.daggerlite.core.ConditionExpression.NeverScoped] or because it was requested and
     * could not be satisfied)
     *
     * @throws IllegalStateException if no such binding is found at all (requested but missing bindings are still safe
     * to request).
     */
    fun resolveBinding(node: NodeModel): Binding

    /**
     * Behaves as [resolveBinding] only doesn't follow aliases.
     */
    fun resolveBindingRaw(node: NodeModel): BaseBinding

    /**
     * Provides counts of each dependency [kind][com.yandex.daggerlite.core.DependencyKind] requests for the
     * [target][Binding.target].
     */
    interface BindingUsage {
        val direct: Int
        val provider: Int
        val lazy: Int
        val optional: Int
        val optionalLazy: Int
        val optionalProvider: Int
    }

    /**
     * Literal usage kind depending on expression position.
     *
     * In expression `A && B || C`, `A` is [Eager], while `B`, `C` are [Lazy].
     * In other words, a literal is [Eager] iff it is present in eager position at in at least one expression.
     */
    enum class LiteralUsage {
        /**
         * When a literal is certainly going to be evaluated.
         */
        Eager,

        /**
         * When a literal evaluation may be skipped due to a short-circuit expression evaluation.
         * Such literal should be evaluated according to the short-circuit rules.
         */
        Lazy,
    }
}
