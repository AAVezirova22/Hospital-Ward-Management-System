package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoomDirectoryCapabilityTest extends HospitalSupport {
  @Test
  void pagedDirectoryFiltersOnEveryRequiredCapability() throws Exception {
    String prefix = "CAP-" + unique();
    long equipped = createRoom(prefix + "-A", List.of("Oxygen", "isolation"));
    long oxygenOnly = createRoom(prefix + "-B", List.of("oxygen"));
    createRoom(prefix + "-C", List.of());

    var both =
        result(
            request(
                "admin",
                "GET",
                "/api/v1/rooms?q=" + prefix + "&requiredCapabilities=oxygen&requiredCapabilities=ISOLATION",
                null),
            200);
    assertThat(both.get("totalElements").asLong()).isEqualTo(1);
    assertThat(both.get("items").get(0).get("id").asLong()).isEqualTo(equipped);

    var oxygen =
        result(request("admin", "GET", "/api/v1/rooms?q=" + prefix + "&requiredCapabilities=oxygen", null), 200);
    assertThat(oxygen.get("totalElements").asLong()).isEqualTo(2);
    assertThat(oxygen.get("items").findValues("id").stream().map(n -> n.asLong()))
        .containsExactly(equipped, oxygenOnly);

    var all = result(request("admin", "GET", "/api/v1/rooms?q=" + prefix + "&size=2&page=1", null), 200);
    assertThat(all.get("totalElements").asLong()).isEqualTo(3);
    assertThat(all.get("items").size()).isEqualTo(1);
    assertThat(all.get("items").get(0).has("heldBeds")).isTrue();
  }

  private long createRoom(String number, List<String> capabilities) throws Exception {
    return result(
            request(
                "admin",
                "POST",
                "/api/v1/rooms",
                Map.of("roomNumber", number, "bedCount", 2, "active", true, "capabilities", capabilities)),
            201)
        .get("id")
        .asLong();
  }
}
