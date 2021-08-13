package layers.transportLayer;

import gui.ChatGUI;

import java.nio.ByteBuffer;
import java.util.List;

class SendingThreadCBC extends Thread {
    private int destination;
    private int randomConversationID;
    private byte[] message;
    private TransportLayer transportLayer;

    public SendingThreadCBC(int destination, int randomConversationID, byte[] message, TransportLayer transportLayer) {
        this.message = message;
        this.destination = destination;
        this.randomConversationID = randomConversationID;
        this.transportLayer = transportLayer;
    }

    public synchronized void run() {
        List<ByteBuffer> packets = transportLayer.constructCBCPacket(message, randomConversationID, destination);
        transportLayer.getApplicationLayer().setSending(true);

        for (int i = 0; i < packets.size(); i++) {
            transportLayer.setFalseAck();
            int attempt = 0;
            while (!transportLayer.getAckReceive()) {
                transportLayer.getAddressLayer().createCBCPacket(packets.get(i), destination);
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
                if (!transportLayer.getAckReceive()) {
                    System.out.println("Time out, retransmission!");
                }
                attempt++;
                if (attempt > transportLayer.MAXIMUM_RETRANSMIT_ATTEMPT + 2) {
                    transportLayer.getAddressLayer().linkBroken(destination);
                    transportLayer.getApplicationLayer().setSending(false);
                    transportLayer.getApplicationLayer().getSecurityProtocol()[destination] = null;
                    transportLayer.getApplicationLayer().updateOnlineList(destination, ChatGUI.OnlineStatus.OFFLINE);
                    return;
                }
            }
            transportLayer.setSeqNum((transportLayer.getSeqNum() + 1) % 16);
        }

        transportLayer.getApplicationLayer().setHasMyCBC(destination, true);
        transportLayer.getApplicationLayer().setSending(false);
        transportLayer.getApplicationLayer().otherSideToggledEncryption(destination, true);
    }
}