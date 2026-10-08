package com.bms.service;

import com.bms.database.DatabaseInitializer;
import com.bms.database.DatabaseManager;
import com.bms.exception.AccessDeniedException;
import com.bms.exception.BmsException;
import com.bms.exception.ValidationException;
import com.bms.model.Product;
import com.bms.model.Supplier;
import com.bms.model.SupplierOption;
import com.bms.util.Session;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * A console self-test for supplier management. It adds a few TEST suppliers and one TEST product
 * (names starting "Check"), tests the rules, then removes its own test rows again.
 * It needs the demo data (run DemoDataLoader once first). Run it on its own: right-click -> Run File.
 */
public final class SupplierServiceCheck {

    private static int passed = 0;
    private static int total = 0;
    /** Ids of the test rows this program created, so it removes only those. */
    private static final List<Long> createdSupplierIds = new ArrayList<>();
    private static final List<Long> createdProductIds = new ArrayList<>();

    private SupplierServiceCheck() {
    }

    public static void main(String[] args) {
        try {
            DatabaseInitializer.initialise();
            AuthService auth = new AuthService();
            SupplierService suppliers = new SupplierService();
            ProductService products = new ProductService();

            // --- Not logged in ---
            Session.end();
            check("Suppliers are refused when nobody is logged in", fails(() -> suppliers.search(""), AccessDeniedException.class));

            // --- Employee: no access at all ---
            auth.login("thandi", "Employee@123");
            check("Employee cannot view suppliers", fails(() -> suppliers.search(""), AccessDeniedException.class));
            check("Employee cannot add a supplier", fails(() -> suppliers.add(make("CheckNope")), AccessDeniedException.class));
            check("Employee cannot edit a supplier", fails(() -> suppliers.update(make("CheckNope")), AccessDeniedException.class));
            check("Employee cannot delete a supplier", fails(() -> suppliers.delete(1), AccessDeniedException.class));
            check("Employee cannot validate a supplier form", fails(() -> suppliers.validateSupplier(make("CheckNope")), AccessDeniedException.class));

            // --- Administrator: the demo suppliers ---
            auth.login("admin", "Admin@123");
            List<Supplier> all = suppliers.search("");
            Supplier tech = findByName(all, "TechSource Distributors");
            Supplier office = findByName(all, "OfficeWorld Wholesale");
            check("Administrator can view the suppliers", tech != null && office != null);
            check("TechSource shows 2 products", tech != null && tech.getProductCount() == 2);
            check("OfficeWorld shows 3 products", office != null && office.getProductCount() == 3);
            check("A supplier that provides products cannot be deleted",
                    tech != null && fails(() -> suppliers.delete(tech.getSupplierId()), ValidationException.class));
            check("...and is still listed afterwards", findByName(suppliers.search(""), "TechSource Distributors") != null);

            // --- Add and search ---
            Supplier a = make("CheckSupplier One");
            a.setContactPerson("Zinhle Check");
            a.setEmail("orders@checkone.example");
            a.setPhone("011 555 0199");
            a.setAddress("1 Test Road, Johannesburg");
            long idA = suppliers.add(a);
            createdSupplierIds.add(idA);
            check("Administrator can add a supplier", idA > 0);
            check("Search finds it by name", containsId(suppliers.search("checksupplier"), idA));
            check("Search finds it by contact person", containsId(suppliers.search("zinhle"), idA));
            check("Search finds it by email", containsId(suppliers.search("checkone.example"), idA));
            check("Search finds it by phone", containsId(suppliers.search("555 0199"), idA));
            check("Search for something that does not exist finds nothing", suppliers.search("zzzqqq").isEmpty());
            check("Searching for % is treated as a normal character", suppliers.search("%").isEmpty());
            Supplier savedA = find(suppliers.search("CheckSupplier One"), idA);
            check("A new supplier has 0 products", savedA != null && savedA.getProductCount() == 0);
            check("Contact, email, phone and address were saved",
                    savedA != null && "Zinhle Check".equals(savedA.getContactPerson())
                    && "orders@checkone.example".equals(savedA.getEmail())
                    && "011 555 0199".equals(savedA.getPhone())
                    && "1 Test Road, Johannesburg".equals(savedA.getAddress()));
            check("Only the name is required (other fields may be empty)", tryAdd(suppliers, make("CheckSupplier Bare")) > 0);

            // --- Validation ---
            check("A name is required", fails(() -> suppliers.add(make("   ")), ValidationException.class));
            check("A too-long name is refused", fails(() -> suppliers.add(make("C".repeat(101))), ValidationException.class));
            check("A bad email is refused", fails(() -> {
                Supplier s = make("CheckBadEmail");
                s.setEmail("not-an-email");
                suppliers.add(s);
            }, ValidationException.class));
            check("A bad phone (letters) is refused", fails(() -> {
                Supplier s = make("CheckBadPhone");
                s.setPhone("abc123");
                suppliers.add(s);
            }, ValidationException.class));
            check("A too-short phone is refused", fails(() -> {
                Supplier s = make("CheckShortPhone");
                s.setPhone("123");
                suppliers.add(s);
            }, ValidationException.class));
            check("A too-long contact person is refused", fails(() -> {
                Supplier s = make("CheckLongContact");
                s.setContactPerson("P".repeat(101));
                suppliers.add(s);
            }, ValidationException.class));
            check("A too-long address is refused", fails(() -> {
                Supplier s = make("CheckLongAddress");
                s.setAddress("A".repeat(201));
                suppliers.add(s);
            }, ValidationException.class));

            // --- Duplicate names ---
            check("The same name (different capitals) is refused", fails(() -> suppliers.add(make("CHECKSUPPLIER ONE")), ValidationException.class));
            Supplier keep = make("CheckSupplier One");
            keep.setSupplierId(idA);
            check("Saving a supplier under its own name is allowed", ok(() -> suppliers.update(keep)));
            Supplier clash = make("CheckSupplier Bare");
            clash.setSupplierId(idA);
            check("Renaming to another supplier's name is refused", fails(() -> suppliers.update(clash), ValidationException.class));

            // --- Edit ---
            Supplier edit = make("CheckSupplier One Renamed");
            edit.setSupplierId(idA);
            edit.setContactPerson("New Contact");
            suppliers.update(edit);
            Supplier afterEdit = find(suppliers.search("Renamed"), idA);
            check("Edit changes the name and contact person",
                    afterEdit != null && "New Contact".equals(afterEdit.getContactPerson()));
            check("Edit can clear the email, phone and address", afterEdit != null && afterEdit.getEmail() == null
                    && afterEdit.getPhone() == null && afterEdit.getAddress() == null);
            Supplier ghost = make("CheckGhost");
            ghost.setSupplierId(999999);
            check("Editing a supplier that does not exist gives a clear error", fails(() -> suppliers.update(ghost), ValidationException.class));

            // --- Link with products ---
            check("A new supplier appears in the product form's supplier list", optionsContain(products.getSupplierOptions(), idA));
            Product p = new Product();
            p.setProductName("CheckSupplierProduct");
            p.setPriceCents(1000);
            p.setQuantityInStock(0);
            p.setReorderLevel(1);
            p.setSupplierId(idA);
            long pid = products.add(p);
            createdProductIds.add(pid);
            check("A product using the supplier raises its product count to 1",
                    find(suppliers.search("Renamed"), idA).getProductCount() == 1);
            check("A supplier with a product cannot be deleted", fails(() -> suppliers.delete(idA), ValidationException.class));
            products.delete(pid);
            check("Once the product is deleted, the count drops to 0", find(suppliers.search("Renamed"), idA).getProductCount() == 0);

            // --- Delete ---
            suppliers.delete(idA);
            check("Administrator can delete a supplier with no products", find(suppliers.search("Renamed"), idA) == null);
            check("The deleted supplier is only hidden (row still in the database)", supplierRowExists(idA));
            check("A deleted supplier no longer appears in the product form's list", !optionsContain(products.getSupplierOptions(), idA));
            check("A deleted supplier's name can be used again", tryAdd(suppliers, make("CheckSupplier One Renamed")) > 0);
            check("Deleting a supplier that does not exist gives a clear error", fails(() -> suppliers.delete(999999), ValidationException.class));

        } catch (Exception e) {
            total++;
            System.out.println("FAIL: unexpected error: " + e);
            e.printStackTrace(System.out);
        } finally {
            cleanUp();
            Session.end();
        }
        System.out.println();
        System.out.println(passed + " of " + total + " checks passed.");
        System.out.println(passed == total ? "ALL CHECKS PASSED." : "SOME CHECKS FAILED - read the FAIL lines above.");
    }

    // ---------- small helpers ----------

    private interface Action {
        void run() throws Exception;
    }

    private static boolean fails(Action action, Class<? extends BmsException> expected) {
        try {
            action.run();
            return false;
        } catch (Exception e) {
            return expected.isInstance(e);
        }
    }

    private static boolean ok(Action action) {
        try {
            action.run();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Supplier make(String name) {
        Supplier s = new Supplier();
        s.setSupplierName(name);
        return s;
    }

    private static long tryAdd(SupplierService service, Supplier s) throws BmsException {
        long id = service.add(s);
        createdSupplierIds.add(id);
        return id;
    }

    private static Supplier find(List<Supplier> list, long id) {
        return list.stream().filter(s -> s.getSupplierId() == id).findFirst().orElse(null);
    }

    private static Supplier findByName(List<Supplier> list, String name) {
        return list.stream().filter(s -> name.equals(s.getSupplierName())).findFirst().orElse(null);
    }

    private static boolean containsId(List<Supplier> list, long id) {
        return find(list, id) != null;
    }

    private static boolean optionsContain(List<SupplierOption> options, long id) {
        return options.stream().anyMatch(o -> o.supplierId() == id);
    }

    private static boolean supplierRowExists(long id) throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement st = c.prepareStatement("SELECT COUNT(*) FROM suppliers WHERE supplier_id = ?")) {
            st.setLong(1, id);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    /** Removes ONLY the test rows this program created (products first, because they point at suppliers). */
    private static void cleanUp() {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement adjustments = c.prepareStatement("DELETE FROM stock_adjustments WHERE product_id = ?");
             PreparedStatement products = c.prepareStatement("DELETE FROM products WHERE product_id = ?");
             PreparedStatement suppliers = c.prepareStatement("DELETE FROM suppliers WHERE supplier_id = ?")) {
            for (long id : createdProductIds) {
                adjustments.setLong(1, id);
                adjustments.executeUpdate();
                products.setLong(1, id);
                products.executeUpdate();
            }
            for (long id : createdSupplierIds) {
                suppliers.setLong(1, id);
                suppliers.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("Could not remove the test rows: " + e.getMessage());
        }
    }

    private static void check(String description, boolean ok) {
        total++;
        if (ok) {
            passed++;
            System.out.println("PASS: " + description);
        } else {
            System.out.println("FAIL: " + description);
        }
    }
}
