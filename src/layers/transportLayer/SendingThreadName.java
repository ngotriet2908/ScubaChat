package layers.transportLayer;

import gui.ChatGUI;

import java.nio.ByteBuffer;
import java.util.List;

class SendingThreadName extends Thread {
    private int destination;
    private int randomConversationID;
    private byte[] name;
    private TransportLayer transportLayer;

    public SendingThreadName(int destination, int randomConversationID, byte[] name, TransportLayer transportLayer) {
        // TODO Auto-generated constructor stub
        this.name = name;
        this.destination = destination;
        this.randomConversationID = randomConversationID;
        this.transportLayer = transportLayer;
    }

    public void run() {
        transportLayer.getApplicationLayer().setSending(true);

        List<ByteBuffer> packets = transportLayer.constructNamePacket(name, randomConversationID, destination);

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
        transportLayer.getApplicationLayer().setNameSent(destination);
        transportLayer.getApplicationLayer().setSending(false);
    }

}