package fr.tv5mondeplus

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** Adds license discovery for the standard Cast receiver; encrypted media is unchanged. */
internal object TV5CastManifest {
    private const val DASH = "urn:mpeg:dash:schema:mpd:2011"
    private const val WIDEVINE = "urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"

    fun rewrite(xml: String, manifestUrl: String, licenseUrl: String): ByteArray {
        require(xml.length <= 1_048_576 && !xml.contains("<!DOCTYPE", ignoreCase = true)) { "Invalid TV5MONDEplus manifest." }
        val builder = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        val document = builder.parse(InputSource(StringReader(xml)))
        val root = document.documentElement
        require(root.localName == "MPD" && root.namespaceURI == DASH) { "Invalid TV5MONDEplus DASH manifest." }
        val base = (0 until root.childNodes.length).map { root.childNodes.item(it) }
            .filterIsInstance<Element>().firstOrNull { it.localName == "BaseURL" && it.namespaceURI == DASH }
            ?: document.createElementNS(DASH, "BaseURL").also { root.insertBefore(it, root.firstChild) }
        base.textContent = URI(manifestUrl).resolve(base.textContent.trim().ifEmpty { "." }).toString()

        var count = 0
        val protections = document.getElementsByTagNameNS(DASH, "ContentProtection")
        for (index in 0 until protections.length) {
            val protection = protections.item(index) as Element
            if (!protection.getAttribute("schemeIdUri").equals(WIDEVINE, ignoreCase = true)) continue
            (0 until protection.childNodes.length).map { protection.childNodes.item(it) }
                .filter { it.localName?.equals("laurl", ignoreCase = true) == true }
                .forEach { protection.removeChild(it) }
            protection.appendChild(document.createElementNS("https://dashif.org/CPS", "dashif:Laurl").apply {
                setAttribute("licenseType", "EME-1.0")
                textContent = licenseUrl
            })
            protection.appendChild(document.createElementNS("urn:microsoft", "ms:laurl").apply {
                setAttribute("licenseUrl", licenseUrl)
            })
            count++
        }
        require(count > 0) { "The TV5MONDEplus manifest contains no Widevine track." }
        val output = StringWriter()
        TransformerFactory.newInstance().newTransformer().apply { setOutputProperty(OutputKeys.ENCODING, "UTF-8") }
            .transform(DOMSource(document), StreamResult(output))
        return output.toString().toByteArray(Charsets.UTF_8)
    }
}
