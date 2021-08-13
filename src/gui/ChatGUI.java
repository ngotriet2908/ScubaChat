package gui;

import javafx.animation.RotateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import layers.ApplicationLayerInterface;
import layers.transportLayer.RSA;
import layers.transportLayer.SendingThreadSecurity;
import layers.transportLayer.TransportLayer;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ChatGUI extends Application implements ApplicationLayerInterface {
    public static final int MAX_USERS = 4;
    private static final PseudoClass SELECTED_PSEUDOCLASS = PseudoClass.getPseudoClass("selected");
    public static int deviceWidth;
    public static int deviceHeight;
    public int id;
    FileChooser imageChooser;
    RotateTransition rotateTransition;
    private Stage stage;
    private TransportLayer transportLayer;
    private Map<String, GridPane> users = new HashMap<>();
    private int chatWithOpened;
    private RSA rsa;
    private String CBC;
    private boolean sending;
    private boolean multicastSending = false;
    private SendingThreadSecurity[] securityProtocolvoid;
    private boolean[] hasMyRSA;
    private boolean[] hasMyCBC;
    private Scene mainScene;
    private List<Integer> nameSentTo;
    @FXML
    private Pane chat;
    @FXML
    private Pane chatCover;
    private String name;
    @FXML
    private TextField setupName;
    @FXML
    private TextField setupId;
    @FXML
    private Pane setupScreen;
    @FXML
    private Text menuName;
    @FXML
    private Pane chatScreen;
    @FXML
    private Pane menuConversationsButton;
    @FXML
    private Pane menuContactsButton;
    @FXML
    private Pane conversations;
    @FXML
    private Pane contacts;
    @FXML
    private Pane contactsContainer;
    private List<Integer> contactsList = new ArrayList<>();
    @FXML
    private Pane chatMain;
    @FXML
    private Text chatHeaderReceiverName;
    @FXML
    private Pane chatLockButton;
    private Map<Integer, OnlineStatus> onlineStatus = new HashMap<>();
    @FXML
    private Pane conversationsContainer;
    @FXML
    private TextField chatFooterTypeField;
    @FXML
    private FontIcon chatFooterSendIcon;
    @FXML
    private Pane chatFooterAttachButton;
    @FXML
    private Pane chatNameButton;

    public static void main(String[] args) {
        launch(args);
    }

    public synchronized boolean getSending() {
        return sending;
    }

    public synchronized void setSending(boolean value) {
        sending = value;

        if (!sending) {
            notifyWhenMessageSent();
        }
    }

    public synchronized boolean isMulticastSending() {
        return multicastSending;
    }

    public synchronized void setMulticastSending(boolean multicastSending) {
        this.multicastSending = multicastSending;
    }

    public synchronized SendingThreadSecurity[] getSecurityProtocol() {
        return securityProtocolvoid;
    }

    public synchronized void setHasMyRSA(int destination, boolean value) {
        hasMyRSA[destination] = value;
    }

    public synchronized void setHasMyCBC(int destination, boolean value) {
        hasMyCBC[destination] = value;
    }

    public synchronized String getCBC() {
        return CBC;
    }

    // function to generate a random string of length n
    private synchronized String getAlphaNumericString(int n) {
        // chose a Character random from this String
        String AlphaNumericString = "ABCDEFGHIJKLMNOPabcdefghijklmnop";

        // create StringBuffer size of AlphaNumericString
        StringBuilder sb = new StringBuilder(n);

        for (int i = 0; i < n; i++) {

            // generate a random number between
            // 0 to AlphaNumericString variable length
            int index
                    = (int) (AlphaNumericString.length()
                    * Math.random());

            // add Character one by one in end of sb
            sb.append(AlphaNumericString
                    .charAt(index));
        }

        return sb.toString();
    }

    public synchronized boolean alreadyReceivedRSA(int destination) {
        return hasMyRSA[destination];
    }

    public synchronized TransportLayer getTransportLayer() {
        return transportLayer;
    }

    public synchronized boolean alreadyReceivedCBC(int destination) {
        return hasMyCBC[destination];
    }

    public synchronized void securityProtocol(int destination) {
        SendingThreadSecurity securityProtocolvoid = new SendingThreadSecurity(destination, this);
        securityProtocolvoid.start();
    }

    public RSA getRSA() {
        return rsa;
    }

    public int getId() {
        return id;
    }

    @Override
    public synchronized void start(Stage primaryStage) {
        try {
            // get device screen's width and height
            GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().
                    getDefaultScreenDevice();
            deviceWidth = device.getDisplayMode().getWidth();
            deviceHeight = device.getDisplayMode().getHeight();

            stage = primaryStage;
            stage.setWidth(deviceWidth / 1.5);
            stage.setHeight(deviceHeight / 1.5);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("chat_view.fxml"));

            mainScene = new Scene(loader.load());

            primaryStage.setScene(mainScene);

            primaryStage.setTitle("Scooby-dooby-doooo chat");

            stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
                public void handle(WindowEvent evt) {
                    System.exit(1);
                }
            });

            primaryStage.show();
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    public synchronized void initialize() throws Exception {
        super.init();

        // initialize list of users who know this user's name
        nameSentTo = new ArrayList<>();

        // display setup screen and hide app's main screen
        setupScreen.setDisable(false);
        setupScreen.setVisible(true);
        chatScreen.setDisable(true);
        chatScreen.setVisible(false);

        // hide chat pane
        chat.setDisable(true);
        chat.setVisible(false);

        // show chat cover
        chatCover.setDisable(false);
        chatCover.setVisible(true);

        imageChooser = new FileChooser();
        imageChooser.setTitle("Choose image");
    }

    @FXML
    private synchronized void setupSubmitClicked(MouseEvent event) {
        // get id, get name, save them, but check for valid input before TODO
        id = Integer.valueOf(setupId.getText());
        name = setupName.getText();

        // create tcp layer
        transportLayer = new TransportLayer(this, rsa);

        // apply transition effect
        // save id and name to respective labels
        menuName.setText(name);

        // add contacts to contacts pane
        for (int i = 0; i < MAX_USERS; i++) {
            if (i != id) {
                contactsList.add(i);
                contactsContainer.getChildren().add(createContact(i, "last message"));
            }
        }

        // highlight contacts button and show contacts pane/hide conversations pane
        menuContactsButton.pseudoClassStateChanged(SELECTED_PSEUDOCLASS, true);

        conversations.setDisable(true);
        conversations.setVisible(false);
        conversations.setManaged(false);

        contacts.setDisable(false);
        contacts.setVisible(true);
        contacts.setManaged(true);

        // remove setup screen
        setupScreen.setDisable(true);
        setupScreen.setVisible(false);
        setupScreen.setManaged(false);

        // show app's main screen
        chatScreen.setDisable(false);
        chatScreen.setVisible(true);

        // start the app
        securityProtocolvoid = new SendingThreadSecurity[4];
        hasMyCBC = new boolean[4];
        hasMyRSA = new boolean[4];

        rsa = new RSA();
        CBC = getAlphaNumericString(3);

        sending = false;
    }

    public GridPane createContact(int userNum, String message) {
        GridPane contactPane = new GridPane();

        contactPane.setPrefWidth(Region.USE_COMPUTED_SIZE);
        contactPane.setMaxWidth(Region.USE_COMPUTED_SIZE);
        contactPane.setMinWidth(Region.USE_COMPUTED_SIZE);

        contactPane.getStyleClass().add("usersTabEntry");

        contactPane.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        contactPane.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

        // user picture/number
        StackPane user = new StackPane();
        contactPane.add(user, 0, 0);
        Label shortUsername = new Label(String.valueOf(userNum));
        shortUsername.getStyleClass().add("entryId");
        user.getChildren().add(shortUsername);

        // "User 1" name and last message
        GridPane username = new GridPane();
        contactPane.add(username, 1, 0);

        RowConstraints firstRow = new RowConstraints();
        firstRow.setPercentHeight(45);
        firstRow.setVgrow(Priority.NEVER);

        firstRow.setPrefHeight(Region.USE_COMPUTED_SIZE);
        firstRow.setMaxHeight(Region.USE_COMPUTED_SIZE);
        firstRow.setMinHeight(Region.USE_COMPUTED_SIZE);

        RowConstraints secondRow = new RowConstraints();
        secondRow.setPercentHeight(55);
        secondRow.setVgrow(Priority.NEVER);

        secondRow.setPrefHeight(Region.USE_COMPUTED_SIZE);
        secondRow.setMaxHeight(Region.USE_COMPUTED_SIZE);
        secondRow.setMinHeight(Region.USE_COMPUTED_SIZE);

        username.getRowConstraints().addAll(firstRow, secondRow);

        // full name
        Label fullUsername;
        if (transportLayer.getNames().containsKey(userNum)) {
            fullUsername = new Label(transportLayer.getNames().get(userNum));
        } else {
            fullUsername = new Label("User " + String.valueOf(userNum));
        }
        fullUsername.getStyleClass().add("entryUsername");
        username.add(fullUsername, 0, 0);

        // last message in conversation
        Label lastMessage = new Label(message);
        lastMessage.getStyleClass().add("entryLastMessage");
        username.add(lastMessage, 0, 1);

        // something else
        StackPane status = new StackPane();
        FontIcon circle = new FontIcon();
        circle.setIconLiteral("fa-circle");
        status.getChildren().add(circle);

        if (onlineStatus.get(userNum) == OnlineStatus.OFFLINE) {
            // red
            circle.getStyleClass().add("statusRed");
        } else if (onlineStatus.get(userNum) == OnlineStatus.ONLINE) {
            // green
            circle.getStyleClass().add("statusGreen");
        } else {
            // orange
            circle.getStyleClass().add("statusOrange");
        }

        contactPane.add(status, 2, 0);

        ColumnConstraints firstCol = new ColumnConstraints();
        firstCol.setPercentWidth(20);
        firstCol.setHgrow(Priority.NEVER);

        firstCol.setPrefWidth(Region.USE_COMPUTED_SIZE);
        firstCol.setMaxWidth(Region.USE_COMPUTED_SIZE);
        firstCol.setMinWidth(Region.USE_COMPUTED_SIZE);

        ColumnConstraints secondCol = new ColumnConstraints();
        secondCol.setPercentWidth(60);
        secondCol.setHgrow(Priority.NEVER);

        secondCol.setPrefWidth(Region.USE_COMPUTED_SIZE);
        secondCol.setMaxWidth(Region.USE_COMPUTED_SIZE);
        secondCol.setMinWidth(Region.USE_COMPUTED_SIZE);

        ColumnConstraints thirdCol = new ColumnConstraints();
        thirdCol.setPercentWidth(20);
        thirdCol.setHgrow(Priority.NEVER);

        thirdCol.setPrefWidth(Region.USE_COMPUTED_SIZE);
        thirdCol.setMaxWidth(Region.USE_COMPUTED_SIZE);
        thirdCol.setMinWidth(Region.USE_COMPUTED_SIZE);

        contactPane.getColumnConstraints().addAll(firstCol, secondCol, thirdCol);

        contactPane.setId("user" + userNum);

        // add event listener
        contactPane.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public synchronized void handle(MouseEvent event) {
                // show chat pane
                chatCover.setDisable(true);
                chatCover.setVisible(false);

                // hide chat cover
                chat.setDisable(false);
                chat.setVisible(true);

                // open chat with that user
                chatWithOpened = Integer.valueOf(((GridPane) event.getSource()).getId().split("r")[1]);

                // remove previous chat pane
                chatMain.getChildren().clear();

                // load messages with the user selected
                TransportLayer.OneToOneConversation conversationWithUser = transportLayer.getAllConversations().get(chatWithOpened);

                if (conversationWithUser != null) {
                    for (int i = 0; i < conversationWithUser.getConversation().size(); i++) {
                        if (conversationWithUser.getConversation().get(i).source == -1) {
                            int type = conversationWithUser.getConversation().get(i).type;

                            if (type == 0) {
                                chatMain.getChildren().add(createOutgoingMessagePane(id, new String(conversationWithUser.getConversation().get(i).message), type));
                            } else if (type == 1) {
                                // image
                                chatMain.getChildren().add(createOutgoingMessagePane(id, conversationWithUser.getConversation().get(i).link, type));
                            }
                        } else {
                            int type = conversationWithUser.getConversation().get(i).type;

                            if (type == 0) {
                                chatMain.getChildren().add(createIncomingMessagePane(conversationWithUser.getConversation().get(i).source, new String(conversationWithUser.getConversation().get(i).message), type));
                            } else if (type == 1) {
                                // image
                                chatMain.getChildren().add(createIncomingMessagePane(conversationWithUser.getConversation().get(i).source, conversationWithUser.getConversation().get(i).link, type));
                            }
                        }
                    }
                }

                // remove highlight from every selected node
                for (Node contact : contactsContainer.getChildren()) {
                    contact.pseudoClassStateChanged(SELECTED_PSEUDOCLASS, false);
                }

                // highlight selected username
                ((GridPane) event.getSource()).pseudoClassStateChanged(SELECTED_PSEUDOCLASS, true);

                // add user's name to chat pane
                if (transportLayer.getNames().containsKey(userNum)) {
                    chatHeaderReceiverName.setText(transportLayer.getNames().get(userNum));
                } else {
                    chatHeaderReceiverName.setText("User " + chatWithOpened);
                }

                // remove previous name icon
                chatNameButton.getChildren().clear();

                // display correct previous name icon
                if (transportLayer.getNames().containsKey(chatWithOpened)) {
                    FontIcon nameNotSecret = new FontIcon("fa-id-card");
                    nameNotSecret.setId("nameIcon");

                    nameNotSecret.getStyleClass().add("iconNameNotSecret");
                    chatNameButton.getChildren().add(nameNotSecret);
                } else {
                    FontIcon nameSecret = new FontIcon("fa-user-secret");
                    nameSecret.setId("nameIcon");

                    nameSecret.getStyleClass().add("iconNameSecret");
                    chatNameButton.getChildren().add(nameSecret);
                }

                // remove previous lock
                chatLockButton.getChildren().clear();

                // display correct lock state
                if (chatWithOpened < 4 && hasMyCBC[chatWithOpened]) {
                    // display locked
                    FontIcon lockedLock = new FontIcon("fa-lock");
                    lockedLock.setId("lockIcon");

                    // set new color
                    lockedLock.getStyleClass().add("iconLockLocked");

                    chatLockButton.getChildren().add(lockedLock);

                } else {
                    // display unlocked
                    FontIcon unlockedLock = new FontIcon("fa-unlock");
                    unlockedLock.setId("lockIcon");

                    // set new color
                    unlockedLock.getStyleClass().add("iconLock");

                    chatLockButton.getChildren().add(unlockedLock);
                }
            }
        });

        return contactPane;
    }

    @FXML
    private synchronized void contactsHelloClicked(MouseEvent event) {
        // send handshake
        for (int contact : contactsList) {
            updateOnlineList(contact, OnlineStatus.UNKNOWN);
        }

        ByteBuffer hellopacket = ByteBuffer.allocate(2);
        byte[] bytes = getTransportLayer().getAddressLayer().createHelloPacket();
        hellopacket.put(bytes[0]);
        hellopacket.put(bytes[1]);
        getTransportLayer().getAddressLayer().sendMessage(hellopacket);
    }

    @Override
    public void updateOnlineList(int source, OnlineStatus status) {
        onlineStatus.put(source, status);

        Platform.runLater(new Runnable() {
            public void run() {
                menuContactsButtonClicked(null);
            }
        });
    }

    public void refreshChatMain() {
        Platform.runLater(new Runnable() {
            public void run() {
                chatMain.getChildren().clear();

                if (chatWithOpened >= 4) {
                    // load messages with the group selected
                    TransportLayer.GroupChat conversationWithUser = transportLayer.getGroupChats().get(chatWithOpened);

                    if (conversationWithUser != null) {
                        for (int i = 0; i < conversationWithUser.getConversation().size(); i++) {
                            if (conversationWithUser.getConversation().get(i).source == -1) {
                                int type = conversationWithUser.getConversation().get(i).type;

                                if (type == 0) {
                                    chatMain.getChildren().add(createOutgoingMessagePane(id, new String(conversationWithUser.getConversation().get(i).message), type));
                                } else if (type == 1) {
                                    // image
                                    chatMain.getChildren().add(createOutgoingMessagePane(id, conversationWithUser.getConversation().get(i).link, type));
                                }
                            } else {
                                int type = conversationWithUser.getConversation().get(i).type;

                                if (type == 0) {
                                    chatMain.getChildren().add(createIncomingMessagePane(conversationWithUser.getConversation().get(i).source, new String(conversationWithUser.getConversation().get(i).message), type));
                                } else if (type == 1) {
                                    // image
                                    chatMain.getChildren().add(createIncomingMessagePane(conversationWithUser.getConversation().get(i).source, conversationWithUser.getConversation().get(i).link, type));
                                }
                            }
                        }
                    }

                    return;
                }

                // load messages with the user selected
                TransportLayer.OneToOneConversation conversationWithUser = transportLayer.getAllConversations().get(chatWithOpened);

                if (conversationWithUser != null) {
                    for (int i = 0; i < conversationWithUser.getConversation().size(); i++) {
                        if (conversationWithUser.getConversation().get(i).source == -1) {
                            int type = conversationWithUser.getConversation().get(i).type;

                            if (type == 0) {
                                chatMain.getChildren().add(createOutgoingMessagePane(id, new String(conversationWithUser.getConversation().get(i).message), type));
                            } else if (type == 1) {
                                // image
                                chatMain.getChildren().add(createOutgoingMessagePane(id, conversationWithUser.getConversation().get(i).link, type));
                            }
                        } else {
                            int type = conversationWithUser.getConversation().get(i).type;

                            if (type == 0) {
                                chatMain.getChildren().add(createIncomingMessagePane(conversationWithUser.getConversation().get(i).source, new String(conversationWithUser.getConversation().get(i).message), type));
                            } else if (type == 1) {
                                // image
                                chatMain.getChildren().add(createIncomingMessagePane(conversationWithUser.getConversation().get(i).source, conversationWithUser.getConversation().get(i).link, type));
                            }
                        }
                    }
                }


            }
        });
    }

    public synchronized GridPane createConversationPane(int userNum, String message) {
        GridPane conversationPane = new GridPane();

        conversationPane.setPrefWidth(Region.USE_COMPUTED_SIZE);
        conversationPane.setMaxWidth(Region.USE_COMPUTED_SIZE);
        conversationPane.setMinWidth(Region.USE_COMPUTED_SIZE);

        conversationPane.getStyleClass().add("usersTabEntry");

        conversationPane.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        conversationPane.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

        // user picture/number
        StackPane user = new StackPane();
        conversationPane.add(user, 0, 0);
        Label shortUsername = new Label(String.valueOf(userNum));
        shortUsername.getStyleClass().add("entryId");
        user.getChildren().add(shortUsername);

        // "Node 1" name and last message
        GridPane username = new GridPane();
        conversationPane.add(username, 1, 0);

        RowConstraints firstRow = new RowConstraints();
        firstRow.setPercentHeight(45);
        firstRow.setVgrow(Priority.NEVER);

        firstRow.setPrefHeight(Region.USE_COMPUTED_SIZE);
        firstRow.setMaxHeight(Region.USE_COMPUTED_SIZE);
        firstRow.setMinHeight(Region.USE_COMPUTED_SIZE);

        RowConstraints secondRow = new RowConstraints();
        secondRow.setPercentHeight(55);
        secondRow.setVgrow(Priority.NEVER);

        secondRow.setPrefHeight(Region.USE_COMPUTED_SIZE);
        secondRow.setMaxHeight(Region.USE_COMPUTED_SIZE);
        secondRow.setMinHeight(Region.USE_COMPUTED_SIZE);

        username.getRowConstraints().addAll(firstRow, secondRow);

        // last message in conversation
        // full name
        Label fullUsername;
        if (transportLayer.getNames().containsKey(userNum)) {
            fullUsername = new Label(transportLayer.getNames().get(userNum));
        } else {
            if (userNum < 4) {
                fullUsername = new Label("User " + String.valueOf(userNum));
            } else {
                fullUsername = new Label("Group " + String.valueOf(userNum));
            }
        }
        fullUsername.getStyleClass().add("entryUsername");
        username.add(fullUsername, 0, 0);

        // last message in conversation
        Label lastMessage = new Label(message);
        lastMessage.getStyleClass().add("entryLastMessage");
        username.add(lastMessage, 0, 1);

        // something else
        StackPane status = new StackPane();
        conversationPane.add(status, 2, 0);

        ColumnConstraints firstCol = new ColumnConstraints();
        firstCol.setPercentWidth(20);
        firstCol.setHgrow(Priority.NEVER);

        firstCol.setPrefWidth(Region.USE_COMPUTED_SIZE);
        firstCol.setMaxWidth(Region.USE_COMPUTED_SIZE);
        firstCol.setMinWidth(Region.USE_COMPUTED_SIZE);

        ColumnConstraints secondCol = new ColumnConstraints();
        secondCol.setPercentWidth(60);
        secondCol.setHgrow(Priority.NEVER);

        secondCol.setPrefWidth(Region.USE_COMPUTED_SIZE);
        secondCol.setMaxWidth(Region.USE_COMPUTED_SIZE);
        secondCol.setMinWidth(Region.USE_COMPUTED_SIZE);

        ColumnConstraints thirdCol = new ColumnConstraints();
        thirdCol.setPercentWidth(20);
        thirdCol.setHgrow(Priority.NEVER);

        thirdCol.setPrefWidth(Region.USE_COMPUTED_SIZE);
        thirdCol.setMaxWidth(Region.USE_COMPUTED_SIZE);
        thirdCol.setMinWidth(Region.USE_COMPUTED_SIZE);

        conversationPane.getColumnConstraints().addAll(firstCol, secondCol, thirdCol);

        conversationPane.setId("chat" + userNum);

        // add listener
        conversationPane.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public synchronized void handle(MouseEvent event) {
                // show chat pane
                chatCover.setDisable(true);
                chatCover.setVisible(false);

                // hide chat cover
                chat.setDisable(false);
                chat.setVisible(true);

                // open chat with that user
                chatWithOpened = Integer.valueOf(((GridPane) event.getSource()).getId().split("t")[1]);

                // remove previous chat pane
                chatMain.getChildren().clear();

                // remove highlight from every selected node
                for (Node child : conversationsContainer.getChildren()) {
                    child.pseudoClassStateChanged(SELECTED_PSEUDOCLASS, false);
                }

                // highlight selected username
                ((GridPane) event.getSource()).pseudoClassStateChanged(SELECTED_PSEUDOCLASS, true);

                // add user's name to chat pane
                if (transportLayer.getNames().containsKey(userNum)) {
                    chatHeaderReceiverName.setText(transportLayer.getNames().get(userNum));
                } else {
                    if (chatWithOpened < 4) {
                        chatHeaderReceiverName.setText("User " + chatWithOpened);
                    } else {
                        chatHeaderReceiverName.setText("Group " + chatWithOpened);
                    }
                }

                // remove previous name icon
                chatNameButton.getChildren().clear();

                // remove previous lock
                chatLockButton.getChildren().clear();


                if (chatWithOpened >= 4) {
                    // show + button
                    FontIcon add = new FontIcon();
                    add.setIconLiteral("fa-plus");

                    chatLockButton.getChildren().add(add);


                    // load messages with the group selected
                    TransportLayer.GroupChat conversationWithUser = transportLayer.getGroupChats().get(chatWithOpened);

                    if (conversationWithUser != null) {
                        for (int i = 0; i < conversationWithUser.getConversation().size(); i++) {
                            if (conversationWithUser.getConversation().get(i).source == -1) {
                                int type = conversationWithUser.getConversation().get(i).type;

                                if (type == 0) {
                                    chatMain.getChildren().add(createOutgoingMessagePane(id, new String(conversationWithUser.getConversation().get(i).message), type));
                                } else if (type == 1) {
                                    // image
                                    chatMain.getChildren().add(createOutgoingMessagePane(id, conversationWithUser.getConversation().get(i).link, type));
                                }
                            } else {
                                int type = conversationWithUser.getConversation().get(i).type;

                                if (type == 0) {
                                    chatMain.getChildren().add(createIncomingMessagePane(conversationWithUser.getConversation().get(i).source, new String(conversationWithUser.getConversation().get(i).message), type));
                                } else if (type == 1) {
                                    // image
                                    chatMain.getChildren().add(createIncomingMessagePane(conversationWithUser.getConversation().get(i).source, conversationWithUser.getConversation().get(i).link, type));
                                }
                            }
                        }
                    }

                    return;
                }


                // load messages with the user selected
                TransportLayer.OneToOneConversation conversationWithUser = transportLayer.getAllConversations().get(chatWithOpened);

                if (conversationWithUser != null) {
                    for (int i = 0; i < conversationWithUser.getConversation().size(); i++) {
                        if (conversationWithUser.getConversation().get(i).source == -1) {
                            int type = conversationWithUser.getConversation().get(i).type;

                            if (type == 0) {
                                chatMain.getChildren().add(createOutgoingMessagePane(id, new String(conversationWithUser.getConversation().get(i).message), type));
                            } else if (type == 1) {
                                // image
                                chatMain.getChildren().add(createOutgoingMessagePane(id, conversationWithUser.getConversation().get(i).link, type));
                            }
                        } else {
                            int type = conversationWithUser.getConversation().get(i).type;

                            if (type == 0) {
                                chatMain.getChildren().add(createIncomingMessagePane(conversationWithUser.getConversation().get(i).source, new String(conversationWithUser.getConversation().get(i).message), type));
                            } else if (type == 1) {
                                // image
                                chatMain.getChildren().add(createIncomingMessagePane(conversationWithUser.getConversation().get(i).source, conversationWithUser.getConversation().get(i).link, type));
                            }
                        }
                    }
                }


                // display correct previous name icon
                if (transportLayer.getNames().containsKey(chatWithOpened)) {
                    FontIcon nameNotSecret = new FontIcon("fa-id-card");
                    nameNotSecret.setId("nameIcon");

                    nameNotSecret.getStyleClass().add("iconNameNotSecret");
                    chatNameButton.getChildren().add(nameNotSecret);
                } else {
                    FontIcon nameSecret = new FontIcon("fa-user-secret");
                    nameSecret.setId("nameIcon");

                    nameSecret.getStyleClass().add("iconNameSecret");
                    chatNameButton.getChildren().add(nameSecret);
                }


                // display correct lock state
                if (chatWithOpened < 4 && hasMyCBC[chatWithOpened]) {
                    // display locked
                    FontIcon lockedLock = new FontIcon("fa-lock");
                    lockedLock.setId("lockIcon");

                    // set new color
                    lockedLock.getStyleClass().add("iconLockLocked");

                    chatLockButton.getChildren().add(lockedLock);

                } else {
                    // display unlocked
                    FontIcon unlockedLock = new FontIcon("fa-unlock");
                    unlockedLock.setId("lockIcon");

                    // set new color
                    unlockedLock.getStyleClass().add("iconLock");

                    chatLockButton.getChildren().add(unlockedLock);
                }
            }
        });

        return conversationPane;
    }

    @FXML
    private synchronized void conversationsHeaderCreateButtonClicked(MouseEvent event) {
        // show check boxes container with contacts
        VBox choiceContainer = new VBox();
        choiceContainer.getStyleClass().add("choiceContainer");
        VBox choices = new VBox();

        AnchorPane ap = new AnchorPane();

        // choice for user to start chat with
        ComboBox choiceBox = new ComboBox();
        choiceBox.getStyleClass().add("comboBox");
        ap.getChildren().add(choiceBox);

        AnchorPane.setTopAnchor(choiceBox, 0.0);
        AnchorPane.setLeftAnchor(choiceBox, 0.0);
        AnchorPane.setRightAnchor(choiceBox, 0.0);
        AnchorPane.setBottomAnchor(choiceBox, 0.0);

        for (int contact : contactsList) {
            if (transportLayer.getNames().containsKey(contact)) {
                choiceBox.getItems().add(transportLayer.getNames().get(contact));
                choiceBox.setValue(transportLayer.getNames().get(contact));
            } else {
                choiceBox.getItems().add("User " + contact);
                choiceBox.setValue("User " + contact);
            }
        }

        choices.getChildren().add(ap);
        choiceContainer.getChildren().add(choices);

        // choice box for conversation id
        VBox choicesId = new VBox();
        ComboBox choiceBoxId = new ComboBox();
        choiceBoxId.getStyleClass().add("comboBox");

        AnchorPane ap1 = new AnchorPane();
        ap1.getChildren().add(choiceBoxId);

        AnchorPane.setTopAnchor(choiceBoxId, 0.0);
        AnchorPane.setLeftAnchor(choiceBoxId, 0.0);
        AnchorPane.setRightAnchor(choiceBoxId, 0.0);
        AnchorPane.setBottomAnchor(choiceBoxId, 0.0);

        for (int i = 4; i < 8; i++) {
            if (!transportLayer.getGroupChats().containsKey(i)) {
                choiceBoxId.getItems().add(i);
                choiceBoxId.setValue(i);
            }
        }

        choicesId.getChildren().add(ap1);
        choiceContainer.getChildren().add(choicesId);

        choicesId.getStyleClass().add("lastChoiceContainer");

        // show create button
        Button createButton = new Button("Create");
        createButton.getStyleClass().add("groupButton");

        AnchorPane ap2 = new AnchorPane();
        ap2.getChildren().add(createButton);

        AnchorPane.setTopAnchor(createButton, 0.0);
        AnchorPane.setLeftAnchor(createButton, 0.0);
        AnchorPane.setRightAnchor(createButton, 0.0);
        AnchorPane.setBottomAnchor(createButton, 0.0);

        choiceContainer.getChildren().add(ap2);

        // add choice container to conversations container
        conversationsContainer.getChildren().clear();
        conversationsContainer.getChildren().add(choiceContainer);

        // create listener for create button
        createButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public synchronized void handle(MouseEvent event) {
                // get user id
                String value = (String) choiceBox.getValue();
                int userId = 0;

                if (transportLayer.getNames().containsValue(value)) {
                    for (Map.Entry entry : transportLayer.getNames().entrySet()) {
                        if (entry.getValue().equals(value)) {
                            userId = (int) entry.getKey();
                        }
                    }
                } else {
                    userId = Integer.valueOf(value.split(" ")[1]);
                }

                // get conversation id
                int conversationID = (int) choiceBoxId.getValue();

                // create a chat with this person, add it to the list of chats
                if (!transportLayer.getGroupChats().containsKey(conversationID)) {
                    transportLayer.addGroupChat(conversationID);
                    transportLayer.getGroupChats().get(conversationID).getMembers().add(id);
                }

                transportLayer.getGroupChats().get(conversationID).getMembers().add(userId);

                chatMain.getChildren().add(createOutgoingMessagePane(id, "You added user " + userId + " to the party!", 0));
                transportLayer.createMessage(-1, ("You added user " + userId + " to the party!").getBytes(), 0, conversationID);

                transportLayer.forwardGroupMember(conversationID, userId);

                // show conversations pane and open that chat in chats screen
                menuConversationsButtonClicked(null);

                setSendingAvailability(false, true);

                // replace send icon with waiting icon
                chatFooterSendIcon.setIconLiteral("fa-spinner");

                // animate rotating spinner
                rotateTransition = new RotateTransition();
                rotateTransition.setNode(chatFooterSendIcon);
                rotateTransition.setByAngle(360);
                rotateTransition.setCycleCount(2);
                rotateTransition.setAutoReverse(false);
                rotateTransition.setOnFinished((ActionEvent evt) -> rotateTransition.play());

                rotateTransition.setDuration(Duration.millis(2000));

                // playing animation
                rotateTransition.play();
            }
        });
    }

    @FXML
    private synchronized void menuConversationsButtonClicked(MouseEvent event) {
        // highlight
        menuContactsButton.pseudoClassStateChanged(SELECTED_PSEUDOCLASS, false);
        menuConversationsButton.pseudoClassStateChanged(SELECTED_PSEUDOCLASS, true);

        // show all conversations
        conversationsContainer.getChildren().clear();

        // selection of currently shown chat should remain
        for (Node child : conversationsContainer.getChildren()) {
            if (child.getId().equals("chat" + String.valueOf(chatWithOpened))) {
                child.pseudoClassStateChanged(SELECTED_PSEUDOCLASS, true);
            }
        }

        // load messages with the user selected
        Map<Integer, TransportLayer.OneToOneConversation> conversationsWithUser = transportLayer.getAllConversations();

        for (Map.Entry<Integer, TransportLayer.OneToOneConversation> conversation : conversationsWithUser.entrySet()) {
            if (conversation.getValue().getConversation().size() != 0) {
                int type = conversation.getValue().getConversation().get(conversation.getValue().getConversation().size() - 1).type;

                if (type == 0) {
                    conversationsContainer.getChildren().add(createConversationPane(conversation.getKey(),
                            new String(conversation.getValue().getConversation().get(conversation.getValue().getConversation().size() - 1).message)));
                } else if (type == 1) {
                    conversationsContainer.getChildren().add(createConversationPane(conversation.getKey(), "image"));
                }
            }
        }

        // load group chats with the user (and display last message)
        Map<Integer, TransportLayer.GroupChat> groupsWithUser = transportLayer.getGroupChats();

        for (Map.Entry<Integer, TransportLayer.GroupChat> groupChat : groupsWithUser.entrySet()) {
            int type = -1;
            if (groupChat.getValue().getConversation().size() > 0) {
                type = groupChat.getValue().getConversation().get(groupChat.getValue().getConversation().size() - 1).type;
            }

            if (type == 0) {
                conversationsContainer.getChildren().add(createConversationPane(groupChat.getKey(),
                        new String(groupChat.getValue().getConversation().get(groupChat.getValue().getConversation().size() - 1).message)));
            } else if (type == 1) {
                conversationsContainer.getChildren().add(createConversationPane(groupChat.getKey(), "image"));
            } else if (type == -1) {
                conversationsContainer.getChildren().add(createConversationPane(groupChat.getKey(), "last message"));
            }
        }

        // show current chats tab
        conversations.setDisable(false);
        contacts.setDisable(true);

        conversations.setVisible(true);
        contacts.setVisible(false);
    }

    @FXML
    private synchronized void menuContactsButtonClicked(MouseEvent event) {
        // highlight
        menuConversationsButton.pseudoClassStateChanged(SELECTED_PSEUDOCLASS, false);
        menuContactsButton.pseudoClassStateChanged(SELECTED_PSEUDOCLASS, true);

        // show all contacts
        contactsContainer.getChildren().clear();

        // load contact list
        for (int i : contactsList) {
            contactsContainer.getChildren().add(createContact(i, "contact"));
        }

        // selection of currently shown chat should remain
        for (Node child : contactsContainer.getChildren()) {
            if (child.getId().equals("user" + String.valueOf(chatWithOpened))) {
                child.pseudoClassStateChanged(SELECTED_PSEUDOCLASS, true);
            }
        }

        // highlight contact with which conversation is opened
        menuContactsButton.pseudoClassStateChanged(SELECTED_PSEUDOCLASS, true);

        // show contacts tab
        conversations.setDisable(true);
        contacts.setDisable(false);

        conversations.setVisible(false);
        contacts.setVisible(true);
    }

    public synchronized void deliverMessage(int source, String message, int type, int senderInGroupChat) {
        // update UI
        Platform.runLater(new Runnable() {
            public void run() {
                synchronized (this) {
                    if (senderInGroupChat == -1) {
                        if (source == chatWithOpened) {
                            if (type == 0) {
                                chatMain.getChildren().add(createIncomingMessagePane(source, message, type));
                                //                            chatMainScroll.setVvalue(1.0);
                            } else if (type == 1) {
                                chatMain.getChildren().add(createIncomingMessagePane(source, message, type));
                                //                            chatMainScroll.setVvalue(1.0);
                            } else if (type == 3) {
                                chatHeaderReceiverName.setText(message);
                            }
                        }

                        if (type == 3) {
                            // name arrived
                            // set names in all currently opened panes
                            if (!contacts.isDisabled()) {
                                // find contact, set name
                                Pane contact = (Pane) contacts.lookup("#user" + source);
                                Label text = (Label) contact.lookup(".entryUsername");

                                text.setText(message);
                            }

                            if (!conversations.isDisabled()) {
                                Pane contact = (Pane) conversations.lookup("#chat" + source);

                                if (contact != null) {
                                    Label text = (Label) contact.lookup(".entryUsername");

                                    if (text != null) {
                                        text.setText(message);
                                    }
                                }
                            }
                        }
                    } else {
                        if (source == chatWithOpened) {
                            if (type == 0) {
                                chatMain.getChildren().add(createIncomingMessagePane(senderInGroupChat, message, type));
                            }
                        }
                    }
                }
            }
        });
    }

    @FXML
    private synchronized void chatFooterTypeFieldMessageTyped(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            String message = chatFooterTypeField.getText();
            // clear the field
            chatFooterTypeField.clear();

            // receive messages
            int randomConversationID = 1;
            String command;
            String[] commands;

            command = message;
            if (command.equals("\n")) {
                return;
            }
            if (chatWithOpened == id) {
                return;
            }

            // disable sending messages

            if (chatWithOpened >= 4) {
                setSendingAvailability(false, true);
            } else {
                setSendingAvailability(false, false);
            }

            // replace send icon with waiting icon
            chatFooterSendIcon.setIconLiteral("fa-spinner");

            // animate rotating spinner
            rotateTransition = new RotateTransition();
            rotateTransition.setNode(chatFooterSendIcon);
            rotateTransition.setByAngle(360);
            rotateTransition.setCycleCount(2);
            rotateTransition.setAutoReverse(false);
            rotateTransition.setOnFinished((ActionEvent evt) -> rotateTransition.play());

            rotateTransition.setDuration(Duration.millis(2000));

            //Playing the animation
            rotateTransition.play();

            if (chatWithOpened >= 4) {
                int conversationID = chatWithOpened;
                transportLayer.forwardMessageGroupChat(conversationID, message);
            } else {
                transportLayer.reliableDelivery(chatWithOpened, randomConversationID, message);
            }

            // create and show a message
            chatMain.getChildren().add(createOutgoingMessagePane(id, message, 0));
        }
    }

    public void notifyWhenMessageSent() {

        if (multicastSending) {
            return;
        }

        Platform.runLater(new Runnable() {
            public void run() {
                if (rotateTransition != null) {
                    // enable sending again
                    rotateTransition.setOnFinished(null);
                    setSendingAvailability(true, false);
                    chatFooterSendIcon.setIconLiteral("fa-send");
                }
            }
        });
    }

    public synchronized GridPane createIncomingMessagePane(int userNum, String message, int type) {
        GridPane incomingMessage = new GridPane();

        incomingMessage.setPrefWidth(Region.USE_COMPUTED_SIZE);
        incomingMessage.setMaxWidth(Region.USE_COMPUTED_SIZE);
        incomingMessage.setMinWidth(Region.USE_COMPUTED_SIZE);

        incomingMessage.getStyleClass().add("messagePane");

        incomingMessage.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        incomingMessage.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

        // user picture/number
        StackPane user = new StackPane();
        incomingMessage.add(user, 0, 0);

        user.getChildren().add(new Text(String.valueOf(userNum)));

        user.getStyleClass().add("messageUser");

        // text
        AnchorPane textMessagePane = new AnchorPane();

        if (type == 0) {
            Text textMessage = new Text(message);
            textMessage.setWrappingWidth(chatMain.getWidth() * 0.4);
            textMessagePane.getChildren().add(textMessage);

            AnchorPane.setTopAnchor(textMessage, 0.0);
            AnchorPane.setLeftAnchor(textMessage, 0.0);
            AnchorPane.setRightAnchor(textMessage, 0.0);
            AnchorPane.setBottomAnchor(textMessage, 0.0);
        } else if (type == 1) {
            ImageView image = new ImageView("file:" + message);
            image.setFitHeight(50);
            image.setFitWidth(50);
            textMessagePane.getChildren().add(image);

            AnchorPane.setTopAnchor(image, 0.0);
            AnchorPane.setLeftAnchor(image, 0.0);
            AnchorPane.setRightAnchor(image, 0.0);
            AnchorPane.setBottomAnchor(image, 0.0);
        }

        textMessagePane.getStyleClass().add("messageIncomingBody");

        incomingMessage.add(textMessagePane, 1, 0);

        incomingMessage.setOpaqueInsets(new Insets(100, 100, 100, 100));


        ColumnConstraints firstCol = new ColumnConstraints();
        firstCol.setPercentWidth(15);
        firstCol.setHgrow(Priority.NEVER);

        firstCol.setPrefWidth(Region.USE_COMPUTED_SIZE);
        firstCol.setMaxWidth(Region.USE_COMPUTED_SIZE);
        firstCol.setMinWidth(Region.USE_COMPUTED_SIZE);

        ColumnConstraints secondCol = new ColumnConstraints();
        secondCol.setPercentWidth(45);
        secondCol.setHgrow(Priority.NEVER);

        secondCol.setPrefWidth(Region.USE_COMPUTED_SIZE);
        secondCol.setMaxWidth(Region.USE_COMPUTED_SIZE);
        secondCol.setMinWidth(Region.USE_COMPUTED_SIZE);

        ColumnConstraints thirdCol = new ColumnConstraints();
        thirdCol.setPercentWidth(40);
        thirdCol.setHgrow(Priority.NEVER);

        thirdCol.setPrefWidth(Region.USE_COMPUTED_SIZE);
        thirdCol.setMaxWidth(Region.USE_COMPUTED_SIZE);
        thirdCol.setMinWidth(Region.USE_COMPUTED_SIZE);

        incomingMessage.getColumnConstraints().addAll(firstCol, secondCol, thirdCol);

        return incomingMessage;
    }

    public synchronized GridPane createOutgoingMessagePane(int userNum, String message, int type) {
        GridPane outgoingMessage = new GridPane();

        outgoingMessage.setPrefWidth(Region.USE_COMPUTED_SIZE);
        outgoingMessage.setMaxWidth(Region.USE_COMPUTED_SIZE);
        outgoingMessage.setMinWidth(Region.USE_COMPUTED_SIZE);

        outgoingMessage.getStyleClass().add("messagePane");

        outgoingMessage.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        outgoingMessage.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

        // user picture/number
        StackPane user = new StackPane();
        outgoingMessage.add(user, 2, 0);

        user.getChildren().add(new Text(String.valueOf(userNum)));

        user.getStyleClass().add("messageUser");

        // text
        AnchorPane textMessagePane = new AnchorPane();

        if (type == 0) {
            Text textMessage = new Text(message);
            textMessage.setWrappingWidth(chatMain.getWidth() * 0.4);
            textMessagePane.getChildren().add(textMessage);

            AnchorPane.setTopAnchor(textMessage, 0.0);
            AnchorPane.setLeftAnchor(textMessage, 0.0);
            AnchorPane.setRightAnchor(textMessage, 0.0);
            AnchorPane.setBottomAnchor(textMessage, 0.0);
        } else if (type == 1) {
            ImageView image = new ImageView("file:" + message);
            image.setFitHeight(50);
            image.setFitWidth(50);
            textMessagePane.getChildren().add(image);

            AnchorPane.setTopAnchor(image, 0.0);
            AnchorPane.setLeftAnchor(image, 0.0);
            AnchorPane.setRightAnchor(image, 0.0);
            AnchorPane.setBottomAnchor(image, 0.0);
        }

        textMessagePane.getStyleClass().add("messageOutgoingBody");

        outgoingMessage.add(textMessagePane, 1, 0);

        outgoingMessage.setOpaqueInsets(new Insets(100, 100, 100, 100));


        ColumnConstraints firstCol = new ColumnConstraints();
        firstCol.setPercentWidth(40);
        firstCol.setHgrow(Priority.NEVER);

        firstCol.setPrefWidth(Region.USE_COMPUTED_SIZE);
        firstCol.setMaxWidth(Region.USE_COMPUTED_SIZE);
        firstCol.setMinWidth(Region.USE_COMPUTED_SIZE);

        ColumnConstraints secondCol = new ColumnConstraints();
        secondCol.setPercentWidth(45);
        secondCol.setHgrow(Priority.NEVER);

        secondCol.setPrefWidth(Region.USE_COMPUTED_SIZE);
        secondCol.setMaxWidth(Region.USE_COMPUTED_SIZE);
        secondCol.setMinWidth(Region.USE_COMPUTED_SIZE);

        ColumnConstraints thirdCol = new ColumnConstraints();
        thirdCol.setPercentWidth(15);
        thirdCol.setHgrow(Priority.NEVER);

        thirdCol.setPrefWidth(Region.USE_COMPUTED_SIZE);
        thirdCol.setMaxWidth(Region.USE_COMPUTED_SIZE);
        thirdCol.setMinWidth(Region.USE_COMPUTED_SIZE);

        outgoingMessage.getColumnConstraints().addAll(firstCol, secondCol, thirdCol);

        return outgoingMessage;
    }

    @FXML
    private synchronized void chatLockButtonClicked(MouseEvent event) {
        // group chat
        if (chatWithOpened >= 4) {

            // show check boxes container with contacts
            VBox choiceContainer = new VBox();
            choiceContainer.getStyleClass().add("choiceContainer");
            VBox choices = new VBox();

            AnchorPane ap = new AnchorPane();

            // choice for user to start chat with
            ComboBox choiceBox = new ComboBox();
            choiceBox.getStyleClass().add("comboBox");
            ap.getChildren().add(choiceBox);

            AnchorPane.setTopAnchor(choiceBox, 0.0);
            AnchorPane.setLeftAnchor(choiceBox, 0.0);
            AnchorPane.setRightAnchor(choiceBox, 0.0);
            AnchorPane.setBottomAnchor(choiceBox, 0.0);

            for (int contact : contactsList) {
                if (!transportLayer.getGroupChats().get(chatWithOpened).getMembers().contains(contact)) {
                    if (transportLayer.getNames().containsKey(contact)) {
                        choiceBox.getItems().add(transportLayer.getNames().get(contact));
                        choiceBox.setValue(transportLayer.getNames().get(contact));
                    } else {
                        choiceBox.getItems().add("User " + contact);
                        choiceBox.setValue("User " + contact);
                    }
                }
            }

            choices.getChildren().add(ap);
            choiceContainer.getChildren().add(choices);

            // show create button
            Button createButton = new Button("Add");
            createButton.getStyleClass().add("groupButton");

            AnchorPane ap2 = new AnchorPane();
            ap2.getChildren().add(createButton);

            AnchorPane.setTopAnchor(createButton, 0.0);
            AnchorPane.setLeftAnchor(createButton, 0.0);
            AnchorPane.setRightAnchor(createButton, 0.0);
            AnchorPane.setBottomAnchor(createButton, 0.0);

            choiceContainer.getChildren().add(ap2);

            // add choice container to conversations container
            conversationsContainer.getChildren().clear();
            conversationsContainer.getChildren().add(choiceContainer);

            // create listener for create button
            createButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
                @Override
                public synchronized void handle(MouseEvent event) {
                    // get user id
                    String value = (String) choiceBox.getValue();
                    int userId = 0;

                    if (transportLayer.getNames().containsValue(value)) {
                        for (Map.Entry entry : transportLayer.getNames().entrySet()) {
                            if (entry.getValue().equals(value)) {
                                userId = (int) entry.getKey();
                            }
                        }
                    } else {
                        userId = Integer.valueOf(value.split(" ")[1]);
                    }


                    int conversationID = chatWithOpened;
                    int memberWantToAdd = userId;

                    transportLayer.getGroupChats().get(conversationID).getMembers().add(memberWantToAdd);

                    chatMain.getChildren().add(createOutgoingMessagePane(id, "You added user " + userId + " to the party!", 0));
                    transportLayer.createMessage(-1, ("You added user " + userId + " to the party!").getBytes(), 0, conversationID);

                    transportLayer.forwardGroupMember(conversationID, memberWantToAdd);

                    // show conversations pane and open that chat in chats screen
                    menuConversationsButtonClicked(null);

                    setSendingAvailability(false, true);

                    // replace send icon with waiting icon
                    chatFooterSendIcon.setIconLiteral("fa-spinner");

                    // animate rotating spinner
                    rotateTransition = new RotateTransition();
                    rotateTransition.setNode(chatFooterSendIcon);
                    rotateTransition.setByAngle(360);
                    rotateTransition.setCycleCount(2);
                    rotateTransition.setAutoReverse(false);
                    rotateTransition.setOnFinished((ActionEvent evt) -> rotateTransition.play());

                    rotateTransition.setDuration(Duration.millis(2000));

                    //Playing the animation
                    rotateTransition.play();
                }
            });
            return;
        }

        FontIcon lock = (FontIcon) (((Pane) event.getSource()).getChildren().get(0));

        if (lock.getIconCode().toString().equals("LOCK")) {
            // rotation
            rotateTransition = new RotateTransition();
            rotateTransition.setNode((Node) event.getSource());
            rotateTransition.setByAngle(360);
            rotateTransition.setCycleCount(2);
            rotateTransition.setAutoReverse(false);
            rotateTransition.setOnFinished((ActionEvent evt) -> rotateTransition.play());

            rotateTransition.setDuration(Duration.millis(1000));

            rotateTransition.play();

            // replace with new icon
            lock.setIconLiteral("fa-unlock");

            // set new color
            lock.getStyleClass().remove("iconLockLocked");
            lock.getStyleClass().add("iconLock");

            securityProtocolvoid[chatWithOpened] = null;
            transportLayer.getCBCkey().remove(chatWithOpened);
            transportLayer.getRSAforN().remove(chatWithOpened);

            setSendingAvailability(false, false);

            transportLayer.reliableDeliveryAndDisableSecurity(chatWithOpened, 1, "disable");
        } else {
            // rotation
            rotateTransition = new RotateTransition();
            rotateTransition.setNode((Node) event.getSource());
            rotateTransition.setByAngle(360);
            rotateTransition.setCycleCount(2);
            rotateTransition.setAutoReverse(false);
            rotateTransition.setOnFinished((ActionEvent evt) -> rotateTransition.play());

            rotateTransition.setDuration(Duration.millis(1000));

            //Playing the animation
            rotateTransition.play();

            // replace with new icon
            lock.setIconLiteral("fa-lock");

            // set new color
            lock.getStyleClass().remove("iconLock");
            lock.getStyleClass().add("iconLockLocked");

            setSendingAvailability(false, false);

            if (securityProtocolvoid[chatWithOpened] == null) {
                securityProtocolvoid[chatWithOpened] = new SendingThreadSecurity(chatWithOpened, this);
                securityProtocolvoid[chatWithOpened].start();
            }
        }
    }

    @FXML
    private synchronized void chatNameButtonClicked(MouseEvent event) {
        if (!nameSentTo.contains(chatWithOpened)) {
            FontIcon nameIcon = (FontIcon) (((Pane) event.getSource()).getChildren().get(0));

            // rotation
            rotateTransition = new RotateTransition();
            rotateTransition.setNode((Node) event.getSource());
            rotateTransition.setByAngle(360);
            rotateTransition.setCycleCount(2);
            rotateTransition.setAutoReverse(false);
            rotateTransition.setOnFinished((ActionEvent evt) -> rotateTransition.play());

            rotateTransition.setDuration(Duration.millis(1000));

            rotateTransition.play();

            // replace with new icon
            nameIcon.setIconLiteral("fa-id-card");

            // set new color
            nameIcon.getStyleClass().remove("iconNameSecret");
            nameIcon.getStyleClass().add("iconNameNotSecret");

            // send name
            transportLayer.reliableDeliveryName(chatWithOpened, 1, name);
            setSendingAvailability(false, false);
        }
    }

    public void setNameSent(int source) {
        nameSentTo.add(source);
    }

    @FXML
    private synchronized void chatFooterAttachButtonClicked(MouseEvent event) {
        File file = imageChooser.showOpenDialog(stage);

        if (file != null) {
            int randomConversationID = 1;

            // disable sending messages
            setSendingAvailability(false, false);

            // replace send icon with waiting icon
            chatFooterSendIcon.setIconLiteral("fa-spinner");

            // animate rotating spinner
            rotateTransition = new RotateTransition();
            rotateTransition.setNode(chatFooterSendIcon);
            rotateTransition.setByAngle(360);
            rotateTransition.setCycleCount(2);
            rotateTransition.setAutoReverse(false);
            rotateTransition.setOnFinished((ActionEvent evt) -> rotateTransition.play());

            rotateTransition.setDuration(Duration.millis(2000));

            //Playing the animation
            rotateTransition.play();

            // show it in the chat main pane
            chatMain.getChildren().add(createOutgoingMessagePane(id, file.toURI().toString().split("file:")[1], 1));

            // send an image
            transportLayer.reliableDeliveryImage(chatWithOpened, randomConversationID, file.toURI().toString());
        }
    }

    public synchronized void setSendingAvailability(boolean enable, boolean groupSending) {
        if (enable && groupSending) {
            return;
        }

        if (!enable) {
            chatFooterTypeField.setDisable(true);
            chatFooterTypeField.setPromptText("Sending message. Please wait...");
            chatNameButton.setDisable(true);
            chatLockButton.setDisable(true);
            chatFooterAttachButton.setDisable(true);
        } else {
            chatFooterTypeField.setDisable(false);
            chatFooterTypeField.setPromptText("Type something...");
            chatNameButton.setDisable(false);
            chatLockButton.setDisable(false);
            chatFooterAttachButton.setDisable(false);
        }
    }

    public synchronized void otherSideToggledEncryption(int user, boolean encrypt) {
        Platform.runLater(new Runnable() {
            public void run() {
                if (chatWithOpened == user) {
                    // change lock icon to green one
                    FontIcon lock = (FontIcon) chatLockButton.getChildren().get(0);

                    if (!encrypt) {
                        // rotation
                        lock.setDisable(true);
                        RotateTransition rotateTransition = new RotateTransition();
                        rotateTransition.setNode(lock);
                        rotateTransition.setByAngle(360);
                        rotateTransition.setCycleCount(1);
                        rotateTransition.setAutoReverse(false);
                        rotateTransition.setOnFinished((ActionEvent evt) -> lock.setDisable(false));
                        rotateTransition.setDuration(Duration.millis(1000));
                        rotateTransition.play();

                        // replace with new icon
                        lock.setIconLiteral("fa-unlock");

                        // set new color
                        lock.getStyleClass().remove("iconLockLocked");
                        lock.getStyleClass().add("iconLock");
                    } else {
                        // rotation
                        RotateTransition rotateTransition = new RotateTransition();
                        rotateTransition.setNode(lock);
                        rotateTransition.setByAngle(360);
                        rotateTransition.setCycleCount(1);
                        rotateTransition.setAutoReverse(false);
                        rotateTransition.setDuration(Duration.millis(1000));
                        rotateTransition.play();

                        // replace with new icon
                        lock.setIconLiteral("fa-lock");

                        // set new color
                        lock.getStyleClass().remove("iconLock");
                        lock.getStyleClass().add("iconLockLocked");
                    }
                }
            }
        });
    }

    public enum OnlineStatus {
        ONLINE, OFFLINE, UNKNOWN
    }
}
