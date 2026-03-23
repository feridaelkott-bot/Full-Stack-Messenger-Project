package com.mygroup;

import java.util.List;

public interface MessageRepo {

    //This method is called every time a user sends a message
    //Parameters: who sent, who received, and the text itself
    //Returns true upon successful save and false otherwise
    boolean saveMessage(String senderPhone, String recipientPhone, String content);

    //This method is used as a fetch for the message history between 2 users
    //Limit controles the amount of messages to load
    //Limit<String> holds the formatted message (used by GUI)
    List<String> getConversation(String userPhone1, String userPhone2, int limit);
}
