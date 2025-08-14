package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream

open class GenerateChangelogTask : DefaultTask() {

    @Input
    var appName: String = ""

    @Input
    var versionName: String = ""

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
    fun generate() {

        // Retrieve all tags sorted by date, most recent first
        val tagsCommand = listOf("git", "tag", "--sort=-committerdate")
        val tagsOutput = execAndGetOutput(tagsCommand)
        val tags = tagsOutput.split("\n")

        val filteredTags = tags.filter { it.startsWith(tagPrefix, true) }

        // Extracting the two most recent tags
        val currentVersion = filteredTags.firstOrNull()
        val previousVersion = filteredTags.getOrNull(1) ?: "" // If there is no previous version, start from the beginning

        val changes = execAndGetOutput(listOf("git", "log", "--pretty=format:%s <%an>", "$previousVersion..$currentVersion"))
        var features = "Features:\n"
        var fixes = "Fixes:\n"
        var improvements = "Improvements:\n"
        changes.lineSequence().forEach { line ->
            val parts = line.split(":", limit = 2)
            val prefix = parts.getOrNull(0) ?: ""
            val rest = parts.getOrNull(1) ?: ""
            when {
                prefix.startsWith("ft") -> features += "$rest\n"
                prefix.startsWith("fi") -> fixes += "$rest\n"
                prefix.startsWith("im") -> improvements += "$rest\n"
            }
        }


        val header = "Changelog from $previousVersion to $currentVersion\n\n"
        val changelog = header + features + "\n" + fixes + "\n" + improvements
        val fileName = "${appName}-${versionName}-$tagPrefix"
        val changelogDir = project.file("./build/outputs/apk/$buildType")
        val changelogFilePath = "$changelogDir/CHANGELOG-$fileName.md"

        println("Changelog file path: $changelogFilePath")
        // Ensure the directory exists
        changelogDir.mkdirs()

        // Create and write to the changelog file
        val changelogFile = project.file(changelogFilePath)
        changelogFile.createNewFile()
        changelogFile.writeText(changelog)

        println(changelog)
    }

    private fun execAndGetOutput(command: List<String>): String {
        val outputStream = ByteArrayOutputStream()

        project.exec {
            commandLine(command)

            standardOutput = outputStream
        }
        return String(outputStream.toByteArray(), Charsets.UTF_8).trim()
    }
}