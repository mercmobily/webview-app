package com.mathacademy.secure

/**
 * Site configuration data class
 * Defines allowed domains and start URLs for the secure WebView
 */
data class Site(
    val name: String,
    val domain: String,
    val startUrl: String
)

/**
 * List of available sites for the secure browser
 * Add new sites here to make them available in the site selector
 */
object Sites {
    val all = listOf(
        Site(
            name = "Math Academy",
            domain = "mathacademy.com",
            startUrl = "https://www.mathacademy.com/"
        ),
        Site(
            name = "BOM Perth Forecast",
            domain = "bom.gov.au",
            startUrl = "https://www.bom.gov.au/wa/forecasts/perth.shtml"
        )
    )
}
