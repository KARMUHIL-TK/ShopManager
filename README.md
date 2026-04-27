# Shop Inventory & Billing Management System

A complete console-based inventory and billing application 
developed for a retail provisions shop.

## About the Project
Built from scratch as the sole developer. This application 
helps manage stock efficiently and generate customer bills 
with realistic business logic.

## Tech Stack
- Java (Core Java, OOP, JDBC)
- SQLite (Database)
- Tools: VS Code, Git

## Key Features
- Multi-product stock tracking (sealed and opened bags)
- Auto-split billing logic (full bag price + loose kg price)
- Bill history tracking
- Stock returns management
- Product editing and full input validation
- 6-table database schema with complete CRUD operations

## How to Run
1. Clone the repository
2. Add SQLite JDBC driver to lib/ folder
3. Compile: javac -cp "lib/sqlite-jdbc-3.53.0.0.jar" -d out src/com/shop/*.java
4. Run: java -cp "out;lib/sqlite-jdbc-3.53.0.0.jar" com.shop.Main

## Developer
Karmuhil TK
B.Tech Computer Science & Engineering (2024)
