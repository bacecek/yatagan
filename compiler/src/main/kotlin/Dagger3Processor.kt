package com.yandex.dagger3.compiler

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.yandex.dagger3.core.BindingGraph
import com.yandex.dagger3.generator.ComponentGenerator
import com.yandex.dagger3.generator.GenerationLogger

internal class Dagger3Processor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val logger = KspGenerationLogger(environment.logger)

        resolver.getSymbolsWithAnnotation(AnnotationNames.Component)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { symbol ->
                val model = KspComponentModel(symbol)
                val graph = BindingGraph(
                    root = model,
                )
                if (graph.missingBindings.isNotEmpty()) {
                    graph.missingBindings.forEach { node ->
                        logger.error("Missing binding for $node")
                    }
                    return@forEach
                }
                val generator = ComponentGenerator(
                    logger = logger,
                    graph = graph,
                )
                environment.codeGenerator.createNewFile(
                    Dependencies(
                        aggregating = false,
                    ),
                    packageName = generator.targetPackageName,
                    fileName = generator.targetClassName,
                    extensionName = generator.targetLanguage.extension,
                ).use { file ->
                    file.bufferedWriter().use { writer ->
                        generator.generateTo(
                            out = writer,
                        )
                    }
                }
            }
        return emptyList()
    }
}

internal class KspGenerationLogger(
    private val logger: KSPLogger,
) : GenerationLogger {
    override fun error(message: String) {
        logger.error(message /*TODO: support where*/)
    }

    override fun warning(message: String) {
        logger.warn(message /*TODO: support where*/)
    }
}