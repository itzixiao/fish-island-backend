package com.cong.fishisland.model.vo.auth;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * OAuth2 用户信息响应
 *
 * @author cong
 */
@Data
@ApiModel(value = "OAuth2UserInfoVO", description = "OAuth2 用户信息")
public class OAuth2UserInfoVO implements Serializable {

    @ApiModelProperty(value = "用户 ID")
    private String id;

    @ApiModelProperty(value = "用户名")
    private String username;

    @ApiModelProperty(value = "用户昵称")
    private String name;

    @ApiModelProperty(value = "用户头像")
    private String avatar;

    @ApiModelProperty(value = "是否为永久会员")
    private Boolean isPermanentVip;

    @ApiModelProperty(value = "累计赞助金额（元）")
    private BigDecimal donationAmount;


    private static final long serialVersionUID = 1L;
}
