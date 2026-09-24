package app.opal.buildlogic

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Copies the `.so` files under `jni/<abi>/` out of AARs into a jniLibs-shaped directory. Used to take only the native
 * Tor binary from tor-android while providing our own JNI binding class.
 */
@CacheableTask
abstract class ExtractJniLibsTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val aars: ConfigurableFileCollection

    @get:Input abstract val abis: SetProperty<String>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @get:Inject protected abstract val archives: ArchiveOperations

    @get:Inject protected abstract val fs: FileSystemOperations

    @TaskAction
    fun extract() {
        val wanted = abis.get()
        fs.sync {
            into(outputDir)
            includeEmptyDirs = false
            for (aar in aars.files) {
                from(archives.zipTree(aar)) {
                    wanted.forEach { include("jni/$it/*.so") }
                    eachFile { path = path.removePrefix("jni/") }
                }
            }
        }
    }
}
