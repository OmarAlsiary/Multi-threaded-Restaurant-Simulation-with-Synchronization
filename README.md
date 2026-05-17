[README.md](https://github.com/user-attachments/files/27905719/README.md)
# Multi-threaded Restaurant Simulation with Synchronization

A Java-based concurrent simulation that models a restaurant environment where **customers**, **chefs**, and **waiters** operate as independent threads, coordinating through semaphores, locks, and condition variables to manage shared resources like tables and meal queues.

This project demonstrates classic synchronization concepts — race-condition prevention, mutual exclusion, producer-consumer queues, and bounded resource access — in a real-world-flavored setting.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [How It Works](#how-it-works)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Input Format](#input-format)
- [Example Run](#example-run)
- [Synchronization Design](#synchronization-design)
- [Simulation Metrics](#simulation-metrics)
- [Authors](#authors)

---

## Overview

The simulation models the lifecycle of a restaurant service:

1. Customers arrive at their configured arrival time.
2. They wait for a free table (bounded by the table semaphore).
3. Once seated, they place an order into a shared order queue.
4. Chefs pick up orders, prepare meals (with realistic delays), and push them to a ready-meals queue.
5. Waiters deliver ready meals to the correct customer.
6. Customers eat, leave, and free their table for the next arrival.

Random delays make the schedule unpredictable, so the synchronization logic must hold up under genuine concurrency rather than a fixed script.

## Features

- Concurrent execution of customers, chefs, and waiters as separate threads
- Custom `Semaphore` implementation built on `wait()` / `notifyAll()`
- Producer-consumer pattern between **Customer → Chef** and **Chef → Waiter**
- Configurable counts of chefs, waiters, and tables
- File-based input for system configuration, meal prep times, and customer details
- Timestamped event log of every action in the restaurant
- End-of-run summary with key metrics (average wait time, average prep time, total customers served)

## How It Works

### Main Classes

| Class | Responsibility |
|-------|----------------|
| `Customer` | Implements `Runnable`. Arrives, waits for a table, orders, eats, leaves. |
| `Chef` | Pulls orders from the order queue, prepares meals, pushes them to ready meals. |
| `Waiter` | Pulls ready meals from the ready-meals queue and serves them to customers. |
| `Order` | Data object linking a customer to their requested meal. |
| `Semaphore` | Custom counting semaphore used to bound access to tables and worker pools. |
| `Main` | Reads the input file, spawns threads, runs the simulation, prints the summary. |

### Shared Resources

```java
public static Queue<Order>     orderQueue       = new LinkedList<>();
public static Queue<Order>     readyMeals       = new LinkedList<>();
public static Queue<Customer>  waitingCustomers = new LinkedList<>();
private static List<Integer>   availableTables  = new ArrayList<>();

public static Semaphore tableSemaphore;
public static Semaphore chefSemaphore;
public static Semaphore waiterSemaphore;

public static final Object orderQueueLock = new Object();
public static final Object readyMealsLock = new Object();
```

## Project Structure

```
.
├── src/
│   ├── Main.java         # Entry point — parses input and starts threads
│   ├── Customer.java     # Customer thread logic
│   ├── Chef.java         # Chef thread logic
│   ├── Waiter.java       # Waiter thread logic
│   ├── Order.java        # Customer-to-meal mapping
│   └── Semaphore.java    # Custom semaphore implementation
├── input/
│   ├── input1.txt        # 2 chefs, 3 waiters, 4 tables
│   ├── input2.txt        # 1 chef,  2 waiters, 3 tables
│   └── input3.txt        # 3 chefs, 4 waiters, 5 tables
├── output/
│   └── ...               # Sample simulation logs
└── README.md
```

## Getting Started

### Prerequisites

- Java JDK 8 or higher
- Any terminal / IDE (IntelliJ, Eclipse, VS Code)

### Compile

```bash
javac -d out src/*.java
```

### Run

```bash
java -cp out Main input/input1.txt
```

## Input Format

The input is a structured text file with three sections:

**1. System configuration** — number of chefs, waiters, and tables:

```
NC=2 NW=3 NT=4
```

**2. Meal preparation times** — per meal, in `MM:SS` format:

```
Burger=00:8   Pizza=00:10   Pasta=00:10   Salad=00:5
```

**3. Customer details** — one customer per line:

```
CustomerID=1 ArrivalTime=08:00 Order=Burger
CustomerID=2 ArrivalTime=08:02 Order=Pizza
```

## Example Run

**Input:**

```
NC=2 NW=3 NT=4
Burger=00:8 Pizza=00:10 Pasta=00:10 Salad=00:5 Steak=00:8
CustomerID=1 ArrivalTime=08:00 Order=Burger
CustomerID=2 ArrivalTime=08:02 Order=Pizza
CustomerID=3 ArrivalTime=08:05 Order=Pasta
CustomerID=4 ArrivalTime=08:07 Order=Salad
CustomerID=5 ArrivalTime=08:10 Order=Steak
```

**Output:**

```
Simulation Started with 2 Chefs, 3 Waiters, and 4 Tables.

[8:00] Customer 1 arrives.
[8:00] Customer 1 is seated at Table 1.
[8:00] Customer 1 places an order: Burger
[8:00] Chef 2 starts preparing Burger for Customer 1
[8:02] Customer 2 arrives.
[8:02] Customer 2 is seated at Table 2.
[8:02] Customer 2 places an order: Pizza
[8:02] Chef 1 starts preparing Pizza for Customer 2
...
[8:18] Customer 1 finishes eating and leaves.
[8:18] Table 1 is now available.
...
[End of Simulation]

Summary:
Total Customers Served: 5
Average Wait Time for Table: 1.6 minutes
Average Order Preparation Time: 8.2 minutes
Total Simulation Time: 36 minutes
```

## Synchronization Design

| Mechanism | Where it's used | Why |
|-----------|-----------------|-----|
| **Custom Semaphore** | `tableSemaphore`, `chefSemaphore`, `waiterSemaphore` | Cap concurrent access to a finite resource pool. |
| **`synchronized` blocks + locks** | `orderQueueLock`, `readyMealsLock` | Protect queue mutation against concurrent producers/consumers. |
| **`wait()` / `notifyAll()`** | Chef and Waiter idle loops | Block threads when no work is available, wake them when it is. |
| **Random delays** | Meal prep, eating duration | Make the schedule realistic — concurrency must hold under jitter. |

Care was taken to avoid the two classic concurrency hazards:

- **Deadlocks** — locks are always acquired in a consistent order, and no thread holds a lock across a blocking `wait()` on a different resource.
- **Race conditions** — every read/write to a shared queue or counter happens inside a `synchronized` block or behind a semaphore.

## Simulation Metrics

Each run prints an end-of-simulation summary:

- **Total Customers Served** — count of customers who completed the full flow.
- **Average Wait Time for Table** — mean time between arrival and seating.
- **Average Order Preparation Time** — mean time chefs spent on each meal.
- **Total Simulation Time** — wall-clock duration from first arrival to last departure.

Sample results across the three included input files:

| Config | Customers | Avg. Wait | Avg. Prep | Total Time |
|--------|-----------|-----------|-----------|------------|
| 2 chefs / 3 waiters / 4 tables | 5 | 1.6 min | 8.2 min | 36 min |
| 1 chef  / 2 waiters / 3 tables | 5 | 4.4 min | 8.2 min | 51 min |
| 3 chefs / 4 waiters / 5 tables | 8 | 4.6 min | 7.6 min | 42 min |

The contrast between the first and second configurations highlights the effect of chef throughput on customer wait times even when the meal mix is similar.

## Authors

- **Salem Edah ben Eshaq**
- **Mohammed Abu Taleb**
- **Omar Waleed Alsiary**

---


