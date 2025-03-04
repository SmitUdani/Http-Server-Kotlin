import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.ServerSocket
import java.net.Socket

const val HTTP_VERSION = "HTTP/1.1"
const val CRLF = "\r\n"
const val OCTET_STREAM = "application/octet-stream"
var DIRECTORY = ""

enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
}

sealed interface HttpStatus {
    val code: String
    val reason: String?
    enum class Success(override val code: String, override val reason: String) : HttpStatus {
        OK(code = "200", reason = "OK"),
        Created(code = "201", reason = "Created")
    }
    enum class Error(override val code: String, override val reason: String) : HttpStatus {
        NotFound(code = "404", reason = "Not Found")
    }
}

data class Request(
    val method: HttpMethod,
    val target: String,
    val httpVersion: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
)

data class Response(
    val status: HttpStatus,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
)

fun String.toHttpMethod(): HttpMethod? {
    return HttpMethod.entries.firstOrNull { this == it.name }
}

fun Response.toHttpResponse(): String {
    return buildString {
        append("$HTTP_VERSION ${status.code} ${status.reason}$CRLF")
        headers.forEach {
                (key, value) -> append("$key: $value$CRLF")
        }

        if (headers["Content-Type"].equals(OCTET_STREAM)) {
            append("Content-Length: ${body?.toByteArray()?.size ?: 0}$CRLF")
        } else {
            append("Content-Length: ${body?.length ?: 0}$CRLF")
        }
        append(CRLF)
        if (body != null) {
            append(body)
        }
    }
}

fun makeRequestObj(client: Socket): Request {
    return client.getInputStream().bufferedReader().let { input ->
        val (method, target, version) = input.readLine().split(" ")
        val headers = buildMap {
            var header: String
            while ((input.readLine().also { header = it }) != "") {
                val (key, value) = header.split(": ")
                put(key, value)
            }
        }

        val contentLength = headers["Content-Length"]?.toIntOrNull() ?: 0
        val body = if (contentLength > 0) {
            val bodyBytes = CharArray(contentLength)
            input.read(bodyBytes, 0, contentLength)
            String(bodyBytes)  // Convert bytes to string
        } else {
            null
        }

        Request(
            method.toHttpMethod() ?: throw RuntimeException("Unknown Http Method"),
            target,
            version,
            headers,
            body
        )
    }
}

fun makeResponseObj(request: Request): Response {

    val headers = mutableMapOf<String, String>()
    
    if ("gzip" in (request.headers["Content-Encoding"] ?: ""))
        headers["Content-Encoding"] = "gzip"

    return with(request) {
        when {
            method == HttpMethod.POST -> {
                val file = File(DIRECTORY, target.substringAfter("/files/"))
                file.createNewFile()
                body?.let { file.writeText(it) }

                Response(status = HttpStatus.Success.Created, headers = headers)
            }

            target == "/" -> Response(status = HttpStatus.Success.OK, headers = headers)

            target.startsWith("/echo") -> {
                val echo = target.substringAfter("/echo/")
                headers["content-Type"] = "text/plain"

                Response(
                    HttpStatus.Success.OK,
                    headers = headers,
                    body = echo
                )
            }

            target == "/user-agent" -> {
                headers["Content-Type"] = "text/plain"

                Response(
                    HttpStatus.Success.OK,
                    headers = headers,
                    body = request.headers["User-Agent"] ?: ""
                )
            }

            target.startsWith("/files/") -> {
                val fileName = target.substringAfter("/files/")
                val pathName = "${DIRECTORY}${fileName}"
                val file = File(pathName)

                if (file.exists() && file.isFile) {
                    headers["Content-Type"] = OCTET_STREAM

                    Response(
                        HttpStatus.Success.OK,
                        headers = headers,
                        file.readText()
                    )
                } else {
                    Response(
                        HttpStatus.Error.NotFound
                    )
                }
            }

            else -> Response(status = HttpStatus.Error.NotFound)
        }
    }
}

fun handleClient(client: Socket) {
    val request = makeRequestObj(client)

    val response = makeResponseObj(request)

    client.getOutputStream().writer().apply {
        this.write(response.toHttpResponse())
        this.flush()
    }
}

fun main(args: Array<String>) = runBlocking {

    val serverSocket = ServerSocket(4221)
    // Since the tester restarts your program quite often, setting SO_REUSEADDR
    // ensures that we don't run into 'Address already in use' errors
    serverSocket.reuseAddress = true

    args.forEachIndexed { index, s ->
        if(s == "--directory" && index + 1 < args.size)
            DIRECTORY = args[index + 1]
    }

    while (true) {
        val client = serverSocket.accept()
        println("accepted new connection")
        launch(Dispatchers.IO) { handleClient(client) }
    }
}