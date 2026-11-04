package com.qaryati.village.mapper;
import com.qaryati.village.dto.*;
import com.qaryati.village.entity.*;
import org.hibernate.proxy.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VillageRelationMapperTest {
    final VillageRelationMapper mapper = new VillageRelationMapper();
    @Test void replacementPreservesIdentityAndSourceAndClearsDistance() {
        var source = new Village(); source.setId(1L);
        var entity = mapper.toEntity(new VillageRelationRequest(null," Place ",VillageRelationType.NEARBY,new BigDecimal("1.25")));
        entity.setId(3L); entity.setVillage(source);
        assertEquals(" Place ",mapper.toResponse(entity).relatedPlaceName());
        mapper.updateEntity(new VillageRelationRequest(2L,null,VillageRelationType.OTHER,null),entity);
        assertNull(entity.getRelatedPlaceName()); assertNull(entity.getDistanceKm());
        assertEquals(3L,entity.getId()); assertSame(source,entity.getVillage());
    }
    @Test void readsProxyIdsWithoutInitialization() {
        var source = mock(Village.class,withSettings().extraInterfaces(HibernateProxy.class));
        var target = mock(Village.class,withSettings().extraInterfaces(HibernateProxy.class));
        var sourceLazy = mock(LazyInitializer.class); var targetLazy = mock(LazyInitializer.class);
        when(((HibernateProxy)source).getHibernateLazyInitializer()).thenReturn(sourceLazy);
        when(((HibernateProxy)target).getHibernateLazyInitializer()).thenReturn(targetLazy);
        when(sourceLazy.getInternalIdentifier()).thenReturn(1L); when(targetLazy.getInternalIdentifier()).thenReturn(2L);
        var entity = new VillageRelation(); entity.setVillage(source); entity.setRelatedVillage(target);
        assertEquals(1L,mapper.toResponse(entity).villageId()); assertEquals(2L,mapper.toResponse(entity).relatedVillageId());
        verify(sourceLazy,never()).initialize(); verify(targetLazy,never()).initialize();
        verify(source,never()).getId(); verify(target,never()).getId();
    }
}
