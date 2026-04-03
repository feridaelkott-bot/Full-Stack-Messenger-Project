# CustomChat — Full Stack Messenger Application
COMP.2800 | University of Windsor | 2026

A secure, real-time desktop messaging application built with Java. 
Users register with a phone number, exchange messages, and customize 
their chat experience with personal background images and color themes.

## Repository
https://github.com/feridaelkott-bot/Full-Stack-Messenger-Project

## Team
Maria Malic       — Database Engineer
Ferida Elkott     — Backend Developer
Ujwal Bastola     — Frontend Engineer
Emma McConnell    — Business Lead

## Technologies Used
JavaFX       — Desktop GUI
Javalin      — WebSocket server
PostgreSQL   — Database (Neon.tech)
BCrypt       — Password hashing
HikariCP     — Connection pooling
Render.com   — Server deployment
Gson         — JSON serialization

## Database
Hosted on Render
Schema: /schema.sql
No manual database setup required — connects automatically

To run locally with your own database:
1. Copy config.example.properties to config.properties
2. Fill in your PostgreSQL credentials
3. Run schema.sql to create the tables

## Project Structure
backend_code/my-javalin-project/   ← Javalin WebSocket server
frontend_code/messenger-gui/       ← JavaFX desktop GUI
schema.sql                         ← PostgreSQL table definitions
config.example.properties          ← Database config template

## How to Run
There is a GUI JAR file, and depending on your operating system, you may need to run it differently: 

For Windows Users: 
Command Line Prompts Method
Make sure that Java is installed on your system: 
→ java -version

Download JavaFX SDK (Software Development Kit)
	→visit https://gluonhq.com/products/javafx
	→Download the SDK for your Windows system
	→ Unzip the file on your system
Once the JavaFX package has been installed and unzipped, give it an environment variable so that its path is easier to use in the next step: 
→set VAR_JAVAFX=”C:\path\to\your\folder\lib”
Run the JAR file: 
→ java –module-path %VAR_JAVAFX% –add-modules javafx.controls,javafx.fxml,javafx.base,javafx.graphics

→ Note: the last line to run the JAR file is loading all of the files that were downloaded from the zip file

JarFix Utility Method

Download and run JarFix for Windows from the following website: https://johann.loefflmann.net/en/software/jarfix/ 
	(Scroll down to find the jarfix.exe file to download)

You will be prompted with configuration questions: just keep clicking Ok/Next to proceed
	
In the end, you may navigate to your .jar file on your system, and double click on it, which will run the application through this JarFix utility


For MacOS Users: 

Command Line Prompts
Make sure that Java is installed on your system: 
→ java -version

Download JavaFX SDK (Software Development Kit)
	→visit https://gluonhq.com/products/javafx
	→Download the SDK for your MacOS system
	→ Unzip the file on your system

Once the JavaFX package has been installed and unzipped, give it an environment variable so that its path is easier to use in the next step: 
→export VAR_JAVAFX=”/path/to/the/file/lib”

Run the JAR file: 
→ java --module-path $VAR_JAVAFX --add-modules javafx.controls,javafx.fxml,javafx.base,javafx.graphics -jar /path/to/CustomChat.jar

→ Note: the last part is the path to your JAR file

For Linux Users: 

Command Line Prompts

Verify that Java is installed, and check which version your computer is running on.
→ java -version
Install the JavaFX package: 
https://gluonhq.com/products/javafx/#ea
→ On this website, choose from the dropdown menu the specific version of Java that is installed on your operating systems from step 1.
Find the path of the downloaded JavaFX folders, copy that path
→find / -name "javafx.controls.jar" 2>/dev/null
Create a path to the JavaFX library folder (stopping at the ‘lib’ part of the path)
→export PATH_JAVAFX=/path/to/javafx/lib
Run the JAR: 
→java --module-path $PATH_JAVAFX --add-modules javafx.controls,javafx.fxml,javafx.base,javafx.graphics -jar /path/to/jar/file


Then the GUI window should pop up on your screen. 

