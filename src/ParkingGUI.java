import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * ParkingGUI.java - Main Application Class (extends JFrame)
 * 
 * Purpose: Provides graphical user interface for parking spot booking system.
 * This is the main class that creates and manages all worker threads.
 * 
 * WHY extends JFrame: Creates the main application window with Swing
 * components.
 * Serves as the central coordinator for all threads and queues.
 * 
 * Thread Architecture:
 * - GUI runs on Event Dispatch Thread (EDT) - Swing's main thread
 * - 6 worker threads run concurrently in background
 * - BlockingQueues provide thread-safe communication
 */
public class ParkingGUI extends JFrame {

    // ==================== BlockingQueues for Thread Communication
    // ====================

    // WHY BlockingQueue: Provides thread-safe producer-consumer pattern
    // 1. Thread-safe: No manual synchronization needed
    // 2. Blocking: Consumers wait automatically when queue empty
    // 3. Bounded: Capacity prevents memory overflow
    // 4. Interruption-aware: Supports graceful shutdown

    private BlockingQueue<BookingRequest> bookingQueue; // GUI → BookingProcessor
    private BlockingQueue<BookingRequest> entryQueue; // GUI → EntryGateController
    private BlockingQueue<BookingRequest> exitQueue; // GUI → ExitGateController
    private BlockingQueue<BookingRequest> paymentQueue; // BookingProcessor/ExitGate → PaymentProcessor
    private BlockingQueue<String> recordQueue; // PaymentProcessor → RecordSaver

    // ==================== Worker Threads ====================

    private Thread bookingProcessorThread;
    private Thread entryGateThread;
    private Thread exitGateThread;
    private Thread paymentProcessorThread;
    private Thread recordSaverThread;
    private ParkingMonitor parkingMonitorThread; // Extends Thread directly

    // ==================== Worker Thread Runnables ====================

    private BookingProcessor bookingProcessor;
    private EntryGateController entryGateController;
    private ExitGateController exitGateController;
    private PaymentProcessor paymentProcessor;
    private RecordSaver recordSaver;

    // ==================== Active Bookings Storage ====================

    private List<BookingRequest> activeBookings; // Bookings that can enter
    private List<BookingRequest> enteredVehicles; // Vehicles that can exit

    // ==================== GUI Components ====================

    // Input fields
    private JTextField nameField;
    private JTextField plateField;
    private JComboBox<String> zoneComboBox;
    private JComboBox<String> spotTypeComboBox;
    private JSpinner dateSpinner;
    private JSpinner timeSpinner;

    // Status display
    private JTextArea statusArea;
    private JLabel zoneALabel;
    private JLabel zoneBLabel;
    private JLabel zoneCLabel;
    private JProgressBar zoneAProgress;
    private JProgressBar zoneBProgress;
    private JProgressBar zoneCProgress;
    private JLabel zoneAPercent;
    private JLabel zoneBPercent;
    private JLabel zoneCPercent;

    // Action buttons
    private JButton bookButton;
    private JButton entryButton;
    private JButton exitButton;
    private JButton testDataButton;

    /**
     * Constructor - initializes GUI and starts all threads
     */
    public ParkingGUI() {
        super("Campus Parking Spot Booking System");

        // Initialize data structures
        activeBookings = new ArrayList<>();
        enteredVehicles = new ArrayList<>();

        // Initialize BlockingQueues with capacity 50
        // WHY capacity 50: Prevents unbounded memory growth while allowing burst
        // traffic
        bookingQueue = new LinkedBlockingQueue<>(50);
        entryQueue = new LinkedBlockingQueue<>(50);
        exitQueue = new LinkedBlockingQueue<>(50);
        paymentQueue = new LinkedBlockingQueue<>(50);
        recordQueue = new LinkedBlockingQueue<>(50);

        // Build GUI
        initializeComponents();

        // Start all worker threads
        // WHY start threads in constructor: Begin background processing immediately
        // when application launches, ready to handle requests
        startAllThreads();

        // Setup window properties
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // WHY custom window listener: Ensures graceful thread shutdown
                // before application exits, preventing data loss
                shutdownGracefully();
            }
        });

        setSize(900, 700);
        setLocationRelativeTo(null); // Center on screen
        setVisible(true);
    }

    /**
     * Initialize all GUI components
     */
    private void initializeComponents() {
        setLayout(new BorderLayout(10, 10));

        // Main panel with padding
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top: Input panel
        mainPanel.add(createInputPanel(), BorderLayout.NORTH);

        // Center: Status display
        mainPanel.add(createStatusPanel(), BorderLayout.CENTER);

        // Bottom: Availability display
        mainPanel.add(createAvailabilityPanel(), BorderLayout.SOUTH);

        add(mainPanel);
    }

    /**
     * Create input panel for booking information
     */
    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("New Booking"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Name
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1;
        nameField = new JTextField(20);
        panel.add(nameField, gbc);

        // Row 0: Vehicle Plate
        gbc.gridx = 2;
        panel.add(new JLabel("Vehicle Plate:"), gbc);
        gbc.gridx = 3;
        plateField = new JTextField(15);
        panel.add(plateField, gbc);

        // Row 1: Zone
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Zone:"), gbc);
        gbc.gridx = 1;
        zoneComboBox = new JComboBox<>(new String[] { "A", "B", "C" });
        panel.add(zoneComboBox, gbc);

        // Row 1: Spot Type
        gbc.gridx = 2;
        panel.add(new JLabel("Spot Type:"), gbc);
        gbc.gridx = 3;
        spotTypeComboBox = new JComboBox<>(new String[] { "Regular", "VIP", "Disabled" });
        panel.add(spotTypeComboBox, gbc);

        // Row 2: Date
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("Date:"), gbc);
        gbc.gridx = 1;
        dateSpinner = createDateSpinner();
        panel.add(dateSpinner, gbc);

        // Row 2: Time
        gbc.gridx = 2;
        panel.add(new JLabel("Time:"), gbc);
        gbc.gridx = 3;
        timeSpinner = createTimeSpinner();
        panel.add(timeSpinner, gbc);

        // Row 3: Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));

        bookButton = new JButton("Book Spot");
        bookButton.addActionListener(e -> handleBooking());
        buttonPanel.add(bookButton);

        entryButton = new JButton("Simulate Entry");
        entryButton.addActionListener(e -> handleEntry());
        buttonPanel.add(entryButton);

        exitButton = new JButton("Simulate Exit");
        exitButton.addActionListener(e -> handleExit());
        buttonPanel.add(exitButton);

        testDataButton = new JButton("Generate Test Data");
        testDataButton.addActionListener(e -> generateTestData());
        buttonPanel.add(testDataButton);

        JButton exitAppButton = new JButton("Exit Application");
        exitAppButton.addActionListener(e -> shutdownGracefully());
        buttonPanel.add(exitAppButton);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 4;
        panel.add(buttonPanel, gbc);

        return panel;
    }

    /**
     * Create date spinner with current date
     */
    private JSpinner createDateSpinner() {
        SpinnerDateModel dateModel = new SpinnerDateModel();
        JSpinner spinner = new JSpinner(dateModel);
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "yyyy-MM-dd");
        spinner.setEditor(editor);
        return spinner;
    }

    /**
     * Create time spinner for hour:minute selection
     */
    private JSpinner createTimeSpinner() {
        SpinnerDateModel timeModel = new SpinnerDateModel();
        JSpinner spinner = new JSpinner(timeModel);
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "HH:mm");
        spinner.setEditor(editor);
        return spinner;
    }

    /**
     * Create status display panel
     */
    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("System Status"));

        statusArea = new JTextArea(20, 60);
        statusArea.setEditable(false);
        statusArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(statusArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Create availability display panel with progress bars
     */
    private JPanel createAvailabilityPanel() {
        JPanel mainPanel = new JPanel(new GridLayout(1, 3, 20, 10));
        mainPanel.setBorder(new TitledBorder("Real-Time Parking Availability"));
        mainPanel.setPreferredSize(new Dimension(800, 120));

        // Zone A Panel - createZonePanel assigns zoneALabel and zoneAProgress
        mainPanel.add(createZonePanel("Zone A", 20));

        // Zone B Panel - createZonePanel assigns zoneBLabel and zoneBProgress
        mainPanel.add(createZonePanel("Zone B", 15));

        // Zone C Panel - createZonePanel assigns zoneCLabel and zoneCProgress
        mainPanel.add(createZonePanel("Zone C", 10));

        return mainPanel;
    }

    /**
     * Create individual zone panel with label and progress bar
     * 
     * @param zoneName Name of the zone (e.g., "Zone A")
     * @param capacity Maximum capacity of the zone
     */
    private JPanel createZonePanel(String zoneName, int capacity) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        // Label showing zone name and numbers
        JLabel label = new JLabel(zoneName + ": " + capacity + "/" + capacity);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Progress bar (without text inside)
        JProgressBar progressBar = new JProgressBar(0, capacity);
        progressBar.setValue(capacity);
        progressBar.setStringPainted(false); // No text inside the bar
        progressBar.setPreferredSize(new Dimension(200, 25));
        progressBar.setMaximumSize(new Dimension(250, 25));
        progressBar.setForeground(new Color(46, 204, 113)); // Green
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Percentage label below the progress bar
        JLabel percentLabel = new JLabel("100% Available");
        percentLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        percentLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        percentLabel.setForeground(new Color(46, 139, 87)); // Dark green

        // Assign to instance variables based on zone
        if (zoneName.equals("Zone A")) {
            zoneALabel = label;
            zoneAProgress = progressBar;
            zoneAPercent = percentLabel;
        } else if (zoneName.equals("Zone B")) {
            zoneBLabel = label;
            zoneBProgress = progressBar;
            zoneBPercent = percentLabel;
        } else if (zoneName.equals("Zone C")) {
            zoneCLabel = label;
            zoneCProgress = progressBar;
            zoneCPercent = percentLabel;
        }

        panel.add(label);
        panel.add(Box.createVerticalStrut(8));
        panel.add(progressBar);
        panel.add(Box.createVerticalStrut(3));
        panel.add(percentLabel);

        return panel;
    }

    /**
     * Start all worker threads
     * WHY .start(): Creates new thread and calls run() method asynchronously.
     * Each .start() call:
     * 1. Allocates new thread stack
     * 2. Registers thread with JVM scheduler
     * 3. Calls run() method in new thread context
     * 4. Returns immediately (non-blocking)
     */
    private void startAllThreads() {
        appendStatus("=== Starting Campus Parking System ===\n");

        // Create ParkingMonitor (extends Thread)
        parkingMonitorThread = new ParkingMonitor((zone, available) -> {
            // WHY SwingUtilities.invokeLater: Update GUI from worker thread safely
            SwingUtilities.invokeLater(() -> updateAvailabilityDisplay(zone, available));
        });
        // WHY .start(): Begin monitoring in separate thread
        parkingMonitorThread.start();
        appendStatus("✓ ParkingMonitor thread started\n");

        // Create BookingProcessor
        bookingProcessor = new BookingProcessor(
                bookingQueue, paymentQueue, parkingMonitorThread,
                this::appendStatus);
        bookingProcessorThread = new Thread(bookingProcessor);
        bookingProcessorThread.setName("BookingProcessor");
        // WHY .start(): Begin processing bookings in separate thread
        bookingProcessorThread.start();
        appendStatus("✓ BookingProcessor thread started\n");

        // Create EntryGateController
        entryGateController = new EntryGateController(
                entryQueue, parkingMonitorThread,
                this::appendStatus);
        entryGateThread = new Thread(entryGateController);
        entryGateThread.setName("EntryGateController");
        // WHY .start(): Begin handling entries in separate thread
        entryGateThread.start();
        appendStatus("✓ EntryGateController thread started\n");

        // Create ExitGateController
        exitGateController = new ExitGateController(
                exitQueue, paymentQueue, parkingMonitorThread,
                this::appendStatus);
        exitGateThread = new Thread(exitGateController);
        exitGateThread.setName("ExitGateController");
        // WHY .start(): Begin handling exits in separate thread
        exitGateThread.start();
        appendStatus("✓ ExitGateController thread started\n");

        // Create PaymentProcessor
        paymentProcessor = new PaymentProcessor(
                paymentQueue, recordQueue,
                this::appendStatus);
        paymentProcessorThread = new Thread(paymentProcessor);
        paymentProcessorThread.setName("PaymentProcessor");
        // WHY .start(): Begin payment processing in separate thread
        paymentProcessorThread.start();
        appendStatus("✓ PaymentProcessor thread started\n");

        // Create RecordSaver
        recordSaver = new RecordSaver(recordQueue, this::appendStatus);
        recordSaverThread = new Thread(recordSaver);
        recordSaverThread.setName("RecordSaver");
        // WHY .start(): Begin file I/O in separate thread
        recordSaverThread.start();
        appendStatus("✓ RecordSaver thread started\n");

        appendStatus("\n=== All threads running - System ready ===\n\n");
    }

    /**
     * Handle booking button click
     */
    private void handleBooking() {
        // Validate inputs
        String name = nameField.getText().trim();
        String plate = plateField.getText().trim();

        if (name.isEmpty() || plate.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter name and vehicle plate",
                    "Input Required",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Get selected values
        String zone = (String) zoneComboBox.getSelectedItem();
        String spotType = (String) spotTypeComboBox.getSelectedItem();

        // Get date and time
        Calendar dateCal = Calendar.getInstance();
        dateCal.setTime((java.util.Date) dateSpinner.getValue());

        Calendar timeCal = Calendar.getInstance();
        timeCal.setTime((java.util.Date) timeSpinner.getValue());

        LocalDateTime dateTime = LocalDateTime.of(
                dateCal.get(Calendar.YEAR),
                dateCal.get(Calendar.MONTH) + 1,
                dateCal.get(Calendar.DAY_OF_MONTH),
                timeCal.get(Calendar.HOUR_OF_DAY),
                timeCal.get(Calendar.MINUTE));

        // Create booking request
        BookingRequest request = new BookingRequest(name, plate, zone, spotType, dateTime);

        // Add to queue
        // WHY offer() instead of put(): Non-blocking - prevents GUI freeze
        // Returns false if queue full (graceful degradation)
        if (bookingQueue.offer(request)) {
            appendStatus(String.format("→ Booking request submitted for %s\n", name));
            activeBookings.add(request);

            // Clear form
            nameField.setText("");
            plateField.setText("");
        } else {
            JOptionPane.showMessageDialog(this,
                    "Booking queue is full. Please try again.",
                    "Queue Full",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Handle entry button click
     */
    private void handleEntry() {
        if (activeBookings.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No confirmed bookings available for entry",
                    "No Bookings",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Show selection dialog
        BookingRequest[] bookings = activeBookings.toArray(new BookingRequest[0]);
        BookingRequest selected = (BookingRequest) JOptionPane.showInputDialog(
                this,
                "Select booking to simulate entry:",
                "Simulate Entry",
                JOptionPane.QUESTION_MESSAGE,
                null,
                bookings,
                bookings[0]);

        if (selected != null && selected.getStatus() == BookingRequest.Status.CONFIRMED) {
            // Add to entry queue
            if (entryQueue.offer(selected)) {
                appendStatus(String.format("→ Entry simulation for %s\n", selected.getVehiclePlate()));
                activeBookings.remove(selected);
                enteredVehicles.add(selected);
            }
        }
    }

    /**
     * Handle exit button click
     */
    private void handleExit() {
        if (enteredVehicles.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No vehicles currently in parking facility",
                    "No Vehicles",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Show selection dialog
        BookingRequest[] vehicles = enteredVehicles.toArray(new BookingRequest[0]);
        BookingRequest selected = (BookingRequest) JOptionPane.showInputDialog(
                this,
                "Select vehicle to simulate exit:",
                "Simulate Exit",
                JOptionPane.QUESTION_MESSAGE,
                null,
                vehicles,
                vehicles[0]);

        if (selected != null && selected.getStatus() == BookingRequest.Status.ENTERED) {
            // Add to exit queue
            if (exitQueue.offer(selected)) {
                appendStatus(String.format("→ Exit simulation for %s\n", selected.getVehiclePlate()));
                enteredVehicles.remove(selected);
            }
        }
    }

    /**
     * Generate test data for quick testing
     * Creates 5 sample bookings automatically
     */
    private void generateTestData() {
        appendStatus("\n=== Generating Test Data ===\n");

        String[] names = { "Alice Johnson", "Bob Smith", "Charlie Lee", "Diana Wong", "Ethan Brown" };
        String[] plates = { "ABC1234", "XYZ5678", "DEF9012", "GHI3456", "JKL7890" };
        String[] zones = { "A", "B", "C", "A", "B" };
        String[] types = { "Regular", "VIP", "Regular", "Disabled", "VIP" };

        for (int i = 0; i < 5; i++) {
            LocalDateTime dateTime = LocalDateTime.now().plusHours(i);
            BookingRequest request = new BookingRequest(names[i], plates[i], zones[i], types[i], dateTime);

            if (bookingQueue.offer(request)) {
                activeBookings.add(request);
                appendStatus(String.format("→ Test booking %d: %s (%s)\n", i + 1, names[i], plates[i]));
            }

            // Small delay between submissions
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }

        appendStatus("=== Test data generated ===\n\n");
    }

    /**
     * Update availability display labels and progress bars with color coding
     * WHY called from ParkingMonitor: Callback pattern for real-time updates
     * Must already be on EDT (called via invokeLater from ParkingMonitor)
     */
    private void updateAvailabilityDisplay(String zone, int available) {
        int capacity = zone.equals("A") ? 20 : zone.equals("B") ? 15 : 10;
        int percentage = (int) ((available * 100.0) / capacity);

        String text = String.format("Zone %s: %d/%d", zone, available, capacity);
        String progressText = String.format("%d%% Available", percentage);

        // Color coding based on availability
        Color barColor;
        Color textColor;
        if (percentage >= 50) {
            barColor = new Color(46, 204, 113); // Green - plenty available
            textColor = new Color(46, 139, 87); // Dark green
        } else if (percentage >= 25) {
            barColor = new Color(241, 196, 15); // Yellow - moderate
            textColor = new Color(183, 149, 11); // Dark yellow
        } else if (percentage > 0) {
            barColor = new Color(231, 76, 60); // Red - low availability
            textColor = new Color(192, 57, 43); // Dark red
        } else {
            barColor = new Color(189, 195, 199); // Gray - full
            textColor = new Color(127, 140, 141); // Dark gray
            progressText = "FULL";
        }

        switch (zone) {
            case "A":
                zoneALabel.setText(text);
                zoneAProgress.setValue(available);
                zoneAProgress.setForeground(barColor);
                zoneAPercent.setText(progressText);
                zoneAPercent.setForeground(textColor);
                break;
            case "B":
                zoneBLabel.setText(text);
                zoneBProgress.setValue(available);
                zoneBProgress.setForeground(barColor);
                zoneBPercent.setText(progressText);
                zoneBPercent.setForeground(textColor);
                break;
            case "C":
                zoneCLabel.setText(text);
                zoneCProgress.setValue(available);
                zoneCProgress.setForeground(barColor);
                zoneCPercent.setText(progressText);
                zoneCPercent.setForeground(textColor);
                break;
        }
    }

    /**
     * Append message to status area
     * WHY SwingUtilities.invokeLater: Can be called from worker threads
     * Ensures thread-safe GUI update by running on EDT
     */
    private void appendStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            // Ensure message ends with newline for proper formatting
            String formattedMessage = message;
            if (!formattedMessage.endsWith("\n")) {
                formattedMessage += "\n";
            }
            statusArea.append(formattedMessage);
            // Auto-scroll to bottom
            statusArea.setCaretPosition(statusArea.getDocument().getLength());
        });
    }

    /**
     * Graceful shutdown of all threads
     * WHY graceful: Ensures:
     * 1. Current operations complete
     * 2. Files are properly closed
     * 3. No data loss
     * 4. Clean resource cleanup
     */
    private void shutdownGracefully() {
        appendStatus("\n=== Initiating graceful shutdown ===\n");

        // Step 1: Signal all threads to stop
        // WHY set flags first: Allows current operations to complete naturally
        bookingProcessor.stopProcessing();
        entryGateController.stopProcessing();
        exitGateController.stopProcessing();
        paymentProcessor.stopProcessing();
        recordSaver.stopProcessing();
        parkingMonitorThread.stopMonitoring();
        appendStatus("✓ Shutdown signal sent to all threads\n");

        // Step 2: Interrupt all threads
        // WHY interrupt(): Wakes threads blocked on queue.take() or Thread.sleep()
        // Without interrupt, threads would wait indefinitely
        bookingProcessorThread.interrupt();
        entryGateThread.interrupt();
        exitGateThread.interrupt();
        paymentProcessorThread.interrupt();
        recordSaverThread.interrupt();
        parkingMonitorThread.interrupt();
        appendStatus("✓ Interrupt signal sent to all threads\n");

        // Step 3: Wait for threads to finish (with timeout)
        // WHY join(2000): Waits up to 2 seconds for each thread to terminate
        // Prevents indefinite waiting if thread hangs
        // Timeout ensures application eventually exits
        try {
            bookingProcessorThread.join(2000);
            appendStatus("✓ BookingProcessor terminated\n");

            entryGateThread.join(2000);
            appendStatus("✓ EntryGateController terminated\n");

            exitGateThread.join(2000);
            appendStatus("✓ ExitGateController terminated\n");

            paymentProcessorThread.join(2000);
            appendStatus("✓ PaymentProcessor terminated\n");

            recordSaverThread.join(2000);
            appendStatus("✓ RecordSaver terminated\n");

            parkingMonitorThread.join(2000);
            appendStatus("✓ ParkingMonitor terminated\n");

        } catch (InterruptedException e) {
            appendStatus("! Shutdown interrupted\n");
        }

        appendStatus("\n=== Shutdown complete ===\n");

        // Small delay for user to see final messages
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            // Ignore
        }

        // Step 4: Exit application
        System.exit(0);
    }

    /**
     * Main method - application entry point
     */
    public static void main(String[] args) {
        // WHY SwingUtilities.invokeLater:
        // Ensures GUI creation happens on Event Dispatch Thread (EDT)
        // Swing requirement: All GUI code must run on EDT for thread safety
        SwingUtilities.invokeLater(() -> {
            new ParkingGUI();
        });
    }
}
