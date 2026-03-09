package com.mygroup;
// import gson n javafx especially being important for gui
import com.google.gson.Gson;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

// for low level socket and connection needs
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;

public class MessengerGui extends Application {

    // keep track of active connection n json tool
    private WebSocket webSocket;
    private final Gson gson = new Gson();

    // data classes for json formatting
    // make sur json matches what backend expects
    class LoginRequest {
        String type = "login"; // hardcoded for now
        String username;
        String password;

        public LoginRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    class RegisterRequest {
        String type = "register";
        String username;
        String password;
        String phoneNumber;

        public RegisterRequest(String username, String password, String phoneNumber) {
            this.username = username;
            this.password = password;
            this.phoneNumber = phoneNumber;
        }
    }

    @Override
    public void start(Stage primaryStage) {
        // connect immediately before login/register
        connectToServer();

        // rough ui setup
        TextField usernameInput = new TextField();
        usernameInput.setPromptText("Username");

        PasswordField passwordInput = new PasswordField();
        passwordInput.setPromptText("Password");

        TextField phoneInput = new TextField();
        phoneInput.setPromptText("Phone Number (for Register)");

        Button loginButton = new Button("Login");
        Button registerButton = new Button("Register");

        // event listeners for what button is pressed n adding 'type' field to each json
        loginButton.setOnAction(e -> {
            LoginRequest loginReq = new LoginRequest(usernameInput.getText(), passwordInput.getText());
            String jsonOutput = gson.toJson(loginReq);

            // push to server if connection good
            if (webSocket != null) {
                webSocket.sendText(jsonOutput, true);
                System.out.println("GUI sent: " + jsonOutput);
            }
        });

        registerButton.setOnAction(e -> {
            // grab text n put into obj
            RegisterRequest regReq = new RegisterRequest(
                    usernameInput.getText(),
                    passwordInput.getText(),
                    phoneInput.getText()
            );
            // auto formats to json, includes "type": "register"
            String jsonOutput = gson.toJson(regReq);

            // blast to server
            if (webSocket != null) {
                webSocket.sendText(jsonOutput, true);
                System.out.println("GUI sent: " + jsonOutput);
            }
        });

        // simple layout styling
        VBox layout = new VBox(10, usernameInput, passwordInput, phoneInput, loginButton, registerButton);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);

        // window settings n whatnot
        Scene scene = new Scene(layout, 350, 250);
        primaryStage.setTitle("Messenger App - Login");
        primaryStage.setScene(scene);
        primaryStage.show();
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
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }
                })
                .thenAccept(ws -> this.webSocket = ws) // save socket ref for buttons to use
                .exceptionally(ex -> {
                    // catch connection fails
                    System.out.println("Connection failed. " + ex.getMessage());
                    return null;
                });
    }

    @Override
    public void stop() {
        // clean up connection if user closes window
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Application closed");
        }
    }

    public static void main(String[] args) {
        // trigger javafx to start
        launch(args);
    }
}