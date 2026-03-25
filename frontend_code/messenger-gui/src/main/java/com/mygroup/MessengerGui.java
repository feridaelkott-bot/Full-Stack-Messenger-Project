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
import javafx.stage.Stage;

// for low level socket and connection needs
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.*;
import java.util.concurrent.CompletionStage;

public class MessengerGui extends Application {

    // keep track of active connection n json tool
    private WebSocket webSocket;
    private final Gson gson = new Gson();
    private Stage primaryStage;

    private String currentPhone = ""; // phone of logged in user
    private String activeChatContact = ""; // which chat is open

    private final Map<String, List<String>> messageHistory = new HashMap<>(); // cached msgs per contact
    private final List<String> contactList = new ArrayList<>(); // sidebar contacts

    private VBox chatMessageBox = null; // live ref so incoming msgs can append
    private Label loginFeedbackLabel = null; // live ref so server errors can update it


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
        String phone_number; // backend RegisterInfo uses phone_number only, no username
        String password;
        RegisterRequest(String phone_number, String password) {
            this.phone_number = phone_number;
            this.password = password;
        }
    }

    class NewMessageRequest {
        String type = "message"; // backend checks type.equals("message")
        String toPhone;
        String message;
        NewMessageRequest(String toPhone, String message) {
            this.toPhone = toPhone;
            this.message = message;
        }
    }

    // sent when user clicks a chat tab to load history
    class ExistingMessageRequest {
        String type = "existing message";
        String contact;
        ExistingMessageRequest(String contact) {
            this.contact = contact;
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
            sendToServer(gson.toJson(new LoginRequest(phone, pass))); // push to server if connection good
        });

        registerButton.setOnAction(e -> {
            String phone = phoneInput.getText().trim(); // grab text n put into obj
            String pass  = passwordInput.getText().trim();
            if (phone.isEmpty() || pass.isEmpty()) {
                feedbackLabel.setText("Phone number and password are required to register.");
                return;
            }
            sendToServer(gson.toJson(new RegisterRequest(phone, pass))); // auto formats to json, includes "type": "register"
        });

        HBox buttonRow = new HBox(10, loginButton, registerButton);
        buttonRow.setAlignment(Pos.CENTER);

        // simple layout styling
        VBox layout = new VBox(12,
                makeTitleLabel("Messenger App"),
                phoneInput,
                passwordInput,
                buttonRow,
                feedbackLabel
        );
        layout.setPadding(new Insets(30));
        layout.setAlignment(Pos.CENTER);

        // window settings n whatnot
        Scene scene = new Scene(layout, 380, 300);
        primaryStage.setTitle("Messenger - Login");
        primaryStage.setScene(scene);
        primaryStage.show();
    }


    // todo : link settingsBtn to a settings screen
    // todo : link newMessageBtn to a new message dialog
    // todo : add remove/block/report buttons inside buildContactTab
    private void showMainPage() {
        Label appTitle = new Label("Messenger");
        appTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        HBox.setHgrow(appTitle, Priority.ALWAYS);

        Label userLabel = new Label("Logged in as: " + currentPhone);
        userLabel.setTextFill(Color.GRAY);

        Button settingsBtn = new Button("Settings"); // todo : hook up to settings screen

        HBox topBar = new HBox(12, appTitle, userLabel, settingsBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(10, 15, 10, 15));
        topBar.setStyle("-fx-background-color: #e8e8e8; -fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");

        VBox contactSidebar = new VBox(6);
        contactSidebar.setPadding(new Insets(10));

        Button newMessageBtn = new Button("+ New Message"); // todo : hook up to new message dialog
        newMessageBtn.setMaxWidth(Double.MAX_VALUE);
        contactSidebar.getChildren().add(newMessageBtn);

        for (String contact : contactList) {
            contactSidebar.getChildren().add(buildContactTab(contact));
        }

        ScrollPane sidebarScroll = new ScrollPane(contactSidebar);
        sidebarScroll.setFitToWidth(true);
        sidebarScroll.setPrefWidth(220);
        sidebarScroll.setStyle("-fx-background-color: #f0f0f0;");

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

        Scene scene = new Scene(root, 720, 520);
        primaryStage.setTitle("Messenger - Home");
        primaryStage.setScene(scene);
        primaryStage.show(); // needed when transitioning from login screen
    }

    // todo : add remove/block/report buttons to this row
    private HBox buildContactTab(String contact) {
        Label nameLabel = new Label(contact);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);
        nameLabel.setOnMouseClicked(e -> openChatScreen(contact));

        HBox tab = new HBox(nameLabel);
        tab.setAlignment(Pos.CENTER_LEFT);
        tab.setPadding(new Insets(8, 12, 8, 12));
        tab.setStyle("-fx-background-color: white; -fx-background-radius: 5; -fx-cursor: hand;");
        tab.setOnMouseEntered(e -> tab.setStyle("-fx-background-color: #dce8ff; -fx-background-radius: 5; -fx-cursor: hand;"));
        tab.setOnMouseExited(e ->  tab.setStyle("-fx-background-color: white;   -fx-background-radius: 5; -fx-cursor: hand;"));

        return tab;
    }


    private void openChatScreen(String contact) {
        activeChatContact = contact;

        sendToServer(gson.toJson(new ExistingMessageRequest(contact))); // ask backend for history

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
        inputBar.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #ddd; -fx-border-width: 1 0 0 0;");

        Button backBtn = new Button("<- Back");
        backBtn.setOnAction(e -> {
            chatMessageBox = null;
            activeChatContact = "";
            showMainPage();
        });

        Label contactLabel = new Label("Chat with: " + contact);
        contactLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        HBox topBar = new HBox(12, backBtn, contactLabel);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(10, 15, 10, 15));
        topBar.setStyle("-fx-background-color: #e8e8e8; -fx-border-color: #ccc; -fx-border-width: 0 0 1 0;");

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(scrollPane);
        root.setBottom(inputBar);

        Scene scene = new Scene(root, 720, 520);
        primaryStage.setTitle("Chat - " + contact);
        primaryStage.setScene(scene);
    }

    // green bubble right for own msgs, white bubble left for theirs
    private HBox buildMessageBubble(String message) {
        boolean isOwn = message.startsWith("You:");
        Label bubble = new Label(message);
        bubble.setWrapText(true);
        bubble.setMaxWidth(420);
        bubble.setPadding(new Insets(8, 14, 8, 14));
        bubble.setStyle(isOwn
                ? "-fx-background-color: #dcf8c6; -fx-background-radius: 14; -fx-font-size: 13;"
                : "-fx-background-color: #ffffff; -fx-background-radius: 14; -fx-font-size: 13;"
                + "-fx-border-color: #e0e0e0; -fx-border-radius: 14;");

        HBox wrapper = new HBox(bubble);
        wrapper.setAlignment(isOwn ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        wrapper.setPadding(new Insets(2, 10, 2, 10));
        return wrapper;
    }


    private void connectToServer() {
        // setup client to handle async websocket build
        HttpClient client = HttpClient.newHttpClient();
        client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:7070/"), new WebSocket.Listener() {

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        // on successful connection
                        System.out.println("Connected to backend server!");
                        WebSocket.Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        // !!!runs when the backend sends data back to the gui
                        System.out.println("Received from server: " + data);
                        handleServerMessage(data.toString());
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }
                })
                .thenAccept(ws -> this.webSocket = ws) // save socket ref for buttons to use
                .exceptionally(ex -> {
                    // catch connection fails
                    System.out.println("Connection failed: " + ex.getMessage());
                    return null;
                });
    }

    private void sendToServer(String json) {
        if (webSocket != null) {
            webSocket.sendText(json, true);
            System.out.println("GUI sent: " + json);
        } else {
            System.out.println("Cannot send - not connected to server.");
        }
    }


    // todo : add cases for logout/delete if backend sends confirmation
    private void handleServerMessage(String raw) {
        Platform.runLater(() -> { // all ui updates must go through runLater, runs on background thread
            try {
                JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
                String type = json.has("type") ? json.get("type").getAsString() : "";

                switch (type) {

                    case "login_success": { // login confirmed, move to main page
                        if (json.has("phone")) currentPhone = json.get("phone").getAsString();
                        if (json.has("contacts")) {
                            json.getAsJsonArray("contacts").forEach(el -> {
                                String c = el.getAsString();
                                if (!contactList.contains(c)) contactList.add(c);
                            });
                        }
                        showMainPage();
                        break;
                    }

                    case "login_error": { // login rejected, show error on login screen
                        String msg = json.has("message") ? json.get("message").getAsString() : "Login failed. Please try again.";
                        if (loginFeedbackLabel != null) {
                            loginFeedbackLabel.setTextFill(Color.RED);
                            loginFeedbackLabel.setText(msg);
                        }
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

                    case "message_history": { // full history for a contact
                        String contact = json.has("contact") ? json.get("contact").getAsString() : "";
                        if (json.has("messages") && contact.equals(activeChatContact) && chatMessageBox != null) {
                            chatMessageBox.getChildren().clear();
                            List<String> hist = messageHistory.computeIfAbsent(contact, k -> new ArrayList<>());
                            hist.clear();
                            json.getAsJsonArray("messages").forEach(el -> {
                                String line = el.getAsString();
                                hist.add(line);
                                chatMessageBox.getChildren().add(buildMessageBubble(line));
                            });
                        }
                        break;
                    }

                    case "incoming_message": { // real-time msg from another user
                        String from = json.has("from") ? json.get("from").getAsString() : "Unknown";
                        String text = json.has("text") ? json.get("text").getAsString() : "";
                        String line = from + ": " + text;

                        messageHistory.computeIfAbsent(from, k -> new ArrayList<>()).add(line);

                        if (chatMessageBox != null && from.equals(activeChatContact)) {
                            chatMessageBox.getChildren().add(buildMessageBubble(line)); // append live if chat is open
                        }

                        if (!contactList.contains(from)) contactList.add(from);
                        break;
                    }

                    default:
                        System.out.println("Unhandled server message type: " + type);
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
            activeChatContact = "";
            chatMessageBox = null;
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
        // clean up connection if user closes window
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Application closed");
        }
    }

    public static void main(String[] args) {
        launch(args); // trigger javafx to start
    }
}