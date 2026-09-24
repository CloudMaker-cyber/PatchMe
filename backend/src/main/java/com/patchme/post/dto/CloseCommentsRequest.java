package com.patchme.post.dto;

import jakarta.validation.constraints.NotNull;

/** 楼主开关评论。 */
public record CloseCommentsRequest(@NotNull(message = "请指明开关") Boolean closed) {
}
