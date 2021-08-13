package layers.transportLayer;

import gui.ChatGUI;

import java.nio.ByteBuffer;
import java.util.List;

class SendingThreadN extends Thread {
    private int destination;
    private int randomConversationID;
    private String message;
    private TransportLayer transportLayer;

    public SendingThreadN(int destination, int randomConversationID, String message, TransportLayer transportLayer) {
        this.message = message;
        this.destination = destination;
        this.randomConversationID = randomConversationID;
        this.transportLayer = transportLayer;
    }

    public void run() {
        transportLayer.getApplicationLayer().setSending(true);
        List<ByteBuffer> packets = transportLayer.constructTcpPacket(message.getBytes(), randomConversationID, destination);

        for (int i = 0; i < packets.size(); i++) {
            transportLayer.setFalseAck();
            int attempt = 0;
            while (!transportLayer.getAckReceive()) {
                transportLayer.getAddressLayer().createRSAPacket(packets.get(i), destination);
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
        transportLayer.getApplicationLayer().setHasMyRSA(destination, true);
    }

}