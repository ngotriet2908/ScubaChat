package layers.transportLayer;

import gui.ChatGUI;

import java.nio.ByteBuffer;
import java.util.List;

class SendingThreadDisableSecurity extends Thread {
    private int destination;
    private int randomConversationID;
    private byte[] message;
    private TransportLayer transportLayer;

    public SendingThreadDisableSecurity(int destination, int randomConversationID, byte[] message, TransportLayer transportLayer) {
        this.message = message;
        this.destination = destination;
        this.randomConversationID = randomConversationID;
        this.transportLayer = transportLayer;
    }

    public void run() {
        transportLayer.getApplicationLayer().setSending(true);

        List<ByteBuffer> packets = transportLayer.constructDisablePacket(message, randomConversationID, destination);

        for (int i = 0; i < packets.size(); i++) {
            transportLayer.setFalseAck();
            int attempt = 0;

            while (!transportLayer.getAckReceive()) {
                transportLayer.getAddressLayer().createIpPacket(packets.get(i), destination);
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
                if (!transportLayer.getAckReceive()) {
                    System.out.println("Time out, retransmission!");
                }
                attempt++;
                if (attempt > transportLayer.MAXIMUM_RETRANSMIT_ATTEMPT) {
                    transportLayer.getAddressLayer().linkBroken(destination);
                    transportLayer.getApplicationLayer().setSending(false);
                    transportLayer.getApplicationLayer().updateOnlineList(destination, ChatGUI.OnlineStatus.OFFLINE);
                    return;
                }
            }

            transportLayer.setSeqNum((transportLayer.getSeqNum() + 1) % 16);
        }
        transportLayer.getApplicationLayer().setHasMyCBC(destination, false);
        transportLayer.getApplicationLayer().setHasMyRSA(destination, false);
        transportLayer.getCBCkey().remove(destination);
        transportLayer.getRSAforN().remove(destination);
        transportLayer.getApplicationLayer().setSending(false);
    }
}