package com.anantghadi.pricing.api;

import com.anantghadi.pricing.config.AppConfig;
import com.anantghadi.pricing.discount.DiscountRepository;
import com.anantghadi.pricing.engine.PricingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PricingApiIntegrationTest {

    private static final String AMAZON_QUOTE = """
            {"sku":"SKU-1","baseCost":100,"quantity":1,"weightKg":0.5,"zone":"LOCAL","marketplace":"AMAZON"}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DiscountRepository discountRepository;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private PricingService pricingService;

    @BeforeEach
    void cleanState() {
        discountRepository.deleteAll();
        cacheManager.getCache(AppConfig.QUOTES_CACHE).clear();
    }

    @Test
    void quoteReturnsBreakdownAndFinalPrice() throws Exception {
        quote(AMAZON_QUOTE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.breakdown.length()").value(6))
                .andExpect(jsonPath("$.breakdown[0].rule").value("BASE_COST_MARGIN"))
                .andExpect(jsonPath("$.breakdown[5].rule").value("GST"))
                .andExpect(jsonPath("$.unitPrice").value(256.83))
                .andExpect(jsonPath("$.totalPrice").value(256.83))
                .andExpect(jsonPath("$.floorApplied").value(false));
    }

    @Test
    void springOrdersTheRuleBeansIntoThePipeline() {
        assertThat(pricingService.ruleNames()).containsExactly(
                "BASE_COST_MARGIN", "DISCOUNT", "QUANTITY_TIER", "FREIGHT", "MARKETPLACE_FEE", "PRICE_FLOOR", "GST");
    }

    @Test
    void invalidQuoteReturnsProblemDetailWithFieldErrors() throws Exception {
        quote("""
                {"sku":"","baseCost":-5,"quantity":0,"weightKg":1,"zone":"LOCAL"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors.sku").exists())
                .andExpect(jsonPath("$.errors.baseCost").exists())
                .andExpect(jsonPath("$.errors.quantity").exists())
                .andExpect(jsonPath("$.errors.marketplace").exists());
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        quote("{not json").andExpect(status().isBadRequest());
    }

    @Test
    void quotesAreCachedAndDiscountWritesEvictTheCache() throws Exception {
        quote(AMAZON_QUOTE).andExpect(jsonPath("$.unitPrice").value(256.83));
        ConcurrentMapCache cache = (ConcurrentMapCache) cacheManager.getCache(AppConfig.QUOTES_CACHE);
        assertThat(cache.getNativeCache()).hasSize(1);

        String created = mockMvc.perform(post("/api/discounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Diwali","type":"PERCENTAGE","value":10,"sku":"SKU-1","marketplace":"AMAZON",
                                 "validFrom":"%s","validTo":"%s"}
                                """.formatted(LocalDate.now().minusDays(1), LocalDate.now().plusDays(7))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/discounts/" + discountRepository.findAll().get(0).getId())))
                .andReturn().getResponse().getContentAsString();
        assertThat(cache.getNativeCache()).isEmpty();

        quote(AMAZON_QUOTE)
                .andExpect(jsonPath("$.breakdown[2].rule").value("DISCOUNT"))
                .andExpect(jsonPath("$.breakdown[2].amount").value(-12.50))
                .andExpect(jsonPath("$.unitPrice").value(239.47));

        long id = objectMapper.readTree(created).get("id").asLong();
        mockMvc.perform(delete("/api/discounts/{id}", id)).andExpect(status().isNoContent());
        quote(AMAZON_QUOTE).andExpect(jsonPath("$.unitPrice").value(256.83));
    }

    @Test
    void expiredDiscountsAreIgnored() throws Exception {
        createDiscount("""
                {"name":"Last season","type":"FLAT","value":50,"validFrom":"2020-01-01","validTo":"2020-01-31"}
                """);

        quote(AMAZON_QUOTE).andExpect(jsonPath("$.unitPrice").value(256.83));
    }

    @Test
    void discountCrudLifecycle() throws Exception {
        long id = createDiscount("""
                {"name":"Launch","type":"FLAT","value":25}
                """).get("id").asLong();

        mockMvc.perform(get("/api/discounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Launch"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(put("/api/discounts/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Launch v2","type":"PERCENTAGE","value":5,"active":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PERCENTAGE"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/discounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete("/api/discounts/{id}", id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/discounts/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Discount " + id + " not found"));
    }

    @Test
    void discountValidationCoversCrossFieldRules() throws Exception {
        mockMvc.perform(post("/api/discounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Broken","type":"PERCENTAGE","value":150,
                                 "validFrom":"2026-12-31","validTo":"2026-12-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.validWindow").value("validTo must not be before validFrom"))
                .andExpect(jsonPath("$.errors.validPercentage").value("percentage discount must not exceed 100"));
    }

    @Test
    void batchQuotesKeepRequestOrder() throws Exception {
        mockMvc.perform(post("/api/prices/quote/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[
                                  {"sku":"A","baseCost":100,"quantity":1,"weightKg":0.5,"zone":"LOCAL","marketplace":"AMAZON"},
                                  {"sku":"B","baseCost":250,"quantity":12,"weightKg":1.5,"zone":"REGIONAL","marketplace":"FLIPKART"},
                                  {"sku":"C","baseCost":40,"quantity":120,"weightKg":0.2,"zone":"NATIONAL","marketplace":"OWN_STORE"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.quotes[0].sku").value("A"))
                .andExpect(jsonPath("$.quotes[0].unitPrice").value(256.83))
                .andExpect(jsonPath("$.quotes[1].sku").value("B"))
                .andExpect(jsonPath("$.quotes[2].sku").value("C"));
    }

    @Test
    void emptyBatchIsRejected() throws Exception {
        mockMvc.perform(post("/api/prices/quote/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.items").exists());
    }

    @Test
    void defaultProfileUsesInMemoryCache() {
        assertThat(cacheManager).isInstanceOf(ConcurrentMapCacheManager.class);
    }

    private ResultActions quote(String body) throws Exception {
        return mockMvc.perform(post("/api/prices/quote").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode createDiscount(String body) throws Exception {
        String response = mockMvc.perform(post("/api/discounts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }
}
