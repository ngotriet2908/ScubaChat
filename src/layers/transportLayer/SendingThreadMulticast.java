package layers.transportLayer;

import java.util.List;

public class SendingThreadMulticast extends Thread {
    private TransportLayer transportLayer;
    private List<Integer> destinations;
    private byte[] message;
    private boolean isListMember;
    private int conversationID;
    private int memberWantToAdd;

    public SendingThreadMulticast(TransportLayer transportLayer, List<Integer> destinations, byte[] message, boolean isListMember, int conversationID, int memberWantToAdd) {
        this.transportLayer = transportLayer;
        this.destinations = destinations;
        this.message = message;
        this.isListMember = isListMember;
        this.conversationID = conversationID;
        this.memberWantToAdd = memberWantToAdd;
    }

    public void run() {
        transportLayer.getApplicationLayer().setMulticastSending(true);
        for (int i = destinations.size() - 1; i >= 0; i--) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            while (transportLayer.getApplicationLayer().getSending()) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (isListMember) {
                SendingThreadGroupMember sendingThreadGroupMember = new SendingThreadGroupMember(destinations.get(i), conversationID, message, transportLayer, memberWantToAdd);
                sendingThreadGroupMember.start();
            } else {
                SendingThreadMessage sendingMessage = new SendingThreadMessage(destinations.get(i), conversationID, new String(message), transportLayer);
                sendingMessage.start();
            }
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        while (transportLayer.getApplicationLayer().getSending()) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        transportLayer.getApplicationLayer().setMulticastSending(false);
        transportLayer.getApplicationLayer().setSending(false);
    }
}
