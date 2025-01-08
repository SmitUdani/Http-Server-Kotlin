import java.net.ServerSocket;

fun main() {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    println("Logs from your program will appear here!")

    // Uncomment this block to pass the first stage
    val serverSocket = ServerSocket(4221)

    // Since the tester restarts your program quite often, setting SO_REUSEADDR
    // ensures that we don't run into 'Address already in use' errors
    serverSocket.reuseAddress = true

    val client = serverSocket.accept() // Wait for connection from client
    println("accepted new connection")

    val outputStream = client.getOutputStream()
    val inputStream = client.getInputStream()

    inputStream.bufferedReader().use {
        val request = it.readLine()
        val url = request.split(" ")[1]

        when(url) {
            "/" -> outputStream.write("HTTP/1.1 200 OK\r\n\r\n".toByteArray())
            "/user-agent" -> {
                it.readLine()
                val userAgent = it.readLine().split(":")[1].trim()
                outputStream.write(response(userAgent.length, userAgent))
            }
            else -> {
                val paths = url.split("/")
                when(paths[1]) {
                    "echo" -> outputStream.write(response(paths[2].length, paths[2]))
                    else -> outputStream.write("HTTP/1.1 404 Not Found\r\n\r\n".toByteArray())
                }
            }


        }
    }

    outputStream.flush()
    outputStream.close()

}

fun response(length: Int, body: String, type: String = "text/plain"): ByteArray {
    return """
        HTTP/1.1 200 OK\r
        Content-Type: $type\r
        Content-Length: $length\r
        \r
        $body
    """.trimIndent().toByteArray()
}
