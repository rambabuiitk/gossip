package config;

import java.io.Serializable;
import java.time.Duration;

public class GossipConfig implements Serializable {

    public final Duration failureTimeout;
    public final Duration cleanupTimeout;
    public final Duration updateFrequency;
    public final Duration failureDetectionFrequency;
    public final int peersToUpdatePerInterval;

    public GossipConfig(Duration failureTimeout, Duration cleanupTimeout,
                        Duration updateFrequency, Duration failureDetectionFrequency,
                        int peersToUpdatePerInterval) {
        this.failureTimeout = failureTimeout;
        this.cleanupTimeout = cleanupTimeout;
        this.updateFrequency = updateFrequency;
        this.failureDetectionFrequency = failureDetectionFrequency;
        this.peersToUpdatePerInterval = peersToUpdatePerInterval;
    }
}
