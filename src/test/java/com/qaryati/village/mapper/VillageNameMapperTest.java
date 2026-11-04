package com.qaryati.village.mapper;
import com.qaryati.village.dto.*;
import com.qaryati.village.entity.*;
import org.hibernate.proxy.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VillageNameMapperTest {
    private final VillageNameMapper mapper = new VillageNameMapper();
    @Test void roundTripAndReplacementPreserveIdentityAndParent() {
        var request = new VillageNameRequest(" Name ", " EN ", VillageNameType.HISTORICAL, "Note");
        var entity = mapper.toEntity(request);
        var village = new Village(); village.setId(3L);
        entity.setVillage(village); entity.setId(4L);
        assertEquals(new VillageNameResponse(4L, 3L, " Name ", " EN ", VillageNameType.HISTORICAL, "Note"), mapper.toResponse(entity));
        mapper.updateEntity(new VillageNameRequest("Other", "ar", VillageNameType.FORMER, null), entity);
        assertEquals(4L, entity.getId()); assertSame(village, entity.getVillage()); assertNull(entity.getNotes());
        assertEquals("Other", entity.getName()); assertEquals("ar", entity.getLanguageCode()); assertEquals(VillageNameType.FORMER, entity.getNameType());
    }
    @Test void readsProxyIdentifierWithoutInitialization() {
        Village parent = mock(Village.class, withSettings().extraInterfaces(HibernateProxy.class));
        var initializer = mock(LazyInitializer.class);
        when(((HibernateProxy) parent).getHibernateLazyInitializer()).thenReturn(initializer);
        when(initializer.getInternalIdentifier()).thenReturn(7L);
        var entity = mapper.toEntity(new VillageNameRequest("N", "ar", VillageNameType.ALTERNATIVE, null)); entity.setVillage(parent);
        assertEquals(7L, mapper.toResponse(entity).villageId());
        verify(initializer).getInternalIdentifier(); verifyNoMoreInteractions(initializer);
        verify(parent, never()).getId();
    }
}