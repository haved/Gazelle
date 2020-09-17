package gazelle.persistence;

import gazelle.model.Course;
import gazelle.model.Database;
import gazelle.model.User;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import static org.junit.jupiter.api.Assertions.*;

public class DatabasePersistenceTest {

    @Test
    public void testDatabaseSaveLoad() throws IOException {
        Database db = new Database();
        Course c1 = db.newCourse("Hello");
        Course c2 = db.newCourse("Hi");

        User u1 = db.newUser();
        User u2 = db.newUser();
        User u3 = db.newUser();

        c1.addOwner(u1);
        u1.addAsOwner(c2);

        u2.addAsOwner(c1);

        c2.addOwner(u3);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DatabaseSaver saver = new DatabaseSaver(baos);
        saver.save(db);
        saver.close();

        byte[] bytes = baos.toByteArray();

        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        DatabaseLoader loader = new DatabaseLoader(bais);
        Database db2 = loader.load();
        loader.close();

        assertEquals(db2.getCourses().size(), 2);
        assertEquals(db2.getUsers().size(), 3);

        Course C1 = db2.getCourse(c1.getId());
        Course C2 = db2.getCourse(c2.getId());

        User U1 = db2.getUser(u1.getId());
        User U2 = db2.getUser(u2.getId());
        User U3 = db2.getUser(u3.getId());

        assertFalse(C1 == c1 || C2 == c2 || U1 == u1 || U2 == u2 || U3 == u3);
        assertEquals(c1.getName(), C1.getName());

        assertTrue(C1.getOwners().contains(U1));
        assertTrue(U1.getOwnedCourses().contains(C1));
        assertFalse(C1.getOwners().contains(u1));
        assertFalse(U1.getOwnedCourses().contains(c1));

        assertTrue(C1.getOwners().contains(U2));
        assertFalse(C1.getOwners().contains(U3));
        assertTrue(U3.getOwnedCourses().contains(C2));
        assertFalse(U3.getOwnedCourses().contains(C1));

        // Illegal to load into a database that already has data
        assertThrows(IllegalStateException.class, ()-> {
            try(ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                db2.load(ois);
            } catch(IOException e) {
                e.printStackTrace();
            }
        });
    }
}
