import java.io.BufferedReader
import java.io.InputStreamReader
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

        if(url == "/")
            outputStream.write("HTTP/1.1 200 OK\r\n\r\n".toByteArray())
        else {
            val paths = url.split("/")
            when(paths[1]) {
                "echo" -> outputStream.write(
                    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${paths[2].length}\r\n\r\n${paths[2]}\r\n"
                        .toByteArray()
                )

                else -> outputStream.write("HTTP/1.1 404 Not Found\r\n\r\n".toByteArray())
            }
        }
    }

    outputStream.flush()
    outputStream.close()



}
