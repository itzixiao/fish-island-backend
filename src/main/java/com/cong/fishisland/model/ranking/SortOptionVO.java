package com.cong.fishisland.model.ranking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 排序维度选项 VO
 *
 * @author cong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SortOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String code;

    private String label;

    private String extKey;
}
