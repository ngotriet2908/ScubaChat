package layers.transportLayer;

import java.nio.ByteBuffer;
import java.util.List;

class SendingThreadGroupMember extends Thread {
    private int destination;
    private int randomConversationID;
    private byte[] activeMember;
    private TransportLayer transportLayer;
    private int memberWantToAdd;

    public SendingThreadGroupMember(int destination, int randomConversationID, byte[] activeMember, TransportLayer transportLayer, int memberWantToAdd) {
        this.activeMember = activeMember;
        this.destination = destination;
        this.randomConversationID = randomConversationID;
        this.transportLayer = transportLayer;
        this.memberWantToAdd = memberWantToAdd;
    }

    public void run() {
        transportLayer.getApplicationLayer().setSending(true);

        List<ByteBuffer> packets = transportLayer.constructTcpMemberListPacket(activeMember, randomConversationID, destination);

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
                    for (int j = transportLayer.getGroupChats().get(randomConversationID).getMembers().size() - 1; j >= 0; j--) {
                        if (transportLayer.getGroupChats().get(randomConversationID).getMembers().get(j) == memberWantToAdd) {
                            transportLayer.getGroupChats().get(randomConversationID).getMembers().remove(j);
                        }
                    }
                    if (randomConversationID < 4) {
                        for (int j = transportLayer.getAllConversations().get(destination).getConversation().size() - 1; j >= 0; j--) {
                            if (transportLayer.getAllConversations().get(destination).getConversation().get(j).source == -1) {
                                transportLayer.getAllConversations().get(destination).getConversation().remove(j);
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