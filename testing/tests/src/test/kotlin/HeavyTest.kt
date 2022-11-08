package com.yandex.yatagan.testing.tests

import com.yandex.yatagan.testing.procedural.Distribution
import com.yandex.yatagan.testing.procedural.ExperimentalGenerationApi
import com.yandex.yatagan.testing.procedural.GenerationParams
import com.yandex.yatagan.testing.procedural.generate
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import javax.inject.Provider

@RunWith(Parameterized::class)
@OptIn(ExperimentalGenerationApi::class)
class HeavyTest(
    driverProvider: Provider<CompileTestDriverBase>
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    @Test
    fun `base case`() {
        val sourceDir = Files.createTempDirectory("dagger-lite-test").toFile()
        try {
            val params = GenerationParams(
                componentTreeMaxDepth = 6,
                totalGraphNodesCount = 300,
                bindings = Distribution.build {
                    GenerationParams.BindingType.Alias exactly 0.1
                    GenerationParams.BindingType.ComponentDependencyEntryPoint exactly 0.08
                    GenerationParams.BindingType.ComponentDependency exactly 0.0
                    GenerationParams.BindingType.Instance exactly 0.08
                    theRestUniformly()
                },
                maxProvisionDependencies = 30,
                provisionDependencyKind = Distribution.build {
                    GenerationParams.DependencyKind.Direct exactly 0.8
                    theRestUniformly()
                },
                maxEntryPointsPerComponent = 15,
                maxMemberInjectorsPerComponent = 3,
                maxMembersPerInjectee = 10,
                totalRootCount = 3,
                maxChildrenPerComponent = 5,
                totalRootScopes = 1,
                maxChildrenPerScope = 5,
                seed = 1236473289L,
            )

            generate(
                params = params,
                sourceDir = sourceDir,
                testMethodName = "test",
            )

            includeAllFromDirectory(sourceDir)

            compileRunAndValidate()
        } finally {
            sourceDir.deleteRecursively()
        }
    }
}