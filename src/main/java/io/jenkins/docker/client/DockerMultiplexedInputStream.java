package io.jenkins.docker.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * De-multiplex an <code>application/vnd.docker.raw-stream</code> as described on
 * <a href="https://docs.docker.com/engine/api/v1.32/#operation/ContainerAttach">Docker API documentation</a>
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerMultiplexedInputStream extends InputStream {

    private final InputStream multiplexed;
    int next;

    public DockerMultiplexedInputStream(InputStream in) {
        multiplexed = in;
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
            int i = multiplexed.read(header);
            if (i < 0) return; // EOF

            if (i != 8)
                throw new IOException("Expected 8 bytes application/vnd.docker.raw-stream header, got " + i);

            int size = ((header[4] & 0xff) << 24) + ((header[5] & 0xff) << 16) + ((header[6] & 0xff) << 8) + (header[7] & 0xff);
            switch (header[0]) {
                case 1: // STDOUT
                    next = size;
                    break;
                case 2: // STDERR
                    // not expected. Read payload and dump on stderr for diagnostic
                    byte[] payload = new byte[size];
                    int received = 0;
                    while (received < size) {
                        i = multiplexed.read(payload, received, size - received);
                        received += i;
                    }
                    System.err.println(new String(payload, StandardCharsets.UTF_8));
                    break;
                default:
                    throw new IOException("Unexpected application/vnd.docker.raw-stream frame type " + header);
            }
        }
    }

}
