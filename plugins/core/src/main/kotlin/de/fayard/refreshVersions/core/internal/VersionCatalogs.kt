package de.fayard.refreshVersions.core.internal

import de.fayard.refreshVersions.core.FeatureFlag
import de.fayard.refreshVersions.core.ModuleId
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.util.GradleVersion

@InternalRefreshVersionsApi
object VersionCatalogs {

    const val LIBS_VERSIONS_TOML = "gradle/libs.versions.toml"

    val minimumGradleVersion: GradleVersion = GradleVersion.version("7.4")

    fun isSupported(): Boolean = GradleVersion.current() >= minimumGradleVersion

    fun dependencyAliases(versionCatalog: VersionCatalog?): Map<ModuleId.Maven, String> = when {
        FeatureFlag.VERSIONS_CATALOG.isNotEnabled -> emptyMap()
        versionCatalog == null -> emptyMap()
        else -> versionCatalog.libraryAliases.mapNotNull { alias ->
            versionCatalog.findLibrary(alias)
                .orElse(null)
                ?.orNull
                ?.let { dependency: MinimalExternalModuleDependency ->
                    ModuleId.Maven(dependency.module.group, dependency.module.name) to "libs.$alias"
                }
        }.toMap()
    }

    internal fun parseToml(toml: String): Toml {
        val map = parseTomlInSections(toml)
            .map { (sectionName, paragraph) ->
                val section = TomlSection.from(sectionName)
                section to paragraph.lines().map { TomlLine(section, it) }
            }.toMap()
        return Toml(map.toMutableMap())
    }

    /**
     * Returns a map where the key is the section name, and the value, the section content.
     */
    internal fun parseTomlInSections(toml: String): Map<String, String> {
        val result = mutableMapOf<String, StringBuilder>()
        result["root"] = StringBuilder()
        var current: StringBuilder = result["root"]!!
        val lines = toml.lines()
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            val isSectionHeader = trimmed.startsWith("[") && trimmed.endsWith("]")
            if (isSectionHeader) {
                val sectionName = trimmed.removePrefix("[").removeSuffix("]")
                result[sectionName] = StringBuilder()
                current = result[sectionName]!!
            } else {
                current.append(line)
                if (index != lines.lastIndex) current.append("\n")
            }
        }
        return result.mapValues { it.value.toString() }
    }

    fun generateVersionsCatalogText(
        dependenciesAndNames: Map<Dependency, String>,
        currentText: String,
        withVersions: Boolean,
        plugins: List<Dependency>
    ): String {
        val dependencies = dependenciesAndNames.keys.toList()
        val versionRefMap = dependenciesWithVersionRefsMapIfAny(dependencies)

        val toml = parseToml(currentText)
        toml.merge(TomlSection.Plugins, addPlugins(plugins, versionRefMap))
        toml.merge(TomlSection.Libraries, versionsCatalogLibraries(dependenciesAndNames, versionRefMap, withVersions))
        toml.merge(TomlSection.Versions, addVersions(dependenciesAndNames, versionRefMap))
        return toml.toString()
    }

    private fun addPlugins(
        plugins: List<Dependency>,
        versionRefMap: Map<Dependency, TomlVersionRef?>
    ): List<TomlLine> = plugins.distinctBy { d ->
        "${d.group}:${d.name}"
    }.mapNotNull { d ->
        val version = d.version ?: return@mapNotNull null

        val pair = if (d in versionRefMap) {
            "version.ref" to versionRefMap.getValue(d)!!.key
        } else {
            "version" to version
        }

        val pluginId = d.name.removeSuffix(".gradle.plugin")
        TomlLine(
            TomlSection.Plugins,
            pluginId.replace(".", "-"),
            mapOf("id" to pluginId, pair)
        )

    }.flatMap {
        listOf(TomlLine.newLine, it)
    }

    private fun addVersions(
        dependenciesAndNames: Map<Dependency, String>,
        versionRefMap: Map<Dependency, TomlVersionRef?>
    ): List<TomlLine> = dependenciesAndNames.keys.distinctBy { lib ->
        versionRefMap[lib]?.key
    }.flatMap { lib ->
        val (versionName, versionValue) = versionRefMap[lib] ?: return@flatMap emptyList()

        val versionLine = TomlLine(TomlSection.Versions, versionName, versionValue)
        listOf(TomlLine.newLine, versionLine)
    }

    private data class TomlVersionRef(val key: String, val version: String)

    lateinit var versionsMap: Map<String, String>
    lateinit var versionKeyReader: ArtifactVersionKeyReader

    private fun dependenciesWithVersionRefsMapIfAny(
        libraries: List<Dependency>
    ): Map<Dependency, TomlVersionRef?> = libraries.mapNotNull { lib ->
        val group = lib.group ?: return@mapNotNull null

        val name = getVersionPropertyName(ModuleId.Maven(group, lib.name), versionKeyReader)

        if (name.contains("..") || name.startsWith("plugin")) {
            return@mapNotNull null
        }
        val version = versionsMap[name] ?: lib.version
        val versionRef = version?.let {
            TomlVersionRef(
                key = name.removePrefix("version.").replace(".", "-"), // Better match TOML naming convention.
                version = it
            )
        }
        lib to versionRef
    }.toMap()

    private fun versionsCatalogLibraries(
        dependenciesAndNames: Map<Dependency, String>,
        versionRefMap: Map<Dependency, TomlVersionRef?>,
        withVersions: Boolean,
    ): List<TomlLine> {

        return dependenciesAndNames.keys.filterNot { lib ->
            lib.name.endsWith("gradle.plugin") && lib.group != null
        }.flatMap { dependency: Dependency ->
            val group = dependency.group!!
            val line: TomlLine = if (dependency in versionRefMap) {
                val versionRef: TomlVersionRef? = versionRefMap[dependency]
                TomlLine(
                    section = TomlSection.Libraries,
                    key = dependenciesAndNames.getValue(dependency),
                    map = mutableMapOf<String, String?>().apply { //TODO: Replace with buildMap later.
                        put("group", dependency.group)
                        put("name", dependency.name)
                        put("version.ref", versionRef?.key ?: return@apply)
                    }
                )
            } else {
                val versionKey = getVersionPropertyName(ModuleId.Maven(group, dependency.name), versionKeyReader)
                val version = when {
                    dependency.version == null -> null
                    withVersions.not() -> "_"
                    versionKey in versionsMap -> versionsMap[versionKey]!!
                    else -> dependency.version
                }
                val value = ConfigurationLessDependency(group, dependency.name, version)
                TomlLine(TomlSection.Libraries, dependenciesAndNames.get(dependency)!!, value)
            }

            listOf(TomlLine.newLine, line)
        }
    }
}
