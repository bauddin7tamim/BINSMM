package com.example

import com.example.data.model.ServiceCategory
import com.example.data.model.SmmOrder
import com.example.data.model.SmmService
import com.example.data.repository.BinsmmRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun defaultTemplateServices_coversAllFourCategoriesWithoutDuplicates() {
        val templates = BinsmmRepository.defaultTemplateServices()
        assertEquals(12, templates.size)
        assertEquals(templates.size, templates.map { it.id }.toSet().size)

        ServiceCategory.entries.forEach { category ->
            val inCategory = templates.filter { it.category == category.key }
            assertTrue("Expected at least 3 template services in ${category.displayName}", inCategory.size >= 3)
            inCategory.forEach { svc ->
                assertTrue(svc.name.isNotBlank())
                assertTrue(svc.description.contains("DEMO", ignoreCase = true))
                assertTrue(svc.ratePer1000 > 0.0)
                assertTrue(svc.minQuantity >= 1L)
                assertTrue(svc.maxQuantity >= svc.minQuantity)
                assertTrue(svc.isDemoService)
            }
        }
    }

    @Test
    fun smmService_isDemoService_onlyFalseWhenProviderLinkedWithValidProviderId() {
        val demoService = SmmService(
            id = "svc_1",
            name = "Demo",
            providerId = "unconfigured",
            integrationStatus = "template_unconnected"
        )
        assertTrue(demoService.isDemoService)

        val linkedMissingProvider = demoService.copy(
            providerId = "unconfigured",
            integrationStatus = "provider_linked"
        )
        assertTrue(linkedMissingProvider.isDemoService)

        val liveService = demoService.copy(
            providerId = "prv_live_1",
            integrationStatus = "provider_linked"
        )
        assertFalse(liveService.isDemoService)
    }

    @Test
    fun smmOrder_isEligibleForCancellation_onlyForNonTerminalStatuses() {
        assertTrue(SmmOrder(id = "o1", status = "pending").isEligibleForCancellation)
        assertTrue(SmmOrder(id = "o2", status = "processing").isEligibleForCancellation)
        assertTrue(SmmOrder(id = "o3", status = "awaiting_provider_config").isEligibleForCancellation)
        assertFalse(SmmOrder(id = "o4", status = "completed").isEligibleForCancellation)
        assertFalse(SmmOrder(id = "o5", status = "cancelled").isEligibleForCancellation)
    }
}
