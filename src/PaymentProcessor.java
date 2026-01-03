import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import javax.swing.SwingUtilities;

/**
 * PaymentProcessor.java - Implements Runnable (SINGLE THREAD)
 * 
 * Purpose: Processes parking payments based on duration and spot type.
 * This thread calculates fees and prepares records for file storage.
 * 
 * WHY implements Runnable: Demonstrates simple single-threaded payment processing.
 * No nested ExecutorService - keeps implementation straightforward while still
 * showing concurrent operation with other system threads.
 * 
 * WHY single thread: Payment processing is typically sequential to maintain
 * transaction order and simplify accounting. Real systems often serialize
 * payments to avoid race conditions in financial records.
 */
public class PaymentProcessor implements Runnable {
    
    // Parking rates (per day)
    private static final double REGULAR_RATE = 3.00;
    private static final double VIP_RATE = 8.00;
    private static final double DISABLED_RATE = 2.00;
    
    // Peak hour surcharge (50%)
    private static final double PEAK_HOUR_MULTIPLIER = 1.5;
    
    // WHY BlockingQueue: Thread-safe input queue from exit gate
    // Ensures payment requests are processed in order without data loss
    private BlockingQueue<BookingRequest> paymentQueue;
    
    // WHY BlockingQueue: Thread-safe output queue to record saver
    // Decouples payment processing from file I/O operations
    private BlockingQueue<String> recordQueue;
    
    // Callback for GUI updates
    private StatusUpdateListener statusListener;
    
    // Thread control flag
    // WHY volatile: Ensures visibility of running flag changes across threads
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
    public PaymentProcessor(BlockingQueue<BookingRequest> paymentQueue,
                          BlockingQueue<String> recordQueue,
                          StatusUpdateListener statusListener) {
        this.paymentQueue = paymentQueue;
        this.recordQueue = recordQueue;
        this.statusListener = statusListener;
    }
    
    /**
     * Main thread execution method
     * WHY run(): Executed when thread.start() is called on a Thread wrapping this Runnable.
     * Runs independently, processing payments concurrently with bookings and gate operations.
     */
    @Override
    public void run() {
        logMessage("Thread started - ready to process payments");
        
        // Continuous processing loop
        while (running) {
            try {
                // WHY BlockingQueue.take():
                // 1. Blocks when no payments pending - efficient waiting without CPU waste
                // 2. Thread-safe - multiple threads could theoretically add payments
                // 3. Automatically synchronized - no manual locking required
                // 4. Throws InterruptedException for graceful shutdown
                BookingRequest request = paymentQueue.take();
                
                logMessage(String.format("Processing payment for %s (Vehicle: %s)",
                                       request.getName(), request.getVehiclePlate()));
                
                // WHY Thread.sleep(400):
                // 1. Simulates payment gateway processing time (network latency to bank)
                // 2. Simulates credit card authorization delay
                // 3. Makes payment processing visible in GUI
                // 4. Keeps thread interruptible during payment operation
                Thread.sleep(400);
                
                // Calculate parking fee
                double fee = calculateFee(request);
                request.setFee(fee);
                
                logMessage(String.format("✓ Payment processed: RM%.2f for %s",
                                       fee, request.getVehiclePlate()));
                
                // Create record for file storage
                String record = formatRecord(request);
                
                // Send to record saver
                // WHY BlockingQueue.put(): Thread-safe insertion into record queue
                // If queue is full, blocks until space available (backpressure)
                try {
                    recordQueue.put(record);
                    updateGUI(String.format("Payment of RM%.2f completed for %s",
                                          fee, request.getVehiclePlate()));
                } catch (InterruptedException e) {
                    logMessage("Interrupted while queuing record");
                    break;
                }
                
            } catch (InterruptedException e) {
                // WHY catch InterruptedException:
                // sleep() and take() throw this when thread is interrupted.
                // This is how we achieve graceful shutdown - catch, log, and exit cleanly.
                logMessage("Interrupted, shutting down gracefully");
                break;
            }
        }
        
        logMessage("Thread terminated");
    }
    
    /**
     * Calculate parking fee based on spot type, duration, and time of day
     * 
     * WHY separate method: 
     * - Keeps run() method clean and focused
     * - Makes fee calculation logic testable
     * - Allows easy modification of pricing rules
     */
    private double calculateFee(BookingRequest request) {
        // Base rate by spot type
        double baseRate;
        switch (request.getSpotType()) {
            case "VIP":
                baseRate = VIP_RATE;
                break;
            case "Disabled":
                baseRate = DISABLED_RATE;
                break;
            case "Regular":
            default:
                baseRate = REGULAR_RATE;
                break;
        }
        
        // Calculate duration (simplified: assume minimum 1 day charge)
        // In real system, would calculate hours/days from entry to exit time
        LocalDateTime entryTime = request.getEntryTime();
        LocalDateTime exitTime = LocalDateTime.now();
        
        // Safety check: if no entry time recorded, charge minimum (1 day)
        if (entryTime == null) {
            entryTime = exitTime.minusHours(24); // Assume 1 day parking
        }
        
        long hours = Duration.between(entryTime, exitTime).toHours();
        double days = Math.max(1.0, hours / 24.0); // Minimum 1 day
        
        double fee = baseRate * days;
        
        // Apply peak hour surcharge if applicable
        if (isPeakHour(entryTime) || isPeakHour(exitTime)) {
            fee *= PEAK_HOUR_MULTIPLIER;
        }
        
        return Math.round(fee * 100.0) / 100.0; // Round to 2 decimal places
    }
    
    /**
     * Check if given time falls within peak hours
     * Peak hours: 8:00-10:00 AM and 5:00-7:00 PM
     */
    private boolean isPeakHour(LocalDateTime dateTime) {
        int hour = dateTime.getHour();
        return (hour >= 8 && hour < 10) || (hour >= 17 && hour < 19);
    }
    
    /**
     * Format booking record for file storage
     * Format: [YYYY-MM-DD HH:mm] Student: Name | Vehicle: Plate | Zone: X | Spot: X-## | Fee: RM#.##
     */
    private String formatRecord(BookingRequest request) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return String.format("[%s] Student: %s | Vehicle: %s | Zone: %s | Spot: %s | Fee: RM%.2f",
                           request.getDateTime().format(formatter),
                           request.getName(),
                           request.getVehiclePlate(),
                           request.getZone(),
                           request.getSpotNumber(),
                           request.getFee());
    }
    
    /**
     * Update GUI with status message
     * WHY SwingUtilities.invokeLater():
     * Swing is single-threaded and not thread-safe.
     * This schedules GUI updates to run on the Event Dispatch Thread (EDT).
     * Critical for preventing:
     * - Race conditions in GUI rendering
     * - Thread interference causing visual glitches
     * - Potential deadlocks between worker and EDT threads
     * - Application crashes from concurrent GUI access
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
     * Format: [HH:mm:ss] PaymentProcessor: message
     */
    private String formatMessage(String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        return String.format("[%s] PaymentProcessor: %s", timestamp, message);
    }
    
    /**
     * Signal thread to stop
     */
    public void stopProcessing() {
        running = false;
    }
}

