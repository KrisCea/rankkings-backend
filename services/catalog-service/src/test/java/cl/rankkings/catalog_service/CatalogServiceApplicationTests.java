package cl.rankkings.catalog_service;

import cl.rankkings.catalog_service.dto.CatalogDtos.CreateRankableItemInput;
import cl.rankkings.catalog_service.dto.CatalogDtos.CreatorInput;
import cl.rankkings.catalog_service.repository.ItemRepository;
import cl.rankkings.catalog_service.service.CatalogService;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.UUID;

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

	@Test
	@Transactional
	void createsRankableItemsWithGeneratedInternalIdsAndOptionalLinks() {
		var item = catalogService.createItem(new CreateRankableItemInput(
				"books",
				"  The Example Book  ",
				null,
				new CreatorInput(null, "Author", null, null, null),
				null,
				null,
				null,
				Map.of("format", "novel"),
				null,
				"user-1",
				"post-1"
		));

		UUID.fromString(item.id());
		Assertions.assertEquals("The Example Book", item.title());
		Assertions.assertEquals("post-1", item.postId());
		Assertions.assertEquals("", item.coverUrl());
		Assertions.assertEquals(Map.of(), item.identifiers());
		Assertions.assertEquals(Map.of("format", "novel"), item.details());
		Assertions.assertEquals(java.util.List.of(), item.externalLinks());
		Assertions.assertEquals("unverified", item.verificationStatus());
		Assertions.assertEquals("approved", itemRepository.findById(item.id()).orElseThrow().getModerationStatus());
	}

	@Test
	void rejectsMissingCategorySpecificDetails() {
		var input = new CreateRankableItemInput(
				"concerts",
				"Live show",
				null,
				new CreatorInput(null, "Artist", null, null, null),
				null,
				null,
				null,
				Map.of("venue", "Arena", "city", "Santiago"),
				null,
				"user-1",
				"post-1"
		);

		Assertions.assertThrows(
				org.springframework.web.server.ResponseStatusException.class,
				() -> catalogService.createItem(input)
		);
	}

	@Test
	@Transactional
	void createsRankableItemsWhenOptionalDetailsAreOmitted() {
		var item = catalogService.createItem(new CreateRankableItemInput(
				"music",
				"Track",
				null,
				new CreatorInput(null, "Artist", null, null, null),
				null,
				null,
				Map.of(),
				null,
				null,
				"user-1",
				"post-2"
		));

		Assertions.assertEquals(Map.of(), item.details());
	}

	@Test
	@Transactional
	void persistsLongCoverUrls() {
		String coverUrl = "https://example.com/" + "a".repeat(500);
		var item = catalogService.createItem(new CreateRankableItemInput(
				"music",
				"Track with long cover URL",
				null,
				new CreatorInput(null, "Artist", null, null, null),
				null,
				coverUrl,
				Map.of(),
				Map.of(),
				null,
				"user-1",
				"post-long-cover-url"
		));

		Assertions.assertEquals(coverUrl, itemRepository.findById(item.id()).orElseThrow().getCoverUrl());
	}

	@Test
	@Transactional
	void removesAnUnpublishedItemByItsPostId() {
		var item = catalogService.createItem(new CreateRankableItemInput(
				"music",
				"Track",
				null,
				new CreatorInput(null, "Artist", null, null, null),
				null,
				null,
				Map.of(),
				Map.of(),
				null,
				"user-1",
				"post-to-compensate"
		));

		catalogService.deleteCreatedItemByPostId("post-to-compensate");

		Assertions.assertFalse(itemRepository.existsById(item.id()));
	}

}
