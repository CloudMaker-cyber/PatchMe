package com.patchme.reply.dto;

import jakarta.validation.constraints.NotNull;

/** 楼主标记/取消"有帮助"。 */
public record HelpfulRequest(@NotNull(message = "请指明标记值") Boolean helpful) {
}
