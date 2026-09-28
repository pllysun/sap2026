package com.sap.jw.service;

import com.sap.jw.client.JwHttpSession;
import com.sap.jw.config.JwProperties;
import com.sap.jw.parser.ScheduleParser;
import com.sap.jw.vo.ScheduleVO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwScheduleServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void retriesAlternateRouteWhenPrimaryReturnsEmptyScheduleShell() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        AtomicInteger aliasRequests = new AtomicInteger();
        server.createContext("/jsxsd/framework/xsMainV.htmlx", exchange -> send(exchange, "xsMainV"));
        server.createContext("/jsxsd/xskb/xskb_list.do", exchange -> send(exchange, EMPTY_QZ_TABLE));
        server.createContext("/jsxsd/kbcx/kbxxMain", exchange -> {
            aliasRequests.incrementAndGet();
            send(exchange, COURSE_QZ_TABLE);
        });
        server.start();

        JwHttpSession session = new JwHttpSession(5);
        session.configureRoute(new JwHttpSession.Route(
                false, base, base + "/cas/login", base, base, base, "", base, base, base, base));
        JwSessionManager sessions = new JwSessionManager(null, null, null, null) {
            @Override
            public JwHttpSession getSession(Long userId, String account) {
                return session;
            }
        };
        JwCredentialService credentials = new JwCredentialService(null, null) {
            @Override
            public void markSynced(Long userId, String account) {
                // no persistence in this focused service test
            }
        };
        JwCalendarService calendar = new JwCalendarService(null, null, null) {
            @Override
            public String getSemesterStart(Long userId, String account, String term) {
                return null;
            }
        };

        JwScheduleService service = new JwScheduleService(
                sessions, credentials, new ScheduleParser(), calendar, new JwProperties());
        ScheduleVO result = service.getSchedule(7L, "20260001", "2026-2027-1");

        assertEquals("2026-2027-1", result.getTerm());
        assertEquals(1, result.getCourses().size());
        assertEquals("最新学期课程", result.getCourses().get(0).getName());
        assertEquals(1, aliasRequests.get());
    }

    private static void send(HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static final String EMPTY_QZ_TABLE = """
            <table class="qz-weeklyTable"><tr><th>周次</th><th>星期一</th><th>星期二</th><th>星期三</th>
            <th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>
            <tr><td class="qz-weeklyTable-label"><div class="index-title">第1，2节</div></td>
            <td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr></table>
            """;

    private static final String COURSE_QZ_TABLE = """
            <select name="xnxq01id"><option value="2026-2027-1" selected>2026-2027-1</option></select>
            <table class="qz-weeklyTable"><tr><th>周次</th><th>星期一</th><th>星期二</th><th>星期三</th>
            <th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>
            <tr><td class="qz-weeklyTable-label"><div class="index-title">第1，2节</div></td>
            <td class="qz-hasCourse"><ul><li class="courselists-item">
            <div class="qz-hasCourse-title">最新学期课程</div>
            <p class="qz-hasCourse-detaillists">老师:王老师;时间:1-16周[1-2节];地点:北教A101</p>
            </li></ul></td><td></td><td></td><td></td><td></td><td></td><td></td></tr></table>
            """;
}
