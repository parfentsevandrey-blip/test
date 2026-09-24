package app.opal.buildlogic

import groovy.json.JsonSlurper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Refreshes the bundled built-in bridges from tor-browser-build (the same file Tor Browser ships).
 * Run by hand (`./gradlew :core:tunnel:updateBuiltinBridges`), review the diff, commit. Never part
 * of a normal build: builds must be reproducible and work offline.
 */
@DisableCachingByDefault(because = "Downloads a moving upstream file")
abstract class UpdateBuiltinBridgesTask : DefaultTask() {

    @get:Input abstract val url: Property<String>

    @get:OutputFile abstract val target: RegularFileProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun update() {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
        val request = HttpRequest.newBuilder(URI(url.get())).timeout(Duration.ofSeconds(60)).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) throw GradleException("HTTP ${response.statusCode()} for ${url.get()}")
        val text = response.body()
        validate(text)
        val file = target.get().asFile
        val old = if (file.exists()) file.readText() else ""
        if (old == text) {
            logger.lifecycle("Built-in bridges are up to date.")
        } else {
            file.writeText(text)
            logger.lifecycle("Updated ${file.name}; review the diff before committing.")
        }
    }

    private fun validate(text: String) {
        val root = JsonSlurper().parseText(text) as? Map<*, *> ?: throw GradleException("pt_config.json: not an object")
        val bridges = root["bridges"] as? Map<*, *> ?: throw GradleException("pt_config.json: no \"bridges\"")
        for (transport in listOf("snowflake", "obfs4", "meek")) {
            val lines = bridges[transport] as? List<*>
            if (lines.isNullOrEmpty()) throw GradleException("pt_config.json: no $transport bridges")
        }
    }
}
