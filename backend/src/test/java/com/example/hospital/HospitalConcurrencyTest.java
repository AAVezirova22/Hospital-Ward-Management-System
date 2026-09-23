package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class HospitalConcurrencyTest extends HospitalSupport {
  @Test
  void twoConcurrentAdmissionsCannotOverbookLastBed() throws Exception {
    var r = room(1);
    var p1 = createPatient();
    var p2 = createPatient();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var start = new CountDownLatch(1);
      List<Future<Integer>> tasks = new ArrayList<>();
      for (var p : List.of(p1, p2))
        tasks.add(
            executor.submit(
                () -> {
                  start.await();
                  return request(
                          "admin",
                          "POST",
                          "/api/v1/admissions",
                          Map.of(
                              "patientId",
                              p.get("id").asLong(),
                              "doctorId",
                              1,
                              "roomId",
                              r.get("id").asLong()))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      start.countDown();
      var codes = new ArrayList<Integer>();
      for (var task : tasks) codes.add(task.get(20, TimeUnit.SECONDS));
      assertThat(codes).containsExactlyInAnyOrder(201, 409);
    }
    assertThat(occupiedBeds(r.get("id").asLong())).isOne();
  }

  @Test
  void concurrentLastBedAdmissionAndMaintenanceHoldCannotBothSucceed() throws Exception {
    var r = room(1);
    var patient = createPatient();
    var now = Instant.now();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var start = new CountDownLatch(1);
      var admission =
          executor.submit(
              () -> {
                start.await();
                return request(
                        "admin",
                        "POST",
                        "/api/v1/admissions",
                        Map.of(
                            "patientId",
                            patient.get("id").asLong(),
                            "doctorId",
                            1,
                            "roomId",
                            r.get("id").asLong()))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      var hold =
          executor.submit(
              () -> {
                start.await();
                return request(
                        "admin",
                        "POST",
                        "/api/v1/rooms/" + r.get("id").asLong() + "/holds",
                        Map.of(
                            "bedCount",
                            1,
                            "reason",
                            "Concurrent maintenance",
                            "startsAt",
                            now.plusSeconds(60).toString(),
                            "endsAt",
                            now.plusSeconds(3600).toString()))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      start.countDown();
      assertThat(List.of(admission.get(20, TimeUnit.SECONDS), hold.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
  }
}
