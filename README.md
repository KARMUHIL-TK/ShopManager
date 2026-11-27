# Shop Inventory & Billing Management System

A desktop application (Java Swing + SQLite) for managing stock and making bills in a
retail provisions shop. It works offline and keeps all data in one local file (`shop.db`).

It was first written as a console program and now has a point-and-click desktop interface.

## Screens
- **New Bill** - pick a product, enter the quantity, edit prices, save the bill, see the receipt
- **Stock** - see all stock, add products, edit counts and prices, open a bag, remove items
- **Bill History** - earlier bills (newest first) with the items on each bill
- **Returns** - take back sealed bags, opened goods (measured kg) or sealed packets

## Key Features
- Products sold **by weight** (rice, millets, pulses) and **by packet** (oil, spices, health mix)
- **Sealed bags and opened bags**: sealed bags are counted; opened bags are tracked in loose kg
- **Auto-split billing**: 30 kg from 26 kg bags becomes 1 full bag (bag price) + 4 kg loose (per-kg price)
- Prices can be edited on a bill before saving
- **Stock only changes when a bill is saved.** A bill is saved in one database transaction, so it is
  either saved completely or not at all
- **Stock check**: you cannot sell more than you have; the message says how much is in stock
- 6-table SQLite schema accessed through JDBC with full add / view / edit / remove (CRUD)
- Input validation with clear messages on every form

## Tech Stack
- Java 17 or newer (Core Java, OOP, Collections, Exception Handling)
- Swing (desktop interface, no extra libraries)
- SQLite with JDBC (`lib/sqlite-jdbc-3.53.0.0.jar`)
- Git, GitHub, VS Code

## How to Run (Windows)
1. Install Java 17 or newer and clone this repository
2. Double-click `run.bat`
   (or run: `javac -cp "lib/sqlite-jdbc-3.53.0.0.jar" -d out src/com/shop/*.java src/com/shop/ui/*.java`
   then `java -cp "out;lib/sqlite-jdbc-3.53.0.0.jar" com.shop.ui.App`)

The old console version is still there: `java -cp "out;lib/sqlite-jdbc-3.53.0.0.jar" com.shop.Main`.
Use the desktop app for real work; the console billing does not have the stock safety checks above.

## How to Test
Double-click `test.bat`. It runs `test/com/shop/ShopServiceTest.java`, which checks the billing and stock
rules (auto-split, no overselling, all-or-nothing bills, returns, validation) on a temporary database,
so your real `shop.db` is never touched.

## Project Structure
```
src/com/shop/ShopService.java   all shop logic (no printing, no screens)
src/com/shop/Database.java      database connection and table creation
src/com/shop/ui/                the Swing screens (one class per screen)
src/com/shop/*Manager.java      the original console version
test/com/shop/                  tests
```

## Developer
Karmuhil TK
B.Tech Computer Science & Engineering (2024)

Built with the help of an AI assistant (Claude) as a coding aid.
