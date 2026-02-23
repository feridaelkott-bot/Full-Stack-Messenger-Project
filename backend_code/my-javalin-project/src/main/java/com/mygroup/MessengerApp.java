package com.mygroup; //the archetype created this package sturcture, based on my group id

//now let's import javalin, and since we have its dependencies inside the pom.xml file, it should work 
import io.javalin.Javalin; 
import java.util.Map; 
import io.javalin.websocket.WsContext;
import java.util.concurrent.ConcurrentHashMap;



public class MessengerApp 
{

        //track all of the users in a concurrent hash map 
        //-->This will be useful for handling all of the connections from a single program. 
        //-->ConcurrentHashMap is useful since many clients can connect at once --> concurrent hash maps are thread-safe and allow multiple threads to access them at once*/
        private static final Map<WsContext, String> userUsernameMap = new ConcurrentHashMap<>();
        
        //! GUI: when user enters their username, it will be stored in the userUsernameMap, which maps the specific WebSocket connection (WsContext) to the username (String)



    public static void main( String[] args )
    {

        //create the websocket, which will be the server that all clients connect to:
        Javalin app = Javalin.create()//create the websocket server, and its

            //define the websocket endpoint
            .get("/", ctx -> ctx.result("Hello World! This server is working :)")) //Creates a web browser for our server. CTX here is the HTTP request context NOT a client context. NOT required in our program, but useful for testing. 
            
            .ws("/", ws -> { 

                ws.onConnect(ctx -> {
                    //!GUI login/register page gets user connection here:
                    //!--> username, phonenumber, password, ??name?? (name not really needed if we have the username)  


                    String username = "User" + ctx.sessionId(); //create a username based on the session id of the client that just connected
                    userUsernameMap.put(ctx, username); //store the username in the map, with the user's connection ID
                    //!Future Reference: ctx has several methods to work with the specific user connection
                });
                
                ws.onMessage(ctx -> {
                    //!GUI gets user message here 
                    //! --> get the JSON message from the GUI, and DECONSTRUCT IT HERE
                    
                    var user_id = userUsernameMap.get(ctx); //get the username of the client that sent the message
                    //!GUI: you will need to take the user's input as a JSON, send it to the server, and Javalin will automatically interpret the message in this line as a JSON object
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