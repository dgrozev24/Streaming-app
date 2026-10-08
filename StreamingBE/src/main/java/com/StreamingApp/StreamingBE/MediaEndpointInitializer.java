package com.StreamingApp.StreamingBE;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/** Resolves MinIO's advertised address before media services are constructed. */
public class MediaEndpointInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final Logger log = LoggerFactory.getLogger(MediaEndpointInitializer.class);

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        String configured = context.getEnvironment().getProperty("media.endpoint", "auto");
        if (!"auto".equalsIgnoreCase(configured.trim())) {
            log.info("MinIO endpoint: using explicit media.endpoint configuration");
            return;
        }

        try {
            String address = detectAddress();
            String endpoint = "http://" + address + ":9000";
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("detectedMediaEndpoint", Map.of("media.endpoint", endpoint)));
            log.info("MinIO endpoint detected: {}. Restart the backend after changing networks.", endpoint);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot select a network IP for MinIO. Connect to your hotspot "
                    + "and restart, or set MEDIA_ENDPOINT=http://YOUR_LAPTOP_IP:9000. " + e.getMessage(), e);
        }
    }

    private static String detectAddress() throws Exception {
        // UDP connect asks the OS to select a local route; no packet is sent,
        // and no DNS lookup or internet response is required.
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByAddress(new byte[] {1, 1, 1, 1}), 80);
            InetAddress address = socket.getLocalAddress();
            if (usable(address)) {
                return address.getHostAddress();
            }
        } catch (SocketException ignored) {
            // Without a default route, try a single active physical interface.
        }

        Set<String> candidates = new LinkedHashSet<>();
        var interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces != null) {
            for (NetworkInterface network : Collections.list(interfaces)) {
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (usable(address)) {
                        candidates.add(address.getHostAddress());
                    }
                }
            }
        }
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }
        throw new IllegalStateException("Available IPv4 addresses: " + candidates);
    }

    private static boolean usable(InetAddress address) {
        return address instanceof Inet4Address && !address.isAnyLocalAddress()
                && !address.isLoopbackAddress() && !address.isLinkLocalAddress();
    }
}
