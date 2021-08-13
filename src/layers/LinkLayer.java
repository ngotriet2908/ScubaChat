package layers;

import client.Client;
import client.Message;
import client.MessageType;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class LinkLayer extends Thread {
    private static int cap = 1;
    private static int timeout = 0;
    private final int BACKOFF_LIMIT = 4;
    private AddressLayer addressLayer;
    private int id;
    private MessageType lastLinkStatus = MessageType.FREE;
    private BlockingQueue<Message> buffer;
    private int backoffLimitsReached;

    private BlockingQueue<Message> receivedQueue;
    private BlockingQueue<Message> sendingQueue;
    private BlockingQueue<Message> messageQueue;


    public LinkLayer(AddressLayer addressLayer, int id) {
        this.addressLayer = addressLayer;
        this.id = id;

        receivedQueue = new LinkedBlockingQueue<>();
        sendingQueue = new LinkedBlockingQueue<>();
        messageQueue = new LinkedBlockingQueue<>();

        new Client(ApplicationLayerInterface.SERVER_IP, ApplicationLayerInterface.SERVER_PORT, ApplicationLayerInterface.frequency, receivedQueue, sendingQueue, messageQueue);

        backoffLimitsReached = 0;
        buffer = new LinkedBlockingQueue<>();

        new receiveThread(receivedQueue).start();
    }

    public void run() {
        while (true) {

            try {
                Thread.sleep((int) ((Math.random() * 270) + 30));

            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }

            if (buffer.isEmpty()) {
                continue;
            }

            // check if channel is busy
            if (lastLinkStatus == MessageType.BUSY) {
                timeout = (int) (Math.random() * (Math.pow(2, cap) - 0) + 1) + 0;

                // if not, wait for some random time
                System.out.println("Link is busy.");

                try {
                    if (cap < 11) {
                        cap++;
                    } else {
                        backoffLimitsReached++;

                        if (backoffLimitsReached == BACKOFF_LIMIT) {
                            backoffLimitsReached = 0;
                            buffer.take();
                        }

                        cap = 1;
                    }

                    Thread.sleep(timeout);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            } else {
                try {
                    cap = 0;

                    if (!buffer.isEmpty()) {
                        sendingQueue.put(buffer.take());
                    }
                    try {
                        Thread.sleep((int) (Math.random() * (100 - 0) + 1) + 0);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }
                } catch (InterruptedException e) {
                    System.exit(2);
                }
            }
        }
    }


    // SENDING MESSAGES
    public synchronized void sendMessage(byte[] message) {
        ByteBuffer toSend = ByteBuffer.allocate(16);
        toSend.put(message);
        Message msg;

        if (message.length > 2) {
            msg = new Message(MessageType.DATA, toSend);
        } else {
            msg = new Message(MessageType.DATA_SHORT, toSend);
        }

        try {
            buffer.put(msg);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }

    }

    public synchronized void sendMessage(byte[] packet, int nextHop) {
        if (nextHop == -1) {
            System.out.println("Cannot forward the packet to " + nextHop + " !");
        } else {
            byte[] frame = new byte[packet.length + 2];

            // create link layer header for data packet
            frame[0] = (byte) (0x20 | id);
            frame[1] = (byte) (nextHop << 4);

            for (int i = 0; i < packet.length; i++) {
                frame[i + 2] = packet[i];
            }

            sendMessage(frame);
        }
    }

    public synchronized void sendRSA(byte[] packet, int nextHop) {
        if (nextHop == -1) {
            // no entry in the table - drop the packet
            System.out.println("Cannot forward the packet to " + nextHop + " !");
        } else {
            byte[] frame = new byte[packet.length + 2];

            // create link layer header for data packet
            frame[0] = (byte) ((4 << 4) | id);
            frame[1] = (byte) (nextHop << 4);

            for (int i = 0; i < packet.length; i++) {
                frame[i + 2] = packet[i];
            }

            sendMessage(frame);
        }
    }

    public synchronized void sendCBC(byte[] packet, int nextHop) {
        if (nextHop == -1) {
            // no entry in the table - drop the packet
            System.out.println("Cannot forward the packet to " + nextHop + " !");
        } else {
            byte[] frame = new byte[packet.length + 2];

            // create link layer header for data packet
            frame[0] = (byte) ((5 << 4) | id);
            frame[1] = (byte) (nextHop << 4);

            for (int i = 0; i < packet.length; i++) {
                frame[i + 2] = packet[i];
            }

            sendMessage(frame);
        }
    }


    public synchronized void sendAckShort(ByteBuffer packet, int nextHop) {
        if (nextHop == -1) {
            // no entry in the table - drop the packet
            System.out.println("Cannot forward the packet to " + nextHop + " !");
        } else {
            sendMessage(packet);
        }
    }

    public synchronized void sendMessage(ByteBuffer message) {
        sendMessage(message.array());
    }

    public synchronized void setLastLinkStatus(MessageType lastLinkStatus) {
        this.lastLinkStatus = lastLinkStatus;
    }

    // RECEIVING MESSAGES
    private class receiveThread extends Thread {
        private BlockingQueue<Message> receivedQueue;

        public receiveThread(BlockingQueue<Message> receivedQueue) {
            super();
            this.receivedQueue = receivedQueue;
        }

        public synchronized void run() {
            while (true) {
                try {
                    Message m = receivedQueue.take();
                    if (m.getType() == MessageType.BUSY) {
                        setLastLinkStatus(m.getType());
                    } else if (m.getType() == MessageType.FREE) {
                        setLastLinkStatus(m.getType());
                    } else if (m.getType() == MessageType.DATA) {
                        // if message is a data packet
                        if ((((((m.getData().get(0)) + 256) & 255) >> 4) & 15) == 2) {
                            System.out.println("Data packet received!");

                            // if we are the intended receiver of this link-level frame, send it to address layer for processing
                            if ((((((m.getData().get(1)) + 256) & 255) >> 4) == id)) {

                                System.out.println("Passing to IP layer.");
                                addressLayer.processPacket(Arrays.copyOfRange(m.getData().array(), 2, m.getData().capacity()));
                            }
                        }
                        if ((((((m.getData().get(0)) + 256) & 255) >> 4) & 15) == 3) {
                            System.out.println("Ack packet received!");

                            // if we are the intended receiver of this link-level frame, send it to address layer for processing
                            if ((((((m.getData().get(1)) + 256) & 255) >> 4) == id)) {

                                System.out.println("Passing to IP layer.");
                                addressLayer.processAckPacket(Arrays.copyOfRange(m.getData().array(), 2, m.getData().capacity()));
                            }
                        }
                        if ((((((m.getData().get(0)) + 256) & 255) >> 4) & 15) == 4) {
                            System.out.println("RSA packet received!");
                            // if we are the intended receiver of this link-level frame, send it to address layer for processing
                            if ((((((m.getData().get(1)) + 256) & 255) >> 4) == id)) {

                                System.out.println("Passing to IP layer.");
                                addressLayer.processRSAPacket(Arrays.copyOfRange(m.getData().array(), 2, m.getData().capacity()));
                            }
                        }

                        if ((((((m.getData().get(0)) + 256) & 255) >> 4) & 15) == 5) {
                            System.out.println("CBC packet received!");

                            // if we are the intended receiver of this link-level frame, send it to address layer for processing
                            if ((((((m.getData().get(1)) + 256) & 255) >> 4) == id)) {

                                System.out.println("Passing to IP layer.");
                                addressLayer.processCBCPacket(Arrays.copyOfRange(m.getData().array(), 2, m.getData().capacity()));
                            }
                        }
                    } else if (m.getType() == MessageType.DATA_SHORT) {
                        if (((m.getData().get(1)) & 7) == 3) {
                            System.out.println("Ack packet received!");

                            // if we are the intended receiver of this link-level frame, send it to address layer for processing
                            if ((((((m.getData().get(0)) >> 2) & 3)) == id)) {

                                System.out.println("Passing to IP layer.");
                                addressLayer.processAckPacket(m.getData().array());
                            }
                        } else if (((m.getData().get(1)) & 7) == 4) {
                            if (id == (((m.getData().get(1)) >> 4) & 3)) {
                                addressLayer.linkBroken(((((m.getData().get(0)) + 256) & 255) >> 6));
                            }
                        } else if (((m.getData().get(1)) & 7) == 2) {
                            addressLayer.getNeighbours().put(((((m.getData().get(0)) + 256) & 255) >> 6), 7);
                            addressLayer.processRoutingPacket(m.getData().array());
                        } else {
                            addressLayer.processRoutingPacket(m.getData().array());
                        }

                    } else if (m.getType() == MessageType.DONE_SENDING) {

                    } else if (m.getType() == MessageType.HELLO) {
                        System.out.println("HELLO");
                    } else if (m.getType() == MessageType.SENDING) {

                    } else if (m.getType() == MessageType.END) {
                        System.out.println("END");
                        System.exit(0);
                    }
                } catch (InterruptedException e) {
                    System.err.println("Failed to take from queue: " + e);
                }
            }
        }
    }
}