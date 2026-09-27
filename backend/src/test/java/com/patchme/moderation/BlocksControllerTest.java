package com.patchme.moderation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.patchme.moderation.vo.BlockVO;
import com.patchme.support.MvcAuth;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 拉黑切片：操作者身份只取 JWT；绑定校验与出口形状。 */
@WebMvcTest(BlocksController.class)
@AutoConfigureMockMvc(addFilters = false)
class BlocksControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BlockService blockService;

    @AfterEach
    void clearAuth() {
        MvcAuth.clear();
    }

    @Test
    void blockUsesJwtUserId() throws Exception {
        mockMvc.perform(post("/api/blocks").with(MvcAuth.user(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"xiaoman\"}"))
                .andExpect(status().isOk());
        verify(blockService).block(eq(7L), eq("xiaoman"));
    }

    @Test
    void blankUsernameIsRejected() throws Exception {
        mockMvc.perform(post("/api/blocks").with(MvcAuth.user(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("40000"));
    }

    @Test
    void unblockByPathUsername() throws Exception {
        mockMvc.perform(delete("/api/blocks/xiaoman").with(MvcAuth.user(7L)))
                .andExpect(status().isOk());
        verify(blockService).unblock(eq(7L), eq("xiaoman"));
    }

    @Test
    void mineListHasPublicShapeOnly() throws Exception {
        when(blockService.list(7L)).thenReturn(List.of(new BlockVO(9L, "xiaoman", "小满", null)));
        mockMvc.perform(get("/api/blocks/mine").with(MvcAuth.user(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].username").value("xiaoman"))
                // 拉黑列表只回公开身份三件套，不暴露 email 等内部字段
                .andExpect(jsonPath("$..email").doesNotExist())
                .andExpect(jsonPath("$..blockerId").doesNotExist());
    }
}
