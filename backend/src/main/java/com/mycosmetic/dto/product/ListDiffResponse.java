package com.mycosmetic.dto.product;

import java.util.List;

/**
 * 두 성분 버전의 차이.
 *
 * @param added        to에만 있는 성분 이름(to의 순서)
 * @param removed      from에만 있는 성분 이름(from의 순서)
 * @param orderChanged 두 버전에 모두 있는 성분의 순서가 다르면 true
 */
public record ListDiffResponse(List<String> added, List<String> removed, boolean orderChanged) {
}
