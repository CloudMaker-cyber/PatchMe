package com.patchme.post;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patchme.common.enums.IdentityMode;
import com.patchme.common.enums.Intent;
import com.patchme.common.vo.AuthorView;
import com.patchme.post.dto.CreatePostRequest;
import com.patchme.post.vo.PostDetailVO;
import com.patchme.post.vo.PublicPostVO;
import com.patchme.reply.ReplyService;
import com.patchme.support.MvcAuth;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PostController 切片：参数绑定、校验文案、以及最重要的验收——
 * 匿名帖的 JSON 输出只有 {"mode":"anonymous"}，且整个响应无 authorId/schoolId/majorId。
 */
@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private ReplyService replyService;

    @AfterEach
    void clearAuth() {
        MvcAuth.clear();
    }

    private static PublicPostVO anonymousPost() {
        return new PublicPostVO(1L, AuthorView.anonymous(), "ADVICE", "标题", "正文",
                List.of(1L, 2L), LocalDateTime.now(), false, 0L, 3L);
    }

    @Test
    void feedBindsFiltersAndResolvedBucket() throws Exception {
        when(postService.feed(eq(3L), eq(5L), eq("ADVICE"), eq(List.of(1L, 2L)), eq(true), eq(100)))
                .thenReturn(List.of(anonymousPost()));

        mockMvc.perform(get("/api/posts").param("school", "3").param("major", "5")
                        .param("intent", "ADVICE").param("tags", "1,2").param("resolved", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].author.mode").value("anonymous"))
                .andExpect(jsonPath("$.data[0].author.username").doesNotExist())
                .andExpect(jsonPath("$.data[0].author.nickname").doesNotExist());
    }

    @Test
    void unknownIntentEnumIsRejectedAsBadRequest() throws Exception {
        mockMvc.perform(get("/api/posts").param("intent", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("40000"));
    }

    @Test
    void detailHasNoIdentityLeaksAnywhereInJson() throws Exception {
        PostDetailVO detail = new PostDetailVO(anonymousPost(), List.of(), false, false, false);
        when(postService.detail(eq(1L), any())).thenReturn(detail);

        mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isOk())
                // 全树扫描：任何形式的作者内部标识都不允许出现
                .andExpect(jsonPath("$..authorId").doesNotExist())
                .andExpect(jsonPath("$..schoolId").doesNotExist())
                .andExpect(jsonPath("$..majorId").doesNotExist())
                .andExpect(jsonPath("$..email").doesNotExist())
                // 匿名帖的 author 只可能是 {"mode":"anonymous"}
                .andExpect(jsonPath("$.data.post.author.mode").value("anonymous"))
                .andExpect(jsonPath("$.data.post.author.username").doesNotExist())
                .andExpect(jsonPath("$.data.post.author.nickname").doesNotExist())
                .andExpect(jsonPath("$.data.post.author.avatarUrl").doesNotExist());
    }

    @Test
    void createRequiresIntentAndBody() throws Exception {
        mockMvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("40000"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("意图")));
    }

    @Test
    void createReturnsPostId() throws Exception {
        when(postService.create(eq(7L), any())).thenReturn(42L);
        CreatePostRequest request = new CreatePostRequest(Intent.VENT, "t", "b", null, null, List.of(), IdentityMode.ANONYMOUS);

        mockMvc.perform(post("/api/posts").with(MvcAuth.user(7L)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(42));
        verify(postService).create(eq(7L), any());
    }

    @Test
    void missingPostReturnsUnifiedNotFound() throws Exception {
        when(postService.detail(eq(404L), any()))
                .thenThrow(new com.patchme.common.exception.BusinessException(
                        com.patchme.common.api.ErrorCode.NOT_FOUND, "内容不存在或已删除"));
        mockMvc.perform(get("/api/posts/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("40400"));
    }
}
