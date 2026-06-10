package com.koupper.cli.commands

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ProviderScaffoldTest {

    @Test
    fun `provider new should create scaffold files`() {
        val root = System.getProperty("user.dir")
        val providerName = "scaffold-test"
        val id = "scaffoldtest"
        val className = "ScaffoldTest"
        
        val command = ProviderCommand()
        val result = command.execute("koupper", "new", providerName)
        
        System.out.println("DEBUG: Command Output Begin")
        System.out.println(result)
        System.out.println("DEBUG: Command Output End")
        
        val plainOutput = result.replace(Regex("\u001B\\[[;\\d]*m"), "")
        
        assertTrue(plainOutput.contains("Scaffolding new provider: $className"), "Output should contain success message. Output was: $plainOutput")
        
        val filesToCheck = listOf(
            "koupper/providers/src/main/kotlin/com/koupper/providers/$id/${className}Provider.kt",
            "koupper/providers/src/main/kotlin/com/koupper/providers/$id/${className}ServiceProvider.kt",
            "koupper/providers/src/test/kotlin/com/koupper/providers/$id/${className}ProviderTest.kt",
            "koupper-docs/docs/providers/$id.md"
        )
        
        try {
            filesToCheck.forEach { path ->
                val file = File(root, path)
                assertTrue(file.exists(), "File should exist: $path")
                
                val content = file.readText()
                assertTrue(content.contains(className), "Content should contain class name in $path")
                assertTrue(content.contains(id), "Content should contain package/id in $path")
            }
        } finally {
            // Cleanup
            File(root, "koupper/providers/src/main/kotlin/com/koupper/providers/$id").deleteRecursively()
            File(root, "koupper/providers/src/test/kotlin/com/koupper/providers/$id").deleteRecursively()
            File(root, "koupper-docs/docs/providers/$id.md").delete()
        }
    }
}
