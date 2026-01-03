import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import javax.swing.SwingUtilities;

/**
 * RecordSaver.java - Implements Runnable
 * 
 * Purpose: Saves parking transaction records to file.
 * This thread handles all file I/O operations asynchronously.
 * 
 * WHY implements Runnable: Demonstrates asynchronous file I/O using threads.
 * Separates file operations from main application flow, preventing UI freezing
 * during disk writes. Essential for responsive applications.
 * 
 * WHY separate thread for I/O:
 * 1. File operations are slow (disk access)
 * 2. Prevents blocking other threads during writes
 * 3. Buffers records in queue if disk is temporarily slow
 * 4. Allows graceful handling of I/O errors
 */
public class RecordSaver implements Runnable {
    
    private static final String FILE_PATH = "parking_records.txt";
    
    // WHY BlockingQueue: Thread-safe queue for records to save
    // PaymentProcessor adds records, this thread writes them
    // Provides natural buffer if disk writes are slower than record generation
    private BlockingQueue<String> recordQueue;
    
    // Callback for GUI updates
    private StatusUpdateListener statusListener;
    
    // Thread control flag
    // WHY volatile: Ensures running flag changes are visible across threads
    private volatile boolean running = true;
    
    // Writer for file output
    // WHY BufferedWriter: Improves I/O performance by batching writes
    private BufferedWriter writer;
    
    /**
     * Interface for status updates to GUI
     */
    public interface StatusUpdateListener {
        void onStatusUpdate(String message);
    }
    
    /**
     * Constructor
     */
    public RecordSaver(BlockingQueue<String> recordQueue,
                      StatusUpdateListener statusListener) {
        this.recordQueue = recordQueue;
        this.statusListener = statusListener;
    }
    
    /**
     * Main thread execution method
     * WHY run(): Called when thread.start() is invoked.
     * Executes in separate thread, allowing concurrent file I/O
     * without blocking the GUI or other operations.
     */
    @Override
    public void run() {
        logMessage("Thread started - ready to save records");
        
        // Open file for appending
        // WHY append mode: Preserves existing records across application restarts
        try {
            writer = new BufferedWriter(new FileWriter(FILE_PATH, true));
            logMessage("Opened file: " + FILE_PATH);
        } catch (IOException e) {
            logMessage("ERROR: Failed to open file - " + e.getMessage());
            updateGUI("Failed to open parking_records.txt");
            return; // Cannot continue without file access
        }
        
        // Continuous processing loop
        while (running) {
            try {
                // WHY BlockingQueue.take():
                // 1. Blocks (waits) when no records to save - no CPU waste
                // 2. Thread-safe consumer pattern - no manual synchronization
                // 3. Automatically wakes when record added to queue
                // 4. Throws InterruptedException for graceful shutdown
                String record = recordQueue.take();
                
                // WHY Thread.sleep(100):
                // 1. Simulates disk I/O latency (especially for network drives)
                // 2. Prevents excessive file system thrashing
                // 3. Gives time for visual feedback in GUI
                // 4. Allows thread interruption during write operation
                Thread.sleep(100);
                
                // Write record to file
                // WHY synchronized: Ensures atomic write operation
                // Prevents corruption if multiple threads somehow access file
                synchronized (this) {
                    writeRecord(record);
                }
                
                logMessage("✓ Saved record to file");
                
            } catch (InterruptedException e) {
                // WHY catch InterruptedException:
                // Both sleep() and take() throw this when interrupted.
                // Signals graceful shutdown - save pending records and exit cleanly.
                logMessage("Interrupted, shutting down gracefully");
                break;
            }
        }
        
        // Cleanup: close file writer
        // WHY finally-style cleanup: Ensures file is properly closed
        // even if thread is interrupted
        closeFile();
        
        logMessage("Thread terminated");
    }
    
    /**
     * Write a single record to file
     * WHY separate method: Centralizes error handling for file operations
     * 
     * @param record formatted record string
     */
    private void writeRecord(String record) {
        try {
            writer.write(record);
            writer.newLine();
            // WHY flush(): Forces buffered data to disk immediately
            // Ensures records aren't lost if application crashes
            // Trade-off: slower but more reliable
            writer.flush();
            
            updateGUI("Record saved to file");
            
        } catch (IOException e) {
            // WHY catch IOException: File operations can fail (disk full, permissions, etc.)
            // Log error but continue operation - don't crash entire application
            logMessage("ERROR: Failed to write record - " + e.getMessage());
            updateGUI("ERROR: Failed to save record to file");
        }
    }
    
    /**
     * Close file writer safely
     * WHY separate method: Ensures proper resource cleanup
     * Called during shutdown to prevent file handle leaks
     */
    private void closeFile() {
        if (writer != null) {
            try {
                writer.close();
                logMessage("File closed successfully");
            } catch (IOException e) {
                // WHY catch IOException: Even close() can fail
                // Log but don't propagate - we're shutting down anyway
                logMessage("ERROR: Failed to close file - " + e.getMessage());
            }
        }
    }
    
    /**
     * Update GUI with status message
     * WHY SwingUtilities.invokeLater():
     * This is CRITICAL for Swing thread safety.
     * Swing components are NOT thread-safe and must only be modified
     * from the Event Dispatch Thread (EDT).
     * 
     * Without invokeLater():
     * - Race conditions: Multiple threads modifying GUI simultaneously
     * - Deadlocks: Worker thread and EDT waiting for each other
     * - Corruption: Partial/inconsistent GUI state
     * - Crashes: ConcurrentModificationException, NullPointerException
     * 
     * invokeLater() schedules the update to run on EDT, ensuring:
     * - Sequential GUI updates (no concurrent access)
     * - Proper event handling order
     * - Thread-safe GUI modifications
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
     * Format: [HH:mm:ss] RecordSaver: message
     */
    private String formatMessage(String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        return String.format("[%s] RecordSaver: %s", timestamp, message);
    }
    
    /**
     * Signal thread to stop
     * WHY separate method: Provides clean shutdown mechanism
     * Sets flag so while loop exits after current iteration
     */
    public void stopProcessing() {
        running = false;
    }
}

