package com.patchme.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.patchme.common.api.ErrorCode;
import com.patchme.common.exception.BusinessException;
import com.patchme.common.vo.AuthorView;
import com.patchme.post.vo.MinePostVO;
import com.patchme.post.vo.PublicPostVO;
import com.patchme.reply.vo.PublicReplyVO;
import com.patchme.support.MvcAuth;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 公开主页与"我的内容"切片：
 * 主页 JSON 不允许出现 identityMode/authorId（PUBLIC 筛选由 SQL + Service 保证，这里验证出口形状）。
 */
@WebMvcTest(UserContentController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @AfterEach
    void clearAuth() {
        MvcAuth.clear();
    }

    @Test
    void profilePageExposesOnlyPublicShapedContent() throws Exception {
        PublicPostVO post = new PublicPostVO(1L, AuthorView.publicAuthor("xiaoman", "小满", null),
                "VENT", "t", "b", List.of(), LocalDateTime.now(), false, 0L, 0L);
        PublicReplyVO reply = new PublicReplyVO(2L, 1L, AuthorView.publicAuthor("xiaoman", "小满", null),
                "r", LocalDateTime.now(), false);
        when(userService.profile("xiaoman")).thenReturn(new UserService.ProfilePageVO(
                new UserService.ProfileVO("xiaoman", "小满", null, "bio"), List.of(post), List.of(reply)));

        mockMvc.perform(get("/api/users/xiaoman"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.username").value("xiaoman"))
                .andExpect(jsonPath("$..authorId").doesNotExist())
                .andExpect(jsonPath("$..identityMode").doesNotExist())
                .andExpect(jsonPath("$..schoolId").doesNotExist())
                .andExpect(jsonPath("$..majorId").doesNotExist());
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        when(userService.profile(eq("ghost")))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        mockMvc.perform(get("/api/users/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("40400"));
    }

    @Test
    void myPostsCarryOwnIdentityMode() throws Exception {
        MinePostVO mine = new MinePostVO(9L, "ANONYMOUS", "ADVICE", "t", "b", List.of(1L),
                LocalDateTime.now(), false, 1L, 2L);
        when(userService.minePosts(7L)).thenReturn(List.of(mine));
        mockMvc.perform(get("/api/me/posts").with(MvcAuth.user(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].identityMode").value("ANONYMOUS"))
                .andExpect(jsonPath("$.data[0].authorId").doesNotExist());
    }

    @Test
    void bookmarksReturnPublicVo() throws Exception {
        when(userService.myBookmarks(7L)).thenReturn(List.of(new PublicPostVO(
                3L, AuthorView.anonymous(), "VENT", "t", "b", List.of(),
                LocalDateTime.now(), false, 0L, 0L)));
        mockMvc.perform(get("/api/me/bookmarks").with(MvcAuth.user(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].author.mode").value("anonymous"));
    }
}
