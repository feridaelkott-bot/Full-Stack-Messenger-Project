package com.mygroup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
//PostgresMessageRepository implements the MessageRepo interface
public class PostgresMessageRepository implements MessageRepo{

    //Saves a new message to the messages table in the database.
    @Override
    public boolean saveMessage (String senderPhone, String recipientPhone, String content){
        //Validation for null fields
        if(senderPhone == null || recipientPhone == null || content == null) return false;
        if(content.isBlank()) return false;


        //Query parametrized  (INSERT)
        String sql = "INSERT INTO messages(sender_phone, recipient_phone, msg_content) VALUES (?, ?, ?)";

        try(Connection connection = DatabaseManager.getConnection();
            PreparedStatement stmt = connection.prepareStatement(sql)){

            //Bind parameters
            stmt.setString(1, senderPhone.trim());
            stmt.setString(2, recipientPhone.trim());
            stmt.setString(3, content);
            stmt.executeUpdate();

            System.out.println("Message saved to database: " + senderPhone.trim() + " to " + recipientPhone.trim() + " " + content);
            return true;
        }catch(SQLException e){
            System.out.println("Message failed to save: " + e.getMessage());
            return false;
        }
    }

    //Retrieves a conversation history between two users (oldest to newest)
    @Override
    public List<String> getConversation(String user1Phone, String user2Phone, int limit){
        List<String> messages = new ArrayList<>();

        //Query message b/w 2 users
        //JOIN to get the username
        //ORDER_BY to get the list of oldest to newest msg
        String sql = "SELECT u.username, m.msg_content, m.sent_at " +
                "FROM messages m " +
                "JOIN users u ON u.phone_number = m.sender_phone " +
                "WHERE (m.sender_phone = ? AND m.recipient_phone = ?) " +
                "OR (m.sender_phone = ? AND m.recipient_phone = ?) " +
                "ORDER BY m.sent_at ASC " +
                "LIMIT ?";

        
        try(Connection connection = DatabaseManager.getConnection();
            PreparedStatement stmt = connection.prepareStatement(sql)){
            //Binding
            stmt.setString(1, user1Phone);
            stmt.setString(2, user2Phone);
            stmt.setString(3, user2Phone);
            stmt.setString(4, user1Phone);
            stmt.setInt(5, limit);

            ResultSet rs = stmt.executeQuery();

            //Format for gui
            while(rs.next()){
                String formatted = rs.getString("username") + ": " + rs.getString("msg_content");
                messages.add(formatted);
            }
        }catch(SQLException e){
            System.err.println("Conversation fetch failed: " + e.getMessage());
        }
        return messages;
    }
}
