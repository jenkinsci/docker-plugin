package io.jenkins.docker.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * De-multiplex an <code>application/vnd.docker.raw-stream</code> as described on
 * <a href="https://docs.docker.com/engine/api/v1.32/#operation/ContainerAttach">Docker API documentation</a>
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerMultiplexedInputStream extends InputStream {

    private final InputStream multiplexed;
    private final String name;
    int next;

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerMultiplexedInputStream.class);

    public DockerMultiplexedInputStream(InputStream in, String streamName) {
        multiplexed = in;
        name = streamName;
        next = 0;
    }

    @Override
    public int read() throws IOException {
        readInternal();
        next--;
        return multiplexed.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        readInternal();
        int byteRead = multiplexed.read(b, off, Math.min(next, len));
        next -= byteRead;
        return byteRead;
    }

    private void readInternal() throws IOException {
        while (next == 0) {
            byte[] header = new byte[8];
            int todo = 8;
            while (todo > 0) {
                int i = multiplexed.read(header, 8-todo, todo);
                if (i < 0) return; // EOF
                todo -= i;
            }
            int size = ((header[4] & 0xff) << 24) + ((header[5] & 0xff) << 16) + ((header[6] & 0xff) << 8) + (header[7] & 0xff);
            switch (header[0]) {
                case 1: // STDOUT
                    next = size;
                    break;
                case 2: // STDERR
                    // not expected. Read payload and log it for diagnostic
                    byte[] payload = new byte[size];
                    int received = 0;
                    while (received < size) {
                        int i = multiplexed.read(payload, received, size - received);
                        if (i < 0) break; // EOF
                        received += i;
                    }
                    if (LOGGER.isInfoEnabled()) {
                        final String dataAsString = new String(payload, 0, received, StandardCharsets.UTF_8);
                        final String dataAsTrimmedString = dataAsString.replaceAll("\\s*$", "");
                        if (!dataAsTrimmedString.isEmpty()) {
                            LOGGER.info("stderr from {}: {}", name, dataAsTrimmedString);
                        }
                    }
                    break;
                default:
                    throw new IOException("Unexpected application/vnd.docker.raw-stream frame type " + Arrays.toString(header));
            }
        }
    }

}
