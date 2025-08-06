package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

open class DeployTask : DefaultTask() {
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
        get() = field.toLowerCase()

    private val buildType
        get() = if (tagPrefix == "development") {
            "debug"
        } else {
            tagPrefix
        }

    @TaskAction
    fun upload() {
        val apkPath = project.layout.projectDirectory.file("build/outputs/apk/$buildType/$appName-$versionName-$buildType.apk")
        val changelogPath = project.layout.projectDirectory.file("build/outputs/apk/$buildType/CHANGELOG-$appName-$versionName-$tagPrefix.md")
        val apkFile = apkPath.asFile
        val changelogFile = changelogPath.asFile

        when {
            !apkFile.exists() -> {
                throw RuntimeException("APK file does not exist: ${apkFile.absolutePath}")
            }

            !changelogFile.exists() -> {
                throw RuntimeException("Changelog file does not exist: ${changelogFile.absolutePath}")
            }

            nexusUrl.isBlank() || nexusUsername.isBlank() || nexusPassword.isBlank() -> {
                throw RuntimeException("Nexus credentials are not set")
            }

            else -> {
                val targetUrl = "$nexusUrl/kompakt-$appName/$tagPrefix/$versionName"
                project.exec {
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
                project.exec {
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
}