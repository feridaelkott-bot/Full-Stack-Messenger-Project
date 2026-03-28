package com.mygroup;

//imports
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseManager {

    //The single data pool (shared by all threads): originally null until init() called
    private static HikariDataSource dataSource;

    //Creates the pool fails under critical conditions
    public static void init() {

        String dbUrl = System.getenv("DATABASE_URL");

        //if running on Render, then use Render's environment variable: 
        if (dbUrl != null){
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbUrl.replace("postgres://", "jdbc:postgresql://")
                                     .replace("postgresql://", "jdbc:postgresql://"));
            config.addDataSourceProperty("sslmode", "require");
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30_000);
            config.setIdleTimeout(600_000);
            dataSource = new HikariDataSource(config);
            System.out.println("Connected to database via DATABASE_URL");
            return;
        }
        
        Properties properties = new Properties();

        //Reading of the config.properties
        try(InputStream input = DatabaseManager.class.getClassLoader().getResourceAsStream("config.properties")){
            if (input == null) throw new RuntimeException("config.properties not found.");
            properties.load(input);
        }catch(Exception e){
            throw new RuntimeException("Configurations failed to load: "+ e.getMessage());
        }

        HikariConfig config = new HikariConfig();

        //JDBC: url tells the driver to use postgresql on the localhost:5432 server connecting to messenger_db
        config.setJdbcUrl("jdbc:postgresql://"
                + properties.getProperty("db.host") + ":"
                + properties.getProperty("db.port") + "/"
                + properties.getProperty("db.name"));
        config.setUsername(properties.getProperty("db.user"));
        config.setPassword(properties.getProperty("db.password"));

        //maximum number of connections that can be open on PostgreSQL
        config.setMaximumPoolSize(10);

        //connections that are open even if there are no users
        config.setMinimumIdle(2);

        //30 seconds of wait time before error if all connections are occupied
        config.setConnectionTimeout(30_000);

        //10 minutes of unused connection it will be closed
        config.setIdleTimeout(600_000);

        dataSource = new HikariDataSource(config);
        System.out.println("Connected to database (PostgreSQL: localhost:5432)");
    }

    //Every class calls to borrow a connection from the pool
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) throw new IllegalStateException("database not initialized");
        return dataSource.getConnection();
    }

    //Closes the connections
    public static void close() {
        if (dataSource != null) dataSource.close();
    }
}
