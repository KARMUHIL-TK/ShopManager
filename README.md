# Shop Inventory & Billing Management System

A console-based inventory and billing system built for a retail provisions shop.

## Tech Stack
- Java (Core Java, OOP)
- SQLite (via JDBC)

## Features
- Multi-product stock tracking (sealed and opened bags)
- Auto-split billing — full bags charged at bag price, loose kg at per kg price
- Bill history and stock returns
- Full input validation and error handling

## How to Run
1. Download sqlite-jdbc jar and place in lib/ folder
2. Compile: javac -cp "lib/sqlite-jdbc-3.53.0.0.jar" -d out src/com/shop/*.java
3. Run: java -cp "out;lib/sqlite-jdbc-3.53.0.0.jar" com.shop.Main

## Developer
Karmuhil TK
