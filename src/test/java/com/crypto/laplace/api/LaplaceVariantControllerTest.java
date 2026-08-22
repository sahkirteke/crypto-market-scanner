package com.crypto.laplace.api;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crypto.laplace.service.LaplaceRuntimeService;
import com.crypto.laplace.service.LaplaceProfitLockService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LaplaceVariantControllerTest {
 @Test void invertedFalseEndpointsAreNotRegistered() throws Exception {var mvc=MockMvcBuilders.standaloneSetup(new LaplaceVariantController(mock(LaplaceVariantApiService.class),mock(LaplaceRuntimeService.class),mock(LaplaceProfitLockService.class))).build();mvc.perform(get("/api/paper/inverted-false/positions/open")).andExpect(status().isNotFound());mvc.perform(get("/api/paper/inverted-false/summary")).andExpect(status().isNotFound());}
 @Test void manualCloseEndpointDelegatesToProfitLockLiquidationPath() throws Exception {var profitLock=mock(LaplaceProfitLockService.class);var mvc=MockMvcBuilders.standaloneSetup(new LaplaceVariantController(mock(LaplaceVariantApiService.class),mock(LaplaceRuntimeService.class),profitLock)).build();mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/paper/inverted-true/session/manual-close")).andExpect(status().isOk());verify(profitLock).closeCurrentSession();}
}
