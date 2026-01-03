import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ParkingMonitor.java - Extends Thread
 * 
 * Purpose: Continuously monitors parking spot availability across all zones.
 * This thread maintains the central state of parking availability and provides
 * synchronized access to prevent race conditions during spot allocation.
 * 
 * WHY extends Thread: Demonstrates thread creation by extending Thread class
 * (as opposed to implementing Runnable). This approach is used when the class
 * represents a specialized thread with dedicated monitoring responsibilities.
 */
public class ParkingMonitor extends Thread {
    
    // Parking capacity for each zone
    private static final int ZONE_A_CAPACITY = 20;
    private static final int ZONE_B_CAPACITY = 15;
    private static final int ZONE_C_CAPACITY = 10;
    
    // Thread control flag - volatile ensures visibility across threads
    // WHY volatile: Changes made by one thread are immediately visible to other threads
    private volatile boolean running = true;
    
    // Available spots counter for each zone
    // WHY synchronized collections: Prevent concurrent modification issues
    private Map<String, AtomicInteger> availableSpots;
    
    // Track occupied spots to prevent double-booking
    private Map<String, Set<Integer>> occupiedSpots;
    
    // Callback interface for GUI updates
    private ParkingUpdateListener updateListener;
    
    /**
     * Interface for notifying GUI of parking status changes
     */
    public interface ParkingUpdateListener {
        void onParkingUpdate(String zone, int available);
    }
    
    /**
     * Constructor - initializes parking zones
     */
    public ParkingMonitor(ParkingUpdateListener listener) {
        this.updateListener = listener;
        
        // Initialize available spots for each zone
        availableSpots = new HashMap<>();
        availableSpots.put("A", new AtomicInteger(ZONE_A_CAPACITY));
        availableSpots.put("B", new AtomicInteger(ZONE_B_CAPACITY));
        availableSpots.put("C", new AtomicInteger(ZONE_C_CAPACITY));
        
        // Initialize occupied spots tracking
        occupiedSpots = new HashMap<>();
        occupiedSpots.put("A", new HashSet<>());
        occupiedSpots.put("B", new HashSet<>());
        occupiedSpots.put("C", new HashSet<>());
        
        // Set thread name for identification in logs
        setName("ParkingMonitor");
    }
    
    /**
     * Main thread execution method
     * WHY run(): This method is called when thread.start() is invoked.
     * It runs in a separate thread context, allowing concurrent monitoring.
     */
    @Override
    public void run() {
        logMessage("Thread started - monitoring parking availability");
        
        // Continuous monitoring loop
        // WHY while loop: Keeps thread alive and actively monitoring until shutdown
        while (running) {
            try {
                // Check and report availability for all zones
                for (String zone : availableSpots.keySet()) {
                    int available = availableSpots.get(zone).get();
                    int capacity = getZoneCapacity(zone);
                    
                    // Update GUI with current availability
                    if (updateListener != null) {
                        updateListener.onParkingUpdate(zone, available);
                    }
                    
                    logMessage(String.format("Zone %s - %d/%d spots available", 
                                           zone, available, capacity));
                }
                
                // WHY Thread.sleep(1000): 
                // 1. Simulates periodic monitoring interval (real systems check every few seconds)
                // 2. Reduces CPU usage - prevents busy-waiting which would consume 100% CPU
                // 3. Allows other threads to execute (yields processor time)
                // 4. Makes thread interruptible - sleep() responds to interrupt signals
                Thread.sleep(1000);
                
            } catch (InterruptedException e) {
                // WHY catch InterruptedException:
                // Thread.sleep() throws this when another thread calls interrupt()
                // This is the mechanism for graceful shutdown
                logMessage("Interrupted, shutting down gracefully");
                // Exit the loop to terminate thread
                break;
            }
        }
        
        logMessage("Thread terminated");
    }
    
    /**
     * Check if spots are available in a zone
     * WHY synchronized: Prevents race condition where two threads check availability
     * simultaneously and both see available spots, leading to overbooking
     */
    public synchronized boolean checkAvailability(String zone) {
        int available = availableSpots.get(zone).get();
        return available > 0;
    }
    
    /**
     * Reserve a spot in the specified zone
     * WHY synchronized: Critical section - must be atomic to prevent two bookings
     * getting the same spot number. Synchronized ensures only one thread executes
     * this method at a time, preventing race conditions.
     * 
     * @return spot number (e.g., "A-15") or null if no spots available
     */
    public synchronized String reserveSpot(String zone) {
        // Double-check availability inside synchronized block
        if (!checkAvailability(zone)) {
            logMessage(String.format("Zone %s - No spots available", zone));
            return null;
        }
        
        // Find next available spot number
        int capacity = getZoneCapacity(zone);
        Set<Integer> occupied = occupiedSpots.get(zone);
        
        for (int i = 1; i <= capacity; i++) {
            if (!occupied.contains(i)) {
                // Mark spot as occupied
                occupied.add(i);
                
                // Decrement available count
                availableSpots.get(zone).decrementAndGet();
                
                String spotNumber = zone + "-" + i;
                logMessage(String.format("Reserved spot %s", spotNumber));
                
                return spotNumber;
            }
        }
        
        return null;
    }
    
    /**
     * Free up a parking spot when vehicle exits
     * WHY synchronized: Must be atomic to prevent race condition where spot is
     * freed and reserved simultaneously, causing inconsistent state
     */
    public synchronized void freeSpot(String spotNumber) {
        if (spotNumber == null || spotNumber.isEmpty()) {
            return;
        }
        
        // Parse spot number (e.g., "A-15" -> zone="A", number=15)
        String[] parts = spotNumber.split("-");
        if (parts.length != 2) {
            logMessage("Invalid spot number format: " + spotNumber);
            return;
        }
        
        String zone = parts[0];
        int number = Integer.parseInt(parts[1]);
        
        // Remove from occupied set
        Set<Integer> occupied = occupiedSpots.get(zone);
        if (occupied.remove(number)) {
            // Increment available count
            availableSpots.get(zone).incrementAndGet();
            logMessage(String.format("Freed spot %s", spotNumber));
        }
    }
    
    /**
     * Get total capacity for a zone
     */
    private int getZoneCapacity(String zone) {
        switch (zone) {
            case "A": return ZONE_A_CAPACITY;
            case "B": return ZONE_B_CAPACITY;
            case "C": return ZONE_C_CAPACITY;
            default: return 0;
        }
    }
    
    /**
     * Get current available spots for a zone
     */
    public int getAvailableSpots(String zone) {
        return availableSpots.get(zone).get();
    }
    
    /**
     * Signal thread to stop
     * WHY separate method: Provides clean way to signal shutdown
     * Sets running flag to false, causing while loop to exit
     */
    public void stopMonitoring() {
        running = false;
        // WHY interrupt(): Wakes thread if it's sleeping, allowing immediate shutdown
        // Without interrupt(), thread would wait until sleep(1000) completes
        this.interrupt();
    }
    
    /**
     * Log message with timestamp
     * Format: [HH:mm:ss] ParkingMonitor: message
     */
    private void logMessage(String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        System.out.println(String.format("[%s] ParkingMonitor: %s", timestamp, message));
    }
}

