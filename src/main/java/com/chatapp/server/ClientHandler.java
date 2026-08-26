package com.chatapp.server;

import com.chatapp.common.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Handles individual client connections on a dedicated thread.
 * Implements Runnable to process client requests concurrently.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatServer server;
    private BufferedReader reader;
    private PrintWriter writer;
    private String username;
    private boolean registered = false;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            setupStreams();

            // Handshake & Username Negotiation
            if (!handleHandshake()) {
                return;
            }

            // Announce new client join to all connected clients
            String joinNotice = Protocol.formatSystemMessage(username + " joined the chat");
            server.broadcast(joinNotice, null);

            // Send welcome message to the joining client
            sendMessage(Protocol.formatSystemMessage("Welcome to the chat, " + username + "! Type /quit to exit."));

            // Continuous read loop for incoming client messages
            String incomingLine;
            while ((incomingLine = reader.readLine()) != null) {
                String trimmed = incomingLine.trim();

                if (Protocol.COMMAND_QUIT.equalsIgnoreCase(trimmed)) {
                    server.log("Client '" + username + "' sent " + Protocol.COMMAND_QUIT + " command.");
                    break;
                }

                if (!trimmed.isEmpty()) {
                    String formattedMsg = Protocol.formatUserMessage(username, incomingLine);
                    server.broadcast(formattedMsg, this);
                }
            }

        } catch (IOException e) {
            // Abrupt disconnect handling: Client closed connection unexpectedly (e.g. killed process / lost connection)
            server.log("Abrupt disconnect detected for " + (username != null ? "'" + username + "'" : socket.getRemoteSocketAddress()) + ": " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    /**
     * Initializes stream readers and writers for UTF-8 socket communication.
     */
    private void setupStreams() throws IOException {
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    /**
     * Negotiates username registration and handles Render HTTP health check probes.
     *
     * @return true if client successfully registered, false if HTTP probe or failed handshake
     */
    private boolean handleHandshake() throws IOException {
        // Send initial prompt
        writer.println("ENTER_USERNAME");

        String line = reader.readLine();
        if (line == null) {
            return false;
        }

        // Render Web Service HTTP Health Check Probe Detection
        // If Render sends an HTTP health check request (e.g. "GET / HTTP/1.1"), respond with HTTP 200 OK
        if (line.startsWith("GET ") || line.startsWith("HEAD ") || line.startsWith("POST ")) {
            server.log("HTTP Health Check probe detected from " + socket.getRemoteSocketAddress());
            writer.print("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK");
            writer.flush();
            return false;
        }

        // Username negotiation loop for TCP Chat Clients
        while (true) {
            String candidateName = line.trim();

            if (candidateName.isEmpty()) {
                writer.println("ERROR Username cannot be empty. Please enter a valid username:");
            } else if (candidateName.startsWith(Protocol.SYSTEM_PREFIX) || candidateName.contains(" ")) {
                writer.println("ERROR Username cannot contain spaces or system prefixes. Try again:");
            } else if (!server.registerClient(candidateName, this)) {
                writer.println("ERROR Username '" + candidateName + "' is already taken. Try another name:");
            } else {
                this.username = candidateName;
                this.registered = true;
                writer.println("SUCCESS Welcome " + username);
                return true;
            }

            line = reader.readLine();
            if (line == null) {
                return false;
            }
        }
    }

    /**
     * Sends a raw line message to the client socket safely.
     */
    public void sendMessage(String message) {
        if (writer != null) {
            writer.println(message);
        }
    }

    /**
     * Cleanly removes registered client from server collection and closes socket resources.
     */
    private void cleanup() {
        if (registered && username != null) {
            server.removeClient(username);
            String leaveNotice = Protocol.formatSystemMessage(username + " left the chat");
            server.broadcast(leaveNotice, null);
            registered = false;
        }

        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            server.log("Error closing socket resources for " + username + ": " + e.getMessage());
        }
    }

    public String getUsername() {
        return username;
    }
}
