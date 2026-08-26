# Manual Black-Box Test Plan & Verification Matrix

This document provides a step-by-step manual test plan to verify multi-client concurrency, message broadcasting, thread safety, duplicate username prevention, and abrupt disconnect resiliency in the Java TCP Chat Application.

---

## Pre-requisites & Setup

Before running tests, compile the project and generate the fat executable JAR files:

```bash
mvn clean package
```

Verify that `target/chat-server.jar` and `target/chat-client.jar` are created.

---

## Test Execution Matrix

### Test Case 1: Server Initialization & Local Binding
- [ ] **Action**: Open Terminal 1 and start the ChatServer:
  ```bash
  java -jar target/chat-server.jar
  ```
- [ ] **Expected Result**: 
  - Server logs timestamped startup message: `Server starting on port 12345...`
  - Server binds to port 12345 and listens for connections without errors.

---

### Test Case 2: Multi-Client Connection & Handshake
- [ ] **Action**: Open Terminal 2 and start Client 1 ("Alice"):
  ```bash
  java -jar target/chat-client.jar localhost 12345
  ```
  - Enter username `Alice` when prompted.
- [ ] **Action**: Open Terminal 3 and start Client 2 ("Bob"):
  ```bash
  java -jar target/chat-client.jar localhost 12345
  ```
  - Enter username `Bob` when prompted.
- [ ] **Expected Result**:
  - Server stdout displays: `Registered client: 'Alice' [Active Clients: 1]` and `Registered client: 'Bob' [Active Clients: 2]`.
  - Client 1 ("Alice") receives a real-time system announcement: `*** Bob joined the chat ***`.
  - Client 2 ("Bob") receives welcome message: `*** Welcome to the chat, Bob! Type /quit to exit. ***`.

---

### Test Case 3: Duplicate Username & Validation Enforcement
- [ ] **Action**: Open Terminal 4 and start Client 3 attempting to register as `Alice`:
  ```bash
  java -jar target/chat-client.jar localhost 12345
  ```
  - Enter username `Alice`.
- [ ] **Expected Result**:
  - Server rejects the request cleanly.
  - Client 3 receives error: `[Error] Username 'Alice' is already taken. Try another name:`.
  - Enter username `Charlie` -> successfully connects as `Charlie` `[Active Clients: 3]`.

---

### Test Case 4: Real-Time Bidirectional Message Broadcast
- [ ] **Action**: In Terminal 2 ("Alice"), type:
  `Hello everyone!`
- [ ] **Action**: In Terminal 3 ("Bob"), type:
  `Hey Alice, welcome!`
- [ ] **Expected Result**:
  - All connected clients ("Alice", "Bob", "Charlie") receive `[Alice]: Hello everyone!`.
  - All connected clients receive `[Bob]: Hey Alice, welcome!`.
  - Messages arrive in real time without lag or duplicate delivery.

---

### Test Case 5: Graceful Disconnect (`/quit`)
- [ ] **Action**: In Terminal 4 ("Charlie"), type `/quit`.
- [ ] **Expected Result**:
  - Terminal 4 prints `Disconnecting from server...` and exits cleanly.
  - Server stdout displays: `Unregistered client: 'Charlie' [Active Clients: 2]`.
  - Terminal 2 ("Alice") and Terminal 3 ("Bob") receive real-time leave notice: `*** Charlie left the chat ***`.

---

### Test Case 6: Abrupt Disconnect (SIGKILL / Forced Terminal Exit)
- [ ] **Action**: Close Terminal 2 ("Alice") directly (click `X` or press `Ctrl + C` / run `kill -9`).
- [ ] **Expected Result**:
  - `ClientHandler` catches `IOException` on socket read failure.
  - Server does **NOT** crash, throw unhandled stack traces, or leak socket resources.
  - Server stdout displays: `Abrupt disconnect detected for 'Alice' ... Unregistered client: 'Alice' [Active Clients: 1]`.
  - Terminal 3 ("Bob") receives real-time leave notice: `*** Alice left the chat ***`.

---

### Test Case 7: System Recovery & Reconnection
- [ ] **Action**: Open a new terminal and reconnect as `David`:
  ```bash
  java -jar target/chat-client.jar localhost 12345
  ```
- [ ] **Expected Result**:
  - Server registers `David` `[Active Clients: 2]`.
  - Terminal 3 ("Bob") receives `*** David joined the chat ***`.
  - Broadcast functionality remains 100% operational across remaining clients.

---

### Test Case 8: HTTP Health Check Probe Verification (Render Emulation)
- [ ] **Action**: In a new terminal, send an HTTP GET request to the server port using `curl`:
  ```bash
  curl -i http://localhost:12345/
  ```
- [ ] **Expected Result**:
  - `curl` receives `HTTP/1.1 200 OK` with body `OK`.
  - Server stdout logs: `HTTP Health Check probe detected...` and closes the probe connection cleanly without adding a fake user to the client map.
