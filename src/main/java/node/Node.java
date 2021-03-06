package node;

import config.GossipConfig;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.LocalDateTime;

public class Node implements Serializable {
    private final InetSocketAddress address;
    private long heartbeatSequenceNumber = 0;
    private LocalDateTime lastUpdateTime = null;
    private volatile boolean failed = false;
    private GossipConfig config;

    public Node(InetSocketAddress address,
                long initialSequenceNumber,
                GossipConfig config) {
        this.address = address;
        this.heartbeatSequenceNumber = initialSequenceNumber;
        this.config = config;
        setLastUpdatedTime();
    }

    public void setLastUpdatedTime() {
        LocalDateTime updatedTime = LocalDateTime.now();
        System.out.println("Node " + this.getUniqueId() + " at " + updatedTime);
        lastUpdateTime = updatedTime;
    }

    public void updateSequenceNumber(long newSequenceNumber) {
        if (newSequenceNumber > heartbeatSequenceNumber) {
            heartbeatSequenceNumber = newSequenceNumber;
            System.out.println("Sequence number of current node "
                    + this.getUniqueId() + " is " + this.getSequenceNumber()
                    + " updated to " + newSequenceNumber
            );
            setLastUpdatedTime();
        }
    }

    public String getAddress() {
        return address.getHostName();
    }

    public long getSequenceNumber() {
        return heartbeatSequenceNumber;
    }

    public LocalDateTime getLastUpdateTime() {
        return lastUpdateTime;
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public GossipConfig getConfig() {
        return config;
    }

    public void setConfig(GossipConfig config) {
        this.config = config;
    }

    public String getUniqueId() {
        return address.toString();
    }

    public void incrementSequenceNumber() {
        heartbeatSequenceNumber++;
        setLastUpdatedTime();
    }

    public void checkIfFailed() {
        LocalDateTime failureTime = lastUpdateTime.plus(config.failureTimeout);
        LocalDateTime now = LocalDateTime.now();
        failed = now.isAfter(failureTime);
    }

    public boolean shouldCleanup() {
        if (failed) {
            Duration cleanupTimeout = config.failureTimeout.plus(config.cleanupTimeout);
            LocalDateTime cleanupTime = lastUpdateTime.plus(cleanupTimeout);
            LocalDateTime now = LocalDateTime.now();
            return now.isAfter(cleanupTime);
        } else {
            return false;
        }
    }

    public boolean hasFailed() {
        return failed;
    }

    public String getNetworkMessage() {
        return "[" + address.getHostName() +
                ":" + address.getPort() +
                "-" + heartbeatSequenceNumber + "]";
    }

    public InetAddress getInetAddress() {
        return address.getAddress();
    }

    public int getPort() {
        return address.getPort();
    }

    public InetSocketAddress getSocketAddress() {
        return address;
    }
}
