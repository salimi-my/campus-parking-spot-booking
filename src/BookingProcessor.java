import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import javax.swing.SwingUtilities;

/**
 * BookingProcessor.java - Implements Runnable
 * 
 * Purpose: Processes incoming parking spot booking requests from the GUI.
 * This thread validates bookings, checks availability, and assigns spots.
 * 
 * WHY implements Runnable: Demonstrates thread creation using Runnable interface,
 * which is preferred when the class doesn't need to extend Thread and allows
 * better separation of the task (run method) from the thread mechanism.
 */
public class BookingProcessor implements Runnable {
    
    // WHY BlockingQueue: Thread-safe queue for producer-consumer pattern
    // GUI (producer) adds bookings, this thread (consumer) processes them
    // BlockingQueue.take() automatically waits when queue is empty (no busy-waiting)
    private BlockingQueue<BookingRequest> bookingQueue;
    
    // Output queue to payment processor
    private BlockingQueue<BookingRequest> paymentQueue;
    
    // Reference to parking monitor for spot allocation
    private ParkingMonitor parkingMonitor;
    
    // Callback for GUI status updates
    private StatusUpdateListener statusListener;
    
    // Thread control flag
    // WHY volatile: Ensures visibility of changes across threads without synchronization
    private volatile boolean running = true;
    
    /**
     * Interface for sending status updates to GUI
     */
    public interface StatusUpdateListener {
        void onStatusUpdate(String message);
    }
    
    /**
     * Constructor
     */
    public BookingProcessor(BlockingQueue<BookingRequest> bookingQueue,
                          BlockingQueue<BookingRequest> paymentQueue,
                          ParkingMonitor parkingMonitor,
                          StatusUpdateListener statusListener) {
        this.bookingQueue = bookingQueue;
        this.paymentQueue = paymentQueue;
        this.parkingMonitor = parkingMonitor;
        this.statusListener = statusListener;
    }
    
    /**
     * Main thread execution method
     * WHY run(): Called when Thread.start() is invoked on a Thread wrapping this Runnable.
     * Executes in separate thread context, allowing concurrent booking processing.
     */
    @Override
    public void run() {
        logMessage("Thread started - ready to process bookings");
        
        // Continuous processing loop
        // WHY while(running): Keeps thread alive to process multiple bookings
        // until shutdown signal received
        while (running) {
            try {
                // WHY BlockingQueue.take(): 
                // 1. Thread-safe retrieval without explicit synchronization
                // 2. Automatically blocks (waits) when queue is empty - no CPU waste
                // 3. Wakes up immediately when new item added to queue
                // 4. Throws InterruptedException when interrupted - enables clean shutdown
                BookingRequest request = bookingQueue.take();
                
                logMessage(String.format("Processing booking for %s (Vehicle: %s)",
                                       request.getName(), request.getVehiclePlate()));
                
                // WHY Thread.sleep(500):
                // 1. Simulates real-world processing delay (database queries, validation)
                // 2. Makes the booking process visible in GUI (not instantaneous)
                // 3. Demonstrates thread sleeping mechanism
                // 4. Allows thread to be interrupted during processing
                Thread.sleep(500);
                
                // Validate and process booking
                boolean success = processBooking(request);
                
                if (success) {
                    // Booking confirmed - payment will be processed after exit
                    // No need to send to paymentQueue here - ExitGateController handles that
                    logMessage(String.format("Booking confirmed for %s - Spot: %s",
                                           request.getName(), request.getSpotNumber()));
                } else {
                    logMessage(String.format("Booking failed for %s - No spots available in Zone %s",
                                           request.getName(), request.getZone()));
                }
                
            } catch (InterruptedException e) {
                // WHY catch InterruptedException:
                // 1. Thread.sleep() and BlockingQueue.take() throw this when interrupted
                // 2. This is the clean shutdown mechanism - allows thread to exit gracefully
                // 3. We log the interruption and break from loop to terminate thread
                logMessage("Interrupted, shutting down gracefully");
                break;
            }
        }
        
        logMessage("Thread terminated");
    }
    
    /**
     * Process a single booking request
     * WHY separate method: Keeps run() clean and makes logic testable
     * 
     * @return true if booking successful, false otherwise
     */
    private boolean processBooking(BookingRequest request) {
        // Check availability through ParkingMonitor
        // WHY synchronized method call: ParkingMonitor.checkAvailability() is synchronized
        // to prevent race conditions when multiple threads check simultaneously
        if (!parkingMonitor.checkAvailability(request.getZone())) {
            updateGUI(String.format("Zone %s is full - booking rejected", request.getZone()));
            return false;
        }
        
        // Reserve spot
        // WHY synchronized method call: reserveSpot() must be atomic to prevent
        // two threads from getting the same spot number
        String spotNumber = parkingMonitor.reserveSpot(request.getZone());
        
        if (spotNumber != null) {
            // Successfully reserved
            request.setSpotNumber(spotNumber);
            request.setStatus(BookingRequest.Status.CONFIRMED);
            
            updateGUI(String.format("✓ Booking confirmed: %s assigned to %s in Zone %s",
                                  request.getName(), spotNumber, request.getZone()));
            return true;
        } else {
            // Race condition occurred - spot taken between check and reserve
            updateGUI(String.format("Booking failed: Zone %s became full during processing",
                                  request.getZone()));
            return false;
        }
    }
    
    /**
     * Update GUI with status message
     * WHY SwingUtilities.invokeLater():
     * 1. Swing components are NOT thread-safe
     * 2. All GUI updates must run on Event Dispatch Thread (EDT)
     * 3. Worker threads (like this one) cannot directly modify GUI
     * 4. invokeLater() schedules the update to run on EDT
     * 5. Prevents threading bugs: race conditions, deadlocks, UI corruption
     */
    private void updateGUI(String message) {
        if (statusListener != null) {
            SwingUtilities.invokeLater(() -> {
                statusListener.onStatusUpdate(formatMessage(message));
            });
        }
    }
    
    /**
     * Log message to console with timestamp
     */
    private void logMessage(String message) {
        String formattedMessage = formatMessage(message);
        System.out.println(formattedMessage);
        updateGUI(message);
    }
    
    /**
     * Format message with timestamp and thread name
     * Format: [HH:mm:ss] BookingProcessor: message
     */
    private String formatMessage(String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        return String.format("[%s] BookingProcessor: %s", timestamp, message);
    }
    
    /**
     * Signal thread to stop
     * WHY separate method: Provides clean shutdown mechanism
     * Sets running=false so while loop exits after current iteration
     */
    public void stopProcessing() {
        running = false;
    }
}

