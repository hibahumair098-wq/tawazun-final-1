package com.example.domain

import java.net.URI
import java.util.Locale

/**
 * Domain filtering and wildcard matching engine for Tawazun.
 * Complies with strict privacy & security requirements:
 * e.g., "*.youtube.com" blocks "m.youtube.com" and "www.youtube.com",
 * but explicitly does NOT block "notyoutube.com".
 */
object DomainFilter {

    /**
     * Extracts host from input string, whether provided as raw domain or URL.
     */
    fun normalizeHost(rawInput: String): String {
        val trimmed = rawInput.trim().lowercase(Locale.ROOT)
        val withoutProtocol = if (trimmed.contains("://")) {
            runCatching { URI(trimmed).host ?: trimmed.substringAfter("://").substringBefore("/") }.getOrDefault(trimmed)
        } else {
            trimmed.substringBefore("/").substringBefore(":")
        }
        return withoutProtocol.removePrefix("www.")
    }

    /**
     * Checks if a given host matches a block rule.
     * Rule can be exact (e.g. "tiktok.com") or wildcard (e.g. "*.youtube.com").
     */
    fun matchesRule(hostInput: String, rulePattern: String): Boolean {
        val host = hostInput.trim().lowercase(Locale.ROOT)
            .let { if (it.contains("://")) runCatching { URI(it).host ?: it }.getOrDefault(it) else it }
            .substringBefore("/").substringBefore(":")
        val pattern = rulePattern.trim().lowercase(Locale.ROOT)

        if (host.isEmpty() || pattern.isEmpty()) return false

        // Exact match
        if (host == pattern) return true

        // Wildcard match: e.g. "*.youtube.com"
        if (pattern.startsWith("*.")) {
            val rootDomain = pattern.removePrefix("*.")
            // Matches root domain itself (e.g. "youtube.com")
            if (host == rootDomain) return true
            // Matches exact subdomains (e.g. "m.youtube.com", "v.m.youtube.com")
            // Must end with ".rootDomain" so "notyoutube.com" does NOT match
            if (host.endsWith(".$rootDomain")) return true
        }

        // Prefix wildcard match: e.g. "*youtube.com" -> normalize to root domain check
        if (pattern.startsWith("*") && !pattern.startsWith("*.")) {
            val suffix = pattern.removePrefix("*")
            if (host == suffix || host.endsWith(".$suffix")) return true
        }

        return false
    }

    /**
     * Evaluates domain against a list of blocked patterns.
     */
    fun isBlocked(hostInput: String, blocklist: List<String>): Pair<Boolean, String?> {
        for (pattern in blocklist) {
            if (matchesRule(hostInput, pattern)) {
                return Pair(true, pattern)
            }
        }
        return Pair(false, null)
    }
}
