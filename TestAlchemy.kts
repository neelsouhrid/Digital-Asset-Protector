import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter

fun main() {
    val url = URL("https://polygon-amoy.g.alchemy.com/v2/b_INQ4Mz40YTMbhyu95Ky")
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.setRequestProperty("Content-Type", "application/json")
    connection.doOutput = true

    val payload = """{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}"""
    
    try {
        val writer = OutputStreamWriter(connection.outputStream)
        writer.write(payload)
        writer.flush()
        writer.close()

        val responseCode = connection.responseCode
        println("Response Code: ${responseCode}")

        val inputStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = inputStream.bufferedReader().use { it.readText() }
        println("Response Body: $response")
    } catch (e: Exception) {
        println("Error: ${e.message}")
    }
}
