package com.grash.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.dto.pack.AssetPackDTO;
import com.grash.exception.CustomException;
import com.grash.model.CompanyAssetPack;
import com.grash.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetPackServiceTest {

    private static final String SHIPPED = "CNC_MACHINING_CENTER_VMC";

    @Spy
    ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    @Mock
    AssetRepository assetRepository;
    @Mock
    AssetSpecRepository assetSpecRepository;
    @Mock
    SpecKeyCatalogRepository specKeyCatalogRepository;
    @Mock
    MeterRepository meterRepository;
    @Mock
    PreventiveMaintenanceRepository preventiveMaintenanceRepository;
    @Mock
    MaintenanceIntervalRepository maintenanceIntervalRepository;
    @Mock
    FailureModeRepository failureModeRepository;
    @Mock
    AssetBomLineRepository assetBomLineRepository;
    @Mock
    PartRepository partRepository;
    @Mock
    ReadingRepository readingRepository;
    @Mock
    CompanyAssetPackRepository companyAssetPackRepository;
    @InjectMocks
    AssetPackService service;

    @BeforeEach
    void setUp() {
        service.loadPacks();
        assertTrue(service.isShipped(SHIPPED), "the shipped VMC pack should be on the classpath");
    }

    @Test
    void aShippedKeyCannotBeRegistered() {
        AssetPackDTO pack = new AssetPackDTO();
        pack.setKey(SHIPPED);

        CustomException e = assertThrows(CustomException.class, () -> service.register(pack, 2L));
        assertEquals(HttpStatus.CONFLICT, e.getHttpStatus());
        verify(companyAssetPackRepository, never()).save(any());
    }

    @Test
    void aRegisteredPackIsStoredAgainstTheRegisteringCompany() {
        AssetPackDTO pack = new AssetPackDTO();
        pack.setKey("ACME_PRESS");
        pack.setVersion("1");
        when(companyAssetPackRepository.findByCompanyIdAndPackKey(2L, "ACME_PRESS")).thenReturn(Optional.empty());

        service.register(pack, 2L);

        ArgumentCaptor<CompanyAssetPack> saved = ArgumentCaptor.forClass(CompanyAssetPack.class);
        verify(companyAssetPackRepository).save(saved.capture());
        assertEquals(2L, saved.getValue().getCompanyId());
        assertEquals("ACME_PRESS", saved.getValue().getPackKey());
        assertTrue(saved.getValue().getPackJson().contains("ACME_PRESS"));
    }

    @Test
    void aCompanyPackIsOnlyVisibleToItsCompany() throws Exception {
        AssetPackDTO pack = new AssetPackDTO();
        pack.setKey("ACME_PRESS");
        CompanyAssetPack stored = new CompanyAssetPack();
        stored.setCompanyId(2L);
        stored.setPackKey("ACME_PRESS");
        stored.setPackJson(objectMapper.writeValueAsString(pack));
        when(companyAssetPackRepository.findByCompanyIdAndPackKey(2L, "ACME_PRESS")).thenReturn(Optional.of(stored));
        when(companyAssetPackRepository.findByCompanyIdAndPackKey(1L, "ACME_PRESS")).thenReturn(Optional.empty());
        when(companyAssetPackRepository.findByCompanyIdOrderByPackKeyAsc(1L)).thenReturn(List.of());

        assertTrue(service.findByKey("ACME_PRESS", 2L).isPresent());
        assertTrue(service.findByKey("ACME_PRESS", 1L).isEmpty());
        assertTrue(service.findAll(1L).stream().noneMatch(p -> "ACME_PRESS".equals(p.getKey())));
        assertTrue(service.findAll(1L).stream().anyMatch(p -> SHIPPED.equals(p.getKey())));
    }

    @Test
    void anUnsafeKeyIsRejected() {
        AssetPackDTO pack = new AssetPackDTO();
        pack.setKey("../../etc");

        CustomException e = assertThrows(CustomException.class, () -> service.register(pack, 2L));
        assertEquals(HttpStatus.BAD_REQUEST, e.getHttpStatus());
    }
}
