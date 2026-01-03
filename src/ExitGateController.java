import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import javax.swing.SwingUtilities;

/**
 * ExitGateController.java - Implements Runnable
 * 
 * Purpose: Handles vehicle exit from parking facility.
 * This thread processes exits, frees parking spots, and triggers payment.
 * 
 * WHY implements Runnable: Demonstrates Runnable pattern for exit processing.
 * Allows independent exit handling concurrent with entry and booking operations.
 */
public class ExitGateController implements Runnable {
    
    // WHY BlockingQueue: Thread-safe queue for exit requests
    // Ensures sequential, thread-safe processing of vehicle exits
    private BlockingQueue<BookingRequest> exitQueue;
    
    // Output queue to payment processor
    private BlockingQueue<BookingRequest> paymentQueue;
    
    // Reference to parking monitor to free spots
    private ParkingMonitor parkingMonitor;
    
    // Callback for GUI updates
    private StatusUpdateListener statusListener;
    
    // Thread control flag
    // WHY volatile: Ensures visibility of running flag across threads
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
    public ExitGateController(BlockingQueue<BookingRequest> exitQueue,
                            BlockingQueue<BookingRequest> paymentQueue,
                            ParkingMonitor parkingMonitor,
                            StatusUpdateListener statusListener) {
        this.exitQueue = exitQueue;
        this.paymentQueue = paymentQueue;
        this.parkingMonitor = parkingMonitor;
        this.statusListener = statusListener;
    }
    
    /**
     * Main thread execution method
     * WHY run(): Called by thread.start(), executes in separate thread context.
     * Allows concurrent exit processing without blocking other operations.
     */
    @Override
    public void run() {
        logMessage("Thread started - gate ready for exits");
        
        // Continuous processing loop
        while (running) {
            try {
                // WHY BlockingQueue.take():
                // 1. Waits (blocks) when queue empty - efficient, no busy-waiting
                // 2. Thread-safe consumer pattern - no manual locks needed
                // 3. Wakes immediately when exit request arrives
                // 4. Interruptible for graceful shutdown
                BookingRequest request = exitQueue.take();
                
                logMessage(String.format("Processing exit for %s (Vehicle: %s)",
                                       request.getName(), request.getVehiclePlate()));
                
                // Verify vehicle has entered
                if (request.getStatus() == BookingRequest.Status.ENTERED) {
                    // WHY Thread.sleep(300):
                    // 1. Simulates gate opening/closing time (mechanical operation)
                    // 2. Simulates ticket validation/scanning time
                    // 3. Makes exit visible in GUI (not instantaneous)
                    // 4. Thread remains interruptible during gate operation
                    Thread.sleep(300);
                    
                    // Free the parking spot
                    // WHY synchronized call: parkingMonitor.freeSpot() is synchronized
                    // to prevent race condition where spot is freed and reserved simultaneously
                    parkingMonitor.freeSpot(request.getSpotNumber());
                    
                    // Update status
                    request.setStatus(BookingRequest.Status.EXITED);
                    
                    logMessage(String.format("✓ Gate opened - %s exited from spot %s",
                                           request.getVehiclePlate(), request.getSpotNumber()));
                    
                    // Send to payment processor
                    // WHY BlockingQueue.put(): Thread-safe insertion into payment queue
                    // Blocks if queue is full (backpressure mechanism)
                    try {
                        paymentQueue.put(request);
                        updateGUI(String.format("Vehicle %s exited - Processing payment...",
                                              request.getVehiclePlate()));
                    } catch (InterruptedException e) {
                        logMessage("Interrupted while queuing for payment");
                        break;
                    }
                } else {
                    // Invalid status
                    logMessage(String.format("✗ Exit denied - Invalid status for %s (Status: %s)",
                                           request.getVehiclePlate(), request.getStatus()));
                    updateGUI(String.format("Exit denied: %s - Not properly checked in",
                                          request.getVehiclePlate()));
                }
                
            } catch (InterruptedException e) {
                // WHY catch InterruptedException:
                // Both sleep() and take() throw this when thread is interrupted.
                // This is the graceful shutdown mechanism - log and exit cleanly.
                logMessage("Interrupted, shutting down gracefully");
                break;
            }
        }
        
        logMessage("Thread terminated");
    }
    
    /**
     * Update GUI with status message
     * WHY SwingUtilities.invokeLater():
     * Critical for thread safety in Swing applications.
     * All GUI updates from worker threads must be marshalled to EDT.
     * Prevents:
     * - Concurrent modification of GUI components
     * - Deadlocks between EDT and worker threads
     * - Visual artifacts and crashes
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
     * Format: [HH:mm:ss] ExitGateController: message
     */
    private String formatMessage(String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        return String.format("[%s] ExitGateController: %s", timestamp, message);
    }
    
    /**
     * Signal thread to stop
     */
    public void stopProcessing() {
        running = false;
    }
}

