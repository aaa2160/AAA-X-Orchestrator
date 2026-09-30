package com.aaa.orchestrator.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses raw cookie strings into structured anti-detect profiles and validates authentication tokens.
 */
object CookieParser {

    /**
     * Verifies that the extracted cookie jar contains essential Twitter session tokens (auth_token & ct0).
     */
    fun hasValidTwitterSession(cookieString: String): Boolean {
        return cookieString.contains("auth_token=") && cookieString.contains("ct0=")
    }

    /**
     * Converts a standard semicolon-separated cookie header into AdsPower / Dolphin Anty JSON format.
     */
    fun toJsonAntiDetectFormat(cookieString: String, domain: String = ".x.com"): String {
        val pairs = cookieString.split(";")
        val items = mutableListOf<String>()

        for (pair in pairs) {
            val trimmed = pair.trim()
            if (trimmed.isEmpty()) continue

            val eqIndex = trimmed.indexOf('=')
            if (eqIndex > 0) {
                val name = trimmed.substring(0, eqIndex).trim()
                val value = trimmed.substring(eqIndex + 1).trim()
                val isHttpOnly = name == "auth_token"
                val escapedName = escapeJsonString(name)
                val escapedValue = escapeJsonString(value)

                items.add(
                    """  {
    "name": "$escapedName",
    "value": "$escapedValue",
    "domain": "$domain",
    "path": "/",
    "httpOnly": $isHttpOnly,
    "secure": true
  }"""
                )
            }
        }

        return "[\n" + items.joinToString(",\n") + "\n]"
    }

    private fun escapeJsonString(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * Sanitizes raw cookies for single-line CSV/Sheets export.
     */
    fun sanitizeForExport(cookieString: String): String {
        return cookieString.replace("\n", "").replace("\r", "").trim()
    }
}
