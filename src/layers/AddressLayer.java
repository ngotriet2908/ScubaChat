package layers;

import gui.ChatGUI;
import layers.transportLayer.TransportLayer;

import java.nio.ByteBuffer;
import java.util.*;


public class AddressLayer {
    private static final int MAX_IP_TTL = 7;
    private static final int MAX_ROUTING_TTL = 7;
    private List<RoutingColumn> routingTable;
    private LinkLayer linkLayer;
    private TransportLayer transportLayer;
    private int id;
    // neighbour, ttl
    private Map<Integer, Integer> neighbours;
    private boolean[] broadcastIdUsed;

    private List<RequestSeen> requestsSeen;
    private List<RequestSeen> helloSeen;
    private List<ResponseSeen> forwardedResponses;
    // AODVR
    private int sourceSeq;

    private int broadcastID;
    private List<ReverseRoutingColumn> reverseRoutingTable;

    public AddressLayer(TransportLayer transportLayer) {
        this.id = transportLayer.getApplicationLayer().getId();
        this.linkLayer = new LinkLayer(this, id);

        linkLayer.start();

        this.transportLayer = transportLayer;

        routingTable = new ArrayList<>();
        reverseRoutingTable = new ArrayList<>();

        neighbours = new HashMap<>();

        // routing
        broadcastID = 0;
        sourceSeq = 0;

        broadcastIdUsed = new boolean[15];

        requestsSeen = new ArrayList<>();
        forwardedResponses = new ArrayList<>();
        helloSeen = new ArrayList<>();
        new TableCleaner().start();
    }

    public synchronized int getNextHop(int destination) {
        for (int i = 0; i < routingTable.size(); i++) {
            if (routingTable.get(i).destination == destination) {
                return routingTable.get(i).nextHop;
            }
        }

        // if there is no suitable column in the routing table
        return -1;
    }

    public ByteBuffer contructErrorPacket(int destination) {
        ByteBuffer packet = ByteBuffer.allocate(2);

        int nextHop = getNextHop(destination);

        if (nextHop == -1) {
            return null;
        }

        packet.put((byte) ((destination << 6) | (nextHop << 4)));
        packet.put((byte) 4);
        return packet;
    }

    public synchronized void linkBroken(int destination) {
        ByteBuffer packet = contructErrorPacket(destination);
        if (packet != null) {
            sendMessage(packet);
        }
        removeRoutingInformation(destination);
    }

    public synchronized void removeRoutingInformation(int destination) {
        List<RoutingColumn> routingColumnstmp = new ArrayList<>();
        for (int i = 0; i < routingTable.size(); i++) {
            if (routingTable.get(i).destination == destination || routingTable.get(i).nextHop == destination) {
                continue;
            } else {
                routingColumnstmp.add(routingTable.get(i));
            }
        }
        routingTable = new ArrayList<>();
        routingTable.addAll(routingColumnstmp);

        List<ReverseRoutingColumn> reverseRoutingColumnstmp = new ArrayList<>();
        for (int i = 0; i < routingTable.size(); i++) {
            if (reverseRoutingTable.get(i).source == destination || reverseRoutingTable.get(i).previousHop == destination) {
                continue;
            } else {
                reverseRoutingColumnstmp.add(reverseRoutingTable.get(i));
            }
        }
        reverseRoutingTable = new ArrayList<>();
        reverseRoutingTable.addAll(reverseRoutingColumnstmp);
        requestsSeen = new ArrayList<>();
        forwardedResponses = new ArrayList<>();
        transportLayer.getApplicationLayer().getSecurityProtocol()[destination] = null;
        transportLayer.getCBCkey().remove(destination);
        transportLayer.getRSAforN().remove(destination);
        transportLayer.getApplicationLayer().setHasMyCBC(destination, false);
        transportLayer.getApplicationLayer().setHasMyRSA(destination, false);
    }

    public synchronized void findRoute(int destination) {
        // initiate route finding
        broadcastID = (broadcastID + 1) % 8;

        byte[] rreq = constructRREQ(destination, broadcastID);

        if (requestsSeen.size() > 7) {
            requestsSeen.remove(0);
        }
        requestsSeen.add(new RequestSeen(id, destination, broadcastID));
        linkLayer.sendMessage(rreq);
    }

    // IP PACKET CREATE
    public synchronized void createIpPacket(ByteBuffer message, int destination) {
        ByteBuffer packet = ByteBuffer.allocate(message.capacity() + 2);

        for (int i = 2; i < message.capacity() + 2; i++) {
            packet.put(i, message.get(i - 2));
        }

        packet.put(0, (byte) id);
        packet.put(1, (byte) ((destination << 4) + MAX_IP_TTL));

        int nextHop = getNextHop(destination);

        if (nextHop == -1) {
            // initiate route finding
            broadcastID = (broadcastID + 1) % 8;

            byte[] rreq = constructRREQ(destination, broadcastID);

            if (requestsSeen.size() > 7) {
                requestsSeen.remove(0);
            }

            requestsSeen.add(new RequestSeen(id, destination, broadcastID));
            linkLayer.sendMessage(rreq);
        } else {
            // route is used, so reset ttl to max again, what if several routes are in the table? should be impossible, but...
            routeToDestinationAvailable(destination).routeTtl = MAX_ROUTING_TTL;
            linkLayer.sendMessage(packet.array(), nextHop);
        }
    }

    // RSA PACKET CREATE
    public synchronized void createRSAPacket(ByteBuffer message, int destination) {
        ByteBuffer packet = ByteBuffer.allocate(message.capacity() + 2);

        for (int i = 2; i < message.capacity() + 2; i++) {
            packet.put(i, message.get(i - 2));
        }

        packet.put(0, (byte) (id));
        packet.put(1, (byte) ((destination << 4) + MAX_IP_TTL));

        int nextHop = getNextHop(destination);

        if (nextHop == -1) {
            findRoute(destination);
        } else {
            routeToDestinationAvailable(destination).routeTtl = MAX_ROUTING_TTL;
            linkLayer.sendRSA(packet.array(), nextHop);
        }
    }

    // RSA PACKET CREATE
    public synchronized void createCBCPacket(ByteBuffer message, int destination) {
        ByteBuffer packet = ByteBuffer.allocate(message.capacity() + 2);

        for (int i = 2; i < message.capacity() + 2; i++) {
            packet.put(i, message.get(i - 2));
        }

        packet.put(0, (byte) (id));
        packet.put(1, (byte) ((destination << 4) + MAX_IP_TTL));

        int nextHop = getNextHop(destination);

        if (nextHop == -1) {
            findRoute(destination);
        } else {
            routeToDestinationAvailable(destination).routeTtl = MAX_ROUTING_TTL;
            linkLayer.sendCBC(packet.array(), nextHop);
        }
    }

    // ACK PACKET CREATE
    public synchronized void createPacketForAckShort(int destination, int ackNum) {
        ByteBuffer packet = ByteBuffer.allocate(2);
        int nextHop = getNextHop(destination);

        if (nextHop == -1) {
            findRoute(destination);
        } else {
            packet.put(0, (byte) ((destination << 6) | (id << 4) | (nextHop << 2)));
            packet.put(1, (byte) ((((ackNum + 1) % 16) << 4) | 3));
            routeToDestinationAvailable(destination).routeTtl = MAX_ROUTING_TTL;
            linkLayer.sendAckShort(packet, nextHop);
        }
    }

    // SEND MESSAGE TO LINK LAYER
    public synchronized void sendMessage(ByteBuffer message) {
        linkLayer.sendMessage(message);
    }

    // PROCESS RECEIVED PACKET
    public synchronized void processPacket(byte[] packet) {
        // if we are the destination, send to for further processing to TransportLayer
        if (((packet[1]) >> 4) == id) {
            // send the message and the source to TransportLayer
            transportLayer.processReceivedPacket(Arrays.copyOfRange(packet, 2, packet.length), (packet[0] & 15));

        } else {
            // if we are an intermediate node, forward it further

            if (getNextHop(((packet[1]) >> 4)) == -1) {
                return;
            }

            int nextHop = getNextHop(((packet[1]) >> 4));
            RoutingColumn routingColumn = routeToDestinationAvailable(((packet[1]) >> 4));
            routeToDestinationAvailable(((packet[1]) >> 4)).routeTtl = MAX_ROUTING_TTL;

            // decrement the TTL in header
            int ttl = (packet[1] & 15);

            packet[1] = (byte) (packet[1] & (15 << 4));
            packet[1] = (byte) (packet[1] | (ttl - 1));

            // send the packet for further processing to link layer
            linkLayer.sendMessage(packet, nextHop);

        }
    }

    public synchronized void processRSAPacket(byte[] packet) {
        // if we are the destination, send to for further processing to TransportLayer
        if (((packet[1]) >> 4) == id) {
            // send the message and the source to TransportLayer
            transportLayer.processRSAPacket(Arrays.copyOfRange(packet, 2, packet.length), (packet[0] & 15));

        } else {

            if (getNextHop(((packet[1]) >> 4)) == -1) {
                return;
            }

            // if we are an intermediate node, forward it further
            int nextHop = getNextHop(((packet[1]) >> 4));
            routeToDestinationAvailable(((packet[1]) >> 4)).routeTtl = MAX_ROUTING_TTL;

            // decrement the TTL in header
            int ttl = (packet[1] & 15);

            packet[1] = (byte) (packet[1] & (15 << 4));
            packet[1] = (byte) (packet[1] | (ttl - 1));

            // send the packet for further processing to link layer
            linkLayer.sendRSA(packet, nextHop);
        }
    }

    public synchronized void processCBCPacket(byte[] packet) {
        // if we are the destination, send to for further processing to TransportLayer
        if (((packet[1]) >> 4) == id) {
            // send the message and the source to TransportLayer
            transportLayer.processCBCPacket(Arrays.copyOfRange(packet, 2, packet.length), (packet[0] & 15));

        } else {
            if (getNextHop(((packet[1]) >> 4)) == -1) {
                return;
            }

            // if we are an intermediate node, forward it further
            int nextHop = getNextHop(((packet[1]) >> 4));

            // decrement the TTL in header
            int ttl = (packet[1] & 15);

            packet[1] = (byte) (packet[1] & (15 << 4));
            packet[1] = (byte) (packet[1] | (ttl - 1));

            // send the packet for further processing to link layer
            routeToDestinationAvailable(((packet[1]) >> 4)).routeTtl = MAX_ROUTING_TTL;
            linkLayer.sendCBC(packet, nextHop);

        }
    }

    public synchronized void processAckPacket(byte[] packet) {
        // if we are the destination, send to for further processing to TransportLayer
        if ((((packet[0] + 256) & 255) >> 6) == id) {
            // send the message and the source to TransportLayer
            transportLayer.processAckPacket(packet);

        } else {

            if (getNextHop((((packet[0] + 256) & 255) >> 6)) == -1) {
                return;
            }

            // if we are an intermediate node, forward it further
            int nextHop = getNextHop((((packet[0] + 256) & 255) >> 6));

            int des = (((packet[0] + 256) & 255) >> 6);
            int source = (packet[0] >> 4) & 3;

            packet[0] = (byte) ((des << 6) | (source << 4) | (nextHop << 2));

            ByteBuffer frame = ByteBuffer.allocate(2);
            frame.put(0, packet[0]);
            frame.put(1, packet[1]);
            // send the packet for further processing to link layer
            routeToDestinationAvailable((((packet[0] + 256) & 255) >> 6)).routeTtl = MAX_ROUTING_TTL;
            linkLayer.sendAckShort(frame, nextHop);
        }
    }

    public synchronized RoutingColumn routeToDestinationAvailable(int destination) {
        for (int i = 0; i < routingTable.size(); i++) {
            if (routingTable.get(i).destination == destination) {
                return routingTable.get(i);
            }
        }

        return null;
    }

    public synchronized int getPreviousHop(int source) {
        for (int i = 0; i < reverseRoutingTable.size(); i++) {
            if (source == reverseRoutingTable.get(i).source) {
                return reverseRoutingTable.get(i).previousHop;
            }
        }

        return -1;
    }

    public synchronized int getDestinationSeq(int destination) {
        return 0;
    }

    public synchronized byte[] constructRREQ(int destination, int broadcastID) {
        byte[] packet = new byte[2];
        packet[0] = (byte) ((id << 6) | (destination << 4) | (sourceSeq << 2) | ((getDestinationSeq(destination) & 3)));
        packet[1] = (byte) ((id << 6) | (broadcastID << 3) | 1);

        return packet;
    }

    public Map<Integer, Integer> getNeighbours() {
        return neighbours;
    }

    public synchronized void processRoutingPacket(byte[] routingPacket) {
        if (((routingPacket[1]) & 7) == 2) {
            if (!helloSeen.contains(new RequestSeen((((routingPacket[0] + 256) & 255) >> 6), 0, 0))) {
                helloSeen.add(new RequestSeen((((routingPacket[0] + 256) & 255) >> 6), 0, 0));
                ByteBuffer hellopacket = ByteBuffer.allocate(2);
                byte[] bytes = createHelloPacket();
                hellopacket.put(bytes[0]);
                hellopacket.put(bytes[1]);
                sendMessage(hellopacket);
                transportLayer.processHelloPacket(((routingPacket[0] + 256) & 255) >> 6, ChatGUI.OnlineStatus.ONLINE);
            }
        } else if (((routingPacket[1]) & 7) == 1) {
            // RREQ, save to reverse routing table OR send a response
            int source = ((routingPacket[0] + 256) & 255) >> 6;
            int destination = (routingPacket[0] >> 4) & 3;

            int sourceSeq = (routingPacket[0] >> 2) & 3;
            int destinationSeq = (routingPacket[0]) & 3;
            int previousHop = ((routingPacket[1] + 256) & 255) >> 6;
            int broadcastId = (routingPacket[1] >> 3) & 7;

            // if we have a route, than construct response
            // if not, broadcast the request further

            if (requestsSeen.contains(new RequestSeen(source, destination, broadcastId))) {
                return;
            } else {
                requestsSeen.add(new RequestSeen(source, destination, broadcastId));
            }

            reverseRoutingTable.add(new ReverseRoutingColumn(source, sourceSeq, previousHop));

            if (routeToDestinationAvailable(destination) != null || destination == id) {
                // create RREP
                ByteBuffer rrep = ByteBuffer.allocate(2);

                if (destination == id) {
                    rrep.put((byte) ((destination << 6) | (sourceSeq << 4) | (source << 2) | (id)));
                } else {
                    rrep.put((byte) ((destination << 6) | (getDestinationSeq(destination) << 4) | (source << 2) | (id)));
                }

                rrep.put((byte) (getPreviousHop(source) << 6));

                linkLayer.sendMessage(rrep.array());
            } else {
                routingPacket[1] = (byte) ((id << 6) | (broadcastId << 3) | 1);
                linkLayer.sendMessage(routingPacket);
            }
        } else if ((((routingPacket[1]) & 7) == 0) && (((routingPacket[1] + 256) & 255) >> 6) == id) {
            // RREP
            int destination = ((routingPacket[0] + 256) & 255) >> 6;
            int destinationSeq = (routingPacket[0] >> 4) & 3;
            int source = (routingPacket[0] >> 2) & 3;

            int nodeFromWhichRREPReceived = (routingPacket[0] & 3);
            int nextHop = ((routingPacket[1] + 256) & 255) >> 6;

            routingTable.add(new RoutingColumn(destination, nodeFromWhichRREPReceived, destinationSeq, MAX_ROUTING_TTL));

            if (source != id) {
                // forward the message back to the source
                routingPacket[0] = (byte) (((routingPacket[0] >> 2) << 2) | id);
                routingPacket[1] = (byte) (getPreviousHop(source) << 6);
                linkLayer.sendMessage(routingPacket);
            }
        }
    }

    public synchronized byte[] createHelloPacket() {
        byte[] packet = new byte[2];

        packet[0] = (byte) (id << 6);
        packet[1] = (byte) 2;

        return packet;
    }

    // ROUTING COLUMN
    public class RoutingColumn {
        private int destination;
        private int nextHop;
        private int destinationSeq;
        private int routeTtl;
        private boolean neighbourActive;

        public RoutingColumn(int destination, int nextHop, int destinationSeq, int routeTtl) {
            this.destination = destination;
            this.nextHop = nextHop;
            this.destinationSeq = destinationSeq;
            this.routeTtl = routeTtl;

            this.neighbourActive = true;
        }
    }

    public class ReverseRoutingColumn {
        private final int MAX_TTL_REVERSE_ROUTE = 6;
        private int source;
        private int sourceSeq;
        private int previousHop;
        private int ttl;

        public ReverseRoutingColumn(int source, int sourceSeq, int previousHop) {
            this.source = source;
            this.sourceSeq = sourceSeq;
            this.previousHop = previousHop;
            this.ttl = MAX_TTL_REVERSE_ROUTE;
        }
    }

    private class RequestSeen {
        private int source;
        private int destination;
        private int broadcastID;

        public RequestSeen(int source, int destination, int broadcastID) {
            this.source = source;
            this.destination = destination;
            this.broadcastID = broadcastID;
        }

        @Override
        public synchronized boolean equals(Object obj) {
            if (obj instanceof RequestSeen) {
                RequestSeen rs = (RequestSeen) obj;
                return rs.destination == destination && rs.source == source && rs.broadcastID == broadcastID;
            } else {
                return false;
            }
        }
    }

    private class ResponseSeen {
        private int source;
        private int destination;
        private int destinationSeq;
        private int ttl;

        public ResponseSeen(int source, int destination, int destinationSeq) {
            this.source = source;
            this.destination = destination;
            this.destinationSeq = destinationSeq;
        }

        @Override
        public synchronized boolean equals(Object obj) {
            if (obj instanceof ResponseSeen) {
                ResponseSeen rs = (ResponseSeen) obj;
                return rs.destination == destination && rs.source == source && rs.destinationSeq == destinationSeq;
            } else {
                return false;
            }
        }
    }

    // REGULAR CLEANUP OF TABLES
    private class TableCleaner extends Thread {
        private static final int INTERVAL_TIME = 9000;

        public synchronized void run() {
            while (true) {
                try {
                    Thread.sleep(INTERVAL_TIME);
                    helloSeen.clear();
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }

                boolean somethingExpired = false;

                // clean routing table
                ListIterator<RoutingColumn> routeIter = routingTable.listIterator();

                while (routeIter.hasNext()) {
                    RoutingColumn rc = routeIter.next();
                    rc.routeTtl--;

                    if (rc.routeTtl <= 0) {
                        somethingExpired = true;
                        routeIter.remove();
                    }
                }

                // clean reverse routing table
                ListIterator<ReverseRoutingColumn> reverseRouteIter = reverseRoutingTable.listIterator();

                while (reverseRouteIter.hasNext()) {
                    ReverseRoutingColumn rrc = reverseRouteIter.next();
                    rrc.ttl--;

                    if (rrc.ttl <= 0) {
                        somethingExpired = true;
                        reverseRouteIter.remove();
                    }
                }

                if (somethingExpired) {
                    requestsSeen.clear();
                    forwardedResponses.clear();
                }


                int[] disconnected = new int[4];
                boolean neighbourDisconnected = false;

                // check for neighbour hellos
                Iterator iterator = neighbours.entrySet().iterator();

                while (iterator.hasNext()) {
                    Map.Entry pair = (Map.Entry) iterator.next();

                    if ((int) pair.getValue() <= 1) {
                        neighbourDisconnected = true;

                        // set correct bit in disconnected
                        // delete from neighbours
                        iterator.remove();
                    } else {
                        neighbours.put((int) pair.getKey(), (int) (pair.getValue()) - 1);
                    }
                }
            }
        }
    }
}