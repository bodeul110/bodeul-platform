package com.example.bodeul.ui.chat;

import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.UserRole;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChatRoomDirectoryTest {
    private AppointmentRequest row(String id,String hospital,AppointmentStatus state) {
        return new AppointmentRequest(id,"p","g",hospital,"","","","",state,"m");
    }
    @Test public void onlyPatientCanBrowseCompletedAndOnlyEligibleIdsAreListedOnce() {
        List<AppointmentRequest> rows = Arrays.asList(row("a","새봄",AppointmentStatus.MATCHED),
                row("a","중복",AppointmentStatus.MATCHED),row("b","가을",AppointmentStatus.COMPLETED),
                row("c","요청",AppointmentStatus.REQUESTED),row("d","취소",AppointmentStatus.CANCELED),null);
        assertEquals(2,ChatRoomDirectory.client(UserRole.PATIENT,rows).size());
        assertEquals("a",ChatRoomDirectory.client(UserRole.GUARDIAN,rows).get(0).id);
        assertEquals(1,ChatRoomDirectory.client(UserRole.GUARDIAN,rows).size());
        assertTrue(ChatRoomDirectory.client(UserRole.MANAGER,rows).isEmpty());
        assertTrue(ChatRoomDirectory.manager(null).isEmpty());
    }
    @Test public void searchCombinesReservationStateWithoutInventingUnreadCounts() {
        List<ChatRoomDirectory.Room> rooms = ChatRoomDirectory.client(UserRole.PATIENT, Arrays.asList(
                row("m","Alpha",AppointmentStatus.MATCHED),row("p","Alpha",AppointmentStatus.IN_PROGRESS),
                row("c","Beta",AppointmentStatus.COMPLETED)));
        assertEquals("p",ChatRoomDirectory.filter(rooms,ChatRoomDirectory.Filter.ACTIVE," ALp ").get(0).id);
        assertEquals("c",ChatRoomDirectory.filter(rooms,ChatRoomDirectory.Filter.COMPLETE,"beta").get(0).id);
        assertTrue(ChatRoomDirectory.filter(rooms,ChatRoomDirectory.Filter.UNREAD,"").isEmpty());
        assertTrue(ChatRoomDirectory.filter(rooms,ChatRoomDirectory.Filter.ACTIVE,"beta").isEmpty());
    }
}
