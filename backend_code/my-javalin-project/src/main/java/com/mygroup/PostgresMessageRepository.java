package com.mygroup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PostgresMessageRepository implements MessageRepo{
    @Override
    public boolean saveMessage (String senderPhone, String recipientPhone, String content){
        if(senderPhone == null || recipientPhone == null || content == null) return false;
        if(content.isBlank()) return false;

        String sql = "INSERT INTO messages(sender_phone, recipient_phone, msg_content) VALUES (?, ?, ?)";

        try(Connection connection = DatabaseManager.getConnection();
            PreparedStatement stmt = connection.prepareStatement(sql)){

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

    @Override
    public List<String> getConversation(String user1Phone, String user2Phone, int limit){
        List<String> messages = new ArrayList<>();

        String sql = "SELECT u.username, m.msg_content, m.sent_at " +
                "FROM messages m " +
                "JOIN users u ON u.phone_number = m.sender_phone " +
                "WHERE (m.sender_phone = ? AND m.recipient_phone = ?) " +
                "OR (m.sender_phone = ? AND m.recipient_phone = ?) " +
                "ORDER BY m.sent_at ASC " +
                "LIMIT ?";

        try(Connection connection = DatabaseManager.getConnection();
            PreparedStatement stmt = connection.prepareStatement(sql)){

            stmt.setString(1, user1Phone);
            stmt.setString(2, user2Phone);
            stmt.setString(3, user2Phone);
            stmt.setString(4, user1Phone);
            stmt.setInt(5, limit);

            ResultSet rs = stmt.executeQuery();
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
