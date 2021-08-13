package layers.transportLayer;

import gui.ChatGUI;
import layers.AddressLayer;
import layers.ApplicationLayerInterface;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.*;

public class TransportLayer {
    public static final int MAXIMUM_RETRANSMIT_ATTEMPT = 6;
    private static final int MAX_PACKET_LENGTH = 10;
    private static final int MAX_PACKET_LENGTH_WITH_TCP = MAX_PACKET_LENGTH + 2;
    private int buffer;
    private int seqNum;
    private AddressLayer addressLayer;
    private ApplicationLayerInterface applicationLayer;
    private HashMap<Integer, Integer> RSAforN;
    private HashMap<Integer, String> CBCkey;
    private HashMap<Integer, String> nameOfUsers;
    private RSA rsa;
    private boolean ackReceive = false;
    private List<MessageReceived> messageReceived;

    private HashMap<Integer, OneToOneConversation> allConversations;

    private HashMap<Integer, GroupChat> groupChats;

    public TransportLayer(ApplicationLayerInterface applicationLayer, RSA rsa) {
        this.applicationLayer = applicationLayer;
        this.rsa = rsa;
        this.addressLayer = new AddressLayer(this);
        messageReceived = new ArrayList<>();
        seqNum = 0;
        RSAforN = new HashMap<Integer, Integer>();
        CBCkey = new HashMap<>();
        allConversations = new HashMap<>();
        nameOfUsers = new HashMap<>();
        groupChats = new HashMap<>();
    }

    public ApplicationLayerInterface getApplicationLayer() {
        return applicationLayer;
    }

    public synchronized void addGroupChat(int conversationID) {
        groupChats.put(conversationID, new GroupChat(conversationID));
    }

    public Map<Integer, String> getNames() {
        return nameOfUsers;
    }

    public synchronized void processHelloPacket(int source, ChatGUI.OnlineStatus status) {
        applicationLayer.updateOnlineList(source, status);
    }

    public synchronized HashMap<Integer, GroupChat> getGroupChats() {
        return groupChats;
    }

    public synchronized HashMap<Integer, String> getCBCkey() {
        return CBCkey;
    }

    public synchronized HashMap<Integer, Integer> getRSAforN() {
        return RSAforN;
    }

    public synchronized boolean getAckReceive() {
        return ackReceive;
    }

    public synchronized int getSeqNum() {
        return seqNum;
    }

    public synchronized void setSeqNum(int x) {
        seqNum = x;
    }

    public synchronized void setFalseAck() {
        ackReceive = false;
    }

    public synchronized AddressLayer getAddressLayer() {
        return addressLayer;
    }

    public synchronized List<MessageReceived> getListMessage() {
        return messageReceived;
    }

    public Map<Integer, OneToOneConversation> getAllConversations() {
        return allConversations;
    }

    public synchronized void createMessage(int source, byte[] message, int seq, int conversationID) {
        getGroupChats().get(conversationID).getConversation().add(new MessageReceived(source, message, seq));
    }

    public synchronized boolean alreadyReceive(MessageReceived messageReceived) {
        for (int i = 0; i < this.messageReceived.size(); i++) {
            if ((Arrays.equals(messageReceived.message, this.messageReceived.get(i).message)) &&
                    (messageReceived.seq == this.messageReceived.get(i).seq) &&
                    (messageReceived.source == this.messageReceived.get(i).source)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void reliableDeliveryN(int destination, int randomConversationID, String message) {
        SendingThreadN sending = new SendingThreadN(destination, randomConversationID, message, this);
        sending.start();
    }

    public synchronized void reliableDeliveryCBC(int destination, int randomConversationID, byte[] message) {
        if (!RSAforN.containsKey(destination)) {
            System.out.println("No RSA public key from " + destination + " to encrypt CBC key");
        }

        RSA tmp = new RSA(BigInteger.valueOf(41), BigInteger.valueOf(0), BigInteger.valueOf(RSAforN.get(destination)));
        byte[] bytemessage = tmp.encrypt(message);

        SendingThreadCBC sending = new SendingThreadCBC(destination, randomConversationID, bytemessage, this);
        sending.start();
    }

    public synchronized void reliableDelivery(int destination, int randomConversationID, String message) {
        if (randomConversationID == 1) {
            SendingThreadMessage sending = new SendingThreadMessage(destination, randomConversationID, message, this);

            if (!allConversations.containsKey(destination)) {
                allConversations.put(destination, new OneToOneConversation());
                allConversations.get(destination).resetFramgments();
            }

            allConversations.get(destination).getConversation().add(new MessageReceived(-1, message.getBytes(), -100));
            sending.start();
        }
    }

    public synchronized void reliableDeliveryListOfMember(int destination, int randomConversationID, String activeMember) {

        SendingThreadMessage sending = new SendingThreadMessage(destination, randomConversationID, activeMember, this);

        sending.start();
    }

    public synchronized void reliableDeliveryAndDisableSecurity(int destination, int randomConversationID, String message) {

        SendingThreadDisableSecurity sending = new SendingThreadDisableSecurity(destination, randomConversationID, message.getBytes(), this);
        sending.start();
    }

    private String getAlphaNumericString(int n) {
        // chose a Character random from this String
        String AlphaNumericString = "ABCDEFGHIJKLMNOPabcdefghijklmnop123456789";

        // create StringBuffer size of AlphaNumericString
        StringBuilder sb = new StringBuilder(n);

        for (int i = 0; i < n; i++) {
            // generate a random number between
            // 0 to AlphaNumericString variable length
            int index = (int) (AlphaNumericString.length() * Math.random());

            // add Character one by one in end of sb
            sb.append(AlphaNumericString.charAt(index));
        }

        return sb.toString();
    }

    public synchronized void reliableDeliveryImage(int destination, int randomConversationID, String link) {

        try {
            BufferedImage image = null;

            try {
                image = ImageIO.read(Paths.get(new URI(link)).toFile());
            } catch (URISyntaxException uri) {
                uri.printStackTrace();
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);

            byte[] data = outputStream.toByteArray();

            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            BufferedImage bImage2 = ImageIO.read(bis);

            String name = getAlphaNumericString(10);

            ImageIO.write(bImage2, "png", new File("image_sent/" + name + ".png"));

            SendingThreadUsingByteArray sending = new SendingThreadUsingByteArray(destination, randomConversationID, data, this);

            if (!allConversations.containsKey(destination)) {
                allConversations.put(destination, new OneToOneConversation());
                allConversations.get(destination).resetFramgments();
            }

            allConversations.get(destination).getConversation().add(new MessageReceived(-1, data, -100, 1, "image_sent/" + name + ".png"));
            sending.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void reliableDeliveryName(int destination, int randomConversationID, String name) {

        SendingThreadName sending = new SendingThreadName(destination, randomConversationID, name.getBytes(), this);
        sending.start();

    }

    public void forwardGroupMember(int conversationID, int memberWantToAdd) {

        List<Integer> destinations = new ArrayList<>();

        for (int i = 0; i < groupChats.get(conversationID).getMembers().size(); i++) {
            if (groupChats.get(conversationID).getMembers().get(i) != applicationLayer.getId()) {
                destinations.add(groupChats.get(conversationID).getMembers().get(i));
            }
        }

        int Zero = (groupChats.get(conversationID).getMembers().contains(0)) ? 1 : 0;
        int One = (groupChats.get(conversationID).getMembers().contains(1)) ? 1 : 0;
        int Two = (groupChats.get(conversationID).getMembers().contains(2)) ? 1 : 0;
        int Three = (groupChats.get(conversationID).getMembers().contains(3)) ? 1 : 0;


        byte[] memberList = new byte[1];
        memberList[0] = (byte) ((Zero << 3) | (One << 2) | (Two << 1) | (Three));

        SendingThreadMulticast sendingThreadMulticast = new SendingThreadMulticast(this, destinations, memberList, true, conversationID, memberWantToAdd);
        sendingThreadMulticast.start();
    }

    public void forwardMessageGroupChat(int conversationID, String message) {

        List<Integer> destinations = new ArrayList<>();

        for (int i = 0; i < groupChats.get(conversationID).getMembers().size(); i++) {
            if (groupChats.get(conversationID).getMembers().get(i) != applicationLayer.getId()) {
                destinations.add(groupChats.get(conversationID).getMembers().get(i));
            }
        }
        groupChats.get(conversationID).getConversation().add(new MessageReceived(-1, message.getBytes(), 100));

        SendingThreadMulticast sendingThreadMulticast = new SendingThreadMulticast(this, destinations, message.getBytes(), false, conversationID, -1);
        sendingThreadMulticast.start();
    }

    public synchronized List<ByteBuffer> splitMessage(byte[] message, int destination) {

        int messageLength = message.length;

        List<ByteBuffer> splitMessages = new ArrayList<>();

        int numberOfPacketNeeded = (messageLength - 1) / MAX_PACKET_LENGTH + 1;

        for (int i = 0; i < numberOfPacketNeeded - 1; i++) {
            ByteBuffer temp = ByteBuffer.allocate(MAX_PACKET_LENGTH_WITH_TCP);
            for (int j = 2; j < MAX_PACKET_LENGTH_WITH_TCP; j++) {
                temp.put(j, message[MAX_PACKET_LENGTH * i + j - 2]);
            }
            splitMessages.add(temp);
        }

        ByteBuffer temp = ByteBuffer.allocate(2 + messageLength - MAX_PACKET_LENGTH * (numberOfPacketNeeded - 1));

        for (int j = 2; j < 2 + messageLength - MAX_PACKET_LENGTH * (numberOfPacketNeeded - 1); j++) {
            temp.put(j, message[MAX_PACKET_LENGTH * (numberOfPacketNeeded - 1) + j - 2]);
        }

        splitMessages.add(temp);
        return splitMessages;
    }

    public synchronized List<ByteBuffer> splitMessageEncode(byte[] message, int destination) {

        if (CBCkey.containsKey(destination)) {
            try {
                message = Security.encrypt(new String(message), CBCkey.get(destination));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        int messageLength = message.length;

        List<ByteBuffer> splitMessages = new ArrayList<>();

        int numberOfPacketNeeded = (messageLength - 1) / MAX_PACKET_LENGTH + 1;

        for (int i = 0; i < numberOfPacketNeeded - 1; i++) {
            ByteBuffer temp = ByteBuffer.allocate(MAX_PACKET_LENGTH_WITH_TCP);
            for (int j = 2; j < MAX_PACKET_LENGTH_WITH_TCP; j++) {
                temp.put(j, message[MAX_PACKET_LENGTH * i + j - 2]);
            }
            splitMessages.add(temp);
        }

        ByteBuffer temp = ByteBuffer.allocate(2 + messageLength - MAX_PACKET_LENGTH * (numberOfPacketNeeded - 1));

        for (int j = 2; j < 2 + messageLength - MAX_PACKET_LENGTH * (numberOfPacketNeeded - 1); j++) {
            temp.put(j, message[MAX_PACKET_LENGTH * (numberOfPacketNeeded - 1) + j - 2]);
        }

        splitMessages.add(temp);
        return splitMessages;
    }

    public synchronized List<ByteBuffer> splitMessageCBC(byte[] message, int destination) {


        int messageLength = message.length;

        List<ByteBuffer> splitMessages = new ArrayList<>();

        int numberOfPacketNeeded = (messageLength - 1) / MAX_PACKET_LENGTH + 1;

        for (int i = 0; i < numberOfPacketNeeded - 1; i++) {
            ByteBuffer temp = ByteBuffer.allocate(MAX_PACKET_LENGTH_WITH_TCP);
            for (int j = 2; j < MAX_PACKET_LENGTH_WITH_TCP; j++) {
                temp.put(j, message[MAX_PACKET_LENGTH * i + j - 2]);
            }
            splitMessages.add(temp);
        }

        ByteBuffer temp = ByteBuffer.allocate(2 + messageLength - MAX_PACKET_LENGTH * (numberOfPacketNeeded - 1));

        for (int j = 2; j < 2 + messageLength - MAX_PACKET_LENGTH * (numberOfPacketNeeded - 1); j++) {
            temp.put(j, message[MAX_PACKET_LENGTH * (numberOfPacketNeeded - 1) + j - 2]);
        }

        splitMessages.add(temp);
        return splitMessages;
    }

    // creating messages, not acks
    public synchronized List<ByteBuffer> constructTcpPacket(byte[] message, int conversationID, int destination) {
        List<ByteBuffer> splitMessage;

        int encoded = 0;

        if (CBCkey.containsKey(destination)) {
            splitMessage = splitMessageEncode(message, destination);
            encoded = 1;
        } else {
            splitMessage = splitMessage(message, destination);
        }


        for (int i = 0; i < splitMessage.size(); i++) {
            // length and cont
            int cont = i == (splitMessage.size() - 1) ? 0 : 1;
            splitMessage.get(i).put(0, (byte) (((splitMessage.get(i).capacity() - 2) << 4) + (cont << 3)));


            // random conversation id
            splitMessage.get(i).put(1, (byte) ((conversationID << 5) | (encoded << 4) | (((seqNum + i) % 16) & 15)));
        }

        return splitMessage;
    }

    public synchronized List<ByteBuffer> constructTcpMemberListPacket(byte[] message, int conversationID, int destination) {
        List<ByteBuffer> splitMessage;
        splitMessage = splitMessage(message, destination);

        for (int i = 0; i < splitMessage.size(); i++) {
            // length and cont
            int cont = i == (splitMessage.size() - 1) ? 0 : 1;
            splitMessage.get(i).put(0, (byte) (((splitMessage.get(i).capacity() - 2) << 4) + (cont << 3) | 5));


            // random conversation id
            splitMessage.get(i).put(1, (byte) ((conversationID << 5) | (((seqNum + i) % 16) & 15)));
        }

        return splitMessage;
    }

    public synchronized List<ByteBuffer> constructDisablePacket(byte[] message, int conversationID, int destination) {
        List<ByteBuffer> splitMessage;

        int encoded = 0;

        splitMessage = splitMessage(message, destination);

        for (int i = 0; i < splitMessage.size(); i++) {
            // length and cont
            int cont = i == (splitMessage.size() - 1) ? 0 : 1;
            splitMessage.get(i).put(0, (byte) (((splitMessage.get(i).capacity() - 2) << 4) + (cont << 3) | 4));


            // random conversation id
            splitMessage.get(i).put(1, (byte) ((conversationID << 5) | (encoded << 4) | (((seqNum + i) % 16) & 15)));
        }

        return splitMessage;
    }

    // creating messages, not acks
    public synchronized List<ByteBuffer> constructNamePacket(byte[] message, int conversationID, int destination) {
        List<ByteBuffer> splitMessage;

        int encoded = 0;

        if (CBCkey.containsKey(destination)) {
            splitMessage = splitMessageEncode(message, destination);
            encoded = 1;
        } else {
            splitMessage = splitMessage(message, destination);
        }

        for (int i = 0; i < splitMessage.size(); i++) {
            // length and cont
            int cont = i == (splitMessage.size() - 1) ? 0 : 1;
            splitMessage.get(i).put(0, (byte) (((splitMessage.get(i).capacity() - 2) << 4) + (cont << 3) | 3));

            // random conversation id
            splitMessage.get(i).put(1, (byte) ((conversationID << 5) | (encoded << 4) | (((seqNum + i) % 16) & 15)));
        }

        return splitMessage;
    }

    // creating messages, not acks
    public synchronized List<ByteBuffer> constructTcpPacketForImage(byte[] message, int conversationID, int destination) {
        List<ByteBuffer> splitMessage;

        int encoded = 0;

        if (CBCkey.containsKey(destination)) {
            splitMessage = splitMessageEncode(message, destination);
            encoded = 1;
        } else {
            splitMessage = splitMessage(message, destination);
        }

        for (int i = 0; i < splitMessage.size(); i++) {
            // length and cont
            int cont = i == (splitMessage.size() - 1) ? 0 : 1;
            splitMessage.get(i).put(0, (byte) (((splitMessage.get(i).capacity() - 2) << 4) | (cont << 3) | 1));

            // random conversation id
            splitMessage.get(i).put(1, (byte) ((conversationID << 5) | (encoded << 4) | (((seqNum + i) % 16) & 15)));
        }

        return splitMessage;
    }

    public synchronized List<ByteBuffer> constructCBCPacket(byte[] message, int conversationID, int destination) {
        List<ByteBuffer> splitMessage = splitMessageCBC(message, destination);

        for (int i = 0; i < splitMessage.size(); i++) {
            // length and cont
            int cont = i == (splitMessage.size() - 1) ? 0 : 1;
            splitMessage.get(i).put(0, (byte) (((splitMessage.get(i).capacity() - 2) << 4) + (cont << 3)));

            // random conversation id
            splitMessage.get(i).put(1, (byte) ((conversationID << 4) | (((seqNum + i) % 16) & 15)));
        }

        return splitMessage;
    }

    public synchronized boolean isAckWithSeqNum(int ack, int seq) {
        if (seq == 15 && ack == 0) {
            return true;
        }
        if (ack - 1 == seq) {
            return true;
        }
        return false;
    }

    public synchronized void processAckPacket(byte[] packet) {
        int ackNum = ((packet[1] + 256) & 255) >> 4;
        if (isAckWithSeqNum(ackNum, seqNum)) {
            ackReceive = true;
        }
    }

    public void addTo(List<MessageReceived> messageReceiveds, MessageReceived packet) {
        if (messageReceiveds.size() < 16) {
            messageReceiveds.add(packet);
        } else {
            messageReceiveds.remove(0);
            messageReceiveds.add(packet);
        }
    }

    public synchronized void processRSAPacket(byte[] packet, int source) {
        String message = "";

        try {
            message = new String(Arrays.copyOfRange(packet, 2, (((packet[0] + 256) & 255) >> 4) + 2), "UTF-8");
            MessageReceived messageReceived = new MessageReceived(source, "RSA".getBytes(), (((packet[1] & 15) + 256) & 255));
            int continuE = (packet[0] >> 3) & 1;
            if (!alreadyReceive(messageReceived)) {
                addTo(this.messageReceived, messageReceived);
                if (continuE == 0) {
                    if (applicationLayer.getSecurityProtocol()[source] == null) {
                        applicationLayer.getSecurityProtocol()[source] = new SendingThreadSecurity(source, applicationLayer);
                        applicationLayer.getSecurityProtocol()[source].start();
                    }
                    RSAforN.put(source, Integer.parseInt(message));
                }
            }
            addressLayer.createPacketForAckShort(source, (((packet[1] & 15) + 256) & 255));

        } catch (UnsupportedEncodingException ue) {
            ue.printStackTrace();
        }

    }

    public synchronized void processCBCPacket(byte[] packet, int source) {
        byte[] message;
        try {
            message = Arrays.copyOfRange(packet, 2, ((((packet[0] + 256) & 255) >> 4) + 2));

            MessageReceived messageReceived = new MessageReceived(source, "CBC".getBytes(), (((packet[1] & 15) + 256) & 255));
            int continuE = (packet[0] >> 3) & 1;
            if (!alreadyReceive(messageReceived)) {
                addTo(this.messageReceived, messageReceived);

                if (continuE == 0) {
                    String CBCtmp = new String(applicationLayer.getRSA().decrypt(message));
                    CBCkey.put(source, CBCtmp);
                }
            }
            addressLayer.createPacketForAckShort(source, (((packet[1] & 15) + 256) & 255));
        } catch (Exception ue) {
            ue.printStackTrace();
        }
    }

    private byte[] combine2ByteArray(byte[] first, byte[] second) {
        byte[] both = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        return both;
    }

    public synchronized void processReceivedPacket(byte[] packet, int source) {
        byte[] message;
        int conversationID = ((packet[1] + 256) & 255) >> 5;

        if (conversationID == 1) {
            if (!allConversations.containsKey(source)) {
                allConversations.put(source, new OneToOneConversation());
                allConversations.get(source).resetFramgments();
            }
            try {
                message = Arrays.copyOfRange(packet, 2, (((packet[0] + 256) & 255) >> 4) + 2);
                int type = packet[0] & 7;
                int encoded = (packet[1] >> 4) & 1;
                MessageReceived messageReceived = new MessageReceived(source, message, (((packet[1] & 15) + 256) & 255));
                int continuE = (packet[0] >> 3) & 1;
                if (!alreadyReceive(messageReceived)) {
                    addTo(this.messageReceived, messageReceived);

                    if (type == 5) {
                        //TODO receive group chat
                    }

                    if (type == 4) {
                        RSAforN.remove(source);
                        CBCkey.remove(source);
                        applicationLayer.setHasMyCBC(source, false);
                        applicationLayer.setHasMyRSA(source, false);
                        applicationLayer.getSecurityProtocol()[source] = null;
                        allConversations.get(source).resetFramgments();
                        addressLayer.createPacketForAckShort(source, (((packet[1] & 15) + 256) & 255));

                        applicationLayer.otherSideToggledEncryption(source, false); // TODO added
                        return;
                    }

                    if (continuE == 0) {

                        byte[] fullMessage = new byte[0];
                        if (!allConversations.get(source).getFragments().isEmpty()) {
                            fullMessage = allConversations.get(source).getFragments().get(0);
                        }
                        for (int i = 1; i < allConversations.get(source).getFragments().size(); i++) {
                            fullMessage = combine2ByteArray(fullMessage, allConversations.get(source).getFragments().get(i));
                        }
                        fullMessage = combine2ByteArray(fullMessage, message);

                        if (CBCkey.containsKey(source) & encoded == 1) {
                            fullMessage = Security.decrypt(fullMessage, applicationLayer.getCBC()).getBytes();
                        } else if (!CBCkey.containsKey(source) & encoded == 1) {
                            allConversations.get(source).resetFramgments();
                            return;
                        }
                        ByteArrayInputStream bis = new ByteArrayInputStream(fullMessage);
                        BufferedImage bImage2 = ImageIO.read(bis);

                        String name = getAlphaNumericString(10);
                        if (type == 1) {
                            ImageIO.write(bImage2, "png", new File("image_receive/" + name + ".png"));
                            applicationLayer.deliverMessage(source, "image_receive/" + name + ".png", type, -1);
                            allConversations.get(source).getConversation().add(new MessageReceived(source, fullMessage, 100, 1, "image_receive/" + name + ".png"));
                        } else if (type == 0) {
                            applicationLayer.deliverMessage(source, Security.ByteArrayToString(fullMessage), type, -1);
                            allConversations.get(source).getConversation().add(new MessageReceived(source, fullMessage, 100));
                        } else if (type == 3) {
                            nameOfUsers.put(source, Security.ByteArrayToString(fullMessage));
                            applicationLayer.deliverMessage(source, Security.ByteArrayToString(fullMessage), 3, -1);
                        }

                        allConversations.get(source).resetFramgments();
                        for (int i = 0; i < allConversations.get(source).getConversation().size(); i++) {
                            if (allConversations.get(source).getConversation().get(i).source == -1) {

                                if (allConversations.get(source).getConversation().get(i).type == 0) {
                                    System.out.println("You: " + Security.ByteArrayToString(allConversations.get(source).getConversation().get(i).message));
                                } else if (allConversations.get(source).getConversation().get(i).type == 1) {
                                    System.out.println("You: " + (allConversations.get(source).getConversation().get(i).link));
                                }

                            } else {
                                if (allConversations.get(source).getConversation().get(i).type == 0) {
                                    if (nameOfUsers.containsKey(source)) {
                                        System.out.println(nameOfUsers.get(source) + ": " + Security.ByteArrayToString(allConversations.get(source).getConversation().get(i).message));
                                    } else {
                                        System.out.println(allConversations.get(source).getConversation().get(i).source + ": " + Security.ByteArrayToString(allConversations.get(source).getConversation().get(i).message));
                                    }
                                } else if (allConversations.get(source).getConversation().get(i).type == 1) {

                                    if (nameOfUsers.containsKey(source)) {
                                        System.out.println(nameOfUsers.get(source) + ": " + (allConversations.get(source).getConversation().get(i).link));

                                    } else {
                                        System.out.println(allConversations.get(source).getConversation().get(i).source + ": " + (allConversations.get(source).getConversation().get(i).link));
                                    }

                                }
                            }
                        }
                    } else {
                        allConversations.get(source).getFragments().add(message);
                    }
                }
                addressLayer.createPacketForAckShort(source, (((packet[1] & 15) + 256) & 255));
            } catch (Exception ue) {
                ue.printStackTrace();
            }
        } else {
            try {
                message = Arrays.copyOfRange(packet, 2, (((packet[0] + 256) & 255) >> 4) + 2);
                int type = packet[0] & 7;
                int encoded = (packet[1] >> 4) & 1;
                MessageReceived messageReceived = new MessageReceived(source, message, (((packet[1] & 15) + 256) & 255));
                int continueAction = (packet[0] >> 3) & 1;
                if (!alreadyReceive(messageReceived)) {
                    addTo(this.messageReceived, messageReceived);

                    if (type == 5) {
                        boolean zeroIsInConversation = ((message[0] >> 3) & 1) == 1;
                        boolean oneIsInConversation = ((message[0] >> 2) & 1) == 1;
                        boolean twoIsInConversation = ((message[0] >> 1) & 1) == 1;
                        boolean threeIsInConversation = ((message[0]) & 1) == 1;
                        if (!groupChats.containsKey(conversationID)) {
                            groupChats.put(conversationID, new GroupChat(conversationID));
                        }
                        if (zeroIsInConversation && !groupChats.get(conversationID).getMembers().contains(0)) {
                            groupChats.get(conversationID).getMembers().add(0);
                            groupChats.get(conversationID).conversation.add(new MessageReceived(source, (source + " added user 0 to the party!").getBytes(), 0));
                        }
                        if (oneIsInConversation && !groupChats.get(conversationID).getMembers().contains(1)) {
                            groupChats.get(conversationID).getMembers().add(1);
                            groupChats.get(conversationID).conversation.add(new MessageReceived(source, (source + " added user 1 to the party!").getBytes(), 0));
                        }
                        if (twoIsInConversation && !groupChats.get(conversationID).getMembers().contains(2)) {
                            groupChats.get(conversationID).getMembers().add(2);
                            groupChats.get(conversationID).conversation.add(new MessageReceived(source, (source + " added user 2 to the party!").getBytes(), 0));
                        }
                        if (threeIsInConversation && !groupChats.get(conversationID).getMembers().contains(3)) {
                            groupChats.get(conversationID).getMembers().add(3);
                            groupChats.get(conversationID).conversation.add(new MessageReceived(source, (source + " added user 3 to the party!").getBytes(), 0));
                        }
                        groupChats.get(conversationID).getFragments(source).resetFragment();
                        addressLayer.createPacketForAckShort(source, (((packet[1] & 15) + 256) & 255));
                        return;
                    }

                    if (continueAction == 0) {

                        byte[] fullMessage = new byte[0];
                        if (!groupChats.get(conversationID).getFragments(source).getFragments().isEmpty()) {
                            fullMessage = groupChats.get(conversationID).getFragments(source).getFragments().get(0);
                        }
                        for (int i = 1; i < groupChats.get(conversationID).getFragments(source).getFragments().size(); i++) {
                            fullMessage = combine2ByteArray(fullMessage, groupChats.get(conversationID).getFragments(source).getFragments().get(i));
                        }
                        fullMessage = combine2ByteArray(fullMessage, message);

                        if (CBCkey.containsKey(source) & encoded == 1) {
                            fullMessage = Security.decrypt(fullMessage, applicationLayer.getCBC()).getBytes();
                        } else if (!CBCkey.containsKey(source) & encoded == 1) {
                            groupChats.get(conversationID).getFragments(source).resetFragment();
                            return;
                        }
                        ByteArrayInputStream bis = new ByteArrayInputStream(fullMessage);
                        BufferedImage bImage2 = ImageIO.read(bis);

                        String name = getAlphaNumericString(10);
                        if (type == 1) {
                            ImageIO.write(bImage2, "png", new File("image_receive/" + name + ".png"));
                            applicationLayer.deliverMessage(conversationID, "image_receive/" + name + ".png", type, source);
                            groupChats.get(conversationID).getConversation().add(new MessageReceived(source, fullMessage, 100, 1, "image_receive/" + name + ".png"));
                        } else if (type == 0) {
                            applicationLayer.deliverMessage(conversationID, Security.ByteArrayToString(fullMessage), type, source);
                            groupChats.get(conversationID).getConversation().add(new MessageReceived(source, fullMessage, 100));
                        } else if (type == 3) {
                            nameOfUsers.put(source, Security.ByteArrayToString(fullMessage));
                        }

                        groupChats.get(conversationID).getFragments(source).resetFragment();
                        for (int i = 0; i < groupChats.get(conversationID).getConversation().size(); i++) {
                            if (groupChats.get(conversationID).getConversation().get(i).source == -1) {

                                if (groupChats.get(conversationID).getConversation().get(i).type == 0) {
                                    System.out.println("You: " + Security.ByteArrayToString(groupChats.get(conversationID).getConversation().get(i).message));
                                } else if (groupChats.get(conversationID).getConversation().get(i).type == 1) {
                                    System.out.println("You: " + (groupChats.get(conversationID).getConversation().get(i).link));
                                }

                            } else {
                                if (groupChats.get(conversationID).getConversation().get(i).type == 0) {
                                    if (nameOfUsers.containsKey(source)) {
                                        System.out.println(nameOfUsers.get(source) + ": " + Security.ByteArrayToString(groupChats.get(conversationID).getConversation().get(i).message));
                                    } else {
                                        System.out.println(groupChats.get(conversationID).getConversation().get(i).source + ": " + Security.ByteArrayToString(groupChats.get(conversationID).getConversation().get(i).message));
                                    }
                                } else if (groupChats.get(conversationID).getConversation().get(i).type == 1) {

                                    if (nameOfUsers.containsKey(source)) {
                                        System.out.println(nameOfUsers.get(source) + ": " + (groupChats.get(conversationID).getConversation().get(i).link));

                                    } else {
                                        System.out.println(groupChats.get(conversationID).getConversation().get(i).source + ": " + (groupChats.get(conversationID).getConversation().get(i).link));
                                    }

                                }
                            }
                        }
                    } else {
                        groupChats.get(conversationID).getFragments(source).getFragments().add(message);
                    }
                }
                addressLayer.createPacketForAckShort(source, (((packet[1] & 15) + 256) & 255));
            } catch (Exception ue) {
                ue.printStackTrace();
            }
        }
    }

    public class GroupChat {

        private List<MessageReceived> conversation;
        private List<Fragments> fragmentsByUser;
        private int conversationID;
        private List<Integer> members;

        public GroupChat(int conversationID) {
            conversation = new ArrayList<>();
            this.conversationID = conversationID;
            fragmentsByUser = new ArrayList<>();
            members = new ArrayList<>();

            fragmentsByUser.add(new Fragments());
            fragmentsByUser.add(new Fragments());
            fragmentsByUser.add(new Fragments());
            fragmentsByUser.add(new Fragments());
        }

        public synchronized int getConversationID() {
            return conversationID;
        }

        public synchronized List<Integer> getMembers() {
            return members;
        }

        public synchronized Fragments getFragments(int source) {
            return fragmentsByUser.get(source);
        }

        public synchronized List<MessageReceived> getConversation() {
            return conversation;
        }

        public synchronized void resetFramgments(int source) {
            fragmentsByUser.get(source).resetFragment();
        }

        public synchronized void addConversation(MessageReceived messageReceived) {
            conversation.add(messageReceived);
        }

        private class Fragments {
            private List<byte[]> fragments;

            public Fragments() {
                fragments = new ArrayList<>();
            }

            public synchronized List<byte[]> getFragments() {
                return fragments;
            }

            public synchronized void resetFragment() {
                fragments = new ArrayList<>();
            }
        }

    }

    public class OneToOneConversation {

        private List<MessageReceived> conversation;
        private List<byte[]> fragments;

        public OneToOneConversation() {
            conversation = new ArrayList<>();
        }

        public synchronized List<byte[]> getFragments() {
            return fragments;
        }

        public synchronized List<MessageReceived> getConversation() {
            return conversation;
        }

        public synchronized void resetFramgments() {
            fragments = new ArrayList<>();
        }

        public synchronized void addConversation(MessageReceived messageReceived) {
            conversation.add(messageReceived);
        }

    }

    public class MessageReceived {
        public int source;
        public int seq;
        public byte[] message;
        public int type;
        public String link;
        //public long Time;

        public MessageReceived(int source, byte[] message, int seq) {
            this.seq = seq;
            this.source = source;
            this.message = message;
            this.type = 0;
        }

        public MessageReceived(int source, byte[] message, int seq, int type, String link) {
            this.seq = seq;
            this.source = source;
            this.message = message;
            this.type = type;
            this.link = link;
        }
    }
}
