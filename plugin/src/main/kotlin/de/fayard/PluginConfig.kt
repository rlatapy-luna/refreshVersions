package de.fayard

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import okio.buffer
import okio.source
import org.gradle.util.GradleVersion
import java.io.File

object PluginConfig {

    const val currentVersion = "0.6.0" // CHECK_VERSION

    /**
     * The name of the extension for configuring the runtime behavior of the plugin.
     *
     * @see org.gradle.plugins.site.SitePluginExtension
     */
    const val EXTENSION_NAME = "buildSrcVersions"
    const val DEPENDENCY_UPDATES = "dependencyUpdates"
    const val DEPENDENCY_UPDATES_PATH = ":$DEPENDENCY_UPDATES"
    const val REFRESH_VERSIONS = "refreshVersions"
    const val BUILD_SRC_VERSIONS = EXTENSION_NAME

    fun isNonStable(version: String): Boolean {
        val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
        val regex = "^[0-9,.v-]+$".toRegex()
        val isStable = stableKeyword || regex.matches(version)
        return isStable.not()
    }

    const val DEFAULT_LIBS = "Libs"
    const val DEFAULT_VERSIONS = "Versions"
    const val DEFAULT_INDENT = "from-editorconfig-file"
    const val BENMANES_REPORT_PATH = "build/dependencyUpdates/report.json"

    /** Documentation **/
    fun issue(number: Int): String = "$buildSrcVersionsUrl/issues/$number"

    val buildSrcVersionsUrl = "https://github.com/jmfayard/buildSrcVersions"
    val issue47UpdatePlugin = "See issue #47: how to update buildSrcVersions itself ${issue(47)}"
    val issue53PluginConfiguration = issue(53)
    val issue54VersionOnlyMode = issue(54)
    val issue19UpdateGradle = issue(19)


    /**
     * We don't want to use meaningless generic libs like Libs.core
     *
     * Found many inspiration for bad libs here https://developer.android.com/jetpack/androidx/migrate
     * **/
    val MEANING_LESS_NAMES: List<String> = listOf(
        "common", "core", "core-testing", "testing", "runtime", "extensions",
        "compiler", "migration", "db", "rules", "runner", "monitor", "loader",
        "media", "print", "io", "media", "collection", "gradle", "android"
    )

    val INITIAL_GITIGNORE = """
.gradle/
build/
"""

    fun gradleKdoc(currentVersion: String): String = """
Current version: "$currentVersion"        
See issue 19: How to update Gradle itself?
$issue19UpdateGradle
"""

    val KDOC_LIBS = """
    Generated by $buildSrcVersionsUrl

    Update this file with
      `$ ./gradlew buildSrcVersions`
    """.trimIndent()

    val KDOC_VERSIONS = """
    Generated by $buildSrcVersionsUrl

    Find which updates are available by running
        `$ ./gradlew buildSrcVersions`
    This will only update the comments.

    YOU are responsible for updating manually the dependency version.
    """.trimIndent()


    const val INITIAL_BUILD_GRADLE_KTS = """
plugins {
    `kotlin-dsl`
}
repositories {
    mavenCentral()
}
        """


    val moshi = Moshi.Builder().build()

    inline fun <reified T : Any> moshiAdapter(clazz: Class<T> = T::class.java): Lazy<JsonAdapter<T>> = lazy { moshi.adapter(clazz) }

    val dependencyGraphAdapter: JsonAdapter<DependencyGraph> by moshiAdapter()

    internal val extensionAdapter: JsonAdapter<BuildSrcVersionsExtensionImpl> by moshiAdapter()

    fun readGraphFromJsonFile(jsonInput: File): DependencyGraph {
        return dependencyGraphAdapter.fromJson(jsonInput.source().buffer())!!
    }

    val VERSIONS_ONLY_START = "<buildSrcVersions>"
    val VERSIONS_ONLY_END = "</buildSrcVersions>"
    val VERSIONS_ONLY_INTRO = listOf(
        VERSIONS_ONLY_START,
        "Generated by ./gradle buildSrcVersions",
        "See $issue54VersionOnlyMode"
    )

    val PLUGIN_NFORMATION_START = listOf(
        "# Plugin versions",
        "# See https://github.com/jmfayard/buildSrcVersions/issues/60"
    )
    val PLUGIN_INFORMATION_END = listOf(
        "# You can edit the rest of the file, it will be kept intact")

    const val GRADLE_LATEST_VERSION = "gradleLatestVersion"

    const val SPACES4 = "    "
    const val SPACES2 = "  "
    const val SPACES0 = ""
    const val TAB = "\t"

    fun supportsTaskAvoidance(): Boolean =
        GradleVersion.current() >= GradleVersion.version("4.9")

    fun supportSettingPluginVersions() : Boolean =
        GradleVersion.current() >= GradleVersion.version("5.6")

    fun spaces(nbSpaces: Int): String =
        StringBuilder().run {
            repeat(Math.max(0, nbSpaces)) {
                append(' ')
            }
            toString()
        }

    lateinit var configureGradleVersions: (DependencyUpdatesTask.() -> Unit) -> Unit

}
