package com.aidocqa;

import com.aidocqa.dto.GeminiResponseDto;
import com.aidocqa.service.GeminiApiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class GeminiConcurrencyTest {

    @Autowired
    private GeminiApiService geminiApiService;

    @Test
    public void testTwoSimultaneousUserStreams() throws Exception {
        System.out.println("\n=======================================================");
        System.out.println("🔥 TEST 1: TWO SIMULTANEOUS USER STREAMS (USER A & USER B)");
        System.out.println("=======================================================");

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(2);

        StringBuilder chunksA = new StringBuilder();
        StringBuilder chunksB = new StringBuilder();

        long[] timesA = new long[2]; // start, end
        long[] timesB = new long[2];

        GeminiResponseDto[] results = new GeminiResponseDto[2];

        // Thread for User A
        Thread threadA = new Thread(() -> {
            try {
                startGate.await();
                timesA[0] = System.currentTimeMillis();
                results[0] = geminiApiService.streamAnswerMultimodal(
                        "req-userA",
                        "userA@aidoc.io",
                        "Design Patterns: Elements of Reusable Object-Oriented Software by Gang of Four.",
                        null,
                        "What is the Factory Pattern in 1-2 sentences?",
                        null,
                        chunksA::append,
                        "LOW"
                );
                timesA[1] = System.currentTimeMillis();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                finishGate.countDown();
            }
        }, "user-sim-thread-A");

        // Thread for User B
        Thread threadB = new Thread(() -> {
            try {
                startGate.await();
                timesB[0] = System.currentTimeMillis();
                results[1] = geminiApiService.streamAnswerMultimodal(
                        "req-userB",
                        "userB@aidoc.io",
                        "Spring Boot makes it easy to create stand-alone, production-grade Spring based Applications.",
                        null,
                        "What is Spring Boot in 1-2 sentences?",
                        null,
                        chunksB::append,
                        "LOW"
                );
                timesB[1] = System.currentTimeMillis();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                finishGate.countDown();
            }
        }, "user-sim-thread-B");

        threadA.start();
        threadB.start();

        // Release both simultaneously
        startGate.countDown();

        boolean finished = finishGate.await(90, TimeUnit.SECONDS);
        assertTrue(finished, "Both simultaneous streams should complete within 90 seconds");

        System.out.println("\n📊 --- CONCURRENCY EXECUTION REPORT (2 USERS) ---");
        System.out.printf("User A: Started=%d, Finished=%d, Duration=%d ms, AnswerLength=%d%n",
                timesA[0], timesA[1], (timesA[1] - timesA[0]), chunksA.length());
        System.out.printf("User B: Started=%d, Finished=%d, Duration=%d ms, AnswerLength=%d%n",
                timesB[0], timesB[1], (timesB[1] - timesB[0]), chunksB.length());

        assertNotNull(results[0], "User A result should not be null");
        assertTrue(results[0].isSuccess(), "User A should succeed");
        assertFalse(chunksA.isEmpty(), "User A should receive stream chunks");

        assertNotNull(results[1], "User B result should not be null");
        assertTrue(results[1].isSuccess(), "User B should succeed");
        assertFalse(chunksB.isEmpty(), "User B should receive stream chunks");

        // Verify that execution overlapped (neither had to wait for the other to finish)
        boolean executionOverlapped = (timesA[0] < timesB[1]) && (timesB[0] < timesA[1]);
        System.out.println("Execution Overlapped: " + executionOverlapped);
        assertTrue(executionOverlapped, "Requests must execute concurrently without serialization!");
    }

    @Test
    public void testFiveSimultaneousUserStreams() throws Exception {
        System.out.println("\n=======================================================");
        System.out.println("🔥 TEST 2: FIVE SIMULTANEOUS USER STREAMS");
        System.out.println("=======================================================");

        int concurrency = 5;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(concurrency);

        List<GeminiResponseDto> results = Collections.synchronizedList(new ArrayList<>());
        List<Long> durations = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        String[] questions = {
                "What is polymorphism in 1 sentence?",
                "What is encapsulation in 1 sentence?",
                "What is abstraction in 1 sentence?",
                "What is inheritance in 1 sentence?",
                "What is an interface in 1 sentence?"
        };

        for (int i = 0; i < concurrency; i++) {
            final int index = i;
            Thread t = new Thread(() -> {
                try {
                    startGate.await();
                    long start = System.currentTimeMillis();
                    StringBuilder chunks = new StringBuilder();
                    GeminiResponseDto resp = geminiApiService.streamAnswerMultimodal(
                            "req-usr" + (index + 1),
                            "user" + (index + 1) + "@aidoc.io",
                            "Object-Oriented Programming (OOP) is a programming paradigm based on the concept of objects.",
                            null,
                            questions[index],
                            null,
                            chunks::append,
                            "LOW"
                    );
                    long duration = System.currentTimeMillis() - start;
                    durations.add(duration);
                    results.add(resp);
                    if (resp.isSuccess() && !chunks.isEmpty()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    finishGate.countDown();
                }
            }, "sim-user-" + (i + 1));
            t.start();
        }

        startGate.countDown();

        boolean finished = finishGate.await(120, TimeUnit.SECONDS);
        assertTrue(finished, "All 5 streams should complete within 120 seconds");

        System.out.println("\n📊 --- CONCURRENCY EXECUTION REPORT (5 USERS) ---");
        System.out.println("Total dispatches: " + concurrency);
        System.out.println("Successful streams: " + successCount.get());
        System.out.println("Durations: " + durations);

        assertEquals(concurrency, successCount.get(), "All 5 concurrent requests must complete successfully");
    }

    @Test
    public void testTenSimultaneousUserStreams() throws Exception {
        System.out.println("\n=======================================================");
        System.out.println("🔥 TEST 3: TEN SIMULTANEOUS USER STREAMS (STRESS TEST)");
        System.out.println("=======================================================");

        int concurrency = 10;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(concurrency);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Long> durations = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < concurrency; i++) {
            final int index = i;
            Thread t = new Thread(() -> {
                try {
                    startGate.await();
                    long start = System.currentTimeMillis();
                    StringBuilder chunks = new StringBuilder();
                    GeminiResponseDto resp = geminiApiService.streamAnswerMultimodal(
                            "req-s" + (index + 1),
                            "stress" + (index + 1) + "@aidoc.io",
                            "Cloud native computing uses an open source software stack to deploy applications as microservices.",
                            null,
                            "Define microservice in 1 sentence (" + index + ")",
                            null,
                            chunks::append,
                            "LOW"
                    );
                    long duration = System.currentTimeMillis() - start;
                    durations.add(duration);
                    if (resp.isSuccess() && !chunks.isEmpty()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    finishGate.countDown();
                }
            }, "stress-user-" + (index + 1));
            t.start();
        }

        startGate.countDown();

        boolean finished = finishGate.await(180, TimeUnit.SECONDS);
        assertTrue(finished, "All 10 streams should complete within 180 seconds");

        System.out.println("\n📊 --- CONCURRENCY EXECUTION REPORT (10 USERS) ---");
        System.out.println("Total dispatches: " + concurrency);
        System.out.println("Successful streams: " + successCount.get());
        System.out.println("Durations: " + durations);

        assertTrue(successCount.get() >= 8, "At least 8 of 10 concurrent requests must succeed (allowing for API rate limits)");
    }
}
