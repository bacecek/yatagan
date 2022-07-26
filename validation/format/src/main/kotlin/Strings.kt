package com.yandex.daggerlite.validation.format

import com.yandex.daggerlite.core.AssistedInjectFactoryModel
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ConditionScope
import com.yandex.daggerlite.core.ConditionalHoldingModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.lang.AnnotationDeclarationLangModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.ConstructorLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.MemberLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.graph.AliasBinding
import com.yandex.daggerlite.graph.BaseBinding
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.BindingGraph

object Strings {
    private const val Indent = "    "

    /**
     * Just a marker for a message string function, that the corresponding case is covered with test(s).
     */
    @Retention(AnnotationRetention.SOURCE)
    private annotation class Covered

    object Errors {
        @Covered
        fun missingBinding(`for`: NodeModel) = buildRichString {
            color = TextColor.Inherit
            append("Missing binding for ").append(`for`)
        }.toError()

        @Covered
        fun noMatchingScopeForBinding(binding: Binding, scopes: Set<AnnotationLangModel>) = buildRichString {
            color = TextColor.Inherit
            appendLine("No components in the hierarchy match binding -> ")
            append(Indent).append(binding).appendLine()
            append(" -> with ").appendMaybeMultiple(
                collection = scopes,
                prefixOnSingle = "scope ",
                prefixOnMultiple = "scopes [",
                postfixOnMultiple = "]",
                transform = { scope -> buildRichString { append(scope) } }
            )
        }.toError()

        @Covered
        fun invalidFlatteningMultibinding(insteadOf: TypeLangModel) =
            "Flattening multi-binding must return `Collection` or any of its subtypes instead of `$insteadOf`".toError()

        @Covered
        fun voidBinding() =
            "Binding method must not return `void`".toError()

        @Covered
        fun nonAbstractBinds() =
            "@Binds annotated method must be abstract".toError()

        @Covered
        fun abstractProvides() =
            "@Provides annotated method must not be abstract (must have a body)".toError()

        @Covered
        fun selfDependentBinding() =
            "Binding depends on itself".toError()

        @Covered
        fun inconsistentBinds(param: TypeLangModel, returnType: TypeLangModel) =
            "@Binds parameter `$param` is not compatible with its return type `$returnType`".toError()

        @Covered
        fun incompatibleCondition(
            aCondition: ConditionScope,
            bCondition: ConditionScope,
            a: Binding, b: Binding,
        ) = buildRichString {
            color = TextColor.Inherit
            append("`").append(a).appendLine("` with a condition:")
            append(Indent).append("(1) ").append(aCondition).appendLine()
            append("cannot be injected into `").append(b).appendLine("` with a condition:")
            append(Indent).append("(2) ").append(bCondition).appendLine()
            append("without Optional<..> wrapper, because condition (2) does not imply condition (1)")
        }.toError()

        @Covered
        fun incompatibleConditionEntryPoint(
            aCondition: ConditionScope, bCondition: ConditionScope,
            binding: Binding, component: BindingGraph,
        ) = buildRichString {
            color = TextColor.Inherit
            append("`").append(binding).appendLine("` with a condition:")
            append(Indent).append("(1) ").append(aCondition).appendLine()
            append("cannot be directly exposed from an entry-point of `").append(component)
                .appendLine("` under condition:")
            append(Indent).append("(2) ").append(bCondition).appendLine()
            append("without Optional<..> wrapper, because component condition (2) does not imply condition (1)")
        }.toError()

        @Covered
        fun invalidBuilderSetterReturn(creatorType: Any) =
            ("Setter method in component creator must return either `void` " +
                    "or creator type itself (`$creatorType`)").toError()

        @Covered
        fun nonInterfaceCreator() =
            "Component creator declaration must be an `interface`".toError()

        @Covered
        fun missingCreatingMethod() =
            ("Component creator is missing a creating method - " +
                    "an abstract method which returns the component interface").toError()

        @Covered
        fun unknownMethodInCreator(method: FunctionLangModel) = buildRichString {
            color = TextColor.Inherit
            appendLine("Unexpected/unrecognized method")
            append(Indent).append("`").appendRichString { append(method) }.appendLine('`')
            append(" for component creator interface")
        }.toError()

        @Covered
        fun missingComponentDependency(missing: ComponentDependencyModel) = buildRichString {
            color = TextColor.Inherit
            append("Declared `").append(missing).append("` is not passed to component creator")
        }.toError()

        @Covered
        fun extraComponentDependency() =
            "Unrecognized type (component dependency?) is present".toError()

        @Covered
        fun missingModule(missing: ModuleModel) = buildRichString {
            color = TextColor.Inherit
            append("Declared `").append(missing).append("` requires instance but it is not passed to component creator")
        }.toError()

        @Covered
        fun extraModule() =
            "Extra/unneeded module instance is present".toError()


        @Covered
        fun invalidInjectorReturn() =
            "Injector method should return `void`/`Unit`.".toError()


        @Covered
        fun multipleCreators() =
            "Multiple component factories detected declared".toError()

        @Covered
        fun unknownMethodInComponent(method: FunctionLangModel) = buildRichString {
            color = TextColor.Inherit
            appendLine("Unexpected method")
            append(Indent).appendRichString{ append(method) }.appendLine()
            append(" in component declaration")
        }.toError()

        @Covered
        fun nonComponent() =
            "Type declaration is used as a component yet not annotated with `@Component`".toError()

        @Covered
        fun nonInterfaceComponent() =
            "Component declaration must be an `interface`".toError()

        @Covered
        fun missingCreatorForNonRoot() =
            "Non-root component declaration must include creator declaration".toError()

        @Covered
        fun missingCreatorForDependencies() =
            "Component declares dependencies, yet no creator declaration is present".toError()

        @Covered
        fun missingCreatorForModules() =
            ("Component includes non-trivially constructable modules that require object instance, " +
                    "yet no creator declaration is present").toError()


        @Covered
        fun noConditionsOnFeature() =
            "Feature declaration has no `@Condition`-family annotations on it.".toError()


        @Covered
        fun nonComponentVariantDimension() =
            ("Type declaration is used as a component variant dimension, " +
                    "yet not annotated with @ComponentVariantDimension").toError()


        @Covered
        fun nonFlavor() =
            "Type declaration is used as a component flavor, yet not annotated with @ComponentFlavor".toError()


        @Covered
        fun nonModule() =
            "Type declaration is used as a module, yet not annotated with @Module".toError()


        @Covered
        fun manualFrameworkType() =
            "Framework types (Lazy, Provider, Optional) can't be manually managed (provided/bound)".toError()


        @Covered
        fun conflictingOrDuplicateFlavors(dimension: Variant.DimensionModel) = buildRichString {
            color = TextColor.Inherit
            append("Duplicate flavors for a single `").append(dimension).append("`")
        }.toError()


        @Covered
        fun undeclaredDimension(dimension: Variant.DimensionModel) = buildRichString {
            color = TextColor.Inherit
            append("No flavor is declared for `").append(dimension).append("` in a variant")
        }.toError()

        fun variantMatchingAmbiguity(
            one: ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel,
            two: ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel,
        ) = buildRichString {
            color = TextColor.Inherit
            append("Variant matching ambiguity: `").append(one).append("` vs `")
                .append(two).append("` could not be resolved")
        }.toError()


        @Covered
        fun invalidCondition(expression: String) =
            "Invalid condition expression '$expression'".toError()

        @Covered
        fun invalidConditionNoBoolean() =
            "Unable to reach boolean result in the given expression".toError()

        @Covered
        fun invalidConditionMissingMember(name: String, type: TypeLangModel) =
            "Can not find accessible `$name` member in $type".toError()

        @Covered
        fun invalidNonStaticMember(name: String, type: TypeLangModel) =
            "Member `$name` in $type is not static".toError()


        @Covered
        fun conflictingBindings(`for`: NodeModel) = buildRichString {
            color = TextColor.Inherit
            append("Conflicting bindings for `").append(`for`).append("`")
        }.toError()

        @Covered
        fun rootAsChild() =
            "Root component can not be a subcomponent".toError()

        @Covered
        fun duplicateComponentScope(scope: AnnotationLangModel) =
            "A single scope `$scope` can not be present on more than one component in a hierarchy".toError()

        @Covered
        fun componentLoop() =
            "Component hierarchy loop detected".toError()

        @Covered
        fun multiThreadStatusMismatch(parent: BindingGraph) = buildRichString {
            color = TextColor.Inherit
            append("Component declares a multi-threaded requirement, but its parent `").append(parent)
                .append("` does not. ").append("Please, specify the same requirement for the parent.")
        }.toError()

        @Covered
        fun dependencyLoop(chain: List<BaseBinding>) = buildRichString {
            color = TextColor.Inherit
            appendLine("Binding dependency loop detected:")
            chain.forEachIndexed { index, binding ->
                if (index == 0) append("(*) ") else append("    ")
                append('`').appendRichString{ append(binding.target) }.append("` provided by `")
                    .append(binding).append("` depends on <-")
                if (index != chain.lastIndex) {
                    appendLine()
                }
            }
            append(" (*)")
        }.toError()

        @Covered
        fun assistedInjectFactoryNotInterface() =
            "Assisted inject factory must be an interface.".toError()

        @Covered
        fun assistedInjectTypeNoConstructor(type: TypeLangModel) =
            "Type `$type` doesn't have an @AssistedInject constructor.".toError()

        @Covered
        fun assistedInjectFactoryNoMethod() =
            "Assisted inject factory must contain exactly one abstract factory method.".toError()

        @Covered
        fun assistedInjectDuplicateParameters() =
            "@AssistedInject constructor's @Assisted parameters contain duplicate types/ids.".toError()

        @Covered
        fun assistedInjectFactoryDuplicateParameters() =
            "factory method's @Assisted parameters contain duplicate types/ids.".toError()

        @Covered
        fun assistedInjectMismatch() =
            ("@AssistedInject constructor @Assisted parameters and " +
                    "factory function @Assisted parameters do not match.").toError()

        private const val AccessMessage = "public/internal"

        @Covered
        fun invalidAccessForProvides() =
            "@Provides-annotated methods must be $AccessMessage.".toError()

        @Covered
        fun invalidAccessForMemberToInject(member: MemberLangModel) =
            "@Inject member `$member` must be $AccessMessage.".toError()

        @Covered
        fun invalidAccessForModuleClass() =
            "Module contains provisions and thus must be $AccessMessage.".toError()

        @Covered
        fun invalidAccessForConditionClass(`class`: TypeDeclarationLangModel) =
            "Class `$`class`` is not accessible for condition computation, make it $AccessMessage".toError()

        @Covered
        fun invalidAccessForConditionMember(member: MemberLangModel) =
            "Member `$member` is not accessible for condition computation, make it $AccessMessage".toError()

        @Covered
        fun invalidAccessInjectConstructor() =
            "@Inject constructor and the class it is defined in must be $AccessMessage.".toError()

        @Covered
        fun invalidAccessForMemberInject() =
            "A class for members injection must be $AccessMessage.".toError()

        @Covered
        fun invalidAccessForAssistedInject() =
            "An @AssistedInject constructor and the declaring class must be $AccessMessage.".toError()

        @Covered
        fun missingMapKey() =
            "Binding is annotated with @IntoMap yet no @IntoMap.Key-annotated annotation is present".toError()

        @Covered
        fun multipleMapKeys() =
            "Multiple IntoMap.Key-annotations are present on the binding".toError()

        @Covered
        fun missingMapKeyValue(annotationClass: AnnotationDeclarationLangModel) =
            "Map key `$annotationClass` is missing a `value` attribute to be used as a key value".toError()

        @Covered
        fun unsupportedAnnotationValueAsMapKey(annotationClass: AnnotationDeclarationLangModel) =
            ("Map key `$annotationClass`'s `value` attribute has annotation type, " +
                    "which is not supported as a map key").toError()

        @Covered
        fun unsupportedArrayValueAsMapKey(annotationClass: AnnotationDeclarationLangModel) =
            ("Map key `$annotationClass`'s `value` attribute has array type, " +
                    "which is not supported as a map key").toError()

        @Covered
        fun duplicateKeysInMapping(mapType: NodeModel, keyValue: AnnotationLangModel.Value) = buildRichString {
            color = TextColor.Inherit
            append("Mapping for `").append(mapType).append("` contains duplicates for key `")
                .append(keyValue).append("`")
        }.toError()
    }

    object Warnings {
        @Covered
        fun scopeRebindIsForbidden() = "Scope has no effect on 'alias' binding".toWarning()

        @Covered
        fun ignoredDependencyOfFrameworkType(function: Any) = buildRichString {
            color = TextColor.Inherit
            appendLine("function")
            append(Indent).appendRichString { append(function) }.appendLine()
            append("returns a framework type (Provider/Lazy/Optional) and such type can not be " +
                    "directly introduced to the graph via component dependency - the function will be ignored. " +
                    "If you need this to form a binding - change the return type, or use a wrapper type. " +
                    "Otherwise remove the function from the dependency interface entirely.")
        }.toWarning()

        @Covered
        fun nonAbstractDependency() =
            ("Component dependency declaration is not abstract. If it is already known how to provide necessary " +
                    "dependencies for the graph, consider using Inject-constructors or a @Module with " +
                    "regular provisions instead.").toWarning()

        @Covered
        fun ignoredBindsInstance() =
            ("A parameter of a builder's method is annotated with @BindsInstance, which has no effect. " +
                    "Maybe you meant to annotate the method itself for it to work as a binding?").toWarning()

        @Covered
        fun fieldInjectShadow(name: String) =
            ("A class hierarchy contains more than one @Inject field with the name \"$name\", " +
                    "which is not supported").toWarning()
    }

    object Notes {
        @Covered
        fun infoOnScopeRebind() = ("Scope is inherited from the source graph node and can not be overridden. " +
                "Use multiple scopes on the source node to declare it compatible with another scope, " +
                "if required.").toNote()

        @Covered
        fun unknownBinding() =
            "No known way to infer the binding".toNote()

        @Covered
        fun missingModuleInstance(module: ModuleModel) = buildRichString {
            color = TextColor.Inherit
            append("Instance of `").append(module).append("` must be provided")
        }.toNote()

        @Covered
        fun conflictingCreator(creator: TypeDeclarationLangModel) = buildRichString {
            color = TextColor.Inherit
            append("Declared `").append(creator).append('`')
        }.toNote()

        @Covered
        fun duplicateBinding(binding: BaseBinding) = buildRichString {
            color = TextColor.Inherit
            append("Conflicting binding: `").append(binding).append('`')
        }.toNote()

        fun duplicateKeyInMapBinding(binding: BaseBinding) = buildRichString {
            color = TextColor.Inherit
            append("Binding with conflicting key: `").append(binding).append('`')
        }.toNote()

        @Covered
        fun duplicateScopeComponent(component: BindingGraph) = buildRichString {
            color = TextColor.Inherit
            append("In component `").append(component).append('`')
        }.toNote()

        @Covered
        fun nestedFrameworkType(target: NodeModel) = buildRichString {
            append("`").append(target).append("` can't be requested in any way (lazy/provider/optional) but ")
            appendRichString {
                isBold = true
                append("directly")
            }
        }.toNote()

        fun subcomponentFactoryInjectionHint(
            factory: ComponentFactoryModel,
            component: ComponentModel,
            owner: BindingGraph,
        ) = buildRichString {
            color = TextColor.Inherit
            append(factory)
            append(" is a factory for `")
            append(component)
            append(", ensure that this component is specified " +
                    "via `@Module(subcomponents=..)` and that module is included into `")
            append(owner)
            append('`')
        }.toNote()

        fun inaccessibleAutoConstructorForMissingModule(constructor: ConstructorLangModel) =
            ("found effectively not public parameterless constructor here: `$constructor`" +
                    "maybe make it public or internal to allow automatic module creation?").toNote()

        fun suspiciousComponentInstanceInject() =
            "A dependency seems to be a component, though it does not belong to the current hierarchy.".toNote()

        fun suspiciousRootComponentFactoryInject(factoryLike: NodeModel) = buildRichString {
            append('`').append(factoryLike).append(" is a factory for a root component, injecting such " +
                    "factory is not supported")
        }.toNote()

        @Covered
        fun undeclaredModulePresent() =
            "The module is undeclared, i. m. not present in @Component(.., module = [<here>], ..)".toNote()

        fun moduleDoesNotRequireInstance() =
            "The module doesn't declare any bindings that require module instance".toNote()

        @Covered
        fun conditionPassedThroughAliasChain(
            aliases: List<AliasBinding>,
        ) = buildRichString {
            color = TextColor.Inherit
            append("Condition passed through ")
            appendMaybeMultiple(
                collection = aliases,
                prefixOnSingle = "the ",
                prefixOnMultiple = "the alias chain: [\n  ",
                postfixOnMultiple = ",\n]",
                separator = ",\n  ",
                transform = {
                    buildRichString {
                        append("`").append(it).append(": ").append(it.target).append("`")
                    }
                },
            )
        }.toNote()

        @Covered
        fun adviceBindInstanceForUnknownInput() =
            "To bind the instance itself, use @BindInstance marker".toNote()

        @Covered
        fun adviceComponentDependencyForUnknownInput() =
            ("To use the instance as component dependency, explicitly declare it in " +
                    "@Component(.., dependencies = [<here>], ..)").toNote()

        @Covered
        fun assistedInjectMismatchFromConstructor(params: Set<AssistedInjectFactoryModel.Parameter.Assisted>) =
            "From @AssistedInject constructor: $params".toNote()

        @Covered
        fun assistedInjectMismatchFromFactory(params: Set<AssistedInjectFactoryModel.Parameter.Assisted>) =
            "From assisted factory's method: $params".toNote()

        fun conflictingFlavorInVariant(flavor: Variant.FlavorModel) = buildRichString {
            color = TextColor.Inherit
            append("Conflicting: `").append(flavor).append('`')
        }.toNote()
    }
}