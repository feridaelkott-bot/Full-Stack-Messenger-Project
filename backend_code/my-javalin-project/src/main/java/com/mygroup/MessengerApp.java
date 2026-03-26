package com.mygroup; //the archetype created this package sturcture, based on my group id

//now let's import javalin, and since we have its dependencies inside the pom.xml file, it should work 
import io.javalin.Javalin;
import java.util.Map;
import io.javalin.websocket.WsContext;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson; //import the Gson library for JSON parsing --> possible only when we added the Maven dependency

//these will be for parsing the JSON string for only the type field first
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;


import java.util.List; 
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;



public class MessengerApp
{

        //track all of the users in a concurrent hash map
        //-->This will be useful for handling all of the connections from a single program.
        //-->ConcurrentHashMap is useful since many clients can connect at once --> concurrent hash maps are thread-safe and allow multiple threads to access them at once*/


        private static final Map<String, String> session_and_phones = new ConcurrentHashMap<>();
        private static final Map<String, WsContext> session_and_contexts = new ConcurrentHashMap<>();


        //clases that have database functionality: 
        public static class LoginInfo{

            String type; //login
            String phone_number;
            String password;

        }
        public static class RegisterInfo{
            String type; //register
            String phone_number;
            String password;
            String username;
        }

        public static class retrieveMessages{
            String type; //retrieve messages
            String userPhone1; 
            String userPhone2; 

        }

        public static class Message{
            String type; //new message
            String toPhone;
            String fromPhone;
            String textMessage; 
        }

        public static class Logout{
            String type; //logout
        }

        

        //classes without database funcitonality
        public static class Block{
            String type; //block
            String phone_number;
        }
        public static class Report{
            String type;//report
            String phone_number;
        }



        //classes for formatting JSON data returned to the GUI: 
        public class login_register_gui_json{
            String type; 
            String phone; 
            String message; 

        }

        private static class ContactInfo {
            String phone;
            String username;

            ContactInfo(String phone, String username) {
                this.phone = phone;
                this.username = username;
            }
        }

        private static List<ContactInfo> getKnownContacts(String phoneNumber) {
            List<ContactInfo> contacts = new ArrayList<>();

            if (phoneNumber == null || phoneNumber.isBlank()) {
                return contacts;
            }

            String sql = "SELECT x.contact_phone, u.username FROM (" +
                    " SELECT CASE " +
                    "   WHEN sender_phone = ? THEN recipient_phone " +
                    "   ELSE sender_phone " +
                    " END AS contact_phone, MAX(sent_at) AS last_seen " +
                    " FROM messages " +
                    " WHERE sender_phone = ? OR recipient_phone = ? " +
                    " GROUP BY contact_phone" +
                    ") x LEFT JOIN users u ON u.phone_number = x.contact_phone " +
                    "ORDER BY x.last_seen DESC";

            try (Connection connection = DatabaseManager.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {

                stmt.setString(1, phoneNumber);
                stmt.setString(2, phoneNumber);
                stmt.setString(3, phoneNumber);

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String contactPhone = rs.getString("contact_phone");
                    if (contactPhone != null && !contactPhone.isBlank()) {
                        String username = rs.getString("username");
                        contacts.add(new ContactInfo(contactPhone, username));
                    }
                }
            } catch (SQLException e) {
                System.out.println("Failed to load known contacts: " + e.getMessage());
            }

            return contacts;
        }

        private static List<String> getLastConversationMessages(String userPhone1, String userPhone2, int limit) {
            List<String> messages = new ArrayList<>();

            String sql = "SELECT m.sender_phone, u.username, m.msg_content, m.sent_at " +
                    "FROM messages m " +
                    "JOIN users u ON u.phone_number = m.sender_phone " +
                    "WHERE (m.sender_phone = ? AND m.recipient_phone = ?) " +
                    "OR (m.sender_phone = ? AND m.recipient_phone = ?) " +
                    "ORDER BY m.sent_at DESC " +
                    "LIMIT ?";

            try (Connection connection = DatabaseManager.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {

                stmt.setString(1, userPhone1);
                stmt.setString(2, userPhone2);
                stmt.setString(3, userPhone2);
                stmt.setString(4, userPhone1);
                stmt.setInt(5, limit);

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String senderPhone = rs.getString("sender_phone");
                    String senderLabel = userPhone1 != null && userPhone1.equals(senderPhone)
                            ? "You"
                            : rs.getString("username");

                    if (senderLabel == null || senderLabel.isBlank()) {
                        senderLabel = senderPhone;
                    }

                    String formatted = senderLabel + ": " + rs.getString("msg_content");
                    messages.add(formatted);
                }
            } catch (SQLException e) {
                System.err.println("Conversation fetch failed: " + e.getMessage());
            }

            // query gets newest-first, reverse for normal chat display order
            Collections.reverse(messages);
            return messages;
        }


    public static void main( String[] args )
    {
        // Initialize Database and Repository
        DatabaseManager.init();
        PostgresUserRepo userRepo = new PostgresUserRepo();

        //Initialize the message repository: 
        PostgresMessageRepository messageRepo = new PostgresMessageRepository(); 
        
        //create the websocket, which will be the server that all clients connect to:
        Javalin.create()//create the websocket server, and its


            //define the websocket endpoint --> you only see this if you enter the url into the browser
            .get("/", ctx -> ctx.result("Hello World! This server is working :)")) //Creates a web browser for our server. CTX here is the HTTP request context NOT a client context. NOT required in our program, but useful for testing.


            //websocket connection object
            .ws("/", ws -> {

                //this on connect part is triggered by teh line buildAsync(URI...("ws://localhost:7070/")) from the GUI
                ws.onConnect(ctx -> {

                    String sid = ctx.sessionId();
                    session_and_contexts.put(sid, ctx);
                    session_and_phones.put(sid, ""); // empty phone until login

                });

                ws.onMessage(ctx -> {

                    //*The actual JSON strings would be gotten from the line ctx.message() */


                    //in JavaFX, there is a certain line: .buildAsync(URI.create("ws://localhost:7070/"), new WebSocket.Listener(), whcih connects the client to the server
                    String rawJson = ctx.message(); 

                    
                    JsonObject json;
                    String type;
                    try {
                        // Parse defensively so bad payloads don't kill an otherwise healthy socket.
                        json = JsonParser.parseString(rawJson).getAsJsonObject();
                        if (!json.has("type")) {
                            ctx.send("{\"type\": \"bad_request\", \"message\": \"Missing message type\"}");
                            return;
                        }
                        type = json.get("type").getAsString();
                    } catch (Exception parseErr) {
                        ctx.send("{\"type\": \"bad_request\", \"message\": \"Malformed JSON payload\"}");
                        return;
                    }

                    //used to format JSON strings, and to deconstruct sent json strings
                    Gson gson = new Gson();




                    if (type.equals("ping")) {
                        ctx.send("{\"type\": \"pong\"}");

                    } else if (type.equals("login")){
                        LoginInfo new_user = gson.fromJson(rawJson, LoginInfo.class);


                        // DATABASE CHECK
                        AuthResult result = userRepo.login(new_user.phone_number, new_user.password);


                        if (result.success){
                            // attach websocket session to authenticated phone for routing and logout
                            session_and_phones.put(ctx.sessionId(), new_user.phone_number);

                            Map<String, Object> loginPayload = new HashMap<>();
                            loginPayload.put("type", "login_success");
                            loginPayload.put("phone", new_user.phone_number);
                            loginPayload.put("contacts", getKnownContacts(new_user.phone_number));

                            ctx.send(gson.toJson(loginPayload));
                        } else {
                            ctx.send("{\"type\": \"login_error\", \"message\": \"" + result.message + "\"}");
                        }



                    }else if(type.equals("message")){

                        //convert the JSON string to a Message object, which has the fields: type, username, text
                        Message new_message = gson.fromJson(rawJson, Message.class); 

                        // use authenticated phone for sender identity when available
                        String authenticatedPhone = session_and_phones.get(ctx.sessionId());
                        if (authenticatedPhone != null && !authenticatedPhone.isEmpty()) {
                            new_message.fromPhone = authenticatedPhone;
                        }

                        if (new_message.fromPhone == null || new_message.fromPhone.isEmpty()) {
                            ctx.send("{\"type\": \"message_error\", \"message\": \"You must be logged in to send messages.\"}");
                            return;
                        }

                        boolean result = messageRepo.saveMessage(new_message.fromPhone, new_message.toPhone, new_message.textMessage); 
                        if (result == true){
                            ctx.send("{\"type\": \"message successfully sent\"}");

                            // all live sessions for the recipient phone

                            Map<String, String> incomingPayload = new HashMap<>();
                            incomingPayload.put("type", "incoming_message");
                            incomingPayload.put("from", new_message.fromPhone);
                            incomingPayload.put("fromPhone", new_message.fromPhone);
                            incomingPayload.put("fromUsername", getUsernameByPhone(new_message.fromPhone));
                            incomingPayload.put("text", new_message.textMessage);
                            String incomingJson = gson.toJson(incomingPayload);

                            for (Map.Entry<String, String> entry : session_and_phones.entrySet()) {
                                if (new_message.toPhone != null && new_message.toPhone.equals(entry.getValue())) {
                                    WsContext recipientCtx = session_and_contexts.get(entry.getKey());
                                    if (recipientCtx != null) {
                                        try {
                                            recipientCtx.send(incomingJson);
                                        } catch (Exception sendErr) {
                                            // Clean stale socket mappings so future fanout stays accurate.
                                            session_and_contexts.remove(entry.getKey());
                                            session_and_phones.remove(entry.getKey());
                                        }
                                    }
                                }
                            }
                        }else{
                            ctx.send("{\"type\": \"message not successfully sent\"}");
                        }



                    }else if (type.equals("register")){
                        RegisterInfo new_register = gson.fromJson(rawJson, RegisterInfo.class);

                        // DATABASE REGISTRATION
                        AuthResult result = userRepo.register(new_register.username, new_register.password, new_register.phone_number);

                        if (result.success) {
                            ctx.send("{\"type\": \"register_success\", \"message\": \"" + result.message + "\"}");
                        } else {
                            ctx.send("{\"type\": \"register_error\", \"message\": \"" + result.message + "\"}");
                        }



                    }else if (type.equals("logout")){
                        
                        String phone = session_and_phones.get(ctx.sessionId());
                        userRepo.logout(phone); 

                        
                        ctx.send("{\"type\": \"logging_out\", \"message\": \"logging out: " + phone + "\"}");                        

                        session_and_phones.remove(ctx.sessionId()); 
                        session_and_contexts.remove(ctx.sessionId());
                        ctx.session.close(); 


                    } 

                    else if (type.equals("retrieve messages")){

                        retrieveMessages retrieve = gson.fromJson(rawJson, retrieveMessages.class); 

                        // return latest 5 messages in this conversation
                        List<String> messages = getLastConversationMessages(retrieve.userPhone1, retrieve.userPhone2, 5); 

                        Map<String, Object> historyPayload = new HashMap<>();
                        historyPayload.put("type", "message_history");
                        historyPayload.put("contact", retrieve.userPhone2);
                        historyPayload.put("contactUsername", getUsernameByPhone(retrieve.userPhone2));
                        historyPayload.put("messages", messages);

                        //send typed message history to GUI as json:
                        String retrieved_messages = gson.toJson(historyPayload); 
                        ctx.send(retrieved_messages); 

                        
                    }

                    else if (type.equals("block")){

                        Block blocked_info = gson.fromJson(rawJson, Block.class); 

                        ctx.send("{\"type\": \"successfully_blocked\", \"message\": \"successfully blocked: " + blocked_info.phone_number + "\"}");                        

                    }else if (type.equals("report")){
                        Report reported_info = gson.fromJson(rawJson, Report.class);
                        
                        ctx.send("{\"type\": \"successfully_reported\", \"message\": \"successfully reported: " + reported_info.phone_number + "\"}"); 
                    }
                    

                });

                ws.onError(ctx -> {
                    // Keep full context text in logs to diagnose socket drops.
                    System.out.println("WebSocket error context: " + ctx);
                });



                //this happens when the user closes their GUI window
                ws.onClose(ctx -> {

                    //print the session id of the client that just disconnected
                    System.out.println(ctx.sessionId() + " disconnected"); 

                    session_and_phones.remove(ctx.sessionId()); 
                    session_and_contexts.remove(ctx.sessionId());

                });

            })
            .start(7070);

        //define the websocket connection's endpoint:


    }
    // get username by linking it to the number so the displayed thing will be username even tho phone number for login
    private static String getUsernameByPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "";
        }

        String sql = "SELECT username FROM users WHERE phone_number = ?";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, phoneNumber);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String username = rs.getString("username");
                return username != null ? username : "";
            }
        } catch (SQLException e) {
            System.out.println("Failed to load username: " + e.getMessage());
        }

        return "";
    }
}
