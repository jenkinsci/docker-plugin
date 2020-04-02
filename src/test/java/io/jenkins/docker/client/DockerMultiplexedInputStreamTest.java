package io.jenkins.docker.client;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DemuxTester implements AutoCloseable, Runnable {

    // helper class for testing DockerMultiplexedInputStream
    //
    //                .--------------------------------.
    //                |          this.feeder           |
    //  ------------->|      (PipedOutputStream)       |
    //  input         .--------------------------------.
    //                |    this.feeder_input_stream    |
    //                |       (PipedInputStream)       |
    //                '--------------------------------'
    //                                 |
    //                                 v
    //                .--------------------------------.
    //                |          this.stream           |
    //                | (DockerMultiplexedInputStream) |
    //                '--------------------------------'
    //                                 |
    //                                 v
    //                  .-----------------------------.
    //                  |         this.thread         |
    //                  |                             |
    //                  | [reads from this.stream     |
    //                  |  and writes into this.sink] |
    //                  '-----------------------------'
    //                                 |
    //                                 v
    //                .--------------------------------.
    //                |           this.sink            |
    //  <-------------|    (ByteArrayOutputStream)     |
    //  expected      '--------------------------------'
    //  output    
    
    PipedOutputStream feeder;
    PipedInputStream  feeder_input_stream;
    DockerMultiplexedInputStream stream;
    ByteArrayOutputStream sink;
    Thread thread;
    boolean eof;
    IOException exc;

    public DemuxTester() throws IOException {
        feeder = new PipedOutputStream();

        feeder_input_stream = new PipedInputStream();
        feeder_input_stream.connect(feeder);
        
        stream = new DockerMultiplexedInputStream(feeder_input_stream);
        sink = new ByteArrayOutputStream();
        eof = false;
        exc = null;

        thread = new Thread(this);
        thread.start();
    }

    // DemuxTester thread function
    //
    // forwards this.stream into this.sink
    @Override
    public void run() {
        try
        {
            byte[] buffer = new byte[64];
            int count;
            while(true) {
                count = stream.read(buffer, 0, buffer.length);
                if (count <= 0) {
                    // Demux EOF
                    eof = true;
                    return;
                }
                sink.write(buffer, 0, count);
                sink.flush();
            }
        }
        catch (IOException e) { 
            exc = e;
            e.printStackTrace();
        }
    }

    // return true if the DockerMultiplexedInputStream is at EOF
    public boolean isEof() {
        return eof;
    }

    // return the exception if set
    public IOException exception() {
        return exc;
    }

    @Override
    public void close() {
        try {
            feeder.close(); 
            thread.join(100);
        }
        catch (IOException | InterruptedException e) { 
            e.printStackTrace();
        }
    }

    // run an iteration of the test
    public void iteration(byte[] input, byte[] expected_output) throws IOException, InterruptedException
    {
        iteration(input, expected_output, false);
    }
    public void iteration(byte[] input, byte[] expected_output, boolean send_eof) throws IOException, InterruptedException
    {
        //  1. feed the 'input' bytes into the channel
        feeder.write(input);
        feeder.flush();
        if (send_eof) {
            feeder.close();
        }

        //  2. wait until all bytes are processed
        //     (with a 1-second timeout to prevent hanging)
        for (int i=200 ; i!=0 ; i--) {
            if (feeder_input_stream.available() == 0) {
                break;
            }
            Thread.sleep(5);
        }
        Thread.sleep(5);

        //  3. ensure the output equals 'expected_output'
        Assert.assertArrayEquals(expected_output, sink.toByteArray());
        sink.reset();
    }
}

public class DockerMultiplexedInputStreamTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerMultiplexedInputStream.class);

    @Test
    public void testIncompleteFrames() throws Exception {
        try (DemuxTester tester = new DemuxTester())
        {
            tester.iteration(new byte[]{
                    // full frame (payload: 3 bytes)
                    1,0,0,0, 0,0,0,3, 65,66,67,
                    // incomplete frame (payload: 3/7 bytes)
                    1,0,0,0, 0,0,0,7, 68,69,70,
                },
                new byte[]{65,66,67,68,69,70});
            
            tester.iteration(new byte[]{
                    // end of previous frame (4 bytes)
                    71,72,73,74,
                    // incomplete header (6/8 bytes)
                    1,0,0,0,0,0,
                },
                new byte[]{71,72,73,74});

            tester.iteration(new byte[]{
                // end of header (2 bytes) + payload (3 bytes)
                0,3, 75,76,77
                },
                new byte[]{75,76,77});

            Assert.assertFalse(tester.isEof());
            Assert.assertTrue(tester.exception() == null);
        }
    }


    void subtestLargeFrame(DemuxTester tester, int size) throws Exception
    {
        // feed the frame header
        tester.iteration(new byte[]{1, 0, 0, 0,
            (byte)(size >> 24),
            (byte)(size >> 16),
            (byte)(size >> 8),
            (byte) size},
            new byte[]{}
        );

        // buffer with dummy data
        byte[] buf = new byte[200];
        for (int i=0 ; i<buf.length ; i++) {
            buf[i] = (byte)i;
        }

        while (size > 0) {
            // feed the frame payload (in 200-bytes chunk)
            if (size >= 200 ) {
                tester.iteration(buf, buf);
                size -= 200;
            } else {
                byte[] slice = Arrays.copyOfRange(buf, 0, size);
                tester.iteration(slice, slice);
                size = 0;
            }
        }
    }

    @Test
    public void testLargeFrames() throws Exception {
        try (DemuxTester tester = new DemuxTester())
        {
            subtestLargeFrame(tester, 24);
            subtestLargeFrame(tester, 327);
            subtestLargeFrame(tester, 76893);
            subtestLargeFrame(tester, 7);

            Assert.assertFalse(tester.isEof());
            Assert.assertTrue(tester.exception() == null);
        }
    }

    @Test
    public void testStderr() throws Exception {
        try (DemuxTester tester = new DemuxTester())
        {
            tester.iteration(new byte[]{
                    // stdout frame (3 bytes)
                    1,0,0,0, 0,0,0,3, 65, 66, 67,
                    // stderr frame (4 bytes)
                    2,0,0,0, 0,0,0,4, 68, 69, 70, 71,
                    // stdout frame (5 bytes)
                    1,0,0,0, 0,0,0,5, 72, 73, 74, 75, 76,
                },
                // output (8 bytes)
                new byte[]{65,66,67,72,73,74,75,76});

            Assert.assertFalse(tester.isEof());
            Assert.assertTrue(tester.exception() == null);
        }
    }

    @Test
    public void testEof() throws Exception {
        // EOF in header
        try (DemuxTester tester = new DemuxTester())
        {
            tester.iteration(new byte[]{
                    // stdout frame (3 bytes)
                    1,0,0,0, 0,0,0,3, 65, 66, 67,
                    // incomplete header
                    1,0,0,0, 0,0,
                },
                new byte[]{65,66,67}, 
                // send EOF
                true);
            Assert.assertTrue(tester.isEof());
            Assert.assertTrue(tester.exception() == null);
        }

        // EOF in stdout
        try (DemuxTester tester = new DemuxTester())
        {
            tester.iteration(new byte[]{
                    // stdout frame (3 bytes)
                    1,0,0,0, 0,0,0,3, 65, 66, 67,
                    // incomplete stdout frame (2/3 bytes)
                    1,0,0,0, 0,0,0,3, 68, 69,
                },
                new byte[]{65,66,67, 68, 69}, 
                // send EOF
                true);
            Assert.assertTrue(tester.isEof());
            Assert.assertTrue(tester.exception() == null);
        }

        // EOF in stderr
        
        // log a dummy line to ensure the logger is initialised
        // (otherwise the test fails because of a race condition)
        LOGGER.warn("dummy log"); 

        try (DemuxTester tester = new DemuxTester())
        {
            tester.iteration(new byte[]{
                    // stdout frame (3 bytes)
                    1,0,0,0, 0,0,0,3, 65, 66, 67,
                    // incomplete stderr frame (2/3 bytes)
                    2,0,0,0, 0,0,0,3, 68, 69,
                },
                new byte[]{65,66,67}, 
                // send EOF
                true);
            Assert.assertTrue(tester.isEof());
            Assert.assertTrue(tester.exception() == null);
        }
    }

    @Test
    public void testUnknownFrameType() throws Exception {
        // EOF in header
        try (DemuxTester tester = new DemuxTester())
        {
            tester.iteration(new byte[]{
                // stdout frame (3 bytes)
                1,0,0,0, 0,0,0,3, 65, 66, 67,
                    // unknown frame (type 4) 
                    4,0,0,0, 0,0,0,2, 68, 69,
            },
            new byte[]{65,66,67}); 

            Assert.assertFalse(tester.isEof());
            Assert.assertTrue(tester.exception() != null);
        }
    }
}
