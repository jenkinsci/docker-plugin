package io.jenkins.docker.connector;

import static org.junit.Assert.assertEquals;

import com.github.dockerjava.api.model.Ports;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class HostPortComparatorTest {

    @Test
    public void testHostPortComparator() {
        assertEquals(
                List.of(new Ports.Binding("0.0.0.0", "9000"), new Ports.Binding("0.0.0.0", "9001")),
                List.of(new Ports.Binding("0.0.0.0", "9000"), new Ports.Binding("0.0.0.0", "9001")).stream()
                        .sorted(new HostPortComparator())
                        .collect(Collectors.toList()));
        assertEquals(
                List.of(new Ports.Binding("::", "9000"), new Ports.Binding("::", "9001")),
                List.of(new Ports.Binding("::", "9000"), new Ports.Binding("::", "9001")).stream()
                        .sorted(new HostPortComparator())
                        .collect(Collectors.toList()));
        assertEquals(
                List.of(new Ports.Binding("8.8.8.8", "9000"), new Ports.Binding("8.8.8.8", "9001")),
                List.of(new Ports.Binding("8.8.8.8", "9000"), new Ports.Binding("8.8.8.8", "9001")).stream()
                        .sorted(new HostPortComparator())
                        .collect(Collectors.toList()));
        assertEquals(
                List.of(new Ports.Binding("8.8.8.8", "9000"), new Ports.Binding("0.0.0.0", "9001")),
                List.of(new Ports.Binding("8.8.8.8", "9000"), new Ports.Binding("0.0.0.0", "9001")).stream()
                        .sorted(new HostPortComparator())
                        .collect(Collectors.toList()));
        assertEquals(
                List.of(new Ports.Binding("8.8.8.8", "9000"), new Ports.Binding("::", "9001")),
                List.of(new Ports.Binding("8.8.8.8", "9000"), new Ports.Binding("::", "9001")).stream()
                        .sorted(new HostPortComparator())
                        .collect(Collectors.toList()));
        assertEquals(
                List.of(new Ports.Binding("0.0.0.0", "9000"), new Ports.Binding("::", "9001")),
                List.of(new Ports.Binding("0.0.0.0", "9000"), new Ports.Binding("::", "9001")).stream()
                        .sorted(new HostPortComparator())
                        .collect(Collectors.toList()));
        assertEquals(
                List.of(new Ports.Binding("8.8.8.8", "9000"), new Ports.Binding("0.0.0.0", "9001")),
                List.of(new Ports.Binding("0.0.0.0", "9001"), new Ports.Binding("8.8.8.8", "9000")).stream()
                        .sorted(new HostPortComparator())
                        .collect(Collectors.toList()));
        assertEquals(
                List.of(new Ports.Binding("8.8.8.8", "9000"), new Ports.Binding("::", "9001")),
                List.of(new Ports.Binding("::", "9001"), new Ports.Binding("8.8.8.8", "9000")).stream()
                        .sorted(new HostPortComparator())
                        .collect(Collectors.toList()));
        assertEquals(
                List.of(new Ports.Binding("0.0.0.0", "9000"), new Ports.Binding("::", "9001")),
                List.of(new Ports.Binding("::", "9001"), new Ports.Binding("0.0.0.0", "9000")).stream()
                        .sorted(new HostPortComparator())
                        .collect(Collectors.toList()));
    }
}
