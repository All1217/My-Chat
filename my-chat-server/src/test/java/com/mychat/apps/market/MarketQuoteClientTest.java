package com.mychat.apps.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.apps.market.entity.vo.KlinePointVO;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 东方财富 K 线解析与 Mock HTTP。 */
class MarketQuoteClientTest {

    @Test
    void parseEastMoneyBar() {
        KlinePointVO bar = MarketQuoteClient.parseEastMoneyBar("2024-01-02,1680,1690,1700,1670,12345,1,1,1,10,1");
        assertEquals("2024-01-02", bar.getDate());
        assertEquals(1680.0, bar.getOpen());
        assertEquals(1690.0, bar.getClose());
        assertEquals(1700.0, bar.getHigh());
        assertEquals(1670.0, bar.getLow());
        assertEquals(12345L, bar.getVolume());
    }

    @Test
    void fetchCnParsesNameAndCloses() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        StringBuilder klines = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            if (i > 1) {
                klines.append(',');
            }
            String day = String.format("2099-01-%02d", i);
            klines.append('"').append(day).append(",1800,181").append(i)
                    .append(",1820,1790,100\"");
        }
        server.expect(requestTo(org.hamcrest.Matchers.containsString("secid=1.600519")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"data\":{\"code\":\"600519\",\"name\":\"贵州茅台\",\"klines\":[" + klines + "]}}",
                        MediaType.APPLICATION_JSON));
        MarketQuoteClient client = new MarketQuoteClient(builder.build(), new ObjectMapper());

        MarketQuoteVO quote = client.fetch("600519", "3M");

        assertEquals("贵州茅台", quote.getName());
        assertEquals("600519", quote.getSymbol());
        assertTrue(quote.getHistory().size() >= 8);
        assertEquals("2099-01-10", quote.getHistory().get(quote.getHistory().size() - 1).getDate());
        server.verify();
    }

    @Test
    void fetchCnAllKeepsOldBarsWhile1YCutsThem() {
        String klines = oldAndRecentKlines();
        RestClient.Builder allBuilder = RestClient.builder();
        MockRestServiceServer allServer = MockRestServiceServer.bindTo(allBuilder).build();
        allServer.expect(requestTo(org.hamcrest.Matchers.containsString("lmt=1500")))
                .andExpect(requestTo(org.hamcrest.Matchers.containsString("klt=102")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"data\":{\"code\":\"600519\",\"name\":\"贵州茅台\",\"klines\":[" + klines + "]}}",
                        MediaType.APPLICATION_JSON));
        MarketQuoteClient allClient = new MarketQuoteClient(allBuilder.build(), new ObjectMapper());
        MarketQuoteVO allQuote = allClient.fetch("600519", "ALL");
        assertTrue(allQuote.getHistory().stream().anyMatch(p -> "2018-01-03".equals(p.getDate())));
        allServer.verify();

        RestClient.Builder yBuilder = RestClient.builder();
        MockRestServiceServer yServer = MockRestServiceServer.bindTo(yBuilder).build();
        yServer.expect(requestTo(org.hamcrest.Matchers.containsString("lmt=280")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"data\":{\"code\":\"600519\",\"name\":\"贵州茅台\",\"klines\":[" + klines + "]}}",
                        MediaType.APPLICATION_JSON));
        MarketQuoteClient yClient = new MarketQuoteClient(yBuilder.build(), new ObjectMapper());
        MarketQuoteVO yQuote = yClient.fetch("600519", "1Y");
        assertTrue(yQuote.getHistory().stream().noneMatch(p -> "2018-01-03".equals(p.getDate())));
        yServer.verify();
    }

    @Test
    void fetchUs3YUsesEastMoneyNasdaqThenCuts() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String klines = oldAndRecentKlines();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("secid=100.AAPL")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":null}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("secid=105.AAPL")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"data\":{\"code\":\"AAPL\",\"name\":\"苹果\",\"klines\":[" + klines + "]}}",
                        MediaType.APPLICATION_JSON));
        MarketQuoteClient client = new MarketQuoteClient(builder.build(), new ObjectMapper());

        MarketQuoteVO quote = client.fetch("AAPL", "3Y");

        assertEquals("苹果", quote.getName());
        assertEquals("US", quote.getMarket());
        assertTrue(quote.getHistory().stream().noneMatch(p -> "2018-01-03".equals(p.getDate())));
        assertTrue(quote.getHistory().size() >= 8);
        server.verify();
    }

    /** 一根远早于一年 + 十根远期，供截断对比。 */
    private static String oldAndRecentKlines() {
        StringBuilder klines = new StringBuilder("\"2018-01-03,10,11,12,9,100\"");
        for (int i = 1; i <= 10; i++) {
            klines.append(",\"").append(String.format("2099-01-%02d", i))
                    .append(",1800,1810,1820,1790,100\"");
        }
        return klines.toString();
    }
}
