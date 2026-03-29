package com.mygroup;
// import gson n javafx especially being important for gui
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

// for low level socket and connection needs
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.prefs.Preferences;

public class MessengerGui extends Application {

    // keep track of active connection n json tool
    private WebSocket webSocket;
    private final Gson gson = new Gson();
    private Stage primaryStage;

    private String currentPhone = ""; // phone of logged in user
    private String activeChatContact = ""; // which chat is open

    private final Map<String, List<String>> messageHistory = new HashMap<>(); // cached msgs per contact
    private final List<String> contactList = new ArrayList<>(); // sidebar contacts
    private final Map<String, String> contactDisplayNames = new HashMap<>(); // phone -> username label
    private final Set<String> unreadContacts = new HashSet<>(); // contacts with unseen incoming messages

    private VBox chatMessageBox = null; // live ref so incoming msgs can append
    private Label loginFeedbackLabel = null; // live ref so server errors can update it

    // live ref to sidebar so new contacts can be added without rebuilding the whole page
    private VBox contactSidebar = null;

    // connection resilience state
    private volatile boolean isSocketConnected = false;
    private volatile boolean connectInProgress = false;
    private volatile boolean reconnectScheduled = false;
    private volatile boolean shuttingDown = false;
    private Timer heartbeatTimer = null;
    private Timer reconnectTimer = null;
    private long lastPongAtMillis = 0L;
    private int reconnectAttempt = 0;
    private boolean autoReauthInProgress = false;
    private String lastLoginPhone = "";
    private String lastLoginPassword = "";

    private static final long HEARTBEAT_INTERVAL_MS = 20000;
    private static final long PONG_TIMEOUT_MS = 70000;
    private static final long MAX_RECONNECT_DELAY_MS = 10000;

    private enum CustomizationMode {
        NONE,
        THEME,
        IMAGE
    }

    private enum ThemePreset {
        SOFT_LIGHT("Black", "#1b1b1b", "#121212", "#232323", "#3a3a3a"),
        MINT_BREEZE("Green", "#eef9f6", "#d7efe8", "#e7f5f1", "#c5ddd7"),
        SUNSET_CREAM("Warm", "#fff7ef", "#f6e5d5", "#fdf1e5", "#e6d4c4");

        final String label;
        final String rootColor;
        final String barColor;
        final String panelColor;
        final String borderColor;

        ThemePreset(String label, String rootColor, String barColor, String panelColor, String borderColor) {
            this.label = label;
            this.rootColor = rootColor;
            this.barColor = barColor;
            this.panelColor = panelColor;
            this.borderColor = borderColor;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Preferences preferences = Preferences.userNodeForPackage(MessengerGui.class);
    private CustomizationMode customizationMode = CustomizationMode.NONE;
    private ThemePreset selectedTheme = ThemePreset.SOFT_LIGHT;
    private String selectedBackgroundImageUri = "";

    private static final String PREF_MODE_PREFIX = "ui.mode.";
    private static final String PREF_THEME_PREFIX = "ui.theme.";
    private static final String PREF_IMAGE_PREFIX = "ui.image.";


    // data classes for json formatting
    // make sur json matches what backend expects

    class LoginRequest {
        String type = "login"; // hardcoded for now
        String phone_number; // backend LoginInfo uses phone_number not username
        String password;
        LoginRequest(String phone_number, String password) {
            this.phone_number = phone_number;
            this.password = password;
        }
    }

    class RegisterRequest {
        String type = "register";
        String username;
        String phone_number; // backend RegisterInfo uses phone_number only, no username
        String password;
        RegisterRequest(String username, String phone_number, String password) {
            this.username = username;
            this.phone_number = phone_number;
            this.password = password;
        }
    }

    class NewMessageRequest {
        String type = "message"; // backend checks type.equals("message")
        String toPhone;
        String fromPhone; // backend Message class requires fromPhone, filled from currentPhone
        String textMessage; // backend Message class uses textMessage not message
        NewMessageRequest(String toPhone, String textMessage) {
            this.toPhone = toPhone;
            this.fromPhone = currentPhone; // grab logged in phone at send time
            this.textMessage = textMessage;
        }
    }

    // sent when user clicks a chat tab to load history
    // backend checks type.equals("retrieve messages")
    class RetrieveMessagesRequest {
        String type = "retrieve messages";
        String userPhone1;
        String userPhone2;
        RetrieveMessagesRequest(String userPhone2) {
            this.userPhone1 = currentPhone; // always the logged in user
            this.userPhone2 = userPhone2;
        }
    }

    class BlockRequest {
        String type = "block";
        String phone_number;
        BlockRequest(String phone_number) { this.phone_number = phone_number; }
    }

    class ReportRequest {
        String type = "report";
        String phone_number;
        ReportRequest(String phone_number) { this.phone_number = phone_number; }
    }

    class LogoutRequest {
        String type = "logout";
    }

    class DeleteAccountRequest {
        String type = "deleteAcc"; // backend checks type.equals("deleteAcc")
        String phone_number;
        DeleteAccountRequest(String phone_number) { this.phone_number = phone_number; }
    }

    // todo : add ContactActionRequest for remove


    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        connectToServer(); // connect immediately before login/register
        showLoginScreen();
    }


    private void showLoginScreen() {
        // backend login + register both now identify by phone_number only, no username field
        TextField phoneInput = new TextField();
        phoneInput.setPromptText("Phone Number");

        PasswordField passwordInput = new PasswordField();
        passwordInput.setPromptText("Password");

        TextField usernameInput = new TextField();
        usernameInput.setPromptText("Username (for registration)");

        // rough ui setup
        // red for errors, green for register success
        Label feedbackLabel = new Label("");
        feedbackLabel.setTextFill(Color.RED);
        feedbackLabel.setWrapText(true);
        feedbackLabel.setMaxWidth(280);
        this.loginFeedbackLabel = feedbackLabel; // save ref for server to update

        Button loginButton = new Button("Login");
        loginButton.setPrefWidth(130);

        Button registerButton = new Button("Register");
        registerButton.setPrefWidth(130);

        // event listeners for what button is pressed n adding 'type' field to each json
        loginButton.setOnAction(e -> {
            String phone = phoneInput.getText().trim();
            String pass  = passwordInput.getText().trim();
            if (phone.isEmpty() || pass.isEmpty()) {
                feedbackLabel.setText("Please enter a phone number and password.");
                return;
            }
            lastLoginPhone = phone;
            lastLoginPassword = pass;
            autoReauthInProgress = false;
            sendToServer(gson.toJson(new LoginRequest(phone, pass))); // push to server if connection good
        });

        registerButton.setOnAction(e -> {
            String username = usernameInput.getText().trim();
            String phone = phoneInput.getText().trim(); // grab text n put into obj
            String pass  = passwordInput.getText().trim();
            if (username.isEmpty() || phone.isEmpty() || pass.isEmpty()) {
                feedbackLabel.setText("Username, phone number and password are required to register.");
                return;
            }
            sendToServer(gson.toJson(new RegisterRequest(username, phone, pass))); // auto formats to json, includes "type": "register"
        });

        HBox buttonRow = new HBox(10, loginButton, registerButton);
        buttonRow.setAlignment(Pos.CENTER);

        // simple layout styling
        VBox layout = new VBox(12,
                makeTitleLabel("CustomChat"),
                phoneInput,
                passwordInput,
                usernameInput,
                buttonRow,
                feedbackLabel
        );
        layout.setPadding(new Insets(30));
        layout.setAlignment(Pos.CENTER);

        // window settings n whatnot
        Scene scene = new Scene(layout, 380, 300);
        primaryStage.setTitle("CustomChat - Login");
        primaryStage.setScene(scene);
        primaryStage.show();
    }




    private void showMainPage() {
        Label appTitle = new Label("CustomChat");
        appTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        HBox.setHgrow(appTitle, Priority.ALWAYS);

        Label userLabel = new Label("Logged in as: " + currentPhone);
        userLabel.setTextFill(Color.GRAY);

        Button customizeBtn = new Button("Customize");
        customizeBtn.setOnAction(e -> showCustomizationDialog());

        // logout
        Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> logoutAndExit());

        HBox topBar = new HBox(12, appTitle, userLabel, customizeBtn, logoutBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(10, 15, 10, 15));
        topBar.setStyle(buildTopBarStyle());

        // keep top-bar labels readable on the new black theme preset
        if (customizationMode == CustomizationMode.THEME && selectedTheme == ThemePreset.SOFT_LIGHT) {
            appTitle.setTextFill(Color.web("#f2f2f2"));
            userLabel.setTextFill(Color.web("#cfcfcf"));
        }

        contactSidebar = new VBox(6); // save ref so new contacts can be appended later
        contactSidebar.setPadding(new Insets(10));

        Button newMessageBtn = buildNewMessageButton();
        contactSidebar.getChildren().add(newMessageBtn);

        for (String contact : contactList) {
            contactSidebar.getChildren().add(buildContactTab(contact));
        }

        ScrollPane sidebarScroll = new ScrollPane(contactSidebar);
        sidebarScroll.setFitToWidth(true);
        sidebarScroll.setFitToHeight(true); // stretches vbox to fill full panel height so empty area gets themed too
        sidebarScroll.setPrefWidth(220);
        sidebarScroll.setStyle(buildPanelStyle());
        // apply theme/image tint to the inner vbox — scroll pane background alone doesn't cover the content area
        contactSidebar.setStyle(buildContentStyle());

        VBox centerPane = new VBox();
        centerPane.setAlignment(Pos.CENTER);
        Label placeholder = new Label("Select a chat or start a new message");
        placeholder.setTextFill(Color.GRAY);
        placeholder.setFont(Font.font("System", 14));
        centerPane.getChildren().add(placeholder);

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setLeft(sidebarScroll);
        root.setCenter(centerPane);
        root.setStyle(buildRootStyle());

        Scene scene = new Scene(root, 720, 520);
        primaryStage.setTitle("CustomChat - Home");
        primaryStage.setScene(scene);
        primaryStage.show(); // needed when transitioning from login screen
    }

    private Button buildNewMessageButton() {
        Button newMessageBtn = new Button("+ New Message");
        newMessageBtn.setMaxWidth(Double.MAX_VALUE);
        newMessageBtn.setOnAction(e -> showNewMessageDialog());
        return newMessageBtn;
    }

    private void refreshContactSidebar() {
        if (contactSidebar == null) {
            return;
        }

        contactSidebar.getChildren().clear();
        contactSidebar.getChildren().add(buildNewMessageButton());
        for (String contact : contactList) {
            contactSidebar.getChildren().add(buildContactTab(contact));
        }
    }

    private void touchContact(String contact, boolean markUnread) {
        if (contact == null || contact.isBlank()) {
            return;
        }

        contactList.remove(contact);
        contactList.add(0, contact);

        if (markUnread) {
            unreadContacts.add(contact);
        } else {
            unreadContacts.remove(contact);
        }

        refreshContactSidebar();
    }

    private void clearUnread(String contact) {
        if (contact == null || contact.isBlank()) {
            return;
        }

        if (unreadContacts.remove(contact)) {
            refreshContactSidebar();
        }
    }

    // asks for recipient phone + first message then sends both
    private void showNewMessageDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("New Message");

        TextField toPhoneInput = new TextField();
        toPhoneInput.setPromptText("Recipient phone number");

        TextField messageInput = new TextField();
        messageInput.setPromptText("Message (max 200 chars)");
        messageInput.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.length() > 200) messageInput.setText(newVal.substring(0, 200)); // enforce 200 char cap
        });

        Label charCount = new Label("0 / 200");
        messageInput.textProperty().addListener((obs, old, newVal) ->
                charCount.setText(newVal.length() + " / 200")
        );

        Label errorLabel = new Label("");
        errorLabel.setTextFill(Color.RED);

        Button sendBtn = new Button("Send");
        sendBtn.setPrefWidth(110);
        sendBtn.setOnAction(e -> {
            String to  = toPhoneInput.getText().trim();
            String msg = messageInput.getText().trim();
            if (to.isEmpty() || msg.isEmpty()) {
                errorLabel.setText("Both fields are required.");
                return;
            }
            // send the message to backend
            sendToServer(gson.toJson(new NewMessageRequest(to, msg)));

            contactDisplayNames.putIfAbsent(to, to);
            touchContact(to, false);

            // cache msg locally n close dialog
            String line = "You: " + msg;
            messageHistory.computeIfAbsent(to, k -> new ArrayList<>()).add(line);
            dialog.close();

            openChatScreen(to); // jump straight into the chat
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setPrefWidth(110);
        cancelBtn.setOnAction(e -> dialog.close());

        HBox btnRow = new HBox(10, sendBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER);

        VBox layout = new VBox(12,
                makeTitleLabel("New Message"),
                toPhoneInput,
                messageInput,
                charCount,
                btnRow,
                errorLabel
        );
        layout.setPadding(new Insets(24));
        layout.setAlignment(Pos.CENTER);

        dialog.setScene(new Scene(layout, 360, 260));
        dialog.showAndWait();
    }

    // todo : add remove/block/report buttons to this row
    private HBox buildContactTab(String contact) {
        String displayName = contactDisplayNames.getOrDefault(contact, contact);
        Label nameLabel = new Label(displayName);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);
        nameLabel.setOnMouseClicked(e -> openChatScreen(contact));

        Label unreadBadge = new Label("NEW");
        unreadBadge.setStyle("-fx-background-color: #d93025; -fx-text-fill: white; -fx-font-size: 10; -fx-padding: 2 6 2 6; -fx-background-radius: 10;");
        boolean hasUnread = unreadContacts.contains(contact);
        unreadBadge.setVisible(hasUnread);
        unreadBadge.setManaged(hasUnread);

        HBox tab = new HBox(8, nameLabel, unreadBadge);
        tab.setAlignment(Pos.CENTER_LEFT);
        tab.setPadding(new Insets(8, 12, 8, 12));

        String tabBase;
        String tabHover;
        String labelColor;

        if (customizationMode == CustomizationMode.IMAGE) {
            tabBase = "rgba(255,255,255,0.60)";
            tabHover = "rgba(255,255,255,0.78)";
            labelColor = "#111111";
        } else if (customizationMode == CustomizationMode.THEME && selectedTheme == ThemePreset.SOFT_LIGHT) {
            // darker hover for black theme keeps light label text readable
            tabBase = "#1f1f1f";
            tabHover = "#2d3b52";
            labelColor = "#f2f2f2";
        } else {
            tabBase = customizationMode == CustomizationMode.NONE
                    ? "white"
                    : (selectedTheme != null ? selectedTheme.rootColor : "white");
            tabHover = "#dce8ff";
            labelColor = "#111111";
        }

        nameLabel.setStyle("-fx-text-fill: " + labelColor + ";");

        tab.setStyle("-fx-background-color: " + tabBase + "; -fx-background-radius: 5; -fx-cursor: hand;");
        tab.setOnMouseEntered(e -> tab.setStyle("-fx-background-color: " + tabHover + "; -fx-background-radius: 5; -fx-cursor: hand;"));
        tab.setOnMouseExited(e ->  tab.setStyle("-fx-background-color: " + tabBase + "; -fx-background-radius: 5; -fx-cursor: hand;"));

        return tab;
    }


    private void openChatScreen(String contact) {
        activeChatContact = contact;
        clearUnread(contact);

        // ask backend for history between current user and this contact
        sendToServer(gson.toJson(new RetrieveMessagesRequest(contact)));

        VBox messagesBox = new VBox(8);
        messagesBox.setPadding(new Insets(10));
        this.chatMessageBox = messagesBox; // save ref so incoming msgs can append

        // render cached msgs right away
        List<String> cached = messageHistory.getOrDefault(contact, new ArrayList<>());
        for (String msg : cached) {
            messagesBox.getChildren().add(buildMessageBubble(msg));
        }

        ScrollPane scrollPane = new ScrollPane(messagesBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true); // stretches messagesBox to fill full chat area so empty space gets themed too
        // apply theme/image tint to both the scroll wrapper and the inner messages vbox
        scrollPane.setStyle(buildPanelStyle());
        messagesBox.setStyle(buildContentStyle());
        messagesBox.heightProperty().addListener((obs, old, newH) -> scrollPane.setVvalue(1.0)); // auto-scroll to bottom

        TextField messageInput = new TextField();
        messageInput.setPromptText("Type a message... (max 200 chars)");
        HBox.setHgrow(messageInput, Priority.ALWAYS);

        messageInput.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.length() > 200) messageInput.setText(newVal.substring(0, 200)); // enforce 200 char cap
        });

        Label charCount = new Label("0 / 200");
        messageInput.textProperty().addListener((obs, old, newVal) ->
                charCount.setText(newVal.length() + " / 200")
        );

        Button sendBtn = new Button("Send");
        sendBtn.setOnAction(e -> {
            String text = messageInput.getText().trim();
            if (!text.isEmpty()) {
                sendToServer(gson.toJson(new NewMessageRequest(contact, text)));
                touchContact(contact, false);
                String line = "You: " + text;
                messageHistory.computeIfAbsent(contact, k -> new ArrayList<>()).add(line);
                messagesBox.getChildren().add(buildMessageBubble(line)); // optimistic update, don't wait for echo
                messageInput.clear();
            }
        });

        messageInput.setOnAction(e -> sendBtn.fire()); // enter key to send

        HBox inputBar = new HBox(8, messageInput, charCount, sendBtn);
        inputBar.setPadding(new Insets(10));
        inputBar.setAlignment(Pos.CENTER);
        inputBar.setStyle(buildInputBarStyle());

        Button backBtn = new Button("<- Back");
        backBtn.setOnAction(e -> {
            chatMessageBox = null;
            activeChatContact = "";
            showMainPage();
        });

        Label contactLabel = new Label("Chat with: " + contactDisplayNames.getOrDefault(contact, contact));
        contactLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        if (customizationMode == CustomizationMode.THEME && selectedTheme == ThemePreset.SOFT_LIGHT) {
            contactLabel.setTextFill(Color.web("#f2f2f2"));
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button customizeBtn = new Button("Customize");
        customizeBtn.setOnAction(e -> showCustomizationDialog());

        HBox topBar = new HBox(12, backBtn, contactLabel, spacer, customizeBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(10, 15, 10, 15));
        topBar.setStyle(buildTopBarStyle());

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(scrollPane);
        root.setBottom(inputBar);
        root.setStyle(buildRootStyle());
        scrollPane.setStyle(buildPanelStyle());

        Scene scene = new Scene(root, 720, 520);
        primaryStage.setTitle("Chat - " + contact);
        primaryStage.setScene(scene);
    }

    // green bubble right for own msgs, white bubble left for theirs
    private HBox buildMessageBubble(String message) {
        boolean isOwn = message.startsWith("You:") || (!currentPhone.isEmpty() && message.startsWith(currentPhone + ":"));

        String bubbleText = message;
        int prefixSeparator = message.indexOf(": ");
        if (prefixSeparator >= 0 && prefixSeparator + 2 < message.length()) {
            bubbleText = message.substring(prefixSeparator + 2);
        }

        Label bubble = new Label(bubbleText);
        bubble.setWrapText(true);
        bubble.setMaxWidth(420);
        bubble.setPadding(new Insets(8, 14, 8, 14));
        // bubbles always use solid opaque colors so text is readable over any theme or image
        bubble.setStyle(isOwn
                ? "-fx-background-color: #dcf8c6; -fx-background-radius: 14; -fx-font-size: 13; -fx-text-fill: #111111;"
                : "-fx-background-color: #ffffff; -fx-background-radius: 14; -fx-font-size: 13;"
                  + "-fx-border-color: #e0e0e0; -fx-border-radius: 14; -fx-text-fill: #111111;");

        HBox wrapper = new HBox(bubble);
        wrapper.setAlignment(isOwn ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        wrapper.setPadding(new Insets(2, 10, 2, 10));
        return wrapper;
    }


    private void connectToServer() {
        if (shuttingDown || isSocketConnected || connectInProgress) {
            return;
        }

        connectInProgress = true;

        // setup client to handle async websocket build
        HttpClient client = HttpClient.newHttpClient();
        client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:7070/"), new WebSocket.Listener() {

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        // on successful connection
                        MessengerGui.this.webSocket = webSocket;
                        isSocketConnected = true;
                        connectInProgress = false;
                        reconnectScheduled = false;
                        reconnectAttempt = 0;
                        lastPongAtMillis = System.currentTimeMillis();
                        startHeartbeat();

                        System.out.println("Connected to backend server!");

                        if (!currentPhone.isEmpty() && !lastLoginPhone.isEmpty() && !lastLoginPassword.isEmpty()) {
                            // sign back in silently after reconnect so routing resumes without user intervention
                            autoReauthInProgress = true;
                            sendToServer(gson.toJson(new LoginRequest(lastLoginPhone, lastLoginPassword)));
                        }

                        WebSocket.Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        // !!!runs when the backend sends data back to the gui
                        System.out.println("Received from server: " + data);
                        handleServerMessage(data.toString());
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        markDisconnected("close " + statusCode + " " + reason);
                        scheduleReconnect();
                        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        String msg = error != null ? error.getMessage() : "unknown";
                        markDisconnected("error " + msg);
                        scheduleReconnect();
                        WebSocket.Listener.super.onError(webSocket, error);
                    }
                })
                .thenAccept(ws -> this.webSocket = ws) // save socket ref for buttons to use
                .exceptionally(ex -> {
                    // catch connection fails
                    connectInProgress = false;
                    System.out.println("Connection failed: " + ex.getMessage());
                    scheduleReconnect();
                    return null;
                });
    }

    // need so server doesnt time out and ping pongs on
    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatTimer = new Timer(true);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!isSocketConnected || shuttingDown) {
                    return;
                }

                long now = System.currentTimeMillis();
                if (lastPongAtMillis > 0 && now - lastPongAtMillis > PONG_TIMEOUT_MS) {
                    markDisconnected("pong timeout");
                    try {
                        if (webSocket != null) {
                            webSocket.abort();
                        }
                    } catch (Exception ignored) {
                        // best-effort close
                    }
                    scheduleReconnect();
                    return;
                }

                sendToServer("{\"type\":\"ping\"}");
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS);
    }

    private void stopHeartbeat() {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
    }

    private void scheduleReconnect() {
        if (shuttingDown || reconnectScheduled) {
            return;
        }

        reconnectScheduled = true;
        reconnectAttempt++;

        long delay = Math.min((1L << Math.min(reconnectAttempt - 1, 6)) * 1000L, MAX_RECONNECT_DELAY_MS);
        System.out.println("Reconnecting in " + delay + "ms...");

        if (reconnectTimer != null) {
            reconnectTimer.cancel();
        }

        reconnectTimer = new Timer(true);
        reconnectTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                reconnectScheduled = false;
                connectToServer();
            }
        }, delay);
    }

    private void markDisconnected(String reason) {
        isSocketConnected = false;
        connectInProgress = false;
        webSocket = null;
        stopHeartbeat();
        System.out.println("Socket disconnected: " + reason);
    }

    private void sendToServer(String json) {
        if (webSocket != null && isSocketConnected) {
            webSocket.sendText(json, true);
            System.out.println("GUI sent: " + json);
        } else {
            System.out.println("Cannot send - not connected to server.");
            scheduleReconnect();
        }
    }

    private void showCustomizationDialog() {
        Stage dialog = new Stage();
        dialog.initOwner(primaryStage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Customize App");

        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton noneRadio = new RadioButton("Default style");
        RadioButton themeRadio = new RadioButton("Use color theme");
        RadioButton imageRadio = new RadioButton("Use background image");
        // slightly bolder text on the options
        noneRadio.setFont(Font.font("System", FontWeight.BOLD, 13));
        themeRadio.setFont(Font.font("System", FontWeight.BOLD, 13));
        imageRadio.setFont(Font.font("System", FontWeight.BOLD, 13));
        noneRadio.setToggleGroup(modeGroup);
        themeRadio.setToggleGroup(modeGroup);
        imageRadio.setToggleGroup(modeGroup);

        ComboBox<ThemePreset> themePicker = new ComboBox<>();
        themePicker.getItems().addAll(ThemePreset.values());
        themePicker.setValue(selectedTheme);
        themePicker.setMaxWidth(Double.MAX_VALUE);

        Label imageLabel = new Label(selectedBackgroundImageUri.isBlank()
                ? "No image selected"
                : "Image selected");
        imageLabel.setWrapText(true);

        final String[] chosenImageUri = {selectedBackgroundImageUri};
        Button chooseImageBtn = new Button("Choose Image");
        chooseImageBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose Chat Background");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
            );
            File file = chooser.showOpenDialog(dialog);
            if (file != null) {
                chosenImageUri[0] = file.toURI().toString();
                imageLabel.setText(file.getName());
            }
        });

        if (customizationMode == CustomizationMode.IMAGE) {
            imageRadio.setSelected(true);
        } else if (customizationMode == CustomizationMode.THEME) {
            themeRadio.setSelected(true);
        } else {
            noneRadio.setSelected(true);
        }

        Runnable syncInputs = () -> {
            boolean usingTheme = themeRadio.isSelected();
            boolean usingImage = imageRadio.isSelected();
            themePicker.setDisable(!usingTheme);
            chooseImageBtn.setDisable(!usingImage);
            imageLabel.setDisable(!usingImage);
        };
        modeGroup.selectedToggleProperty().addListener((obs, old, cur) -> syncInputs.run());
        syncInputs.run();

        Label feedback = new Label("");
        feedback.setTextFill(Color.RED);

        Button saveBtn = new Button("Save");
        saveBtn.setOnAction(e -> {
            if (imageRadio.isSelected()) {
                if (chosenImageUri[0] == null || chosenImageUri[0].isBlank()) {
                    feedback.setText("Pick an image first, or choose another option.");
                    return;
                }
                customizationMode = CustomizationMode.IMAGE;
                selectedBackgroundImageUri = chosenImageUri[0];
            } else if (themeRadio.isSelected()) {
                customizationMode = CustomizationMode.THEME;
                selectedTheme = themePicker.getValue() != null ? themePicker.getValue() : ThemePreset.SOFT_LIGHT;
            } else {
                customizationMode = CustomizationMode.NONE;
            }

            saveCustomizationPreferences();
            dialog.close();
            redrawCurrentScreen();
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> dialog.close());

        HBox buttons = new HBox(10, saveBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox layout = new VBox(10,
                makeTitleLabel("Customize CustomChat"),
                noneRadio,
                themeRadio,
                themePicker,
                imageRadio,
                new HBox(8, chooseImageBtn, imageLabel),
                feedback,
                buttons
        );
        layout.setPadding(new Insets(16));

        dialog.setScene(new Scene(layout, 420, 280));
        dialog.showAndWait();
    }

    private void redrawCurrentScreen() {
        if (!activeChatContact.isEmpty()) {
            openChatScreen(activeChatContact);
        } else {
            showMainPage();
        }
    }

    private void loadCustomizationPreferences() {
        if (currentPhone == null || currentPhone.isBlank()) {
            return;
        }

        try {
            customizationMode = CustomizationMode.valueOf(
                    preferences.get(PREF_MODE_PREFIX + currentPhone, CustomizationMode.NONE.name())
            );
        } catch (Exception ignored) {
            customizationMode = CustomizationMode.NONE;
        }

        try {
            selectedTheme = ThemePreset.valueOf(
                    preferences.get(PREF_THEME_PREFIX + currentPhone, ThemePreset.SOFT_LIGHT.name())
            );
        } catch (Exception ignored) {
            selectedTheme = ThemePreset.SOFT_LIGHT;
        }

        selectedBackgroundImageUri = preferences.get(PREF_IMAGE_PREFIX + currentPhone, "");
        if (customizationMode == CustomizationMode.IMAGE && selectedBackgroundImageUri.isBlank()) {
            customizationMode = CustomizationMode.THEME;
        }
    }

    private void saveCustomizationPreferences() {
        if (currentPhone == null || currentPhone.isBlank()) {
            return;
        }

        preferences.put(PREF_MODE_PREFIX + currentPhone, customizationMode.name());
        preferences.put(PREF_THEME_PREFIX + currentPhone, selectedTheme.name());
        preferences.put(PREF_IMAGE_PREFIX + currentPhone, selectedBackgroundImageUri == null ? "" : selectedBackgroundImageUri);
    }

    private String buildRootStyle() {
        if (customizationMode == CustomizationMode.IMAGE && selectedBackgroundImageUri != null && !selectedBackgroundImageUri.isBlank()) {
            String safeUri = selectedBackgroundImageUri.replace("'", "%27");
            return "-fx-background-image: url('" + safeUri + "');"
                    + " -fx-background-size: cover;"
                    + " -fx-background-position: center center;"
                    + " -fx-background-repeat: no-repeat;";
        }

        ThemePreset theme = selectedTheme != null ? selectedTheme : ThemePreset.SOFT_LIGHT;
        if (customizationMode == CustomizationMode.NONE) {
            return "-fx-background-color: #ffffff;";
        }
        return "-fx-background-color: " + theme.rootColor + ";";
    }

    private String buildTopBarStyle() {
        if (customizationMode == CustomizationMode.IMAGE) {
            // more opaque so labels on the top bar stay readable over any image
            return "-fx-background-color: rgba(255,255,255,0.78); -fx-border-color: rgba(0,0,0,0.15); -fx-border-width: 0 0 1 0;";
        }
        ThemePreset theme = selectedTheme != null ? selectedTheme : ThemePreset.SOFT_LIGHT;
        if (customizationMode == CustomizationMode.NONE) {
            return "-fx-background-color: #e8e8e8; -fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;";
        }
        return "-fx-background-color: " + theme.barColor + "; -fx-border-color: " + theme.borderColor + "; -fx-border-width: 0 0 1 0;";
    }

    private String buildPanelStyle() {
        if (customizationMode == CustomizationMode.IMAGE) {
            // scroll pane wrapper is transparent
            return "-fx-background: transparent; -fx-background-color: transparent;";
        }
        ThemePreset theme = selectedTheme != null ? selectedTheme : ThemePreset.SOFT_LIGHT;
        if (customizationMode == CustomizationMode.NONE) {
            return "-fx-background-color: #f0f0f0;";
        }
        return "-fx-background-color: " + theme.panelColor + ";";
    }

    private String buildInputBarStyle() {
        if (customizationMode == CustomizationMode.IMAGE) {
            // more opaque so text field and send button stay readable over any image
            return "-fx-background-color: rgba(255,255,255,0.78); -fx-border-color: rgba(0,0,0,0.15); -fx-border-width: 1 0 0 0;";
        }
        ThemePreset theme = selectedTheme != null ? selectedTheme : ThemePreset.SOFT_LIGHT;
        if (customizationMode == CustomizationMode.NONE) {
            return "-fx-background-color: #f5f5f5; -fx-border-color: #dddddd; -fx-border-width: 1 0 0 0;";
        }
        return "-fx-background-color: " + theme.panelColor + "; -fx-border-color: " + theme.borderColor + "; -fx-border-width: 1 0 0 0;";
    }


    // returns the background style for content vboxes
    // scroll pane wrapper uses buildPanelStyle, but the inner vbox needs its own color
    private String buildContentStyle() {
        if (customizationMode == CustomizationMode.IMAGE) {
            return "-fx-background-color: rgba(255,255,255,0.2);";
        }
        ThemePreset theme = selectedTheme != null ? selectedTheme : ThemePreset.SOFT_LIGHT;
        if (customizationMode == CustomizationMode.NONE) {
            return "-fx-background-color: #f0f0f0;";
        }
        return "-fx-background-color: " + theme.panelColor + ";";
    }

    // prevent duplicate message sending, especially on first message of new contact
    private boolean containsEquivalentHistoryLine(List<String> existingLines, String candidateLine, String contact) {
        for (String existingLine : existingLines) {
            if (areEquivalentMessageLines(existingLine, candidateLine, contact)) {
                return true;
            }
        }
        return false;
    }

    private boolean areEquivalentMessageLines(String firstLine, String secondLine, String contact) {
        String[] firstParts = splitMessageLine(firstLine);
        String[] secondParts = splitMessageLine(secondLine);

        if (!Objects.equals(firstParts[1], secondParts[1])) {
            return false;
        }

        int firstSenderGroup = classifySenderLabel(firstParts[0], contact);
        int secondSenderGroup = classifySenderLabel(secondParts[0], contact);

        if (firstSenderGroup != 0 && secondSenderGroup != 0) {
            return firstSenderGroup == secondSenderGroup;
        }

        return Objects.equals(firstLine, secondLine);
    }

    private String[] splitMessageLine(String line) {
        int separatorIndex = line.indexOf(": ");
        if (separatorIndex < 0) {
            return new String[]{"", line};
        }
        return new String[]{line.substring(0, separatorIndex), line.substring(separatorIndex + 2)};
    }

    private int classifySenderLabel(String senderLabel, String contact) {
        String label = senderLabel == null ? "" : senderLabel.trim();
        if (label.isEmpty()) {
            return 0;
        }

        if (label.equals("You") || (!currentPhone.isBlank() && label.equals(currentPhone))) {
            return 1;
        }

        String contactDisplay = contactDisplayNames.getOrDefault(contact, contact);
        if ((contact != null && !contact.isBlank() && label.equals(contact))
                || (contactDisplay != null && !contactDisplay.isBlank() && label.equals(contactDisplay))) {
            return 2;
        }

        return 0;
    }


    private void handleServerMessage(String raw) {
        Platform.runLater(() -> { // all ui updates must go through runLater, runs on background thread
            try {
                JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
                String type = json.has("type") ? json.get("type").getAsString() : "";

                switch (type) {

                    case "login_success": { // login confirmed, move to main page
                        boolean wasAutoReauth = autoReauthInProgress;
                        autoReauthInProgress = false;

                        if (json.has("phone")) currentPhone = json.get("phone").getAsString();
                        loadCustomizationPreferences();

                        if (!wasAutoReauth) {
                            contactList.clear();
                            messageHistory.clear();
                            contactDisplayNames.clear();
                            unreadContacts.clear();
                        }

                        if (json.has("contacts")) {
                            json.getAsJsonArray("contacts").forEach(el -> {
                                if (el.isJsonObject()) {
                                    JsonObject contactObj = el.getAsJsonObject();
                                    String phone = contactObj.has("phone") ? contactObj.get("phone").getAsString() : "";
                                    String username = contactObj.has("username") ? contactObj.get("username").getAsString() : "";
                                    if (!phone.isEmpty()) {
                                        if (!contactList.contains(phone)) {
                                            contactList.add(phone);
                                        }
                                        contactDisplayNames.put(phone, (username == null || username.isBlank()) ? phone : username);
                                        if (contactSidebar != null) {
                                            contactSidebar.getChildren().add(buildContactTab(phone));
                                        }
                                    }
                                } else {
                                    String c = el.getAsString();
                                    if (!contactList.contains(c)) {
                                        contactList.add(c);
                                        contactDisplayNames.putIfAbsent(c, c);
                                        if (contactSidebar != null) {
                                            contactSidebar.getChildren().add(buildContactTab(c));
                                        }
                                    }
                                }
                            });
                        }

                        if (!wasAutoReauth) {
                            showMainPage();
                        }
                        break;
                    }

                    case "login_error": { // login rejected, show error on login screen
                        autoReauthInProgress = false;
                        String msg = json.has("message") ? json.get("message").getAsString() : "Login failed. Please try again.";
                        if (loginFeedbackLabel != null) {
                            loginFeedbackLabel.setTextFill(Color.RED);
                            loginFeedbackLabel.setText(msg);
                        }
                        break;
                    }

                    case "pong": {
                        lastPongAtMillis = System.currentTimeMillis();
                        break;
                    }

                    case "register_success": {
                        String msg = json.has("message") ? json.get("message").getAsString() : "Registered! You can now log in.";
                        if (loginFeedbackLabel != null) {
                            loginFeedbackLabel.setTextFill(Color.GREEN);
                            loginFeedbackLabel.setText(msg);
                        }
                        break;
                    }

                    case "register_error": {
                        String msg = json.has("message") ? json.get("message").getAsString() : "Registration failed.";
                        if (loginFeedbackLabel != null) {
                            loginFeedbackLabel.setTextFill(Color.RED);
                            loginFeedbackLabel.setText(msg);
                        }
                        break;
                    }

                    case "logging_out": { // backend confirmed logout, go back to login
                        currentPhone = "";
                        contactList.clear();
                        messageHistory.clear();
                        contactDisplayNames.clear();
                        unreadContacts.clear();
                        activeChatContact = "";
                        chatMessageBox = null;
                        contactSidebar = null;
                        showLoginScreen();
                        break;
                    }

                    case "incoming_message": { // real-time msg from another user
                        String fromPhone = json.has("fromPhone") ? json.get("fromPhone").getAsString() :
                                (json.has("from") ? json.get("from").getAsString() : "Unknown");
                        String fromUsername = json.has("fromUsername") ? json.get("fromUsername").getAsString() : "";
                        String text = json.has("text") ? json.get("text").getAsString() : "";
                        String line = fromPhone + ": " + text;

                        if (fromUsername != null && !fromUsername.isBlank()) {
                            contactDisplayNames.put(fromPhone, fromUsername);
                        } else {
                            contactDisplayNames.putIfAbsent(fromPhone, fromPhone);
                        }

                        messageHistory.computeIfAbsent(fromPhone, k -> new ArrayList<>()).add(line);

                        boolean chatIsOpenForSender = chatMessageBox != null && fromPhone.equals(activeChatContact);
                        touchContact(fromPhone, !chatIsOpenForSender);

                        if (chatIsOpenForSender) {
                            chatMessageBox.getChildren().add(buildMessageBubble(line)); // append live if chat is open
                        }

                        break;
                    }

                    case "message_error": {
                        String msg = json.has("message") ? json.get("message").getAsString() : "Message failed to send.";
                        System.out.println("Message error: " + msg);
                        break;
                    }

                    case "message_history": {
                        String contact = json.has("contact") ? json.get("contact").getAsString() : activeChatContact;
                        String contactUsername = json.has("contactUsername") ? json.get("contactUsername").getAsString() : "";
                        if (contactUsername != null && !contactUsername.isBlank()) {
                            contactDisplayNames.put(contact, contactUsername);
                        } else {
                            contactDisplayNames.putIfAbsent(contact, contact);
                        }
                        List<String> retrieved = new ArrayList<>();
                        if (json.has("messages") && json.get("messages").isJsonArray()) {
                            json.getAsJsonArray("messages").forEach(el -> retrieved.add(el.getAsString()));
                        }

                        List<String> existing = messageHistory.get(contact);
                        List<String> hist;

                        if (existing == null || existing.isEmpty()) {
                            // first open in this run use backend backfill for last 5 messages from chat
                            hist = new ArrayList<>(retrieved);
                        } else {
                            // if no exit from app, jus back button, keep local in-session flow and only add unseen backfill lines
                            hist = new ArrayList<>(existing);
                            for (String line : retrieved) {
                                if (!containsEquivalentHistoryLine(hist, line, contact)) {
                                    hist.add(line);
                                }
                            }
                        }

                        messageHistory.put(contact, hist);

                        if (!contactList.contains(contact)) {
                            contactList.add(contact);
                            refreshContactSidebar();
                        }

                        if (chatMessageBox != null && contact.equals(activeChatContact)) {
                            chatMessageBox.getChildren().clear();
                            for (String line : hist) {
                                chatMessageBox.getChildren().add(buildMessageBubble(line));
                            }
                        }
                        break;
                    }

                    default: {
                        // backend sends history as a plain map with no "type" field
                        // so it falls here, treat it as backfill instead of replacing newer cached messages
                        if (json.has("m1") && chatMessageBox != null) {
                            List<String> hist = messageHistory.computeIfAbsent(activeChatContact, k -> new ArrayList<>());
                            List<String> retrieved = new ArrayList<>();
                            int i = 1;
                            while (json.has("m" + i)) {
                                String line = json.get("m" + i).getAsString();
                                retrieved.add(line);
                                i++;
                            }

                            // first use retrieved history
                            // later keep local cache so newly sent/received lines are not wiped.
                            if (hist.isEmpty()) {
                                hist.addAll(retrieved);
                            }

                            chatMessageBox.getChildren().clear();
                            for (String line : hist) {
                                chatMessageBox.getChildren().add(buildMessageBubble(line));
                            }
                        } else {
                            System.out.println("Unhandled server message type: " + type);
                        }
                    }
                }

            } catch (Exception e) {
                // backend sends some plain string responses (not json), handle those here
                handlePlainResponse(raw);
            }
        });
    }

    // backend sends plain strings for certain events like logout/disconnect
    private void handlePlainResponse(String raw) {
        if (raw.contains("does not exist")) { // login phone not found
            if (loginFeedbackLabel != null) {
                loginFeedbackLabel.setTextFill(Color.RED);
                loginFeedbackLabel.setText(raw);
            }
        } else if (raw.contains("being disconnected") || raw.contains("Deleting account")) { // logout or delete, go back to login
            currentPhone = "";
            contactList.clear();
            messageHistory.clear();
            contactDisplayNames.clear();
            unreadContacts.clear();
            activeChatContact = "";
            chatMessageBox = null;
            contactSidebar = null;
            showLoginScreen();
        } else {
            System.out.println("Unhandled plain server response: " + raw);
        }
    }


    private Label makeTitleLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 18));
        return label;
    }

    @Override
    public void stop() {
        shuttingDown = true;
        stopHeartbeat();
        if (reconnectTimer != null) {
            reconnectTimer.cancel();
            reconnectTimer = null;
        }
        // clean up connection if user closes window
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Application closed");
        }
    }

    private void logoutAndExit() {
        // exit via logging out, stop reconnect/heartbeat first, then close socket best-effort
        shuttingDown = true;
        stopHeartbeat();

        if (reconnectTimer != null) {
            reconnectTimer.cancel();
            reconnectTimer = null;
        }

        try {
            if (webSocket != null && isSocketConnected) {
                webSocket.sendText(gson.toJson(new LogoutRequest()), true);
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "User logged out");
            }
        } catch (Exception ignored) {
            // app is exiting anyway
        }

        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args); // trigger javafx to start
    }
}
