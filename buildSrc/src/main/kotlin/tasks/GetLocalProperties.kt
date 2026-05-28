package tasks

import java.io.File
import java.io.FileInputStream
import java.util.Properties

class GetLocalProperties {
    companion object {
        @JvmStatic
        fun localProperties(rootDir: File): Properties {
            val localPropertiesFile = File(rootDir, "local.properties")
            val localProperties = Properties()

            if (localPropertiesFile.exists()) {
                FileInputStream(localPropertiesFile).use { localProperties.load(it) }
            }

            return localProperties
        }
    }
}
