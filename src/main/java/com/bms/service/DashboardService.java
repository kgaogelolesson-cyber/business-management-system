package com.bms.service;

import com.bms.dao.DashboardDao;
import com.bms.dao.SettingDao;
import com.bms.exception.BmsException;
import com.bms.exception.DatabaseErrors;
import com.bms.exception.DatabaseException;
import com.bms.model.DashboardSummary;
import com.bms.model.DashboardSummary.RecentCustomer;
import com.bms.model.DashboardSummary.RecentOrder;
import com.bms.model.Employee;
import com.bms.util.Session;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Collects the dashboard figures. The administrator gets everything;
 * an employee gets a limited view (customers, products, low stock, and their OWN recent orders).
 */
public class DashboardService {

    private static final Logger LOGGER = Logger.getLogger(DashboardService.class.getName());
    private static final int LIST_SIZE = 8;

    private final DashboardDao dashboardDao = new DashboardDao();
    private final SettingDao settingDao = new SettingDao();

    public DashboardSummary getSummary() throws BmsException {
        Session.requireLoggedIn();

        try {
            String currencySymbol = settingDao.getValue("currency_symbol", "R");

            int customers = dashboardDao.countActiveCustomers();
            int products = dashboardDao.countActiveProducts();
            int lowStockCount = dashboardDao.countLowStockProducts();
            List<DashboardSummary.LowStockItem> lowStockItems = dashboardDao.findLowStockItems(LIST_SIZE);

            if (Session.isAdmin()) {
                return new DashboardSummary(
                        true,
                        customers,
                        products,
                        dashboardDao.countActiveEmployees(),
                        dashboardDao.countCompletedOrders(),
                        dashboardDao.sumRevenueCents(),
                        lowStockCount,
                        lowStockItems,
                        dashboardDao.findRecentOrders(LIST_SIZE, null),   // null = all employees
                        dashboardDao.findRecentCustomers(LIST_SIZE),
                        currencySymbol);
            }

            // Limited view for employees: only their own recent orders.
            Employee employee = Session.getCurrentEmployee();
            List<RecentOrder> myOrders = (employee == null)
                    ? List.of()
                    : dashboardDao.findRecentOrders(LIST_SIZE, employee.getEmployeeId());

            return new DashboardSummary(
                    false,
                    customers,
                    products,
                    0,
                    0,
                    0L,
                    lowStockCount,
                    lowStockItems,
                    myOrders,
                    List.<RecentCustomer>of(),
                    currencySymbol);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error while loading the dashboard", e);
            throw new DatabaseException(DatabaseErrors.friendly("The dashboard could not be loaded. Please try again.", e), e);
        }
    }
}
