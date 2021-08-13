package layers;

import gui.ChatGUI;
import layers.transportLayer.RSA;
import layers.transportLayer.SendingThreadSecurity;
import layers.transportLayer.TransportLayer;

public interface ApplicationLayerInterface {
    public static String SERVER_IP = "netsys2.ewi.utwente.nl";
    public static int SERVER_PORT = 8954;
    public static int frequency = 1200;

    public SendingThreadSecurity[] getSecurityProtocol();

    public RSA getRSA();

    public void setHasMyRSA(int destination, boolean value);

    public void setHasMyCBC(int destination, boolean value);

    public String getCBC();

    public boolean alreadyReceivedRSA(int destination);

    public boolean alreadyReceivedCBC(int destination);

    public TransportLayer getTransportLayer();

    public boolean getSending();

    public void setSending(boolean value);

    public int getId();

    public void deliverMessage(int source, String message, int type, int senderInGroupChat);

    public void otherSideToggledEncryption(int source, boolean encrypt);

    public void setNameSent(int source);

    public boolean isMulticastSending();

    public void setMulticastSending(boolean multicastSending);

    public void updateOnlineList(int source, ChatGUI.OnlineStatus status);

    public void refreshChatMain();
}
