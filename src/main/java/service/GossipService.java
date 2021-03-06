package service;

import config.GossipConfig;
import node.Node;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class GossipService {
    public final InetSocketAddress inetSocketAddress;
    private SocketService socketService;
    private Node self = null;
    private ConcurrentHashMap<String, Node> nodes =
            new ConcurrentHashMap<>();
    private boolean stopped = false;
    private GossipConfig gossipConfig = null;

    private GossipUpdater onNewMember = null;
    private GossipUpdater onFailedMember = null;
    private GossipUpdater onRemovedMember = null;
    private GossipUpdater onRevivedMember = null;

    public GossipService(InetSocketAddress inetSocketAddress,
                         GossipConfig gossipConfig) {
        this.inetSocketAddress = inetSocketAddress;
        this.gossipConfig = gossipConfig;
        this.socketService = new SocketService(inetSocketAddress.getPort());
        self = new Node(inetSocketAddress, 0, gossipConfig);
        nodes.putIfAbsent(self.getUniqueId(), self);
    }

    public GossipService(InetSocketAddress listeningAddress,
                         InetSocketAddress targetAddress,
                         GossipConfig gossipConfig) {
        this(listeningAddress, gossipConfig);
        Node initialTarget = new Node(targetAddress,
                0, gossipConfig);
        nodes.putIfAbsent(initialTarget.getUniqueId(), initialTarget);

    }

    public void start() {
        startSenderThread();
        startReceiverThread();
        startFailureDetectionThread();
        printNodes();
    }

    public void stop() {
        stopped = true;
    }

    public void setOnNewNodeHandler(GossipUpdater onNewMember) {
        this.onNewMember = onNewMember;
    }

    public void setOnFailedNodeHandler(GossipUpdater onFailedMember) {
        this.onFailedMember = onFailedMember;
    }

    public void setOnRevivedNodeHandler(GossipUpdater onRevivedMember) {
        this.onRevivedMember = onRevivedMember;
    }

    public void setOnRemoveNodeHandler(GossipUpdater onRemovedMember) {
        this.onRemovedMember = onRemovedMember;
    }

    private void startSenderThread() {
        new Thread(() -> {
            while (!stopped) {
                sendGossipToRandomNode();
                try {
                    Thread.sleep(gossipConfig.updateFrequency.toMillis());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void startReceiverThread() {
        new Thread(() -> {
            while (!stopped) {
                receivePeerMessage();
            }
        }).start();
    }

    private void startFailureDetectionThread() {
        new Thread(() -> {
            while (!stopped) {
                detectFailedNodes();
                try {
                    Thread.sleep(gossipConfig.failureDetectionFrequency.toMillis());
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }).start();
    }

    private void detectFailedNodes() {
        String[] keys = new String[nodes.size()];
        nodes.keySet().toArray(keys);
        for (String key : keys) {
            Node node = nodes.get(key);
            boolean hadFailed = node.hasFailed();
            node.checkIfFailed();
            if (hadFailed != node.hasFailed()) {
                if (node.hasFailed()) {
                    //nodes.remove(key);
                    if (onFailedMember != null) {
                        onFailedMember.update(node.getSocketAddress());
                    } else {
                        if (onRevivedMember != null) {
                            onRevivedMember.update(node.getSocketAddress());
                        }
                    }
                }
            }
            if (node.shouldCleanup()) {
                synchronized (nodes) {
                    nodes.remove(key);
                    if (onRemovedMember != null) {
                        onRemovedMember.update(node.getSocketAddress());
                    }
                }
            }
        }
    }

    private void sendGossipToRandomNode() {
        self.incrementSequenceNumber();
        List<String> peersToUpdate = new ArrayList<>();
        Object[] keys = nodes.keySet().toArray();
        //
        if (keys.length < gossipConfig.peersToUpdatePerInterval) {
            for (int i = 0; i < keys.length; i++) {
                String key = (String) keys[i];
                if (!key.equals(self.getUniqueId())) {
                    peersToUpdate.add(key);
                }
            }
        } else {
            for (int i = 0; i < gossipConfig.peersToUpdatePerInterval; i++) {
                boolean newTargetFound = false;
                while (!newTargetFound) {
                    String targetKey = (String) keys[getRandomIndex(nodes.size())];
                    if (!targetKey.equals(self.getUniqueId())) {
                        newTargetFound = true;
                        peersToUpdate.add(targetKey);
                    }
                }
            }
        }

        for (String targetAddress : peersToUpdate) {
            Node node = nodes.get(targetAddress);
            new Thread(() -> socketService.sendGossip(node, self)).start();
        }
    }

    private int getRandomIndex(int size) {
        int randomIndex = (int) (Math.random() * size);
        return randomIndex;
    }

    private void receivePeerMessage() {
        Node newNode = socketService.receiveGossip();
        Node existingMember = nodes.get(newNode.getUniqueId());
        if (existingMember == null) {
            synchronized (nodes) {
                newNode.setConfig(gossipConfig);
                newNode.setLastUpdatedTime();
                nodes.putIfAbsent(newNode.getUniqueId(), newNode);
                if (onNewMember != null) {
                    onNewMember.update(newNode.getSocketAddress());
                }
            }
        } else {
            System.out.println("Updating sequence number for node " + existingMember.getUniqueId());
            existingMember.updateSequenceNumber(newNode.getSequenceNumber());
        }
    }

    private void printNodes() {
        new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            getAliveMembers().forEach(node ->
                    System.out.println("Health status: " + node.getHostName() + ":"
                            + node.getPort() + "- alive"));

            getFailedMembers().forEach(node ->
                    System.out.println("Health status: " + node.getHostName() + ":"
                            + node.getPort() + "- failed"));
        }).start();
    }

    public ArrayList<InetSocketAddress> getAliveMembers() {
        int initialSize = nodes.size();
        ArrayList<InetSocketAddress> aliveMembers =
                new ArrayList<>(initialSize);
        for (String key : nodes.keySet()) {
            Node node = nodes.get(key);
            if (!node.hasFailed()) {
                String ipAddress = node.getAddress();
                int port = node.getPort();
                aliveMembers.add(new InetSocketAddress(ipAddress, port));
            }
        }

        return aliveMembers;
    }

    public ArrayList<InetSocketAddress> getFailedMembers() {
        ArrayList<InetSocketAddress> failedMembers = new ArrayList<>();
        for (String key : nodes.keySet()) {
            Node node = nodes.get(key);
            node.checkIfFailed();
            if (node.hasFailed()) {
                String ipAddress = node.getAddress();
                int port = node.getPort();
                failedMembers.add(new InetSocketAddress(ipAddress, port));
            }
        }
        return failedMembers;
    }

    public ArrayList<InetSocketAddress> getAllMembers() {
        // used to prevent resizing of ArrayList.
        int initialSize = nodes.size();
        ArrayList<InetSocketAddress> allMembers =
                new ArrayList<>(initialSize);

        for (String key : nodes.keySet()) {
            Node node = nodes.get(key);
            String ipAddress = node.getAddress();
            int port = node.getPort();
            allMembers.add(new InetSocketAddress(ipAddress, port));
        }
        return allMembers;
    }
}
