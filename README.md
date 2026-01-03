# Campus Parking Spot Booking System

A multithreaded Java application for managing campus parking spot reservations with real-time processing.

## System Overview

This application demonstrates advanced Java multithreading concepts including:
- 6 concurrent worker threads communicating via BlockingQueues
- Thread-safe synchronized methods for resource allocation
- Proper exception handling and graceful shutdown
- Swing GUI with thread-safe updates using SwingUtilities.invokeLater()

## Architecture

### Thread Components

1. **ParkingMonitor** (extends Thread)
   - Monitors parking availability across 3 zones (A, B, C)
   - Provides synchronized spot allocation/deallocation
   - Updates GUI every 1 second

2. **BookingProcessor** (implements Runnable)
   - Processes booking requests from GUI
   - Validates availability and assigns spots
   - 500ms processing delay simulation

3. **EntryGateController** (implements Runnable)
   - Handles vehicle entry at gates
   - Verifies booking status
   - 300ms gate opening simulation

4. **ExitGateController** (implements Runnable)
   - Processes vehicle exits
   - Frees parking spots
   - 300ms gate operation simulation

5. **PaymentProcessor** (implements Runnable)
   - Calculates parking fees based on:
     - Spot type: Regular (RM3/day), VIP (RM8/day), Disabled (RM2/day)
     - Peak hours (8-10 AM, 5-7 PM): +50% surcharge
   - 400ms payment gateway simulation

6. **RecordSaver** (implements Runnable)
   - Saves all transactions to `parking_records.txt`
   - Asynchronous file I/O
   - 100ms file write simulation

### Communication Flow

```
GUI → bookingQueue → BookingProcessor → paymentQueue → PaymentProcessor
                                                              ↓
GUI → entryQueue → EntryGateController                recordQueue
                                                              ↓
GUI → exitQueue → ExitGateController → paymentQueue → RecordSaver → File
                           ↓
                    ParkingMonitor (spot management)
```

## Compilation and Execution

### Compile:
```bash
cd src
javac *.java
```

### Run:
```bash
java ParkingGUI
```

## Using the Application

### 1. Making a Booking
- Enter your name and vehicle plate number
- Select zone (A, B, or C)
- Choose spot type (Regular, VIP, or Disabled)
- Set date and time using spinners
- Click "Book Spot"
- Wait for confirmation with assigned spot number

### 2. Simulating Entry
- Click "Simulate Entry"
- Select a confirmed booking from the list
- Gate will open after verification (300ms)

### 3. Simulating Exit
- Click "Simulate Exit"
- Select an entered vehicle from the list
- Payment will be processed automatically
- Record saved to file

### 4. Quick Testing
- Click "Generate Test Data" to create 5 sample bookings instantly
- Useful for testing concurrent operations

### 5. Graceful Exit
- Click "Exit Application" or close window
- All threads will shutdown gracefully
- Ensures no data loss

## Key Threading Concepts Demonstrated

### 1. Thread.start()
```java
bookingProcessorThread.start();
```
Creates new thread and begins execution. Each .start() call allocates a new thread stack and registers with JVM scheduler.

### 2. Thread.sleep(ms)
```java
Thread.sleep(500);  // Simulate processing delay
```
Pauses thread execution to simulate real-world delays (network latency, hardware operations). Makes thread interruptible for graceful shutdown.

### 3. BlockingQueue.take()
```java
BookingRequest request = bookingQueue.take();
```
Thread-safe consumer pattern. Blocks (waits) when queue empty, preventing CPU waste. Throws InterruptedException for shutdown.

### 4. synchronized Methods
```java
public synchronized String reserveSpot(String zone)
```
Prevents race conditions where multiple threads access shared resources (parking spots). Ensures atomicity of critical sections.

### 5. SwingUtilities.invokeLater()
```java
SwingUtilities.invokeLater(() -> statusArea.append(message));
```
Critical for Swing thread safety. Schedules GUI updates on Event Dispatch Thread (EDT). Worker threads cannot directly modify GUI components.

### 6. thread.interrupt()
```java
bookingProcessorThread.interrupt();
```
Wakes threads blocked on sleep() or queue.take(). Signals graceful shutdown by throwing InterruptedException.

### 7. thread.join(timeout)
```java
bookingProcessorThread.join(2000);
```
Waits up to 2 seconds for thread to complete. Prevents data loss by allowing threads to finish current operations before JVM exits.

## Parking Zones

- **Zone A**: 20 spots
- **Zone B**: 15 spots  
- **Zone C**: 10 spots

## Pricing

- **Regular**: RM3.00/day
- **VIP**: RM8.00/day
- **Disabled**: RM2.00/day
- **Peak Hours** (8-10 AM, 5-7 PM): +50% surcharge

## Output File

All transactions are saved to `parking_records.txt` in the format:

```
[2026-01-04 12:30] Student: John Doe | Vehicle: ABC1234 | Zone: A | Spot: A-15 | Fee: RM5.00
```

## Thread Safety Features

1. **BlockingQueues**: All inter-thread communication uses thread-safe queues
2. **Synchronized Methods**: Critical sections protected from race conditions
3. **Volatile Flags**: Thread control flags marked volatile for visibility
4. **EDT for GUI**: All GUI updates marshalled to Event Dispatch Thread
5. **Exception Handling**: All threads catch InterruptedException for graceful shutdown

## Graceful Shutdown Process

1. **Signal**: Set running=false for all threads
2. **Interrupt**: Wake blocked threads via interrupt()
3. **Join**: Wait up to 2 seconds per thread with join(2000)
4. **Exit**: Close resources and terminate JVM

This ensures:
- Current operations complete
- Files are properly closed
- No data loss
- Clean resource cleanup

## Files

```
src/
├── BookingRequest.java       # Data model (POJO)
├── ParkingMonitor.java        # Zone monitoring (extends Thread)
├── BookingProcessor.java      # Booking validation (Runnable)
├── EntryGateController.java   # Entry processing (Runnable)
├── ExitGateController.java    # Exit processing (Runnable)
├── PaymentProcessor.java      # Payment calculation (Runnable)
├── RecordSaver.java           # File I/O (Runnable)
└── ParkingGUI.java           # Main GUI application (JFrame)

parking_records.txt            # Generated transaction log
```

## Requirements

- Java 8 or higher
- Java Swing (included in JDK)

## Educational Value

This project demonstrates:
- Producer-consumer pattern with BlockingQueues
- Thread lifecycle management (start, interrupt, join)
- Synchronization mechanisms for shared resources
- GUI threading best practices with Swing
- Graceful shutdown patterns
- Exception handling in concurrent code
- Simulated real-world delays (I/O, network, hardware)

Perfect for learning concurrent programming in Java!

