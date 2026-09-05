package com.example.mntnode.scan

import java.io.File

sealed class DiscoveredSource {
    abstract val key: String
    abstract val displayName: String
    abstract val extensionLower: String

    data class LocalFile(val file: File) : DiscoveredSource() {
        override val key: String get() = file.absolutePath
        override val displayName: String get() = file.name
        override val extensionLower: String get() = file.extension.lowercase()
    }
}
