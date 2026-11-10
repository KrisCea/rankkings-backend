package cl.rankkings.catalog_service;

import cl.rankkings.catalog_service.repository.ItemRepository;
import cl.rankkings.catalog_service.service.CatalogService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CatalogServiceApplicationTests {

	@Autowired
	private ItemRepository itemRepository;

	@Autowired
	private CatalogService catalogService;

	@Test
	void contextLoads() {
	}

	@Test
	void loadsExampleCatalogAndParentRelationships() {
		Assertions.assertEquals(58, itemRepository.count());
		Assertions.assertEquals(4, itemRepository.findByParentIdOrderByPositionAsc("item-1").size());
		Assertions.assertEquals("item-101", itemRepository.findById("item-101").orElseThrow().getId());
		var seededItem = itemRepository.findById("item-401").orElseThrow();
		Assertions.assertEquals("catalog-seed", seededItem.getUploadedBy());
		Assertions.assertEquals("", seededItem.getPostId());
	}

	@Test
	void searchesExampleItemsByTitleWithoutCaseSensitivity() {
		Assertions.assertEquals(
				"item-401",
				catalogService.searchItems("hOlLoW", null).getFirst().id()
		);
	}

}
