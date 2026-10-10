package dev.onesnzeroes.vendor.endpoint.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageResponseTest {

    @Test
    void ofListCalculatesTheNumberOfPages() {
        PageResponse<String> page = PageResponse.of(List.of("a", "b", "c"), 0, 3, 10);

        assertEquals(List.of("a", "b", "c"), page.getContent());
        assertEquals(0, page.getPage());
        assertEquals(3, page.getSize());
        assertEquals(10, page.getTotalElements());
        assertEquals(4, page.getTotalPages());
    }

    @Test
    void ofListWithExactlyFullPages() {
        assertEquals(2, PageResponse.of(List.of(), 0, 5, 10).getTotalPages());
    }

    @Test
    void ofListWithSizeZeroHasNoPages() {
        assertEquals(0, PageResponse.of(List.of(), 0, 0, 10).getTotalPages());
    }

    @Test
    void ofPageCopiesSpringDataPage() {
        PageResponse<String> page = PageResponse.of(new PageImpl<>(List.of("x", "y"), PageRequest.of(2, 2), 7));

        assertEquals(List.of("x", "y"), page.getContent());
        assertEquals(2, page.getPage());
        assertEquals(2, page.getSize());
        assertEquals(7, page.getTotalElements());
        assertEquals(4, page.getTotalPages());
    }

    @Test
    void sizeIsClampedBetweenOneAndOneHundred() {
        assertEquals(1, PageResponse.size(0));
        assertEquals(1, PageResponse.size(-10));
        assertEquals(20, PageResponse.size(20));
        assertEquals(100, PageResponse.size(100));
        assertEquals(100, PageResponse.size(101));
    }

    @Test
    void pageIsNeverNegative() {
        assertEquals(0, PageResponse.page(-1));
        assertEquals(0, PageResponse.page(0));
        assertEquals(3, PageResponse.page(3));
    }

    @Test
    void settersAndConstructorsWorkLikeAPlainObject() {
        PageResponse<Integer> page = new PageResponse<>();
        page.setContent(List.of(1));
        page.setPage(1);
        page.setSize(1);
        page.setTotalElements(2);
        page.setTotalPages(2);

        PageResponse<Integer> same = new PageResponse<>(List.of(1), 1, 1, 2, 2);

        assertEquals(same.getContent(), page.getContent());
        assertEquals(same.getPage(), page.getPage());
        assertEquals(same.getSize(), page.getSize());
        assertEquals(same.getTotalElements(), page.getTotalElements());
        assertEquals(same.getTotalPages(), page.getTotalPages());
    }

    @Test
    void deleteResponseHoldsMessageAndId() {
        DeleteResponse response = new DeleteResponse("Order deleted successfully", 5);

        assertEquals("Order deleted successfully", response.getMessage());
        assertEquals(5, response.getId());

        response.setMessage("changed");
        response.setId(6);
        assertEquals("changed", response.getMessage());
        assertEquals(6, response.getId());
    }
}
