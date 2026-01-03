# QUICK START GUIDE

## Prerequisites
- Java 8 or higher installed
- Terminal/Command Prompt access

## Running the Application

### Option 1: Using the script (Linux/Mac)
```bash
./run.sh
```

### Option 2: Manual execution
```bash
# Navigate to source directory
cd src

# Compile all Java files
javac *.java

# Run the application
java ParkingGUI
```

### Option 3: Windows
```cmd
cd src
javac *.java
java ParkingGUI
```

## Quick Test Sequence

Once the application opens:

1. **Generate Test Data**
   - Click "Generate Test Data" button
   - Watch as 5 bookings are processed automatically
   - Observe thread activity in status area

2. **Manual Booking**
   - Enter name: "Your Name"
   - Enter plate: "TEST123"
   - Select Zone: A
   - Select Type: Regular
   - Click "Book Spot"
   - Wait for confirmation message

3. **Simulate Entry**
   - Click "Simulate Entry"
   - Select a confirmed booking from list
   - Watch entry processing (300ms gate delay)

4. **Simulate Exit**
   - Click "Simulate Exit"
   - Select an entered vehicle
   - Watch exit and payment processing
   - Record saved to parking_records.txt

5. **View Records**
   - Open parking_records.txt in project root
   - See all completed transactions

6. **Exit Application**
   - Click "Exit Application" or close window
   - Observe graceful shutdown messages
   - All threads terminate cleanly

## What to Observe

### Real-time Thread Activity
- [HH:mm:ss] timestamps on every message
- Different thread names (BookingProcessor, EntryGateController, etc.)
- Processing delays (500ms booking, 400ms payment, etc.)
- Concurrent operations happening simultaneously

### Availability Display
- Zone counters update in real-time
- Decrements when spots reserved
- Increments when vehicles exit

### File Output
- Check parking_records.txt for transaction records
- Format: [Date Time] Student: Name | Vehicle: Plate | Zone: X | Spot: X-## | Fee: RM#.##

## Troubleshooting

### Application doesn't start
- Ensure Java is installed: `java -version`
- Check you're in correct directory
- Try manual compilation steps

### GUI doesn't appear
- Check if another instance is running
- Verify Java AWT/Swing is available
- Try running from terminal to see error messages

### Compilation errors
- Ensure all 8 Java files are in src/ directory
- Check Java version (need 8+)
- Delete .class files and recompile

## Expected Console Output

```
[00:50:01] EntryGateController: Thread started - gate ready for entries
[00:50:01] BookingProcessor: Thread started - ready to process bookings
[00:50:01] RecordSaver: Thread started - ready to save records
[00:50:01] ExitGateController: Thread started - gate ready for exits
[00:50:01] PaymentProcessor: Thread started - ready to process payments
[00:50:01] ParkingMonitor: Thread started - monitoring parking availability
[00:50:01] RecordSaver: Opened file: parking_records.txt
[00:50:01] ParkingMonitor: Zone A - 20/20 spots available
[00:50:01] ParkingMonitor: Zone B - 15/15 spots available
[00:50:01] ParkingMonitor: Zone C - 10/10 spots available
```

## Key Features to Demonstrate

1. **Concurrent Processing**: Multiple bookings processed simultaneously
2. **Thread Safety**: No double-booking even with rapid requests
3. **Real-time Updates**: GUI updates from multiple threads
4. **Graceful Shutdown**: All threads terminate cleanly
5. **File Persistence**: All transactions saved to file

## For Academic Review

The code demonstrates:
- ✅ 6 worker threads using Runnable/Thread
- ✅ BlockingQueue for thread communication
- ✅ Synchronized methods preventing race conditions
- ✅ Thread.sleep() simulating real-world delays
- ✅ SwingUtilities.invokeLater() for GUI safety
- ✅ Comprehensive exception handling
- ✅ Detailed comments explaining WHY each threading concept is used
- ✅ Graceful shutdown with interrupt() and join()

Enjoy exploring the multithreaded parking system! 🚗

