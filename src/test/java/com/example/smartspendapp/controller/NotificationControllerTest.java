package com.example.smartspendapp.controller;

import com.example.smartspendapp.model.Notification;
import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.UserRepository;
import com.example.smartspendapp.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationController that use reflection helpers so
 * tests compile regardless of exact Notification getter/setter names.
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationController notificationController;

    private final String email = "test@example.com";
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = makeUser(100L, email);
    }

    @Test
    void list_returnsNotifications() {
        // Arrange
        Notification n1 = makeNotification(1L, "First message", false);
        Notification n2 = makeNotification(2L, "Second message", true);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));
        when(notificationService.getAll(testUser)).thenReturn(List.of(n1, n2));

        UserDetails ud = mock(UserDetails.class);
        when(ud.getUsername()).thenReturn(email);

        // Act
        List<Notification> result = notificationController.list(ud);

        // Assert basics
        assertNotNull(result, "Result should not be null");
        assertEquals(2, result.size(), "Should return two notifications");

        // Read values with reflection helpers (works regardless of getter name)
        assertEquals("First message", readMessage(result.get(0)));
        assertEquals("Second message", readMessage(result.get(1)));

        assertFalse(readFlag(result.get(0)), "First notification should be unread");
        assertTrue(readFlag(result.get(1)), "Second notification should be read");

        verify(userRepository, times(1)).findByEmail(email);
        verify(notificationService, times(1)).getAll(testUser);
        verifyNoMoreInteractions(userRepository, notificationService);
    }

    @Test
    void unread_returnsUnreadNotifications() {
        // Arrange
        Notification n1 = makeNotification(10L, "Unread 1", false);
        Notification n2 = makeNotification(11L, "Unread 2", false);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));
        when(notificationService.getUnread(testUser)).thenReturn(List.of(n1, n2));

        UserDetails ud = mock(UserDetails.class);
        when(ud.getUsername()).thenReturn(email);

        // Act
        List<Notification> result = notificationController.unread(ud);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertFalse(readFlag(result.get(0)));
        assertFalse(readFlag(result.get(1)));
        assertEquals("Unread 1", readMessage(result.get(0)));

        verify(userRepository, times(1)).findByEmail(email);
        verify(notificationService, times(1)).getUnread(testUser);
        verifyNoMoreInteractions(userRepository, notificationService);
    }

    @Test
    void markRead_callsServiceWithId() {
        // Arrange
        Long idToMark = 42L;

        // Act
        notificationController.markRead(idToMark);

        // Assert
        verify(notificationService, times(1)).markRead(idToMark);
        verifyNoMoreInteractions(notificationService);
        verifyNoInteractions(userRepository);
    }

    // ----------------- Helpers -----------------

    /**
     * Create a User via setters or reflectively set fields if setters absent.
     */
    private User makeUser(Long id, String email) {
        try {
            User u = new User();
            // try setters first
            try {
                Method m1 = User.class.getMethod("setId", Long.class);
                m1.invoke(u, id);
            } catch (NoSuchMethodException ignored) {
                try {
                    Field f = User.class.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(u, id);
                } catch (NoSuchFieldException ignored2) {
                }
            }

            try {
                Method m2 = User.class.getMethod("setEmail", String.class);
                m2.invoke(u, email);
            } catch (NoSuchMethodException ignored) {
                try {
                    Field f = User.class.getDeclaredField("email");
                    f.setAccessible(true);
                    f.set(u, email);
                } catch (NoSuchFieldException ignored2) {
                }
            }
            return u;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to create User in test", ex);
        }
    }

    /**
     * Create Notification and set id/message/read via setters or reflection fallback.
     */
    private Notification makeNotification(Long id, String message, boolean read) {
        try {
            Notification n = new Notification();

            // id
            try {
                Method mid = Notification.class.getMethod("setId", Long.class);
                mid.invoke(n, id);
            } catch (NoSuchMethodException ignored) {
                try {
                    Field fid = Notification.class.getDeclaredField("id");
                    fid.setAccessible(true);
                    fid.set(n, id);
                } catch (NoSuchFieldException ignored2) { }
            }

            // message (try setMessage / setMsg / field)
            boolean setMessageDone = false;
            String[] msgSetterNames = {"setMessage", "setMsg", "setText"};
            for (String name : msgSetterNames) {
                try {
                    Method mm = Notification.class.getMethod(name, String.class);
                    mm.invoke(n, message);
                    setMessageDone = true;
                    break;
                } catch (NoSuchMethodException ignored) { }
            }
            if (!setMessageDone) {
                // try direct field
                String[] msgFields = {"message", "msg", "text"};
                for (String f : msgFields) {
                    try {
                        Field field = Notification.class.getDeclaredField(f);
                        field.setAccessible(true);
                        field.set(n, message);
                        setMessageDone = true;
                        break;
                    } catch (NoSuchFieldException ignored) { }
                }
            }

            // read boolean (try setRead / setIsRead or field)
            boolean setReadDone = false;
            String[] readSetterNames = {"setRead", "setIsRead", "setReadFlag"};
            for (String name : readSetterNames) {
                try {
                    Method mr = Notification.class.getMethod(name, boolean.class);
                    mr.invoke(n, read);
                    setReadDone = true;
                    break;
                } catch (NoSuchMethodException ignored) { }
            }
            if (!setReadDone) {
                String[] readFields = {"read", "isRead", "readFlag"};
                for (String f : readFields) {
                    try {
                        Field field = Notification.class.getDeclaredField(f);
                        field.setAccessible(true);
                        field.set(n, read);
                        setReadDone = true;
                        break;
                    } catch (NoSuchFieldException ignored) { }
                }
            }

            // if neither setter nor field present, still return object; read helpers will fail with clear message
            return n;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to create Notification in test", ex);
        }
    }

    /**
     * Read message text from Notification using reflection (tries getters then fields).
     */
    private String readMessage(Notification n) {
        if (n == null) return null;

        String[] tryNames = {"getMessage", "message", "getMsg", "getText"};
        for (String name : tryNames) {
            try {
                // try getter
                Method m = Notification.class.getMethod(name);
                Object val = m.invoke(n);
                if (val != null) return val.toString();
            } catch (NoSuchMethodException ignored) {
                // try field next
                try {
                    Field f = Notification.class.getDeclaredField(name);
                    f.setAccessible(true);
                    Object val = f.get(n);
                    if (val != null) return val.toString();
                } catch (NoSuchFieldException ignored2) {
                    // continue
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to read field " + name + " from Notification", ex);
                }
            } catch (Exception ex) {
                throw new RuntimeException("Failed to call method " + name + " on Notification", ex);
            }
        }

        // fallback: try any public method that returns String and looks like message
        for (Method m : Notification.class.getMethods()) {
            if (m.getReturnType().equals(String.class) && (m.getName().toLowerCase().contains("message") || m.getName().toLowerCase().contains("msg"))) {
                try {
                    Object val = m.invoke(n);
                    if (val != null) return val.toString();
                } catch (Exception ignored) { }
            }
        }

        throw new IllegalStateException("Could not find message getter/field on Notification. " +
                "Check your model for a string property that holds the notification message.");
    }

    /**
     * Read boolean 'read' flag from Notification using reflection (tries getters then fields).
     */
    private boolean readFlag(Notification n) {
        if (n == null) return false;

        String[] tryNames = {"isRead", "getRead", "getIsRead", "isread", "getisRead"};
        for (String name : tryNames) {
            try {
                Method m = Notification.class.getMethod(name);
                Object val = m.invoke(n);
                if (val instanceof Boolean) return (Boolean) val;
            } catch (NoSuchMethodException ignored) {
                try {
                    Field f = Notification.class.getDeclaredField(name);
                    f.setAccessible(true);
                    Object val = f.get(n);
                    if (val instanceof Boolean) return (Boolean) val;
                } catch (NoSuchFieldException ignored2) {
                    // continue
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to read field " + name + " on Notification", ex);
                }
            } catch (Exception ex) {
                throw new RuntimeException("Failed to call getter " + name + " on Notification", ex);
            }
        }

        // try other common field names
        String[] fieldNames = {"read", "readFlag", "is_read"};
        for (String fn : fieldNames) {
            try {
                Field f = Notification.class.getDeclaredField(fn);
                f.setAccessible(true);
                Object val = f.get(n);
                if (val instanceof Boolean) return (Boolean) val;
            } catch (NoSuchFieldException ignored) {
            } catch (Exception ex) {
                throw new RuntimeException("Failed to read field " + fn + " on Notification", ex);
            }
        }

        throw new IllegalStateException("Could not determine read flag on Notification. " +
                "Add a getter (isRead/getRead) or a boolean field named 'read' for tests to read.");
    }
}
