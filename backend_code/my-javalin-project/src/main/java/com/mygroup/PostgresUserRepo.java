package com.mygroup;
import org.mindrot.jbcrypt.BCrypt;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
public class PostgresUserRepo implements UserRepository{


    //Registration method following the interface contracT
    @Override
    public AuthResult register (String username, String password, String phoneNumber){
        //Validation
        if(username == null || password == null || phoneNumber == null){
            return AuthResult.failure("Username, password or phone number is null!");
        }
        if(username.isBlank() || phoneNumber.isBlank()){
            return AuthResult.failure("Username or phone number is blank!");
        }
        //Password validation
        if(password.length() < 8){
            return AuthResult.failure("Password must be at least 8 characters.");
        }
        if (!password.matches(".*[0-9].*"))
            return AuthResult.failure("Password must contain at least one number.");
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")){
            return AuthResult.failure("Password must contain at least one special character.");
        }

        //Hashing the password using BCrypt
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12));

        //SQL query to insder the row for the user
        String sql = "INSERT INTO users (phone_number, username, password_hash) VALUES (?, ?, ?)";

        try(Connection connection = DatabaseManager.getConnection();
            PreparedStatement stmt = connection.prepareStatement(sql)){
            stmt.setString(1, phoneNumber.trim());
            stmt.setString(2, username.trim());
            stmt.setString(3, passwordHash);
            stmt.executeUpdate();

            System.out.println("Inserted user: " + username+"("+phoneNumber.trim() + ")");
            return AuthResult.success("Registration successful.", phoneNumber);
        }catch(SQLException e){
            if("23505".equals(e.getSQLState())){
                return AuthResult.failure("Registration failed. User already exists.");
            }
            System.err.println("Registration error: " + e.getMessage());
        }
        return AuthResult.failure("Registration failed.");
    }

    //LOGIN
    @Override
    public AuthResult login(String phoneNumber, String password){
        //Validation
        if(phoneNumber == null || phoneNumber.isBlank()) return AuthResult.failure("Phone number is blank.");
        if(password == null || password.isBlank()) return AuthResult.failure("Password is blank.");

        String sql = "SELECT password_hash FROM users WHERE phone_number = ?";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)){
            stmt.setString(1, phoneNumber.trim());
            ResultSet rs = stmt.executeQuery();

            //Phone number invalid or not found
            if(!rs.next()) return AuthResult.failure("Invalid phone number or password.");
            String storedHash = rs.getString("password_hash");
            if(BCrypt.checkpw(password, storedHash)) return AuthResult.failure("Invalid phone number or password.");

            //Changing the status to online
            setOnlineStatus(phoneNumber, true);

            System.out.println("Login successful: " + phoneNumber);
            return AuthResult.success("Login successful.", phoneNumber);
        }catch(SQLException e){
            System.err.println("Login error: " + e.getMessage());
        }
        return AuthResult.failure("Login failed.");
    }

    //LOGOUT
    @Override
    public void logout(String phoneNumber){
        setOnlineStatus(phoneNumber, false);
        System.out.println("Logout successful: " + phoneNumber);
    }

    private void setOnlineStatus(String phoneNumber, boolean online){
        String sql = "UPDATE users SET online = ? WHERE phone_number = ?";

        try(Connection connection = DatabaseManager.getConnection();
            PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setBoolean(1, online);
            stmt.setString(2, phoneNumber);
            stmt.executeUpdate();
        }catch(SQLException e){
            System.err.println("Update online status error: " + e.getMessage());
        }

    }

}

