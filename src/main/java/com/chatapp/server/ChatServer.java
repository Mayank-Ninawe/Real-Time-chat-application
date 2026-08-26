package com.chatapp.server;

import com.chatapp.common.Protocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-Client Real-Time Chat Server using raw Java TCP Sockets and multithreading.
 * Follows a dedicated-thread-per-client concurrency model.
 */
public class ChatServer {

    private static final int DEFAULT_PORT = 12345;
    private static final DateTimeFormatter LOG_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /*
     * THREAD-SAFETY ARCHITECTURE & RACE CONDITION PREVENTION:
     *
     * Why ConcurrentHashMap over synchronizing a plain HashMap/ArrayList manually?
     *
     * 1. Lock-Striping & High Concurrency Performance:
     *    Plain collections (such as HashMap or ArrayList) wrapped in Collections.synchronizedMap() 
     *    or synchronized blocks rely on a single global lock for all operations (reads, writes, iterations).
     *    This creates severe contention when multiple client handler threads simultaneously attempt to
     *    broadcast messages, connect, or disconnect.
     *    ConcurrentHashMap uses fine-grained lock-striping and atomic CAS (Compare-And-Swap) operations,
     *    allowing concurrent reads and writes across different buckets without blocking the entire data structure.
     *
     * 2. Race Condition Prevention during Broadcast Iteration:
     *    The primary race condition in a chat server occurs when Thread A (broadcasting a message)
     *    iterates over all connected client handlers, while Thread B (a new incoming connection) 
     *    executes clients.put(username, handler), or Thread C (an abruptly disconnected client) 
     *    executes clients.remove(username).
     *
     *    If a plain collection (HashMap or ArrayList) is iterated without full exclusive locking, 
     *    structural modifications by another thread cause a ConcurrentModificationException (fail-fast iterator failure)
     *    or internal pointer corruption.
     *    
     *    ConcurrentHashMap provides weakly-consistent iterators that safely reflect map state at the time 
     *    the iterator was created. It permits concurrent modifications (adds/removes) during iteration 
     *    without throwing ConcurrentModificationException and without requiring long-held global locks during message delivery.
     *
     * 3. Atomic Registration:
     *    The method clients.putIfAbsent(username, handler) guarantees thread-safe, atomic registration 
     *    preventing duplicate username race conditions when two clients attempt to sign in with the same username concurrently.
     */
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final AtomicLong totalMessagesProcessed = new AtomicLong(0);
    private final int port;

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() {
        log("Server starting on port " + port + "...");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log("Server listening successfully on port " + port + " (Waiting for client connections)");

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    log("Accepted new incoming TCP connection from " + clientSocket.getRemoteSocketAddress());

                    // Dedicated-Thread-per-Client Model:
                    // Spawns a new dedicated Thread for every accepted connection.
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    Thread clientThread = new Thread(handler, "ClientThread-" + clientSocket.getRemoteSocketAddress());
                    clientThread.start();
                } catch (IOException e) {
                    log("Error accepting client connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            log("Fatal server error on port " + port + ": " + e.getMessage());
        }
    }

    /**
     * Atomically registers a client handler if the username is available.
     *
     * @param username Requested client username
     * @param handler  Client handler instance
     * @return true if successfully registered, false if username is already taken
     */
    public boolean registerClient(String username, ClientHandler handler) {
        ClientHandler existing = clients.putIfAbsent(username, handler);
        if (existing == null) {
            log("Registered client: '" + username + "' [Active Clients: " + clients.size() + "]");
            return true;
        }
        return false;
    }

    /**
     * Safely removes a client handler from the registry upon disconnect.
     *
     * @param username Client username to remove
     */
    public void removeClient(String username) {
        if (username != null) {
            ClientHandler removed = clients.remove(username);
            if (removed != null) {
                log("Unregistered client: '" + username + "' [Active Clients: " + clients.size() + "]");
            }
        }
    }

    /**
     * Broadcasts a message to all connected clients.
     *
     * @param message Fully formatted message to broadcast
     * @param sender  Client handler sending the message (can be null for system broadcasts)
     */
    public void broadcast(String message, ClientHandler sender) {
        long msgCount = totalMessagesProcessed.incrementAndGet();
        log("[Broadcast #" + msgCount + "] " + message);

        // Safe iteration over ConcurrentHashMap values without ConcurrentModificationException
        for (ClientHandler client : clients.values()) {
            // Deliver message to all clients
            client.sendMessage(message);
        }
    }

    /**
     * Logs server events to stdout with formatted timestamps and metadata.
     */
    public synchronized void log(String message) {
        String timestamp = LocalDateTime.now().format(LOG_DATE_FORMATTER);
        System.out.printf("[%s] [ChatServer] %s%n", timestamp, message);
    }

    public int getActiveClientCount() {
        return clients.size();
    }

    public static void main(String[] args) {
        // Read port from PORT environment variable (assigned dynamically by Render), fallback to DEFAULT_PORT
        int port = DEFAULT_PORT;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.trim().isEmpty()) {
            try {
                port = Integer.parseInt(portEnv.trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid PORT environment variable value '" + portEnv + "'. Falling back to default port " + DEFAULT_PORT);
            }
        }

        ChatServer server = new ChatServer(port);
        server.start();
    }
}
