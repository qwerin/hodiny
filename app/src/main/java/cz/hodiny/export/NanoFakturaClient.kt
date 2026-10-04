package cz.hodiny.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Klient REST API NanoFaktury (autorizace API tokenem „nf_…“, viz Nastavení → API tokeny v NanoFaktuře)
class NanoFakturaClient(baseUrl: String, private val token: String) {

    data class Account(val slug: String, val name: String)
    data class Subject(val id: Long, val name: String, val registrationNo: String)
    data class Invoice(val id: Long, val number: String, val status: String, val total: Long, val currency: String)

    class ApiException(message: String) : IOException(message)

    private val apiBase = baseUrl.trim().trimEnd('/') + "/api"
    private val webBase = baseUrl.trim().trimEnd('/')

    suspend fun accounts(): List<Account> {
        val me = JSONObject(request("GET", "$apiBase/auth/me"))
        return me.getJSONArray("accounts").objects().map { Account(it.getString("slug"), it.getString("name")) }
    }

    suspend fun subjects(slug: String): List<Subject> {
        val body = JSONObject(request("GET", "${accountBase(slug)}/subjects?per_page=200"))
        return body.getJSONArray("items").objects()
            .filter { it.optString("type") != "supplier" }
            .map { Subject(it.getLong("id"), it.getString("name"), it.optString("registration_no")) }
    }

    data class Line(
        val name: String,
        val quantity: String,       // decimální řetězec, max 3 desetinná místa
        val unitName: String,
        val unitPriceMinor: Long    // haléře
    )

    // Vystaví fakturu (nebo koncept) s danými řádky
    suspend fun createInvoice(slug: String, subjectId: Long, lines: List<Line>, draft: Boolean): Invoice {
        val jsonLines = JSONArray()
        lines.forEach { l ->
            jsonLines.put(JSONObject()
                .put("name", l.name)
                .put("quantity", l.quantity)
                .put("unit_name", l.unitName)
                .put("unit_price", l.unitPriceMinor))
        }
        val body = JSONObject()
            .put("subject_id", subjectId)
            .put("draft", draft)
            .put("lines", jsonLines)
        val res = JSONObject(request("POST", "${accountBase(slug)}/invoices", body.toString()))
        return Invoice(
            id = res.getLong("id"),
            number = res.optString("number"),
            status = res.optString("status"),
            total = res.optLong("total"),
            currency = res.optString("currency", "CZK")
        )
    }

    fun invoiceWebUrl(slug: String, id: Long) = "$webBase/a/${enc(slug)}/invoices/$id"

    private fun accountBase(slug: String) = "$apiBase/accounts/${enc(slug)}"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun request(method: String, url: String, body: String? = null): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw ApiException(errorMessage(code, text))
            text
        } finally {
            conn.disconnect()
        }
    }

    // Chyby jsou problem+json: { status, detail, code, errors: [{ message, location }] }
    private fun errorMessage(status: Int, text: String): String {
        val prefix = when (status) {
            401 -> "Neplatný nebo expirovaný API token"
            403 -> "Nedostatečná oprávnění"
            404 -> "Nenalezeno (zkontrolujte URL a účet)"
            else -> "Chyba serveru ($status)"
        }
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return prefix
        val details = buildList {
            json.optString("detail").takeIf { it.isNotBlank() }?.let { add(it) }
            json.optJSONArray("errors")?.objects()?.forEach { e ->
                val loc = e.optString("location")
                add(if (loc.isNotBlank()) "$loc: ${e.optString("message")}" else e.optString("message"))
            }
        }
        return if (details.isEmpty()) prefix else "$prefix: ${details.joinToString("; ")}"
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
