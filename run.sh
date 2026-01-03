#!/bin/bash
# Compilation and Execution Script for Campus Parking System

echo "=================================="
echo "Campus Parking Spot Booking System"
echo "=================================="
echo ""

# Navigate to source directory
cd src

echo "Step 1: Cleaning old class files..."
rm -f *.class

echo "Step 2: Compiling Java files..."
javac *.java

if [ $? -eq 0 ]; then
    echo "✓ Compilation successful"
    echo ""
    echo "Step 3: Running application..."
    echo ""
    java ParkingGUI
else
    echo "✗ Compilation failed"
    exit 1
fi

