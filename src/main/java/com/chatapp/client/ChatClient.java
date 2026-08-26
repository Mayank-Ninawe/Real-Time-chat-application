package com.chatapp.client;

import com.chatapp.common.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

/**
 * Console-based TCP Chat Client.
 * Connects to the ChatServer, handles username authentication,
 * and uses a dedicated thread to listen for incoming broadcasts while the main thread sends user input.
 */
public class ChatClient {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 12345;

    private final String host;
    private final int port;
    private volatile boolean running = true;

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() {
        System.out.println("==================================================");
        System.out.println("     REAL-TIME MULTI-CLIENT JAVA TCP CHAT CLIENT  ");
        System.out.println("==================================================");
        System.out.println("Connecting to server at " + host + ":" + port + "...");

        try (Socket socket = new Socket(host, port);
             BufferedReader serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter serverWriter = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            System.out.println("Connected to server successfully!");

            // Perform Username Negotiation Handshake
            if (!performHandshake(serverReader, serverWriter, consoleReader)) {
                System.out.println("Authentication failed. Exiting.");
                return;
            }

            // Spawn background thread to continuously listen for server messages
            Thread receiverThread = new Thread(() -> {
                try {
                    String incomingMessage;
                    while (running && (incomingMessage = serverReader.readLine()) != null) {
                        System.out.println(incomingMessage);
                    }
                } catch (IOException e) {
                    if (running) {
                        System.out.println("\n[System] Connection to server lost: " + e.getMessage());
                    }
                } finally {
                    running = false;
                }
            }, "ClientReceiverThread");
            receiverThread.start();

            System.out.println("\n--- Connected to Chat Room. Type messages below (or '/quit' to leave) ---\n");

            // Main Thread Loop: Read user input from stdin and send to server
            String userInput;
            while (running && (userInput = consoleReader.readLine()) != null) {
                String trimmed = userInput.trim();
                serverWriter.println(userInput);

                if (Protocol.COMMAND_QUIT.equalsIgnoreCase(trimmed)) {
                    System.out.println("Disconnecting from server...");
                    running = false;
                    break;
                }
            }

            // Wait briefly for receiver thread to finish
            receiverThread.join(1000);

        } catch (UnknownHostException e) {
            System.err.println("Error: Unknown host '" + host + "'. Check hostname/ip.");
        } catch (IOException e) {
            System.err.println("Error connecting to server " + host + ":" + port + " -> " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            System.out.println("Chat client terminated.");
        }
    }

    /**
     * Handles the handshake protocol to register a unique username with the server.
     */
    private boolean performHandshake(BufferedReader serverReader, PrintWriter serverWriter, BufferedReader consoleReader) throws IOException {
        String promptLine = serverReader.readLine();
        if (promptLine == null || !promptLine.equals("ENTER_USERNAME")) {
            System.err.println("Unexpected response from server: " + promptLine);
            return false;
        }

        while (true) {
            System.out.print("Enter your desired username: ");
            String username = consoleReader.readLine();
            if (username == null) {
                return false;
            }

            serverWriter.println(username);

            String response = serverReader.readLine();
            if (response == null) {
                return false;
            }

            if (response.startsWith("SUCCESS")) {
                System.out.println("[System] " + response.substring(7).trim());
                return true;
            } else if (response.startsWith("ERROR")) {
                System.out.println("[Error] " + response.substring(5).trim());
            } else {
                System.out.println("[Server] " + response);
            }
        }
    }

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        if (args.length > 0) {
            host = args[0];
        }
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port argument '" + args[1] + "'. Using default port " + DEFAULT_PORT);
            }
        }

        ChatClient client = new ChatClient(host, port);
        client.start();
    }
}
