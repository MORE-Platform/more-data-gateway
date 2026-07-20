package io.redlink.more.data.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.redlink.more.data.api.app.v1.model.DeregistrationItemDTO;
import io.redlink.more.data.api.app.v1.model.DeregistrationRequestDTO;
import io.redlink.more.data.api.app.v1.model.UpdatePermissionsRequestDTO;
import io.redlink.more.data.api.app.v1.model.UserPermissionChangeDTO;
import io.redlink.more.data.service.garmin.GarminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GarminApiV1Controller.class)
@AutoConfigureMockMvc(addFilters = false)
class GarminApiV1ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GarminService garminService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String USER_AGENT = "Garmin-User-Agent";
    private static final String CLIENT_ID = "garmin-client-id";

    @BeforeEach
    void setUp() {
        // Reset the mock if necessary
    }

    @Test
    void deleteGarminUser_HappyPath() throws Exception {
        when(garminService.garminRequestIsValid(USER_AGENT, CLIENT_ID)).thenReturn(true);

        DeregistrationRequestDTO request = new DeregistrationRequestDTO()
                .addDeregistrationsItem(new DeregistrationItemDTO("user1"));

        mockMvc.perform(post("/api/v1/integrations/garmin/user")
                        .with(csrf())
                        .header("User-Agent", USER_AGENT)
                        .header("Garmin-Client-Id", CLIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(garminService).deleteUserIdAndToken("user1");
    }

    @Test
    void deleteGarminUser_InvalidRequest() throws Exception {
        when(garminService.garminRequestIsValid(USER_AGENT, CLIENT_ID)).thenReturn(false);

        DeregistrationRequestDTO request = new DeregistrationRequestDTO()
                .addDeregistrationsItem(new DeregistrationItemDTO("user1"));

        mockMvc.perform(post("/api/v1/integrations/garmin/user")
                        .header("User-Agent", USER_AGENT)
                        .header("Garmin-Client-Id", CLIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(garminService, never()).deleteUserIdAndToken(anyString());
    }

    @Test
    void updateGarminUserPermissions_HappyPath() throws Exception {
        when(garminService.garminRequestIsValid(USER_AGENT, CLIENT_ID)).thenReturn(true);

        UpdatePermissionsRequestDTO request = new UpdatePermissionsRequestDTO()
                .addUserPermissionsChangeItem(new UserPermissionChangeDTO()
                        .userId("user1")
                        .permissions(List.of("DAILY_STEPS", "BLOOD_PRESSURE")));

        mockMvc.perform(post("/api/v1/integrations/garmin/user/permissions")
                        .header("User-Agent", USER_AGENT)
                        .header("Garmin-Client-Id", CLIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(garminService).updateUserPermissions("user1", List.of("DAILY_STEPS", "BLOOD_PRESSURE"));
    }

    @Test
    void updateGarminUserPermissions_InvalidRequest() throws Exception {
        when(garminService.garminRequestIsValid(USER_AGENT, CLIENT_ID)).thenReturn(false);

        UpdatePermissionsRequestDTO request = new UpdatePermissionsRequestDTO()
                .addUserPermissionsChangeItem(new UserPermissionChangeDTO().userId("user1"));

        mockMvc.perform(post("/api/v1/integrations/garmin/user/permissions")
                        .header("User-Agent", USER_AGENT)
                        .header("Garmin-Client-Id", CLIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(garminService, never()).updateUserPermissions(anyString(), anyList());
    }

    @Test
    void submitSummaries_HappyPath() throws Exception {
        when(garminService.garminRequestIsValid(USER_AGENT, CLIENT_ID)).thenReturn(true);

        Map<String, List<Map<String, Object>>> requestBody = Map.of(
                "dailies", List.of(Map.of("userId", "user1", "startTimeInSeconds", 123456789))
        );

        mockMvc.perform(post("/api/v1/integrations/garmin/summaries")
                        .header("User-Agent", USER_AGENT)
                        .header("Garmin-Client-Id", CLIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk());

        verify(garminService).storeData(anyMap());
    }

    @Test
    void submitSummaries_InvalidRequest() throws Exception {
        when(garminService.garminRequestIsValid(USER_AGENT, CLIENT_ID)).thenReturn(false);

        Map<String, List<Map<String, Object>>> requestBody = Map.of(
                "dailies", List.of(Map.of("userId", "user1"))
        );

        mockMvc.perform(post("/api/v1/integrations/garmin/summaries")
                        .header("User-Agent", USER_AGENT)
                        .header("Garmin-Client-Id", CLIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk());

        verify(garminService, never()).storeData(anyMap());
    }

    @Test
    void submitSummaries_ThrowsClassCastException() throws Exception {
        when(garminService.garminRequestIsValid(USER_AGENT, CLIENT_ID)).thenReturn(true);

        doThrow(new ClassCastException("Forced")).when(garminService).storeData(anyMap());

        Map<String, List<Map<String, Object>>> requestBody = Map.of(
                "dailies", List.of(Map.of("userId", "user1"))
        );

        mockMvc.perform(post("/api/v1/integrations/garmin/summaries")
                        .header("User-Agent", USER_AGENT)
                        .header("Garmin-Client-Id", CLIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void submitSummaries_ThrowsIOException() throws Exception {
        when(garminService.garminRequestIsValid(USER_AGENT, CLIENT_ID)).thenReturn(true);

        doThrow(new IOException("Forced")).when(garminService).storeData(anyMap());

        Map<String, List<Map<String, Object>>> requestBody = Map.of(
                "dailies", List.of(Map.of("userId", "user1"))
        );

        mockMvc.perform(post("/api/v1/integrations/garmin/summaries")
                        .header("User-Agent", USER_AGENT)
                        .header("Garmin-Client-Id", CLIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isInternalServerError());
    }
}