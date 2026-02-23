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

public class MessengerApp 
{

        //track all of the users in a concurrent hash map 
        //-->This will be useful for handling all of the connections from a single program. 
        //-->ConcurrentHashMap is useful since many clients can connect at once --> concurrent hash maps are thread-safe and allow multiple threads to access them at once*/
        
        private static final Map<WsContext, String> userUsernameMap = new ConcurrentHashMap<>();
        
        

        //login fields class (each client gets their own copy of this class)
        public class LoginInfo{

            String type; //login
            String username; 
            String password; 

        }


        public class RegisterInfo{
            String type; //register
            String username; 
            String password; 
            String phoneNumber;
        }

        //message fields class
        public class Message{
            String type; //message
            String id; //message id 
            String date; 
            String time; 
            String sender; 
            String recipient; 
            String text;
        }


    public static void main( String[] args )
    {

        //create the websocket, which will be the server that all clients connect to:
        Javalin app = Javalin.create()//create the websocket server, and its

            
            //define the websocket endpoint
            .get("/", ctx -> ctx.result("Hello World! This server is working :)")) //Creates a web browser for our server. CTX here is the HTTP request context NOT a client context. NOT required in our program, but useful for testing. 
            
            .ws("/", ws -> { 

                ws.onConnect(ctx -> {
                    


                    //just to keep track of current users
                    String username = "User" + ctx.sessionId(); //create a username based on the session id of the client that just connected
                    userUsernameMap.put(ctx, username); //store the username in the map, with the user's connection ID
                    
                    //!GUI: program immediately connects to this server, before the user logs in/ registers
                    //!GUI: event listener for which button is pressed: login or register.
                    //!GUI: Make sure to add a 'type' field to each JSON, for login, register, or message
                    //todo: when GUI sends any json, then it can ONLY be read inside onMessage
                    //todo: --> therefore, program will jump to onMessage
                 

                    
                    
                    
                    //Future Reference: ctx has several methods to work with the specific user connection
                });
                
                ws.onMessage(ctx -> {

                    //*The actual JSON string would be gotten from the line ctx.message() */


                    //1. Read the inputted login/register JSON here: 
                    //-->sample JSON input from user here (delete later)
                    String sampleLogin ="{\n" +
                        "  \"type\": \"login\",\n" +
                        "  \"username\": \"alice123\",\n" +
                        "  \"password\": \"secret123\",\n" +
                        "  \"phoneNumber\": \"519-555-1234\"\n" +
                    "}"; 



                    //create a JSON parser to first look at the 'type' field
                    JsonObject json = JsonParser.parseString(sampleLogin).getAsJsonObject(); //parse the JSON string into a JsonObject
                    String type = json.get("type").getAsString(); //get the value of the 'type' field from the JSON object



                    //2. Deconstruct the JSON to get all three fields: username, password, and phone number using GSON
                    Gson gson = new Gson();
                    if (type.equals("login")){ //if the JSON is a login request
                        
                        LoginInfo new_user = gson.fromJson(sampleLogin, LoginInfo.class); //convert the JSON string to a LoginInfo object, which has the fields: type, username, password, phone number
                        //! DATABASE: check if the user exists in the database here, using the fields from the JSON. --> new_user.username, new_user.password, new_user.phoneNumber
                        //! DATABASE: if the user exists, then 
                        //! --> This is where ERROR CHECKING happens: if the user doesn't exist, we need ot send an error message back to the client, and not log them in.


                    }else if(type.equals("message")){
                        Message new_message = gson.fromJson(sampleLogin, Message.class); //convert the JSON string to a Message object, which has the fields: type, username, text
                        //! DATABASE: store the message in the database here, using the fields from the JSON. --> new_message.username, new_message.text
                    }
                    else if (type.equals("register")){
                        RegisterInfo new_register = gson.fromJson(sampleLogin, RegisterInfo.class); //convert the JSON string to a RegisterInfo object, which has the fields: type, username, password, phone number

                    }
                    



                    //!GUI gets user message here 
                    
                    var user_id = userUsernameMap.get(ctx); //get the username of the client that sent the message
                    var user_message = ctx.message(); //get the message that the client sent
                });
                
                //! what if I wanted to print the error to the webpage? 
                ws.onError(ctx -> {
                    System.out.println("An error occurred: "); //print the error message if an error occurs
                });

                //!what if I wanted to print this disconneciton info on the webpage?
                ws.onClose(ctx -> {
                    System.out.println(ctx.sessionId() + " disconnected"); //print the session id of the client that just disconnected
                    userUsernameMap.remove(ctx); //remove the client from the map when they disconnect
                }); 

            })
            .start(7070); //!

        //define the websocket connection's endpoint: 
        
       
    }
}





/*Step-by-Step Event Flow

    GUI: User types message/username, GUI code does webSocket.send(gson.toJson(new Message("Ferida", "Hi!"))) after connect.

    Network: JSON string travels over open WS connection to server.

    Server onMessage(ctx): Automatically triggered; String rawJson = ctx.message(); gets full JSON like {"username":"Ferida","text":"Hi!"}.

    Parse: Use Message msg = ctx.messageAsClass(Message.class); (Javalin auto-Jackson) or manual ObjectMapper mapper = new ObjectMapper(); Message msg = mapper.readValue(rawJson, Message.class);.

    Process: Get String user = msg.username(); String text = msg.text();, prepend user like "Ferida: Hi!", broadcast userUsernameMap.forEach((c, u) -> c.send(formattedMsg));.

    GUI receives: All clients (including sender) get broadcast in their onMessage handler, append to chat display
    
    
    
    
    
    
    
    
    
    
    
    String raw = ctx.message();
try {
    ObjectMapper mapper = new ObjectMapper();
    Message msg = mapper.readValue(raw, Message.class);
    String formatted = userUsernameMap.get(ctx) + ": " + msg.text;
    userUsernameMap.forEach((c, u) -> c.send(formatted));
} catch (Exception e) {
    ctx.send("Invalid JSON");
}
*/