package com.patchme.post.dto;

import com.patchme.common.enums.IdentityMode;
import com.patchme.common.enums.Intent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 发帖请求。identity 可省略（省略则用账号默认身份）；
 * school/major/tag 只在这里进来，响应 VO 不会回显 school/major。
 */
public record CreatePostRequest(
        @NotNull(message = "请选择意图") Intent intent,
        @Size(max = 60, message = "标题最多 60 字") String title,
        @NotBlank(message = "正文不能为空") @Size(max = 5000, message = "正文最多 5000 字") String body,
        Long schoolId,
        Long majorId,
        @Size(max = 3, message = "标签最多选 3 个") List<Long> tagIds,
        IdentityMode identity
) {
}
