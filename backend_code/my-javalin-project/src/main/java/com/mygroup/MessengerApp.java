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

public class MessengerApp
{

        //track all of the users in a concurrent hash map
        //-->This will be useful for handling all of the connections from a single program.
        //-->ConcurrentHashMap is useful since many clients can connect at once --> concurrent hash maps are thread-safe and allow multiple threads to access them at once*/


        private static final Map<WsContext, String> ctx_and_phones = new ConcurrentHashMap<>();
        private static final Map<WsContext, String> userUsernameMap = new ConcurrentHashMap<>();
        private static final ArrayList<String> allNumbers = new ArrayList<>(); //this array will never have its numbers taken out, unless the user deletes their account


        //each client gets their own copy of each of the classes below:

        //From the main page:
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

        //the new message button is found in teh main page screen
        public class Message{
            String type; //new message
            int toPhone;
            int fromPhone; //should be automatically filled in by the backend
        }

        //these are the three button implementations for each chat that the user has:
        public class Remove{
            String type; //remove
            String phone_number;
        }
        public class Block{
            String type; //block
            String phone_number;
        }
        public class Report{
            String type;//report
            String phone_number;
        }


        //these options are available from the settings tab
        public class Logout{
            String type; //logout
        }//if this is the type gotten, then simply remove ctx connection

        public class DeleteAccount{
            String type; //deleteAcc
            String phone_number;

        }//if this is the type, then delete the information from the database, then remove the ctx connection.



    public static void main( String[] args )
    {
        // Initialize Database and Repository
        DatabaseManager.init();
        PostgresUserRepo userRepo = new PostgresUserRepo();
        //create the websocket, which will be the server that all clients connect to:
        Javalin app = Javalin.create()//create the websocket server, and its


            //define the websocket endpoint --> you only see this if you enter the url into the browser
            .get("/", ctx -> ctx.result("Hello World! This server is working :)")) //Creates a web browser for our server. CTX here is the HTTP request context NOT a client context. NOT required in our program, but useful for testing.


            //websocket connection object
            .ws("/", ws -> {

                //this on connect part is triggered by teh line buildAsync(URI...("ws://localhost:7070/")) from the GUI
                ws.onConnect(ctx -> {


                    ctx_and_phones.put(ctx, ""); //empty phone for now

                    //*At this point, the  user is on the main page of the GUI, where they have the option to login/register*/
                    //*when GUI sends any json, then it can ONLY be read inside onMessage





                    //Future Reference: ctx has several methods to work with the specific user connection
                });

                ws.onMessage(ctx -> {

                    //*The actual JSON strings would be gotten from the line ctx.message() */


                    String rawJson = ctx.message(); //get the json that is sent from the GUI
                    //in JavaFX, there is a certain line: .buildAsync(URI.create("ws://localhost:7070/"), new WebSocket.Listener() {
                    //this line above is part of the method in teh GUI file that is for connecting to the server.
                    //this is the point where GUI JSON input can be sent to the backend server here.
                    //so ctx.message() gets triggered by the line "webSocket.sendText(jsonOutput, true);" from teh GUI


                    //create a JSON parser to first look at the 'type' field
                    JsonObject json = JsonParser.parseString(rawJson).getAsJsonObject(); //parse the JSON string into a JsonObject
                    String type = json.get("type").getAsString(); //get the value of the 'type' field from the JSON object

                    //get the 'type' of the json params
                    Gson gson = new Gson();
                    if (type.equals("login")){
                        LoginInfo new_user = gson.fromJson(rawJson, LoginInfo.class);

                        // DATABASE CHECK
                        AuthResult result = userRepo.login(new_user.phone_number, new_user.password);

                        if (result.success){
                            ctx_and_phones.put(ctx, new_user.phone_number);
                            ctx.send("{\"type\": \"login_success\", \"phone\": \"" + new_user.phone_number + "\"}");
                        } else {
                            ctx.send("{\"type\": \"login_error\", \"message\": \"" + result.message + "\"}");
                        }
                    }
                    else if(type.equals("message")){
                        Message new_message = gson.fromJson(rawJson, Message.class); //convert the JSON string to a Message object, which has the fields: type, username, text
                    }
                    else if (type.equals("register")){
                        RegisterInfo new_register = gson.fromJson(rawJson, RegisterInfo.class);

                        // DATABASE REGISTRATION (Using phone_number for the username field too)
                        AuthResult result = userRepo.register(new_register.phone_number, new_register.password, new_register.phone_number);

                        if (result.success) {
                            ctx.send("{\"type\": \"register_success\", \"message\": \"" + result.message + "\"}");
                        } else {
                            ctx.send("{\"type\": \"register_error\", \"message\": \"" + result.message + "\"}");
                        }
                    }else if (type.equals("block")){

                        Block blocked = gson.fromJson(rawJson, Block.class);
                        String connection_to_remove = blocked.phone_number;

                        //todo: database removes the connection between the blocked and the user

                    }else if (type.equals("report")){
                        Report reported = gson.fromJson(rawJson, Report.class);
                        //todo: ?????
                    }
                    else if (type.equals("logout")){
                        Logout logging_out = gson.fromJson(rawJson, Logout.class);
                        //get the number that is logging out, remove its ctx connection, and remove its number from the array list of numbers
                        userUsernameMap.remove(ctx); //remove their conection fromt eh map of connections
                        ctx.send("You are being disconnected"); //todo: GUI needs to accept this message
                        ctx.session.close(); //todo: make sure teh GUI can detect this close and close its own window accordingly

                    }else if(type.equals("deleteAcc")){
                        DeleteAccount deleteAcc = gson.fromJson(rawJson, DeleteAccount.class);
                        userUsernameMap.remove(ctx);
                        ctx.send("Deleting account...");
                        ctx.session.close();

                        //todo: database deletes the phone number, and their password, and all fo their messages.
                    }

                });

                ws.onError(ctx -> {
                    System.out.println("An error occurred: "); //print the error message if an error occurs
                });

                ws.onClose(ctx -> {
                    System.out.println(ctx.sessionId() + " disconnected"); //print the session id of the client that just disconnected
                    userUsernameMap.remove(ctx); //remove the client from the map when they disconnect
                });

            })
            .start(7070);

        //define the websocket connection's endpoint:


    }
}
