package com.inventory.app.mobile.utils

class Utils {
    companion object {

        fun getBaseUrlError(url: String): String? {
            var formattedUrl = url.trim()

            // Check if URL starts with "http://" or "https://"
            if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
                return "ERROR: Base URL must start with 'http://' or 'https://'"
            }

            // Check if URL ends with "/"
            if (!formattedUrl.endsWith("/")) {
                return "Base URL should end with '/'"
            }

            return null
        }
    }

}