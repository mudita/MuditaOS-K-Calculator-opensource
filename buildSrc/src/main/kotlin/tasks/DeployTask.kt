package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.util.Locale
import javax.inject.Inject

abstract class DeployTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @Input
    var versionName: String = ""

    @Input
    var appName: String = ""

    @Input
    var nexusUrl: String = ""

    @Input
    var nexusUsername: String = ""

    @Input
    var nexusPassword: String = ""

    @Input
    var tagPrefix: String = ""
        get() = field.lowercase(Locale.getDefault())

    private val buildType
        get() = if (tagPrefix == "development") {
            "debug"
        } else {
            tagPrefix
        }

    @TaskAction
    fun upload() {
        val apkDir = project.layout.projectDirectory.dir("build/outputs/apk/$buildType").asFile

        val apkFile = apkDir
            .listFiles { file -> file.extension == "apk" }
            ?.maxByOrNull { it.lastModified() }
            ?: throw RuntimeException("APK file does not exist in: ${apkDir.absolutePath}")

        val changelogFile = apkDir
            .listFiles { file -> file.extension == "md" && file.name.startsWith("CHANGELOG") }
            ?.maxByOrNull { it.lastModified() }

        if (nexusUrl.isBlank() || nexusUsername.isBlank() || nexusPassword.isBlank()) {
            throw RuntimeException("Nexus credentials are not set")
        }

        val targetUrl = "$nexusUrl/kompakt-$appName/$tagPrefix/$versionName"

        execOperations.exec {
            commandLine(
                "curl",
                "-v",
                "-u",
                "$nexusUsername:$nexusPassword",
                "--upload-file",
                apkFile.absolutePath,
                "$targetUrl/${apkFile.name}"
            )
        }

        if (changelogFile != null) {
            execOperations.exec {
                commandLine(
                    "curl",
                    "-v",
                    "-u",
                    "$nexusUsername:$nexusPassword",
                    "--upload-file",
                    changelogFile.absolutePath,
                    "$targetUrl/${changelogFile.name}"
                )
            }
        }
    }
}
