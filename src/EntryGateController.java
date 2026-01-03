import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import javax.swing.SwingUtilities;

/**
 * EntryGateController.java - Implements Runnable
 * 
 * Purpose: Handles vehicle entry at campus parking gates.
 * This thread verifies booking status and processes vehicle entry.
 * 
 * WHY implements Runnable: Demonstrates Runnable interface pattern.
 * Separates the entry processing logic from thread management,
 * allowing flexible thread creation and execution.
 */
public class EntryGateController implements Runnable {
    
    // WHY BlockingQueue: Thread-safe queue for receiving entry requests
    // GUI adds entry requests, this thread processes them sequentially
    // Prevents race conditions when multiple vehicles enter simultaneously
    private BlockingQueue<BookingRequest> entryQueue;
    
    // Reference to parking monitor for status updates
    private ParkingMonitor parkingMonitor;
    
    // Callback for GUI updates
    private StatusUpdateListener statusListener;
    
    // Thread control flag
    // WHY volatile: Ensures thread sees latest value of running flag
    private volatile boolean running = true;
    
    /**
     * Interface for status updates to GUI
     */
    public interface StatusUpdateListener {
        void onStatusUpdate(String message);
    }
    
    /**
     * Constructor
     */
    public EntryGateController(BlockingQueue<BookingRequest> entryQueue,
                              ParkingMonitor parkingMonitor,
                              StatusUpdateListener statusListener) {
        this.entryQueue = entryQueue;
        this.parkingMonitor = parkingMonitor;
        this.statusListener = statusListener;
    }
    
    /**
     * Main thread execution method
     * WHY run(): Executed when thread.start() is called.
     * Runs in separate thread, allowing concurrent entry processing.
     */
    @Override
    public void run() {
        logMessage("Thread started - gate ready for entries");
        
        // Continuous processing loop
        while (running) {
            try {
                // WHY BlockingQueue.take():
                // 1. Blocks (waits) when no entries pending - no CPU waste
                // 2. Thread-safe - no need for manual synchronization
                // 3. Automatically wakes when entry request added
                // 4. Throws InterruptedException for clean shutdown
                BookingRequest request = entryQueue.take();
                
                logMessage(String.format("Processing entry for %s (Vehicle: %s)",
                                       request.getName(), request.getVehiclePlate()));
                
                // Verify booking is confirmed
                if (request.getStatus() == BookingRequest.Status.CONFIRMED) {
                    // WHY Thread.sleep(300):
                    // 1. Simulates physical gate opening time (mechanical delay)
                    // 2. Simulates RFID/barcode scanning time
                    // 3. Makes entry process visible in GUI
                    // 4. Allows thread interruption during gate operation
                    Thread.sleep(300);
                    
                    // Record entry time for payment calculation
                    request.setEntryTime(LocalDateTime.now());
                    request.setStatus(BookingRequest.Status.ENTERED);
                    
                    logMessage(String.format("✓ Gate opened - %s entered spot %s",
                                           request.getVehiclePlate(), request.getSpotNumber()));
                    
                    updateGUI(String.format("Vehicle %s entered Zone %s (Spot: %s)",
                                          request.getVehiclePlate(), request.getZone(),
                                          request.getSpotNumber()));
                } else {
                    // Invalid booking status
                    logMessage(String.format("✗ Entry denied - Invalid booking status for %s",
                                           request.getVehiclePlate()));
                    updateGUI(String.format("Entry denied: %s - No valid booking found",
                                          request.getVehiclePlate()));
                }
                
            } catch (InterruptedException e) {
                // WHY catch InterruptedException:
                // Thrown by sleep() and take() when thread is interrupted
                // Signals graceful shutdown - log and exit cleanly
                logMessage("Interrupted, shutting down gracefully");
                break;
            }
        }
        
        logMessage("Thread terminated");
    }
    
    /**
     * Update GUI with status message
     * WHY SwingUtilities.invokeLater():
     * GUI components (JTextArea, JLabel) are not thread-safe.
     * This method schedules the update to run on the Event Dispatch Thread (EDT).
     * Without this, we risk:
     * - Race conditions in GUI rendering
     * - Deadlocks between threads
     * - UI corruption and crashes
     */
    private void updateGUI(String message) {
        if (statusListener != null) {
            SwingUtilities.invokeLater(() -> {
                statusListener.onStatusUpdate(formatMessage(message));
            });
        }
    }
    
    /**
     * Log message with timestamp
     */
    private void logMessage(String message) {
        String formattedMessage = formatMessage(message);
        System.out.println(formattedMessage);
        updateGUI(message);
    }
    
    /**
     * Format message with timestamp and thread name
     * Format: [HH:mm:ss] EntryGateController: message
     */
    private String formatMessage(String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        return String.format("[%s] EntryGateController: %s", timestamp, message);
    }
    
    /**
     * Signal thread to stop
     */
    public void stopProcessing() {
        running = false;
    }
}

