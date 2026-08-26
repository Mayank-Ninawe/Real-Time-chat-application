# Multi-Client Real-Time TCP Chat Application in Java

![Java 17+](https://img.shields.io/badge/Java-17%2B-orange.svg)
![Maven](https://img.shields.io/badge/Build-Maven-blue.svg)
![Docker](https://img.shields.io/badge/Deployment-Docker-blue.svg)
![Render](https://img.shields.io/badge/Deployed-Render-brightgreen.svg)

A high-performance, production-ready real-time multi-client chat application engineered in raw **Java TCP Sockets** (`java.net.ServerSocket`, `java.net.Socket`) and multithreading, without external networking frameworks like Netty.

---

## 🌟 Resume Highlights & Bullet Points Demonstrated

- **Architected a multi-client chat server using Java TCP sockets, choosing a dedicated-thread-per-client model to support concurrent connections with real-time message broadcasting.**
- **Designed thread-safe client management using synchronized collections to eliminate data races; manually black-box tested connect/disconnect flows across multiple simultaneous clients.**

---

## 🏗️ Architecture & Concurrency Model

The application operates on a **Dedicated Thread-per-Client Model**. The main server loop listens for incoming TCP socket connections. Upon accepting a socket connection, it spawns a dedicated worker thread (`ClientHandler`) that isolates client network I/O operations from the main server loop.

```
                         +-----------------------------------+
                         |         ChatServer (Main)         |
                         |   ServerSocket.accept() Loop     |
                         +-----------------+-----------------+
                                           |
                                  Spawns Dedicated
                                     Worker Threads
                                           |
         +---------------------------------+---------------------------------+
         |                                 |                                 |
         v                                 v                                 v
+-----------------+               +-----------------+               +-----------------+
|  ClientHandler  |               |  ClientHandler  |               |  ClientHandler  |
|   (Thread 1)    |               |   (Thread 2)    |               |   (Thread 3)    |
+--------+--------+               +--------+--------+               +--------+--------+
         |                                 |                                 |
         +---------------------------------+---------------------------------+
                                           |
                               Reads & Modifies Safely
                                           v
                         +-----------------------------------+
                         | ConcurrentHashMap<String,Handler> |
                         |   (Thread-Safe Client Registry)   |
                         +-----------------------------------+
                                           |
                                Broadcasts Messages To
                                           |
                    +----------------------+----------------------+
                    v                                             v
        +-----------------------+                     +-----------------------+
        |   Client 1 (Alice)    |                     |    Client 2 (Bob)     |
        |   ChatClient Instance |                     |  ChatClient Instance  |
        +-----------------------+                     +-----------------------+
```

### 🔒 Thread-Safety Design & Data Race Elimination

To eliminate data races and prevent `ConcurrentModificationException` during concurrent client join/leave operations and message broadcasts, the server uses `java.util.concurrent.ConcurrentHashMap<String, ClientHandler>`.

#### Why `ConcurrentHashMap` over `Collections.synchronizedMap` or Plain Collections?
1. **Lock-Striping vs Global Monolithic Locks**: Standard collections wrapped in `Collections.synchronizedMap()` use a single global mutex lock for all read, write, and iteration operations. This causes severe thread contention when multiple client handler threads simultaneously broadcast messages and connect/disconnect. `ConcurrentHashMap` utilizes fine-grained lock-striping and CAS (Compare-And-Swap) operations, enabling concurrent non-blocking reads and isolated bucket writes.
2. **Eliminating Race Conditions during Iteration**: The primary race condition occurs when **Thread A** (executing `broadcast()`) iterates over all registered clients while **Thread B** (a new connection) calls `clients.put()` or **Thread C** (an abrupt disconnect) calls `clients.remove()`. Plain maps throw a `ConcurrentModificationException` if modified during iteration. `ConcurrentHashMap` provides weakly-consistent iterators that allow safe concurrent additions and removals without throwing exceptions or requiring global lock acquisition.
3. **Atomic Registration**: Uses `clients.putIfAbsent(username, handler)` to guarantee atomic username registration and prevent duplicate username race conditions.

---

## 📜 Communication Protocol

The application implements a lightweight, text-based newline-delimited protocol:

| Command / Message | Source | Description |
| :--- | :--- | :--- |
| `ENTER_USERNAME` | Server | Handshake prompt sent to client upon connection. |
| `SUCCESS Welcome <user>` | Server | Acknowledges successful username registration. |
| `ERROR <reason>` | Server | Indicates registration failure (empty name, duplicate name). |
| `/quit` | Client | Signals graceful exit and client disconnection. |
| `[username]: message` | Broadcast | User chat message delivered to all connected clients. |
| `*** <message> ***` | System Broadcast | System notification (user join, user leave, server notice). |

---

## 📁 Project Structure

```text
chat-app/
├── src/main/java/com/chatapp/
│   ├── client/
│   │   └── ChatClient.java         # Console TCP client with background receiver thread
│   ├── common/
│   │   └── Protocol.java           # Shared protocol constants & message formatting
│   └── server/
│       ├── ChatServer.java         # Socket listener & thread-safe client registry
│       └── ClientHandler.java      # Dedicated thread handler per client connection
├── pom.xml                         # Maven build with fat JAR packaging
├── Dockerfile                      # Multi-stage Docker containerization
├── render.yaml                     # Render Blueprint deployment config
├── README.md                       # Comprehensive project documentation
├── TEST_PLAN.md                    # Step-by-step manual black-box test plan
└── .gitignore                      # Git artifact exclusion file
```

---

## 💻 Local Build & Execution

### Prerequisites
- JDK 17+
- Apache Maven 3.8+

### Step 1: Package Runnable Fat JARs
Run Maven package to generate executable JAR files for both Server and Client:
```bash
mvn clean package
```
This produces `chat-server.jar` and `chat-client.jar` in the `target/` directory.

### Step 2: Start the Chat Server
Launch the server in terminal 1:
```bash
java -jar target/chat-server.jar
```
*By default, the server binds to local port `12345` (or reads `$PORT` if set).*

### Step 3: Launch Chat Clients
Open additional terminal windows and launch clients:

**Client 1 (Alice):**
```bash
java -jar target/chat-client.jar localhost 12345
```

**Client 2 (Bob):**
```bash
java -jar target/chat-client.jar localhost 12345
```

---

## ☁️ Deployment on Render

The server is fully containerized and configured for deployment on [Render](https://render.com).

### Render Dynamic Port & HTTP Health Probe Handling
- **Dynamic Port**: Render dynamically assigns a port via the `PORT` environment variable. `ChatServer` reads `System.getenv("PORT")` and binds accordingly.
- **HTTP Health Probe Compatibility**: Render Web Services issue HTTP health checks (`GET / HTTP/1.1`) to verify service health. `ClientHandler` detects HTTP GET request headers, responds with `HTTP/1.1 200 OK`, and closes the socket probe cleanly without adding probe connections to the active chat registry.

### Steps to Push to GitHub & Deploy on Render

1. **Initialize Git Repository**:
   ```bash
   git init
   git add .
   git commit -m "Initial commit: Multi-client Java TCP Chat Application"
   ```

2. **Push to GitHub**:
   ```bash
   git remote add origin https://github.com/<your-username>/realtime-chat-application.git
   git branch -M main
   git push -u origin main
   ```

3. **Deploy on Render**:
   - Log into [Render Dashboard](https://dashboard.render.com/).
   - Click **New +** -> **Blueprint**.
   - Connect your GitHub repository `realtime-chat-application`.
   - Render automatically detects `render.yaml` and builds the service using `Dockerfile`.
   - Once deployed, note your Render external URL (e.g., `realtime-chat-server.onrender.com`).

4. **Connecting Clients to Render Service**:
   ```bash
   java -jar target/chat-client.jar realtime-chat-server.onrender.com 10000
   ```

---

## 🧪 Testing & Verification

For a complete manual testing protocol (including concurrent broadcast, graceful `/quit`, abrupt process kill resiliency, and duplicate username checks), see [TEST_PLAN.md](file:///d:/Real-Time-chat-application/TEST_PLAN.md).
