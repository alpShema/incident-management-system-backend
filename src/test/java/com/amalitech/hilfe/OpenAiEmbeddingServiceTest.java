package com.amalitech.hilfe;

import com.amalitech.hilfe.config.AmaliAiProperties;
import com.amalitech.hilfe.config.EmbeddingProperties;
import com.amalitech.hilfe.exceptions.ServiceUnavailableException;
import com.amalitech.hilfe.services.OpenAiEmbeddingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiEmbeddingServiceTest {

    private static final long SERVER_DELAY_MS = 2000;

    private ServerSocket serverSocket;
    private ExecutorService serverThread;

    @AfterEach
    void tearDown() throws IOException {
        if (serverSocket != null) serverSocket.close();
        if (serverThread != null) serverThread.shutdownNow();
    }

    @Test
    void embed_abortsAtTheConfiguredTimeout_insteadOfHangingUntilTheProviderResponds() throws IOException {
        serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        serverThread = Executors.newSingleThreadExecutor();
        serverThread.submit(this::acceptAndRespondSlowly);

        AmaliAiProperties amaliAiProps = new AmaliAiProperties(
                "test-key",
                "http://" + serverSocket.getInetAddress().getHostAddress() + ":" + serverSocket.getLocalPort(),
                "openai",
                Duration.ofMillis(200),
                Duration.ofMillis(300));
        OpenAiEmbeddingService service = new OpenAiEmbeddingService(new EmbeddingProperties("test-model"), amaliAiProps);

        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> service.embed("hello"))
                .isInstanceOf(ServiceUnavailableException.class);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isLessThan(SERVER_DELAY_MS);
    }

    private void acceptAndRespondSlowly() {
        try (Socket client = serverSocket.accept()) {
            Thread.sleep(SERVER_DELAY_MS);
            String body = "{}";
            String response = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + body.length() + "\r\n"
                    + "Connection: close\r\n\r\n"
                    + body;
            OutputStream out = client.getOutputStream();
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException | InterruptedException ignored) {
            // Test is over (socket closed) or the delayed write was interrupted by teardown.
        }
    }
}
