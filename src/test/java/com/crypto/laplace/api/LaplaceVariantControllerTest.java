package com.crypto.laplace.api;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crypto.laplace.service.LaplaceRuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LaplaceVariantControllerTest {
 @Test void invertedFalseEndpointsAreNotRegistered() throws Exception {var mvc=MockMvcBuilders.standaloneSetup(new LaplaceVariantController(mock(LaplaceVariantApiService.class),mock(LaplaceRuntimeService.class))).build();mvc.perform(get("/api/paper/inverted-false/positions/open")).andExpect(status().isNotFound());mvc.perform(get("/api/paper/inverted-false/summary")).andExpect(status().isNotFound());}
}
