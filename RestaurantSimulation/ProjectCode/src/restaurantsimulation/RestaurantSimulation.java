package restaurantsimulation;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class RestaurantSimulation {

    public static class Semaphore {
        private int permits;

        public Semaphore(int permits) {
            this.permits = permits;
        }

        public synchronized void acquire() {
            while (permits <= 0) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            permits--;
        }

        public synchronized void release() {
            permits++;
            notifyAll();
        }
    }

    public static Semaphore tableSemaphore;
    public static Semaphore chefSemaphore;
    public static Semaphore waiterSemaphore;
    public static Queue<Order> orderQueue = new LinkedList<>();
    public static Queue<Order> readyMeals = new LinkedList<>(); // Queue for ready meals
    public static final Object orderQueueLock = new Object();
    public static final Object readyMealsLock = new Object();
    public static Queue<Customer> waitingCustomers = new LinkedList<>();
    public static Map<Integer, Customer> customerMap = new HashMap<>();
    public static final Map<String, Integer> mealPreparationTimes = new HashMap<>();
    private static int totalCustomersServed = 0;
    private static double totalWaitTimeForTable = 0;
    private static double totalOrderPrepTime = 0;
    private static long simulationStartTime;
    private static long simulationEndTime;
    private static List<Integer> availableTables;
    private static int arrivalTime;

    public static void main(String[] args) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("input.txt"));
            String line;

            // Read the system configuration
            line = reader.readLine();
            String[] config = line.split(" ");
            int numberOfChefs = Integer.parseInt(config[0].split("=")[1]);
            int numberOfWaiters = Integer.parseInt(config[1].split("=")[1]);
            int numberOfTables = Integer.parseInt(config[2].split("=")[1]);

            System.out.println("Simulation Started with " + numberOfChefs + " Chefs, "
                    + numberOfWaiters + " Waiters, and " + numberOfTables + " Tables.");

            // Initialize semaphores and table tracking
            tableSemaphore = new Semaphore(numberOfTables);
            chefSemaphore = new Semaphore(numberOfChefs);
            waiterSemaphore = new Semaphore(numberOfWaiters);
            availableTables = new ArrayList<>();
            for (int i = 1; i <= numberOfTables; i++) {
                availableTables.add(i);
            }

            // Read meal preparation times
            line = reader.readLine();
            String[] meals = line.split(" ");
            for (String meal : meals) {
                String[] parts = meal.split("=");
                String mealName = parts[0];
                int prepTime = Integer.parseInt(parts[1].split(":")[1]);
                mealPreparationTimes.put(mealName, prepTime);
            }

            // Read customer details and create customer threads
            List<Thread> customerThreads = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                String[] customerDetails = line.split(" ");
                int customerId = Integer.parseInt(customerDetails[0].split("=")[1]);
                String arrival = customerDetails[1].split("=")[1];
                String meal = customerDetails[2].split("=")[1];
                arrivalTime = Integer.parseInt(arrival.split(":")[0]);
                Customer customer = new Customer(customerId, meal, arrival);
                customerMap.put(customerId, customer);
                customerThreads.add(new Thread(customer));
            }

            reader.close();
            simulationStartTime = System.currentTimeMillis();

            // Start customer threads
            for (Thread thread : customerThreads) {
                thread.start();
            }

            // Start chef threads
            for (int i = 1; i <= numberOfChefs; i++) {
                new Thread(new Chef(i)).start();
            }

            // Start waiter threads
            for (int i = 1; i <= numberOfWaiters; i++) {
                new Thread(new Waiter(i)).start();
            }

            // Wait for all customer threads to finish
            for (Thread thread : customerThreads) {
                thread.join();
            }

            simulationEndTime = System.currentTimeMillis();
            printSummary();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void printSummary() {
        long totalSimulationTime = (simulationEndTime - simulationStartTime) / 1000;
        double avgWaitTimeForTable = totalCustomersServed > 0 ? totalWaitTimeForTable / totalCustomersServed : 0;
        double avgOrderPrepTime = totalCustomersServed > 0 ? totalOrderPrepTime / totalCustomersServed : 0;

        System.out.println("[End of Simulation]");
        System.out.println("Summary:");
        System.out.println("Total Customers Served: " + totalCustomersServed);
        System.out.println("Average Wait Time for Table: " + avgWaitTimeForTable + " minutes");
        System.out.println("Average Order Preparation Time: " + avgOrderPrepTime + " minutes");
        System.out.println("Total Simulation Time: " + totalSimulationTime + " minutes");
        System.exit(0);
    }

    static class Order {
        int customerId;
        String meal;
        int tableNumber;

        public Order(int customerId, String meal, int tableNumber) {
            this.customerId = customerId;
            this.meal = meal;
            this.tableNumber = tableNumber;
        }
    }

    static class Customer implements Runnable {
        private final int customerId;
        private final String meal;
        private final String arrival;
        private int tableNumber;

        public Customer(int customerId, String meal, String arrival) {
            this.customerId = customerId;
            this.meal = meal;
            this.arrival = arrival;
        }

@Override
public void run() {
    try {
        // Simulate customer arrival delay
        int arrivalDelay = Integer.parseInt(arrival.split(":")[1]);
        Thread.sleep(arrivalDelay * 1000);
        System.out.println("[" + getCurrentTime() + "] Customer " + customerId + " arrives.");

        long waitStartTime = System.currentTimeMillis(); // Start tracking wait time for table

        // Add customer to the waiting queue
        synchronized (waitingCustomers) {
            waitingCustomers.add(this);
            waitingCustomers.notifyAll(); // Notify other threads waiting for new customers
        }

        // Wait until it's this customer's turn to be seated
        synchronized (waitingCustomers) {
            while (waitingCustomers.peek() != this) {
                waitingCustomers.wait(); // Wait for the turn
            }
        }


        

        // Acquire a table semaphore
        RestaurantSimulation.tableSemaphore.acquire();

        // Remove the current customer from the waiting queue
        synchronized (waitingCustomers) {
            waitingCustomers.poll(); // Customer is now seated
            waitingCustomers.notifyAll(); // Notify other waiting customers
        }
        long waitEndTime = System.currentTimeMillis(); // End tracking wait time for table
        
        totalWaitTimeForTable += (waitEndTime - waitStartTime) / 1000; // Convert to seconds

        // Allocate a table to the customer
        synchronized (availableTables) {
            tableNumber = availableTables.remove(0);
        }

        System.out.println("[" + getCurrentTime() + "] Customer " + customerId + " is seated at Table " + tableNumber + ".");
        
        
        // Place the order
        synchronized (RestaurantSimulation.orderQueueLock) {
            RestaurantSimulation.orderQueue.add(new Order(customerId, meal, tableNumber));
            System.out.println("[" + getCurrentTime() + "] Customer " + customerId + " places an order: " + meal);
            RestaurantSimulation.orderQueueLock.notifyAll(); // Notify chefs
        }

        // Wait for the meal to be served
        synchronized (this) {
            this.wait(); // Wait for waiter to serve the meal
        }

        // Simulate eating time
        Thread.sleep(10 * 1000);

        // Customer finishes eating and leaves
        System.out.println("[" + getCurrentTime() + "] Customer " + customerId + " finishes eating and leaves.");
        RestaurantSimulation.tableSemaphore.release(); // Release the table semaphore

        synchronized (availableTables) {
            availableTables.add(tableNumber); // Mark table as available
            System.out.println("[" + getCurrentTime() + "] Table " + tableNumber + " is now available.");
        }

        synchronized (RestaurantSimulation.class) {
            totalCustomersServed++;
        }

    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}




        private String getCurrentTime() {
            String s = "" + arrivalTime;
            if((System.currentTimeMillis() - simulationStartTime)/1000 < 10)
                return new SimpleDateFormat("" + s + ":0" + (System.currentTimeMillis() - simulationStartTime)/1000).format(new Date());
            else
                return new SimpleDateFormat("" + s + ":" + (System.currentTimeMillis() - simulationStartTime)/1000).format(new Date());
        }
    }

    static class Chef implements Runnable {
        private final int chefId;

        public Chef(int chefId) {
            this.chefId = chefId;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    Order order;
                    synchronized (RestaurantSimulation.orderQueueLock) {
                        while (RestaurantSimulation.orderQueue.isEmpty()) {
                            RestaurantSimulation.orderQueueLock.wait();
                        }
                        order = RestaurantSimulation.orderQueue.poll();
                    }

                    System.out.println("[" + getCurrentTime() + "] Chef " + chefId + " starts preparing "
                            + order.meal + " for Customer " + order.customerId);
                    int prepTime = mealPreparationTimes.get(order.meal);
                    Thread.sleep(prepTime * 1000);
                    synchronized (RestaurantSimulation.class) {
                            totalOrderPrepTime += prepTime; // Accumulate preparation time for summary
                        }

                    synchronized (RestaurantSimulation.readyMealsLock) {
                        RestaurantSimulation.readyMeals.add(order);
                        System.out.println("[" + getCurrentTime() + "] Chef " + chefId + " finishes preparing "
                            + order.meal + " for Customer " + order.customerId);
                        RestaurantSimulation.readyMealsLock.notifyAll();
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private String getCurrentTime() {
            String s = "" + arrivalTime;
            if((System.currentTimeMillis() - simulationStartTime)/1000 < 10)
                return new SimpleDateFormat("" + s + ":0" + (System.currentTimeMillis() - simulationStartTime)/1000).format(new Date());
            else
                return new SimpleDateFormat("" + s + ":" + (System.currentTimeMillis() - simulationStartTime)/1000).format(new Date());
        }
    }

    static class Waiter implements Runnable {
        private final int waiterId;

        public Waiter(int waiterId) {
            this.waiterId = waiterId;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    Order order;
                    synchronized (RestaurantSimulation.readyMealsLock) {
                        while (RestaurantSimulation.readyMeals.isEmpty()) {
                            RestaurantSimulation.readyMealsLock.wait();
                        }
                        order = RestaurantSimulation.readyMeals.poll();
                        System.out.println("[" + getCurrentTime() + "] Waiter " + waiterId + " serves " + order.meal +
                            " to Customer " + order.customerId + " at Table " + order.tableNumber);
                    }


                    synchronized (customerMap.get(order.customerId)) {
                        customerMap.get(order.customerId).notify();
                    }

                    Thread.sleep(5000); // Simulate time to serve

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private String getCurrentTime() {
            String s = "" + arrivalTime;
            if((System.currentTimeMillis() - simulationStartTime)/1000 < 10)
                return new SimpleDateFormat("" + s + ":0" + (System.currentTimeMillis() - simulationStartTime)/1000).format(new Date());
            else
                return new SimpleDateFormat("" + s + ":" + (System.currentTimeMillis() - simulationStartTime)/1000).format(new Date());
        }
    }
}
