package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;

class BedHoldIntegrationTest extends HospitalSupport {
@Autowired JdbcTemplate jdbc;

@Test
void successiveHoldsAppearInRoomCapacityAndReleaseBackToZero() throws Exception {
var room = room(4);
long id = room.get("id").asLong();
admit(createPatient(), room);
admit(createPatient(), room);
var now = Instant.now();
var input = Map.of("bedCount", 1, "reason", "Capacity display regression",
"startsAt", now.minusSeconds(60).toString(), "endsAt", now.plusSeconds(3600).toString());
var first = result(request("admin", "POST", "/api/v1/rooms/" + id + "/holds", input), 201);
var one = roomCapacity(id);
assertThat(one.get("occupiedBeds").asInt()).isEqualTo(2);
assertThat(one.get("heldBeds").asInt()).isEqualTo(1);
assertThat(one.get("availableBeds").asInt()).isEqualTo(1);
assertThat(one.get("holds").get(0).get("id").asLong()).isEqualTo(first.get("id").asLong());
var second = result(request("staff", "POST", "/api/v1/rooms/" + id + "/holds", input), 201);
var two = roomCapacity(id);
assertThat(two.get("occupiedBeds").asInt()).isEqualTo(2);
assertThat(two.get("heldBeds").asInt()).isEqualTo(2);
assertThat(two.get("activeHeldBeds").asInt()).isEqualTo(2);
assertThat(two.get("availableBeds").asInt()).isZero();
assertThat(two.get("holds").size()).isEqualTo(2);
request("admin", "POST", "/api/v1/rooms/" + id + "/holds", input)
.andExpect(status().isConflict());
for (var hold : java.util.List.of(first, second)) {
request("admin", "DELETE", "/api/v1/rooms/" + id + "/holds/" + hold.get("id").asLong(), null)
.andExpect(status().isNoContent());
}
var released = roomCapacity(id);
assertThat(released.get("heldBeds").asInt()).isZero();
assertThat(released.get("activeHeldBeds").asInt()).isZero();
assertThat(released.get("holds").isEmpty()).isTrue();
assertThat(released.get("availableBeds").asInt()).isEqualTo(2);
}

@Test
void availabilityFiltersAndPaginationUsePeakReservationsWithCapabilities() throws Exception {
String marker = "holds-" + unique();
var now = Instant.now();
var ids = new java.util.ArrayList<Long>();
for (int i = 0; i < 3; i++) {
var room = result(request("admin", "POST", "/api/v1/rooms",
Map.of("roomNumber", marker + "-" + i, "bedCount", 4, "active", true,
"capabilities", java.util.List.of("oxygen"))), 201);
ids.add(room.get("id").asLong());
}
// A fully reserved room must be excluded before counting and slicing pages.
result(request("admin", "POST", "/api/v1/rooms/" + ids.get(0) + "/holds",
Map.of("bedCount", 4, "reason", "Full", "startsAt", now.minusSeconds(60).toString(),
"endsAt", now.plusSeconds(3600).toString())), 201);
// Adjacent future windows reserve two beds, not their sum of four.
for (int i = 1; i <= 2; i++) {
result(request("admin", "POST", "/api/v1/rooms/" + ids.get(1) + "/holds",
Map.of("bedCount", 2, "reason", "Adjacent " + i,
"startsAt", now.plusSeconds(i * 3600).toString(),
"endsAt", now.plusSeconds((i + 1) * 3600).toString())), 201);
}
for (String capabilities : java.util.List.of("", "&requiredCapabilities=oxygen")) {
String url = "/api/v1/rooms?q=" + marker + "&minFree=2&size=1" + capabilities;
var first = result(request("admin", "GET", url, null), 200);
assertThat(first.get("totalElements").asInt()).isEqualTo(2);
assertThat(first.get("totalPages").asInt()).isEqualTo(2);
assertThat(first.get("hasNext").asBoolean()).isTrue();
assertThat(first.get("items").get(0).get("id").asLong()).isEqualTo(ids.get(1));
assertThat(first.get("items").get(0).get("heldBeds").asInt()).isEqualTo(2);
assertThat(first.get("items").get(0).get("availableBeds").asInt()).isEqualTo(2);
var second = result(request("admin", "GET", url + "&page=1", null), 200);
assertThat(second.get("items").get(0).get("id").asLong()).isEqualTo(ids.get(2));
assertThat(second.get("items").get(0).get("heldBeds").asInt()).isZero();
assertThat(second.get("hasNext").asBoolean()).isFalse();
}
}

@Test
void timedHoldsReduceAvailabilityAndBlockPlacementUntilReleased() throws Exception {
var room = room(2);
var now = Instant.now();
var hold =
result(
request(
"admin",
"POST",
"/api/v1/rooms/" + room.get("id").asLong() + "/holds",
Map.of(
"bedCount",
1,
"reason",
"Electrical inspection",
"startsAt",
now.plusSeconds(3600).toString(),
"endsAt",
now.plusSeconds(7200).toString())),
201);

```
assertThat(hold.get("reason").asText()).isEqualTo("Electrical inspection");
var matching = roomCapacity(room.get("id").asLong());
assertThat(matching.get("heldBeds").asInt()).isEqualTo(1);
assertThat(matching.get("activeHeldBeds").asInt()).isZero();
assertThat(matching.get("availableBeds").asInt()).isEqualTo(1);
var dashboard = result(request("admin", "GET", "/api/v1/reports/dashboard", null), 200);
assertThat(dashboard.get("heldBeds").asLong()).isGreaterThanOrEqualTo(1);
var minFree = result(request("admin", "GET", "/api/v1/rooms?minFree=2&roomId=" + room.get("id").asLong(), null), 200);
assertThat(java.util.stream.StreamSupport.stream(minFree.get("items").spliterator(), false)
        .anyMatch(row -> row.get("id").asLong() == room.get("id").asLong()))
    .isFalse();

admit(createPatient(), room);
var simulation = result(request("admin", "GET", "/api/v1/planner/simulate?arrivals=100", null), 200);
assertThat(java.util.stream.StreamSupport.stream(simulation.get("placements").spliterator(), false)
        .filter(placement -> placement.get("roomId").asLong() == room.get("id").asLong())
        .count())
    .isZero();
request(
        "admin",
        "POST",
        "/api/v1/admissions",
        Map.of(
            "patientId", createPatient().get("id").asLong(),
            "doctorId", 1,
            "roomId", room.get("id").asLong()))
    .andExpect(status().isConflict())
    .andExpect(jsonPath("$.code").value("ROOM_CAPACITY_EXCEEDED"));

request(
        "admin",
        "DELETE",
        "/api/v1/rooms/" + room.get("id").asLong() + "/holds/" + hold.get("id").asLong(),
        null)
    .andExpect(status().isNoContent());
var afterRelease = roomCapacity(room.get("id").asLong());
assertThat(afterRelease.get("availableBeds").asInt()).isEqualTo(1);
assertThat(afterRelease.get("heldBeds").asInt()).isZero();
assertThat(afterRelease.get("holds").isEmpty()).isTrue();
admit(createPatient(), room);
```

}

@Test
void holdWindowsMustFitUnoccupiedCapacityAndRoomEditsRespectReservations() throws Exception {
var room = room(3);
var now = Instant.now();
var hold =
result(
request(
"admin",
"POST",
"/api/v1/rooms/" + room.get("id").asLong() + "/holds",
Map.of(
"bedCount",
2,
"reason",
"Floor repair",
"startsAt",
now.plusSeconds(600).toString(),
"endsAt",
now.plusSeconds(1800).toString())),
201);
request(
"admin",
"PUT",
"/api/v1/rooms/" + room.get("id").asLong(),
Map.of(
"roomNumber", room.get("roomNumber").asText(),
"bedCount", 1,
"active", true,
"version", 0))
.andExpect(status().isConflict())
.andExpect(jsonPath("$.code").value("ROOM_OCCUPIED"));

```
request(
        "admin",
        "POST",
        "/api/v1/rooms/" + room.get("id").asLong() + "/holds",
        Map.of(
            "bedCount",
            2,
            "reason",
            "Overlapping work",
            "startsAt",
            now.plusSeconds(1200).toString(),
            "endsAt",
            now.plusSeconds(2400).toString()))
    .andExpect(status().isConflict())
    .andExpect(jsonPath("$.code").value("ROOM_CAPACITY_EXCEEDED"));

request(
        "doctor",
        "POST",
        "/api/v1/rooms/" + room.get("id").asLong() + "/holds",
        Map.of(
            "bedCount",
            1,
            "reason",
            "Unauthorized",
            "startsAt",
            now.plusSeconds(3600).toString(),
            "endsAt",
            now.plusSeconds(7200).toString()))
    .andExpect(status().isForbidden());

request(
        "admin",
        "DELETE",
        "/api/v1/rooms/" + room.get("id").asLong() + "/holds/" + hold.get("id").asLong(),
        null)
    .andExpect(status().isNoContent());
```

}

@Test
void adjacentMaintenanceWindowsDoNotDoubleCountTheSameCapacity() throws Exception {
var room = room(2);
var start = Instant.now().plusSeconds(600);
var middle = start.plusSeconds(1200);
var end = middle.plusSeconds(1200);
request(
"admin",
"POST",
"/api/v1/rooms/" + room.get("id").asLong() + "/holds",
Map.of(
"bedCount", 2,
"reason", "First service window",
"startsAt", start.toString(),
"endsAt", middle.toString()))
.andExpect(status().isCreated());
request(
"admin",
"POST",
"/api/v1/rooms/" + room.get("id").asLong() + "/holds",
Map.of(
"bedCount", 2,
"reason", "Second service window",
"startsAt", middle.toString(),
"endsAt", end.toString()))
.andExpect(status().isCreated());
var matching = roomCapacity(room.get("id").asLong());
assertThat(matching.get("heldBeds").asInt()).isEqualTo(2);
assertThat(matching.get("holds").size()).isEqualTo(2);
var filtered = result(request("admin", "GET",
"/api/v1/rooms?minFree=1&roomId=" + room.get("id").asLong(), null), 200);
assertThat(filtered.get("totalElements").asInt()).isZero();
}

@Test
void maintenanceWindowsThatCollapseAtDatabasePrecisionAreRejected() throws Exception {
var room = room(1);
var start = Instant.now().plusSeconds(600).truncatedTo(ChronoUnit.MICROS);
request(
"admin",
"POST",
"/api/v1/rooms/" + room.get("id").asLong() + "/holds",
Map.of(
"bedCount", 1,
"reason", "Sub-microsecond window",
"startsAt", start.toString(),
"endsAt", start.plusNanos(999).toString()))
.andExpect(status().isBadRequest())
.andExpect(jsonPath("$.code").value("INVALID_HOLD_WINDOW"));
}

@Test
void expiredHoldsStopReservingCapacityWithoutABackgroundJob() throws Exception {
var room = room(1);
var now = Instant.now();
var hold =
result(
request(
"admin",
"POST",
"/api/v1/rooms/" + room.get("id").asLong() + "/holds",
Map.of(
"bedCount", 1,
"reason", "Short inspection",
"startsAt", now.plusSeconds(60).toString(),
"endsAt", now.plusSeconds(600).toString())),
201);
jdbc.update(
"update bed_holds set starts_at=?, ends_at=? where id=?",
java.sql.Timestamp.from(now.minusSeconds(60)),
java.sql.Timestamp.from(now.minusSeconds(30)),
hold.get("id").asLong());

```
var matching = roomCapacity(room.get("id").asLong());
assertThat(matching.get("heldBeds").asInt()).isZero();
assertThat(matching.get("activeHeldBeds").asInt()).isZero();
assertThat(matching.get("availableBeds").asInt()).isEqualTo(1);
assertThat(matching.get("holds").isEmpty()).isTrue();
var filtered = result(request("admin", "GET",
    "/api/v1/rooms?minFree=1&roomId=" + room.get("id").asLong(), null), 200);
assertThat(filtered.get("totalElements").asInt()).isEqualTo(1);
```

}

@Test
void inactiveRoomsRejectNewMaintenanceHolds() throws Exception {
var room = room(2);
request(
"admin",
"PUT",
"/api/v1/rooms/" + room.get("id").asLong(),
Map.of(
"roomNumber", room.get("roomNumber").asText(),
"bedCount", 2,
"active", false,
"version", 0))
.andExpect(status().isOk());
var now = Instant.now();
request(
"admin",
"POST",
"/api/v1/rooms/" + room.get("id").asLong() + "/holds",
Map.of(
"bedCount", 1,
"reason", "Unneeded while inactive",
"startsAt", now.plusSeconds(60).toString(),
"endsAt", now.plusSeconds(600).toString()))
.andExpect(status().isConflict())
.andExpect(jsonPath("$.code").value("ROOM_INACTIVE"));
}
}
