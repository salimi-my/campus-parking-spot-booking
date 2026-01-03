import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * BookingRequest.java - Data Model Class
 * 
 * Purpose: Represents a parking spot booking request with all necessary information.
 * This is a Plain Old Java Object (POJO) used to transfer data between threads.
 * 
 * Thread Safety: Immutable after creation (except status updates), making it safe
 * to pass between threads via BlockingQueues without additional synchronization.
 */
public class BookingRequest {
    
    // Booking Status Constants
    public enum Status {
        PENDING,    // Initial state when booking is created
        CONFIRMED,  // Booking validated and spot assigned
        ENTERED,    // Vehicle has entered the parking facility
        EXITED      // Vehicle has exited and payment completed
    }
    
    // Booking Information Fields
    private String name;              // Student/Staff name
    private String vehiclePlate;      // Vehicle license plate number
    private String zone;              // Parking zone (A, B, or C)
    private String spotType;          // Spot type (Regular, VIP, Disabled)
    private LocalDateTime dateTime;   // Booking date and time
    private String spotNumber;        // Assigned spot number (e.g., "A-15")
    private Status status;            // Current booking status
    private LocalDateTime entryTime;  // Actual entry time (for payment calculation)
    private double fee;               // Calculated parking fee
    
    /**
     * Constructor for creating a new booking request
     */
    public BookingRequest(String name, String vehiclePlate, String zone, 
                         String spotType, LocalDateTime dateTime) {
        this.name = name;
        this.vehiclePlate = vehiclePlate;
        this.zone = zone;
        this.spotType = spotType;
        this.dateTime = dateTime;
        this.status = Status.PENDING;
        this.spotNumber = null;
        this.entryTime = null;
        this.fee = 0.0;
    }
    
    // Getters and Setters
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getVehiclePlate() {
        return vehiclePlate;
    }
    
    public void setVehiclePlate(String vehiclePlate) {
        this.vehiclePlate = vehiclePlate;
    }
    
    public String getZone() {
        return zone;
    }
    
    public void setZone(String zone) {
        this.zone = zone;
    }
    
    public String getSpotType() {
        return spotType;
    }
    
    public void setSpotType(String spotType) {
        this.spotType = spotType;
    }
    
    public LocalDateTime getDateTime() {
        return dateTime;
    }
    
    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }
    
    public String getSpotNumber() {
        return spotNumber;
    }
    
    public void setSpotNumber(String spotNumber) {
        this.spotNumber = spotNumber;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public void setStatus(Status status) {
        this.status = status;
    }
    
    public LocalDateTime getEntryTime() {
        return entryTime;
    }
    
    public void setEntryTime(LocalDateTime entryTime) {
        this.entryTime = entryTime;
    }
    
    public double getFee() {
        return fee;
    }
    
    public void setFee(double fee) {
        this.fee = fee;
    }
    
    /**
     * String representation for display and logging
     */
    @Override
    public String toString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return String.format("Booking[%s | %s | Zone %s | %s | Spot: %s | Status: %s]",
                           name, vehiclePlate, zone, spotType, 
                           spotNumber != null ? spotNumber : "Not Assigned",
                           status);
    }
    
    /**
     * Format for file record
     */
    public String toFileRecord() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return String.format("[%s] Student: %s | Vehicle: %s | Zone: %s | Spot: %s | Fee: RM%.2f",
                           dateTime.format(formatter), name, vehiclePlate, zone, 
                           spotNumber != null ? spotNumber : "N/A", fee);
    }
}

