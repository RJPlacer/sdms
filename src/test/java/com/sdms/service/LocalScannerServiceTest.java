package com.sdms.service;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LocalScannerServiceTest {

    @Test
    void pairsOnePhoneAndForwardsQrPayload() throws Exception {
        var scans = new ArrayBlockingQueue<String>(1);
        var paired = new AtomicBoolean();
        try (var service = LocalScannerService.start(scans::offer, () -> paired.set(true))) {
            var client = HttpClient.newHttpClient();
            var base = "http://127.0.0.1:" + service.port() + "/athlete-track/";
            var pair = HttpRequest.newBuilder(URI.create(base + "pair"))
                .header("X-Athlete-Track-Token", service.token())
                .POST(HttpRequest.BodyPublishers.noBody()).build();
            assertEquals(200, client.send(pair, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertTrue(paired.get());

            String payload = "Athlete Track:ATHLETE|ID:ATH-2026-0001";
            var scan = HttpRequest.newBuilder(URI.create(base + "scan"))
                .header("X-Athlete-Track-Token", service.token())
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            assertEquals(202, client.send(scan, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(payload, scans.poll(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void rejectsAnInvalidPairingToken() throws Exception {
        try (var service = LocalScannerService.start(value -> {}, () -> {})) {
            var request = HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + service.port() + "/athlete-track/pair"))
                .header("X-Athlete-Track-Token", "wrong")
                .POST(HttpRequest.BodyPublishers.noBody()).build();
            assertEquals(401, HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }
}
