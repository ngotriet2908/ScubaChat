package layers.transportLayer;

import gui.ChatGUI;

import java.nio.ByteBuffer;
import java.util.List;

class SendingThreadMessage extends Thread {
    private int destination;
    private int randomConversationID;
    private String message;
    private TransportLayer transportLayer;

    public SendingThreadMessage(int destination, int randomConversationID, String message, TransportLayer transportLayer) {
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
                    transportLayer.getApplicationLayer().setMulticastSending(false);
                    transportLayer.getApplicationLayer().setSending(false);

                    if (randomConversationID < 4) {
                        for (int j = transportLayer.getAllConversations().get(destination).getConversation().size() - 1; j >= 0; j--) {
                            if (transportLayer.getAllConversations().get(destination).getConversation().get(j).source == -1) {
                                transportLayer.getAllConversations().get(destination).getConversation().remove(j);
                                transportLayer.getApplicationLayer().updateOnlineList(destination, ChatGUI.OnlineStatus.OFFLINE);
                                break;
                            }
                        }
                    } else {
                        for (int j = transportLayer.getGroupChats().get(randomConversationID).getConversation().size() - 1; j >= 0; j--) {
                            if (transportLayer.getGroupChats().get(randomConversationID).getConversation().get(j).source == -1) {
                                transportLayer.getGroupChats().get(randomConversationID).getConversation().remove(j);
                                break;
                            }
                        }
                    }

                    transportLayer.getApplicationLayer().refreshChatMain();
                    return;
                }
            }

            transportLayer.setSeqNum((transportLayer.getSeqNum() + 1) % 16);
        }
        transportLayer.getApplicationLayer().setSending(false);
    }
}