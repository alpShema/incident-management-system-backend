package com.amalitech.hilfe;

import com.amalitech.hilfe.config.AmaliAiProperties;
import com.amalitech.hilfe.config.EmbeddingProperties;
import com.amalitech.hilfe.exceptions.ServiceUnavailableException;
import com.amalitech.hilfe.services.OpenAiEmbeddingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiEmbeddingServiceTest {

    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);
    // The server never writes a response at all — the client's only way to return is the
    // read timeout firing, so this can't accidentally pass via a slow-but-real response.
    private static final long SERVER_HANG_MS = 5000;
    // HttpURLConnection makes a second attempt to read the error stream after the first read
    // times out, so the real wall-clock cost is roughly 2x READ_TIMEOUT, not 1x. Bound generously
    // above that (observed ~2-2.3x in practice) but well below SERVER_HANG_MS.
    private static final long MAX_EXPECTED_ELAPSED_MS = READ_TIMEOUT.toMillis() * 5;

    private ServerSocket serverSocket;
    private ExecutorService serverThread;

    @AfterEach
    void tearDown() throws IOException {
        if (serverSocket != null) serverSocket.close();
        if (serverThread != null) serverThread.shutdownNow();
    }

    @Test
    void embed_abortsAtTheReadTimeout_insteadOfHangingUntilTheProviderResponds() throws IOException {
        serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        serverThread = Executors.newSingleThreadExecutor();
        serverThread.submit(this::acceptAndHang);

        AmaliAiProperties amaliAiProps = new AmaliAiProperties(
                "test-key",
                "http://" + serverSocket.getInetAddress().getHostAddress() + ":" + serverSocket.getLocalPort(),
                "openai",
                Duration.ofMillis(200),
                READ_TIMEOUT);
        OpenAiEmbeddingService service = new OpenAiEmbeddingService(new EmbeddingProperties("test-model"), amaliAiProps);

        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> service.embed("hello"))
                .isInstanceOf(ServiceUnavailableException.class)
                .cause()
                .isInstanceOf(RestClientException.class)
                .cause()
                .isInstanceOf(SocketTimeoutException.class);
        long elapsed = System.currentTimeMillis() - start;

        // Comfortably above READ_TIMEOUT (and its internal error-stream retry) but well below
        // SERVER_HANG_MS — proves the client's own timeout fired, not that the server answered.
        assertThat(elapsed).isLessThan(MAX_EXPECTED_ELAPSED_MS);
    }

    private void acceptAndHang() {
        try (Socket client = serverSocket.accept()) {
            Thread.sleep(SERVER_HANG_MS);
        } catch (IOException | InterruptedException ignored) {
            // Socket closed by tearDown, or interrupted once the test's assertion is done.
        }
    }
}
