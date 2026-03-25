package com.mygroup; //the archetype created this package sturcture, based on my group id

//now let's import javalin, and since we have its dependencies inside the pom.xml file, it should work 
import io.javalin.Javalin;
import java.util.Map;
import io.javalin.websocket.WsContext;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson; //import the Gson library for JSON parsing --> possible only when we added the Maven dependency
import java.util.ArrayList;

//these will be for parsing the JSON string for only the type field first
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;


import java.util.List; 
import java.util.HashMap;



public class MessengerApp
{

        //track all of the users in a concurrent hash map
        //-->This will be useful for handling all of the connections from a single program.
        //-->ConcurrentHashMap is useful since many clients can connect at once --> concurrent hash maps are thread-safe and allow multiple threads to access them at once*/


        private static final Map<WsContext, String> ctx_and_phones = new ConcurrentHashMap<>();
        private static final Map<WsContext, String> userUsernameMap = new ConcurrentHashMap<>();
        private static final ArrayList<String> allNumbers = new ArrayList<>(); //this array will never have its numbers taken out, unless the user deletes their account


        //clases that have database functionality: 
        public class LoginInfo{

            String type; //login
            String phone_number;
            String password;

        }
        public class RegisterInfo{
            String type; //register
            String phone_number;
            String password;
        }

        public class retrieveMessages{
            String type; //retrieve messages
            String userPhone1; 
            String userPhone2; 

        }

        public class Message{
            String type; //new message
            String toPhone;
            String fromPhone;
            String textMessage; 
        }

        public class Logout{
            String type; //logout
        }

        

        //classes without database funcitonality
        public class Block{
            String type; //block
            String phone_number;
        }
        public class Report{
            String type;//report
            String phone_number;
        }



        //classes for formatting JSON data returned to the GUI: 
        public class login_register_gui_json{
            String type; 
            String phone; 
            String message; 

        }


    public static void main( String[] args )
    {
        // Initialize Database and Repository
        DatabaseManager.init();
        PostgresUserRepo userRepo = new PostgresUserRepo();

        //Initialize the message repository: 
        PostgresMessageRepository messageRepo = new PostgresMessageRepository(); 
        
        //create the websocket, which will be the server that all clients connect to:
        Javalin app = Javalin.create()//create the websocket server, and its


            //define the websocket endpoint --> you only see this if you enter the url into the browser
            .get("/", ctx -> ctx.result("Hello World! This server is working :)")) //Creates a web browser for our server. CTX here is the HTTP request context NOT a client context. NOT required in our program, but useful for testing.


            //websocket connection object
            .ws("/", ws -> {

                //this on connect part is triggered by teh line buildAsync(URI...("ws://localhost:7070/")) from the GUI
                ws.onConnect(ctx -> {


                    ctx_and_phones.put(ctx, ""); //empty phone for now

                });

                ws.onMessage(ctx -> {

                    //*The actual JSON strings would be gotten from the line ctx.message() */


                    //in JavaFX, there is a certain line: .buildAsync(URI.create("ws://localhost:7070/"), new WebSocket.Listener(), whcih connects the client to the server
                    String rawJson = ctx.message(); 

                    
                    //create a JSON parser to first look at the 'type' field
                    JsonObject json = JsonParser.parseString(rawJson).getAsJsonObject(); //parse the JSON string into a JsonObject
                    
                    //get the value of the 'type' field from the JSON object
                    String type = json.get("type").getAsString();

                    //used to format JSON strings, and to deconstruct sent json strings
                    Gson gson = new Gson();




                    if (type.equals("login")){
                        LoginInfo new_user = gson.fromJson(rawJson, LoginInfo.class);


                        // DATABASE CHECK
                        AuthResult result = userRepo.login(new_user.phone_number, new_user.password);



                        //!create a success/failure class instantiation to send as json
                        //login_register_gui_json resultMessage = new login_register_gui_json(); 

                        if (result.success){

                            ctx.send("{\"type\": \"login_success\", \"phone\": \"" + new_user.phone_number + "\"}");
                        } else {
                            ctx.send("{\"type\": \"login_error\", \"message\": \"" + result.message + "\"}");
                        }



                    }else if(type.equals("message")){

                        //convert the JSON string to a Message object, which has the fields: type, username, text
                        Message new_message = gson.fromJson(rawJson, Message.class); 

                        boolean result = messageRepo.saveMessage(new_message.fromPhone, new_message.toPhone, new_message.textMessage); 
                        if (result == true){
                            ctx.send("{\"type\": \"message successfully sent\"}");
                        }else{
                            ctx.send("{\"type\": \"message not successfully sent\"}");
                        }


                    }else if (type.equals("register")){
                        RegisterInfo new_register = gson.fromJson(rawJson, RegisterInfo.class);

                        // DATABASE REGISTRATION (Using phone_number for the username field too)
                        AuthResult result = userRepo.register(new_register.phone_number, new_register.password, new_register.phone_number);

                        if (result.success) {
                            ctx.send("{\"type\": \"register_success\", \"message\": \"" + result.message + "\"}");
                        } else {
                            ctx.send("{\"type\": \"register_error\", \"message\": \"" + result.message + "\"}");
                        }



                    }else if (type.equals("logout")){
                        
                        //get the number that is logging out, remove its ctx connection, and remove its number from the array list of numbers
                        userUsernameMap.remove(ctx);

                        userRepo.logout(ctx_and_phones.get(ctx)); 

                        //!ctx.send("You are being disconnected"); 

                        ctx.session.close(); 

                    } 

                    else if (type.equals("retrieve messages")){

                        retrieveMessages retrieve = gson.fromJson(rawJson, retrieveMessages.class); 

                        //limit of 5 messages from history for simplicity
                        List<String> messages = messageRepo.getConversation(retrieve.userPhone1, retrieve.userPhone2, 5); 

                        //create a hash map for key-value pairs to simplify json handling: 
                        Map<String, String> map = new HashMap<>();

                        //loop through the list of string messages: 
                        for (int i = 0; i < messages.size(); i++){
                            map.put(String.format("m%d", i+1), messages.get(i)); 
                        }

                        //send the map to GUI as json: 
                        String retrieved_messages = gson.toJson(map); 
                        ctx.send(retrieved_messages); 

                        
                    }

                    else if (type.equals("block")){

                        Block blocked = gson.fromJson(rawJson, Block.class);
                        String connection_to_remove = blocked.phone_number;

                        //todo: send message

                    }else if (type.equals("report")){
                        Report reported = gson.fromJson(rawJson, Report.class);
                        //todo: send a message
                    }
                    

                });

                ws.onError(ctx -> {
                    //client GUI would not be connected 
                    System.out.println("An error occurred..."); 
                });



                //this happens when the user closes their GUI window
                ws.onClose(ctx -> {
                    //print the session id of the client that just disconnected
                    System.out.println(ctx.sessionId() + " disconnected"); 

                    //remove the client from the map when they disconnect
                    userUsernameMap.remove(ctx); 
                });

            })
            .start(7070);

        //define the websocket connection's endpoint:


    }
}
